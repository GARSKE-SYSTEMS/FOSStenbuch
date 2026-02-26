package de.fosstenbuch.domain.usecase.stats

import de.fosstenbuch.data.local.MonthlyDistance
import de.fosstenbuch.data.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Returns a per-month distance summary for a given year.
 * Each entry contains the month (1-12) and the total distance driven that month.
 */
class GetMonthlyDistanceSummaryUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    operator fun invoke(year: Int): Flow<List<MonthlyDistance>> =
        tripRepository.getMonthlyDistanceSummary(year)
}
