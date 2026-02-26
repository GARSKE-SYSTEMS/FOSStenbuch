package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.repository.TripRepository
import de.fosstenbuch.domain.validation.TripValidator
import de.fosstenbuch.domain.validation.ValidationResult
import javax.inject.Inject

/**
 * Updates an existing trip after validation.
 */
class UpdateTripUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val tripValidator: TripValidator
) {
    sealed class Result {
        object Success : Result()
        data class ValidationError(val validation: ValidationResult) : Result()
        data class Error(val exception: Throwable) : Result()
    }

    suspend operator fun invoke(trip: Trip): Result {
        val validation = tripValidator.validate(trip)
        if (!validation.isValid) {
            return Result.ValidationError(validation)
        }

        return try {
            tripRepository.updateTrip(trip)
            Result.Success
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
