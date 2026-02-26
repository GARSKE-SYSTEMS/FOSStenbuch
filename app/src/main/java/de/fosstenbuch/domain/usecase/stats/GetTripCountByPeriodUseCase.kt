package de.fosstenbuch.domain.usecase.stats

import de.fosstenbuch.data.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Returns the trip count within a given date range (timestamps in millis).
 */
class GetTripCountByPeriodUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    operator fun invoke(startDate: Long, endDate: Long): Flow<Int> =
        tripRepository.getTripCountByDateRange(startDate, endDate)
}
