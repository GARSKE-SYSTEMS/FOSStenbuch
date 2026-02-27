package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Retrieves the currently active (in-progress) trip, if any.
 */
class GetActiveTripUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    operator fun invoke(): Flow<Trip?> = tripRepository.getActiveTrip()
}
