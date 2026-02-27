package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.repository.TripRepository
import de.fosstenbuch.domain.backup.TripChainService
import de.fosstenbuch.domain.validation.TripValidator
import de.fosstenbuch.domain.validation.ValidationResult
import javax.inject.Inject

/**
 * Ends an active trip by completing all fields and saving.
 * Full validation is performed on the completed trip.
 * Automatically recomputes chain hashes for audit-protected vehicles.
 */
class EndTripUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val tripValidator: TripValidator,
    private val tripChainService: TripChainService
) {
    sealed class Result {
        object Success : Result()
        data class ValidationError(val validation: ValidationResult) : Result()
        data class Error(val exception: Throwable) : Result()
    }

    suspend operator fun invoke(trip: Trip): Result {
        val completedTrip = trip.copy(isActive = false)

        val validation = tripValidator.validate(completedTrip)
        if (!validation.isValid) {
            return Result.ValidationError(validation)
        }

        return try {
            tripRepository.updateTrip(completedTrip)
            tripChainService.updateChainHash(completedTrip.id)
            Result.Success
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
