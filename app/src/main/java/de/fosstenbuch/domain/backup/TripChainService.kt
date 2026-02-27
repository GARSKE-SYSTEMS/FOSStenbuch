package de.fosstenbuch.domain.backup

import de.fosstenbuch.data.repository.TripRepository
import de.fosstenbuch.data.repository.VehicleRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Manages the linked hash chain for trips of audit-protected vehicles.
 *
 * After any trip is inserted or updated, call [updateChainHash] to
 * recompute the chain from that trip onward. This ensures all subsequent
 * chain hashes remain consistent.
 */
class TripChainService @Inject constructor(
    private val tripRepository: TripRepository,
    private val vehicleRepository: VehicleRepository,
    private val chainHashCalculator: TripChainHashCalculator
) {

    /**
     * Recomputes and persists chain hashes for the entire vehicle chain
     * that the given trip belongs to.
     *
     * No-op if the trip's vehicle is not audit-protected or if [vehicleId] is null.
     *
     * @param tripId The ID of the trip that was inserted or updated.
     */
    suspend fun updateChainHash(tripId: Long) {
        val trip = tripRepository.getTripById(tripId).first() ?: return
        val vehicleId = trip.vehicleId ?: return
        val vehicle = vehicleRepository.getVehicleById(vehicleId).first() ?: return
        if (!vehicle.auditProtected) return

        recomputeFullChain(vehicleId)
    }

    /**
     * Recomputes and persists chain hashes for ALL trips of the given vehicle.
     *
     * This walks all trips in insertion order, computing each chain hash
     * from the previous one, and updates any that differ from the stored value.
     */
    suspend fun recomputeFullChain(vehicleId: Long) {
        val trips = tripRepository.getTripsForVehicleOrdered(vehicleId)
        if (trips.isEmpty()) return

        val newHashes = chainHashCalculator.computeChainHashes(trips)

        for ((tripId, newHash) in newHashes) {
            val existingTrip = trips.find { it.id == tripId }
            if (existingTrip?.chainHash != newHash) {
                tripRepository.updateTripChainHash(tripId, newHash)
            }
        }
    }
}
