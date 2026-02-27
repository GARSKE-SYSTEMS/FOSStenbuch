package de.fosstenbuch.ui.mileage

import de.fosstenbuch.domain.usecase.mileage.MileageResult

data class MileageCalculatorUiState(
    val selectedYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
    val oneWayDistanceKm: String = "",
    val workingDays: String = "230",
    val businessDistanceFromStats: Double? = null,
    val businessTripCount: Int = 0,
    val result: MileageResult? = null,
    val isLoading: Boolean = false,
    val useStatsMode: Boolean = false
)
