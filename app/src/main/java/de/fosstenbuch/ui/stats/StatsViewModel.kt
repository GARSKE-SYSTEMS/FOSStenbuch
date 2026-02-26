package de.fosstenbuch.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.fosstenbuch.domain.usecase.stats.GetDistanceByTypeUseCase
import de.fosstenbuch.domain.usecase.stats.GetMonthlyDistanceSummaryUseCase
import de.fosstenbuch.domain.usecase.stats.GetTotalDistanceUseCase
import de.fosstenbuch.domain.usecase.stats.GetTripCountByPeriodUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val getTotalDistanceUseCase: GetTotalDistanceUseCase,
    private val getDistanceByTypeUseCase: GetDistanceByTypeUseCase,
    private val getTripCountByPeriodUseCase: GetTripCountByPeriodUseCase,
    private val getMonthlyDistanceSummaryUseCase: GetMonthlyDistanceSummaryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun setYear(year: Int) {
        _uiState.update { it.copy(selectedYear = year) }
        loadStats()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun loadStats() {
        val year = _uiState.value.selectedYear
        _uiState.update { it.copy(isLoading = true, error = null) }

        val (yearStart, yearEnd) = getYearRange(year)
        val (monthStart, monthEnd) = getCurrentMonthRange()

        viewModelScope.launch {
            combine(
                getTotalDistanceUseCase(),
                getDistanceByTypeUseCase(),
                getTripCountByPeriodUseCase(monthStart, monthEnd),
                getTripCountByPeriodUseCase(yearStart, yearEnd),
                getMonthlyDistanceSummaryUseCase(year)
            ) { totalDistance, distanceByType, monthCount, yearCount, monthlyDistances ->
                StatsUiState(
                    isLoading = false,
                    selectedYear = year,
                    totalDistanceKm = totalDistance,
                    distanceByType = distanceByType,
                    tripCountCurrentMonth = monthCount,
                    tripCountCurrentYear = yearCount,
                    monthlyDistances = monthlyDistances
                )
            }
                .catch { e ->
                    Timber.e(e, "Failed to load statistics")
                    _uiState.update {
                        it.copy(isLoading = false, error = "Statistiken konnten nicht geladen werden")
                    }
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    private fun getYearRange(year: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(year, Calendar.JANUARY, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(year, Calendar.DECEMBER, 31, 23, 59, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return start to end
    }

    private fun getCurrentMonthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return start to end
    }
}
