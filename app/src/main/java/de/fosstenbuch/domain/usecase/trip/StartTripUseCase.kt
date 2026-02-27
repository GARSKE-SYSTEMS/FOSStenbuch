package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.repository.TripRepository
import de.fosstenbuch.domain.validation.TripValidator
import de.fosstenbuch.domain.validation.ValidationResult
import javax.inject.Inject

/**
 * Starts a new trip by inserting a partially filled Trip (isActive=true).
 * Only start-phase fields are validated.
 */
class StartTripUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val tripValidator: TripValidator
) {
    sealed class Result {
        data class Success(val tripId: Long) : Result()
        data class ValidationError(val validation: ValidationResult) : Result()
        data class Error(val exception: Throwable) : Result()
    }

    suspend operator fun invoke(trip: Trip): Result {
        val validation = tripValidator.validateStart(trip)
        if (!validation.isValid) {
            return Result.ValidationError(validation)
        }

        return try {
            val activeTrip = trip.copy(isActive = true)
            val id = tripRepository.insertTrip(activeTrip)
            Result.Success(id)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
