package de.fosstenbuch.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.fosstenbuch.data.repository.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val tripRepository: TripRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState(
        customDateFromMs = getYearStartMs(Calendar.getInstance().get(Calendar.YEAR)),
        customDateToMs = System.currentTimeMillis()
    ))
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun setFilterMode(mode: StatsFilterMode) {
        _uiState.update { it.copy(filterMode = mode) }
        loadStats()
    }

    fun setYear(year: Int) {
        _uiState.update { it.copy(selectedYear = year) }
        loadStats()
    }

    fun setMonth(month: Int) {
        _uiState.update { it.copy(selectedMonth = month) }
        loadStats()
    }

    fun previousMonth() {
        val state = _uiState.value
        if (state.selectedMonth == 0) {
            _uiState.update { it.copy(selectedYear = it.selectedYear - 1, selectedMonth = 11) }
        } else {
            _uiState.update { it.copy(selectedMonth = it.selectedMonth - 1) }
        }
        loadStats()
    }

    fun nextMonth() {
        val state = _uiState.value
        if (state.selectedMonth == 11) {
            _uiState.update { it.copy(selectedYear = it.selectedYear + 1, selectedMonth = 0) }
        } else {
            _uiState.update { it.copy(selectedMonth = it.selectedMonth + 1) }
        }
        loadStats()
    }

    fun setCustomDateFrom(ms: Long) {
        _uiState.update { it.copy(customDateFromMs = ms) }
        loadStats()
    }

    fun setCustomDateTo(ms: Long) {
        _uiState.update { it.copy(customDateToMs = ms) }
        loadStats()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun loadStats() {
        _uiState.update { it.copy(isLoading = true, error = null) }

        val (startMs, endMs) = _uiState.value.dateRangeMs

        viewModelScope.launch {
            combine(
                tripRepository.getTotalDistanceByDateRange(startMs, endMs),
                tripRepository.getBusinessDistanceByDateRange(startMs, endMs),
                tripRepository.getPrivateDistanceByDateRange(startMs, endMs),
                tripRepository.getTripCountByDateRange(startMs, endMs)
            ) { totalDist, businessDist, privateDist, tripCount ->
                _uiState.value.copy(
                    isLoading = false,
                    totalDistanceKm = totalDist ?: 0.0,
                    businessDistanceKm = businessDist ?: 0.0,
                    privateDistanceKm = privateDist ?: 0.0,
                    tripCount = tripCount
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

    private fun getYearStartMs(year: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(year, Calendar.JANUARY, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
