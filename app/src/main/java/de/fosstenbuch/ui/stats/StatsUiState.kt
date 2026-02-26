package de.fosstenbuch.ui.stats

import de.fosstenbuch.data.local.MonthlyDistance
import de.fosstenbuch.domain.usecase.stats.GetDistanceByTypeUseCase

/**
 * UI state for the Statistics screen.
 */
data class StatsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
    val totalDistanceKm: Double = 0.0,
    val distanceByType: GetDistanceByTypeUseCase.DistanceByType? = null,
    val tripCountCurrentMonth: Int = 0,
    val tripCountCurrentYear: Int = 0,
    val monthlyDistances: List<MonthlyDistance> = emptyList()
) {
    val averageDistancePerTrip: Double
        get() = if (tripCountCurrentYear > 0) totalDistanceKm / tripCountCurrentYear else 0.0
}
