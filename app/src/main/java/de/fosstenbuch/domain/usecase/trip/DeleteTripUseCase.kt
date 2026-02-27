package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.repository.TripRepository
import de.fosstenbuch.data.repository.VehicleRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Deletes a trip after checking audit protection.
 * Trips belonging to audit-protected vehicles cannot be deleted.
 */
class DeleteTripUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val vehicleRepository: VehicleRepository
) {
    sealed class Result {
        object Success : Result()
        object AuditProtected : Result()
        data class Error(val exception: Throwable) : Result()
    }

    suspend operator fun invoke(trip: Trip): Result {
        // Check if the trip's vehicle is audit-protected
        val vehicleId = trip.vehicleId
        if (vehicleId != null) {
            val vehicle = vehicleRepository.getVehicleById(vehicleId).first()
            if (vehicle?.auditProtected == true) {
                return Result.AuditProtected
            }
        }

        return try {
            tripRepository.deleteTrip(trip)
            Result.Success
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
