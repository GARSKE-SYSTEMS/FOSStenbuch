package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.repository.TripRepository
import de.fosstenbuch.domain.backup.TripChainService
import de.fosstenbuch.domain.validation.TripValidator
import javax.inject.Inject

/**
 * Accepts a ghost trip by converting it to a regular trip entry.
 * Applies validation and, for audit-protected vehicles, recomputes the chain hash.
 */
class AcceptGhostTripUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val tripValidator: TripValidator,
    private val tripChainService: TripChainService
) {
    sealed class Result {
        object Success : Result()
        data class ValidationError(val validation: de.fosstenbuch.domain.validation.ValidationResult) : Result()
        data class Error(val exception: Throwable) : Result()
    }

    suspend operator fun invoke(trip: Trip): Result {
        val accepted = trip.copy(isGhost = false, isActive = false)
        val validation = tripValidator.validate(accepted)
        if (!validation.isValid) {
            return Result.ValidationError(validation)
        }

        return try {
            tripRepository.updateTrip(accepted)
            tripChainService.updateChainHash(accepted.id)
            Result.Success
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
