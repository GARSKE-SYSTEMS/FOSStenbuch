package de.fosstenbuch.domain.backup

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.model.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * Tests the chain hash verification logic that is applied during backup import.
 *
 * Verifies that:
 * - Trips with valid chain hashes are accepted
 * - Trips with missing (null) chain hashes for audit-protected vehicles are rejected
 * - Trips with tampered chain hashes are rejected
 * - Non-audit-protected vehicles are not subject to chain verification
 */
class BackupChainHashVerificationTest {

    private lateinit var calculator: TripChainHashCalculator

    private val auditVehicle = Vehicle(
        id = 10L,
        make = "BMW",
        model = "320d",
        licensePlate = "B AB 1234",
        fuelType = "Diesel",
        auditProtected = true
    )

    private val normalVehicle = Vehicle(
        id = 20L,
        make = "VW",
        model = "Golf",
        licensePlate = "M XY 5678",
        fuelType = "Benzin",
        auditProtected = false
    )

    @Before
    fun setup() {
        calculator = TripChainHashCalculator()
    }

    private fun trip(
        id: Long,
        vehicleId: Long,
        startLocation: String = "Berlin",
        endLocation: String = "Hamburg",
        distanceKm: Double = 280.0,
        endTime: Date? = Date(1700000000000L + 3600_000),
        gpsDistanceKm: Double? = null,
        businessPartner: String? = null,
        route: String? = null,
        chainHash: String? = null
    ) = Trip(
        id = id,
        date = Date(1700000000000L),
        startLocation = startLocation,
        endLocation = endLocation,
        distanceKm = distanceKm,
        purpose = "Kundentermin",
        purposeId = 1L,
        vehicleId = vehicleId,
        startOdometer = 50000,
        endOdometer = 50280,
        endTime = endTime,
        gpsDistanceKm = gpsDistanceKm,
        businessPartner = businessPartner,
        route = route,
        chainHash = chainHash
    )

    /**
     * Builds a list of trips with correctly computed chain hashes for a vehicle.
     */
    private fun buildValidChain(
        vehicleId: Long,
        count: Int,
        customizer: ((Int, Trip) -> Trip)? = null
    ): List<Trip> {
        val trips = mutableListOf<Trip>()
        var previousHash: String? = null
        for (i in 1..count) {
            var t = trip(
                id = i.toLong(),
                vehicleId = vehicleId,
                startLocation = "Stadt $i",
                endLocation = "Stadt ${i + 1}",
                distanceKm = 100.0 * i
            )
            if (customizer != null) {
                t = customizer(i, t)
            }
            val hash = calculator.computeChainHash(t, previousHash)
            t = t.copy(chainHash = hash)
            trips.add(t)
            previousHash = hash
        }
        return trips
    }

    // ==================== Verification logic (mirrors BackupManager.importBackup) ====================

    /**
     * Runs the same verification logic as BackupManager.importBackup()
     * against the given vehicles and trips.
     */
    private fun verifyImport(vehicles: List<Vehicle>, trips: List<Trip>) {
        val auditProtectedVehicleIds = vehicles
            .filter { it.auditProtected }
            .map { it.id }
            .toSet()

        for (vehicleId in auditProtectedVehicleIds) {
            val vehicleTrips = trips
                .filter { it.vehicleId == vehicleId }
                .sortedBy { it.id }

            if (vehicleTrips.isNotEmpty()) {
                // All trips of audit-protected vehicles must have chain hashes
                if (vehicleTrips.any { it.chainHash == null }) {
                    val vehicleName = vehicles.find { it.id == vehicleId }
                        ?.let { "${it.make} ${it.model} (${it.licensePlate})" }
                        ?: "ID $vehicleId"
                    throw IntegrityViolationException(
                        "Fehlende Hash-Kette für änderungssicheres Fahrzeug $vehicleName. " +
                            "Alle Fahrten müssen verifizierbare Chain-Hashes besitzen."
                    )
                }

                val result = calculator.verifyChain(vehicleTrips)
                if (result is TripChainHashCalculator.ChainVerificationResult.Broken) {
                    val vehicleName = vehicles.find { it.id == vehicleId }
                        ?.let { "${it.make} ${it.model} (${it.licensePlate})" }
                        ?: "ID $vehicleId"
                    throw IntegrityViolationException(
                        "Hash-Kette für Fahrzeug $vehicleName ist beschädigt bei Fahrt #${result.trip.id}. " +
                            "Die Backup-Daten wurden möglicherweise manipuliert."
                    )
                }
            }
        }
    }

