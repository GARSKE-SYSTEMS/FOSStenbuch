package de.fosstenbuch.domain.usecase.stats

import de.fosstenbuch.data.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Returns the total distance across all trips.
 */
class GetTotalDistanceUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    operator fun invoke(): Flow<Double> =
        tripRepository.getTotalDistance().map { it ?: 0.0 }
}
