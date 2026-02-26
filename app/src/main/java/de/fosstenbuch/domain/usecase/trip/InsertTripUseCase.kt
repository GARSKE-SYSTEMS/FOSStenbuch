package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.repository.TripRepository
import de.fosstenbuch.domain.validation.TripValidator
import de.fosstenbuch.domain.validation.ValidationResult
import javax.inject.Inject

/**
 * Inserts a new trip after validation.
 * Returns a [Result] containing either the new trip ID or a validation error.
 */
class InsertTripUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val tripValidator: TripValidator
) {
    sealed class Result {
        data class Success(val tripId: Long) : Result()
        data class ValidationError(val validation: ValidationResult) : Result()
        data class Error(val exception: Throwable) : Result()
    }

    suspend operator fun invoke(trip: Trip): Result {
        val validation = tripValidator.validate(trip)
        if (!validation.isValid) {
            return Result.ValidationError(validation)
        }

        return try {
            val id = tripRepository.insertTrip(trip)
            Result.Success(id)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