    // ==================== Tests: Valid imports ====================

    @Test
    fun `valid chain hashes are accepted for audit-protected vehicle`() {
        val trips = buildValidChain(auditVehicle.id, 5)
        verifyImport(listOf(auditVehicle), trips) // should not throw
    }

    @Test
    fun `single trip with valid chain hash is accepted`() {
        val trips = buildValidChain(auditVehicle.id, 1)
        verifyImport(listOf(auditVehicle), trips)
    }

    @Test
    fun `audit-protected vehicle with no trips is accepted`() {
        verifyImport(listOf(auditVehicle), emptyList())
    }

    @Test
    fun `normal vehicle trips without hashes are accepted`() {
        val trips = listOf(
            trip(1L, normalVehicle.id, chainHash = null),
            trip(2L, normalVehicle.id, chainHash = null),
            trip(3L, normalVehicle.id, chainHash = null)
        )
        verifyImport(listOf(normalVehicle), trips) // should not throw
    }

    @Test
    fun `mixed vehicles - audit with valid hashes and normal without hashes`() {
        val auditTrips = buildValidChain(auditVehicle.id, 3)
        val normalTrips = listOf(
            trip(10L, normalVehicle.id, chainHash = null),
            trip(11L, normalVehicle.id, chainHash = null)
        )
        verifyImport(listOf(auditVehicle, normalVehicle), auditTrips + normalTrips)
    }

    // ==================== Tests: Rejected imports — null hashes ====================

    @Test
    fun `all null chain hashes rejected for audit-protected vehicle`() {
        val trips = listOf(
            trip(1L, auditVehicle.id, chainHash = null),
            trip(2L, auditVehicle.id, chainHash = null),
            trip(3L, auditVehicle.id, chainHash = null)
        )

        val exception = assertThrows(IntegrityViolationException::class.java) {
            verifyImport(listOf(auditVehicle), trips)
        }
        assertTrue(exception.message!!.contains("Fehlende Hash-Kette"))
        assertTrue(exception.message!!.contains(auditVehicle.licensePlate))
    }

    @Test
    fun `single null chain hash among valid ones rejected for audit-protected vehicle`() {
        val validTrips = buildValidChain(auditVehicle.id, 3)
        // Remove the chain hash from the middle trip
        val tamperedTrips = validTrips.mapIndexed { index, trip ->
            if (index == 1) trip.copy(chainHash = null) else trip
        }

        val exception = assertThrows(IntegrityViolationException::class.java) {
            verifyImport(listOf(auditVehicle), tamperedTrips)
        }
        assertTrue(exception.message!!.contains("Fehlende Hash-Kette"))
    }

    @Test
    fun `first trip with null hash rejected even if rest is valid`() {
        val validTrips = buildValidChain(auditVehicle.id, 3)
        val tamperedTrips = listOf(validTrips[0].copy(chainHash = null)) + validTrips.drop(1)

        val exception = assertThrows(IntegrityViolationException::class.java) {
            verifyImport(listOf(auditVehicle), tamperedTrips)
        }
        assertTrue(exception.message!!.contains("Fehlende Hash-Kette"))
    }

