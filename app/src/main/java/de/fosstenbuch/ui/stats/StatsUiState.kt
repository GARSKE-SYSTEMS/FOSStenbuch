package de.fosstenbuch.ui.stats

import de.fosstenbuch.data.local.MonthlyDistance

/**
 * Filter mode for the statistics screen.
 */
enum class StatsFilterMode {
    YEAR, MONTH, CUSTOM
}

/**
 * UI state for the Statistics screen.
 */
data class StatsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val filterMode: StatsFilterMode = StatsFilterMode.YEAR,
    val selectedYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
    val selectedMonth: Int = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH), // 0-based
    val customDateFromMs: Long = 0L,
    val customDateToMs: Long = 0L,
    val totalDistanceKm: Double = 0.0,
    val businessDistanceKm: Double = 0.0,
    val privateDistanceKm: Double = 0.0,
    val tripCount: Int = 0,
    val monthlyDistances: List<MonthlyDistance> = emptyList()
) {
    val averageDistancePerTrip: Double
        get() = if (tripCount > 0) totalDistanceKm / tripCount else 0.0

    val businessPercentage: Double
        get() {
            val total = businessDistanceKm + privateDistanceKm
            return if (total > 0) (businessDistanceKm / total) * 100 else 0.0
        }

    val dateRangeMs: Pair<Long, Long>
        get() = when (filterMode) {
            StatsFilterMode.YEAR -> {
                val cal = java.util.Calendar.getInstance()
                cal.set(selectedYear, java.util.Calendar.JANUARY, 1, 0, 0, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(selectedYear, java.util.Calendar.DECEMBER, 31, 23, 59, 59)
                cal.set(java.util.Calendar.MILLISECOND, 999)
                start to cal.timeInMillis
            }
            StatsFilterMode.MONTH -> {
                val cal = java.util.Calendar.getInstance()
                cal.set(selectedYear, selectedMonth, 1, 0, 0, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(java.util.Calendar.DAY_OF_MONTH, cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
                cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
                cal.set(java.util.Calendar.MINUTE, 59)
                cal.set(java.util.Calendar.SECOND, 59)
                cal.set(java.util.Calendar.MILLISECOND, 999)
                start to cal.timeInMillis
            }
            StatsFilterMode.CUSTOM -> customDateFromMs to customDateToMs
        }
}
