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
        // Ghost trips can always be deleted – audit protection only applies to accepted trips
        val vehicleId = trip.vehicleId
        if (!trip.isGhost && vehicleId != null) {
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