    @Test
    fun `last trip with null hash rejected even if rest is valid`() {
        val validTrips = buildValidChain(auditVehicle.id, 3)
        val tamperedTrips = validTrips.dropLast(1) + validTrips.last().copy(chainHash = null)

        val exception = assertThrows(IntegrityViolationException::class.java) {
            verifyImport(listOf(auditVehicle), tamperedTrips)
        }
        assertTrue(exception.message!!.contains("Fehlende Hash-Kette"))
    }

    // ==================== Tests: Rejected imports — tampered hashes ====================

    @Test
    fun `tampered first trip hash rejected`() {
        val validTrips = buildValidChain(auditVehicle.id, 3)
        val tamperedTrips = listOf(
            validTrips[0].copy(chainHash = "0000000000000000000000000000000000000000000000000000000000000000")
        ) + validTrips.drop(1)

        val exception = assertThrows(IntegrityViolationException::class.java) {
            verifyImport(listOf(auditVehicle), tamperedTrips)
        }
        assertTrue(exception.message!!.contains("beschädigt"))
    }

    @Test
    fun `tampered middle trip data detected through broken chain`() {
        val validTrips = buildValidChain(auditVehicle.id, 5)
        // Modify middle trip data but keep its original hash
        val tamperedTrips = validTrips.mapIndexed { index, trip ->
            if (index == 2) trip.copy(distanceKm = 999.0) else trip
        }

        val exception = assertThrows(IntegrityViolationException::class.java) {
            verifyImport(listOf(auditVehicle), tamperedTrips)
        }
        assertTrue(exception.message!!.contains("beschädigt"))
        assertTrue(exception.message!!.contains("#${validTrips[2].id}"))
    }

    @Test
    fun `swapped trip order detected`() {
        val validTrips = buildValidChain(auditVehicle.id, 3)
        // Swap trips 1 and 2 (but sort by id should restore order, so swap ids too)
        val swapped = listOf(
            validTrips[0],
            validTrips[2].copy(id = 2L),
            validTrips[1].copy(id = 3L)
        )

        val exception = assertThrows(IntegrityViolationException::class.java) {
            verifyImport(listOf(auditVehicle), swapped)
        }
        assertTrue(exception.message!!.contains("beschädigt"))
    }

    @Test
    fun `removed trip from chain detected`() {
        val validTrips = buildValidChain(auditVehicle.id, 5)
        // Remove the third trip
        val withRemoved = validTrips.filterIndexed { index, _ -> index != 2 }

        val exception = assertThrows(IntegrityViolationException::class.java) {
            verifyImport(listOf(auditVehicle), withRemoved)
        }
        assertTrue(exception.message!!.contains("beschädigt"))
    }

    @Test
    fun `inserted extra trip in chain detected`() {
        val validTrips = buildValidChain(auditVehicle.id, 3)
        // Insert a foreign trip with a made-up hash between trip 1 and 2
        val extraTrip = trip(
            id = 2L,
            vehicleId = auditVehicle.id,
            startLocation = "Injected",
            chainHash = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
        )
        val withInserted = listOf(validTrips[0], extraTrip) +
            validTrips.drop(1).map { it.copy(id = it.id + 1) }

        val exception = assertThrows(IntegrityViolationException::class.java) {
            verifyImport(listOf(auditVehicle), withInserted)
        }
        assertTrue(exception.message!!.contains("beschädigt"))
    }

    // ==================== Tests: Export/Import field roundtrip ====================

    @Test
    fun `chain hash includes endTime - changing endTime breaks chain`() {
        val tripsWithEndTime = buildValidChain(auditVehicle.id, 2) { _, trip ->
            trip.copy(endTime = Date(1700003600000L))
        }
        // Simulate import where endTime is altered
        val tampered = tripsWithEndTime.mapIndexed { index, trip ->
            if (index == 0) trip.copy(endTime = Date(1700099999000L)) else trip
        }

        val exception = assertThrows(IntegrityViolationException::class.java) {
            verifyImport(listOf(auditVehicle), tampered)
        }
        assertTrue(exception.message!!.contains("beschädigt"))
    }

