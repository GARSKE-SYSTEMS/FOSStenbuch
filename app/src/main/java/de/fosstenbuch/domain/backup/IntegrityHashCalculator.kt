package de.fosstenbuch.domain.backup

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.model.TripAuditLog
import de.fosstenbuch.data.model.Vehicle
import java.security.MessageDigest
import javax.inject.Inject

/**
 * Calculates SHA-256 integrity hashes for audit-protected vehicle data.
 *
 * When a vehicle has [Vehicle.auditProtected] = true, all its trips and
 * associated audit logs are hashed into a single deterministic digest.
 * This hash is stored in the JSON backup so that tampering can be detected
 * on import.
 *
 * The canonical format uses a pipe-delimited representation sorted by ID
 * to guarantee determinism regardless of insertion order.
 */
class IntegrityHashCalculator @Inject constructor() {

    /**
     * Computes integrity hashes for every audit-protected vehicle.
     *
     * @param vehicles All vehicles (only audit-protected ones are hashed)
     * @param trips All trips in the backup
     * @param auditLogs All audit log entries in the backup
     * @return Map of vehicleId → SHA-256 hex string
     */
    fun computeHashes(
        vehicles: List<Vehicle>,
        trips: List<Trip>,
        auditLogs: List<TripAuditLog>
    ): Map<Long, String> {
        val auditProtectedVehicles = vehicles.filter { it.auditProtected }

        if (auditProtectedVehicles.isEmpty()) return emptyMap()

        val tripsByVehicle = trips.groupBy { it.vehicleId }
        val auditLogsByTrip = auditLogs.groupBy { it.tripId }

        return auditProtectedVehicles.associate { vehicle ->
            val vehicleTrips = (tripsByVehicle[vehicle.id] ?: emptyList())
                .sortedBy { it.id }
            val hash = computeVehicleHash(vehicle, vehicleTrips, auditLogsByTrip)
            vehicle.id to hash
        }
    }

    /**
     * Computes the SHA-256 hash for a single audit-protected vehicle and its trips.
     */
    private fun computeVehicleHash(
        vehicle: Vehicle,
        trips: List<Trip>,
        auditLogsByTrip: Map<Long, List<TripAuditLog>>
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")

        // Hash vehicle identity
        digest.update(canonicalVehicle(vehicle))

        // Hash each trip in deterministic order (sorted by ID)
        for (trip in trips) {
            digest.update(canonicalTrip(trip))

            // Hash associated audit logs (sorted by ID)
            val logs = (auditLogsByTrip[trip.id] ?: emptyList()).sortedBy { it.id }
            for (log in logs) {
                digest.update(canonicalAuditLog(log))
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Canonical byte representation of a vehicle.
     * Format: "V|id|make|model|licensePlate|fuelType|auditProtected"
     */
    private fun canonicalVehicle(v: Vehicle): ByteArray {
        return "V|${v.id}|${v.make}|${v.model}|${v.licensePlate}|${v.fuelType}|${v.auditProtected}\n"
            .toByteArray(Charsets.UTF_8)
    }

    /**
     * Canonical byte representation of a trip.
     * Format: "T|id|date|startLocation|endLocation|distanceKm|purpose|purposeId|
     *          startOdometer|endOdometer|vehicleId|isCancelled|cancellationReason|endTime"
     */
    private fun canonicalTrip(t: Trip): ByteArray {
        return buildString {
            append("T|")
            append("${t.id}|")
            append("${t.date.time}|")
            append("${t.startLocation}|")
            append("${t.endLocation}|")
            append("${t.distanceKm}|")
            append("${t.purpose}|")
            append("${t.purposeId ?: "null"}|")
            append("${t.notes ?: "null"}|")
            append("${t.startOdometer ?: "null"}|")
            append("${t.endOdometer ?: "null"}|")
            append("${t.vehicleId ?: "null"}|")
            append("${t.isCancelled}|")
            append("${t.cancellationReason ?: "null"}|")
            append("${t.endTime?.time ?: "null"}|")
            append("${t.businessPartner ?: "null"}|")
            append("${t.route ?: "null"}")
            append("\n")
        }.toByteArray(Charsets.UTF_8)
    }

    /**
     * Canonical byte representation of an audit log entry.
     * Format: "A|id|tripId|fieldName|oldValue|newValue|changedAt"
     */
    private fun canonicalAuditLog(l: TripAuditLog): ByteArray {
        return "A|${l.id}|${l.tripId}|${l.fieldName}|${l.oldValue ?: "null"}|${l.newValue ?: "null"}|${l.changedAt.time}\n"
            .toByteArray(Charsets.UTF_8)
    }

    /**
     * Verifies that the integrity hashes in the backup match the actual data.
     *
     * @return A [VerificationResult] indicating success or detailing which vehicles failed.
     */
    fun verifyHashes(
        storedHashes: Map<Long, String>,
        vehicles: List<Vehicle>,
        trips: List<Trip>,
        auditLogs: List<TripAuditLog>
    ): VerificationResult {
        if (storedHashes.isEmpty()) {
            // No hashes stored — nothing to verify (old backup format)
            return VerificationResult.Success
        }

        val recomputed = computeHashes(vehicles, trips, auditLogs)
        val tamperedVehicles = mutableListOf<Vehicle>()

        for ((vehicleId, expectedHash) in storedHashes) {
            val actualHash = recomputed[vehicleId]
            if (actualHash == null || actualHash != expectedHash) {
                val vehicle = vehicles.find { it.id == vehicleId }
                if (vehicle != null) {
                    tamperedVehicles.add(vehicle)
                }
            }
        }

        return if (tamperedVehicles.isEmpty()) {
            VerificationResult.Success
        } else {
            VerificationResult.TamperingDetected(tamperedVehicles)
        }
    }

    sealed class VerificationResult {
        data object Success : VerificationResult()
        data class TamperingDetected(val affectedVehicles: List<Vehicle>) : VerificationResult()
    }
}