    @Test
    fun `chain hash includes gpsDistanceKm - changing it breaks chain`() {
        val trips = buildValidChain(auditVehicle.id, 2) { _, trip ->
            trip.copy(gpsDistanceKm = 15.3)
        }
        val tampered = listOf(trips[0].copy(gpsDistanceKm = 20.0)) + trips.drop(1)

        val exception = assertThrows(IntegrityViolationException::class.java) {
            verifyImport(listOf(auditVehicle), tampered)
        }
        assertTrue(exception.message!!.contains("beschädigt"))
    }

    @Test
    fun `chain hash includes businessPartner - changing it breaks chain`() {
        val trips = buildValidChain(auditVehicle.id, 2) { _, trip ->
            trip.copy(businessPartner = "ACME Corp")
        }
        val tampered = listOf(trips[0].copy(businessPartner = "Evil Corp")) + trips.drop(1)

        val exception = assertThrows(IntegrityViolationException::class.java) {
            verifyImport(listOf(auditVehicle), tampered)
        }
        assertTrue(exception.message!!.contains("beschädigt"))
    }

    @Test
    fun `chain hash includes route - changing it breaks chain`() {
        val trips = buildValidChain(auditVehicle.id, 2) { _, trip ->
            trip.copy(route = "A1/A3")
        }
        val tampered = listOf(trips[0].copy(route = "B2")) + trips.drop(1)

        val exception = assertThrows(IntegrityViolationException::class.java) {
            verifyImport(listOf(auditVehicle), tampered)
        }
        assertTrue(exception.message!!.contains("beschädigt"))
    }

    @Test
    fun `export-import roundtrip with all fields preserves valid chain`() {
        // Simulate a trip with ALL fields populated (as would be exported and re-imported)
        val trips = buildValidChain(auditVehicle.id, 3) { i, trip ->
            trip.copy(
                endTime = Date(1700000000000L + i * 3600_000L),
                gpsDistanceKm = 10.0 + i,
                businessPartner = "Partner $i",
                route = "Route $i"
            )
        }

        // This should pass because the chain was built WITH these fields
        verifyImport(listOf(auditVehicle), trips)
    }

    @Test
    fun `large chain with 50 trips validates correctly`() {
        val trips = buildValidChain(auditVehicle.id, 50)
        verifyImport(listOf(auditVehicle), trips)
    }

    @Test
    fun `large chain with 50 trips detects tampered trip in middle`() {
        val trips = buildValidChain(auditVehicle.id, 50)
        val tampered = trips.mapIndexed { index, trip ->
            if (index == 25) trip.copy(distanceKm = 0.0) else trip
        }

        val exception = assertThrows(IntegrityViolationException::class.java) {
            verifyImport(listOf(auditVehicle), tampered)
        }
        assertTrue(exception.message!!.contains("#${trips[25].id}"))
    }

    // ==================== Tests: Error message quality ====================

    @Test
    fun `error message for missing hashes contains vehicle name`() {
        val trips = listOf(trip(1L, auditVehicle.id, chainHash = null))

        val exception = assertThrows(IntegrityViolationException::class.java) {
            verifyImport(listOf(auditVehicle), trips)
        }
        assertTrue(exception.message!!.contains("BMW"))
        assertTrue(exception.message!!.contains("320d"))
        assertTrue(exception.message!!.contains("B AB 1234"))
    }

    @Test
    fun `error message for broken chain contains vehicle name and trip id`() {
        val validTrips = buildValidChain(auditVehicle.id, 3)
        val tampered = listOf(validTrips[0].copy(distanceKm = 0.0)) + validTrips.drop(1)

        val exception = assertThrows(IntegrityViolationException::class.java) {
            verifyImport(listOf(auditVehicle), tampered)
        }
        assertTrue(exception.message!!.contains("BMW"))
        assertTrue(exception.message!!.contains("#1"))
    }
}
