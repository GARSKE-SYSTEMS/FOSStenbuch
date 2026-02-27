package de.fosstenbuch.ui.mileage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.fosstenbuch.domain.usecase.mileage.CalculateMileageAllowanceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class MileageCalculatorViewModel @Inject constructor(
    private val calculateMileageAllowance: CalculateMileageAllowanceUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MileageCalculatorUiState())
    val uiState: StateFlow<MileageCalculatorUiState> = _uiState.asStateFlow()

    init {
        loadStatsForYear(_uiState.value.selectedYear)
    }

    fun setYear(year: Int) {
        _uiState.update { it.copy(selectedYear = year, result = null) }
        loadStatsForYear(year)
    }

    fun setOneWayDistance(distance: String) {
        _uiState.update { it.copy(oneWayDistanceKm = distance, result = null) }
    }

    fun setWorkingDays(days: String) {
        _uiState.update { it.copy(workingDays = days, result = null) }
    }

    fun toggleStatsMode() {
        _uiState.update { it.copy(useStatsMode = !it.useStatsMode, result = null) }
    }

    fun calculate() {
        val state = _uiState.value
        val workingDays = state.workingDays.toIntOrNull()
            ?: CalculateMileageAllowanceUseCase.DEFAULT_WORKING_DAYS

        val result = if (state.useStatsMode) {
            val totalDistance = state.businessDistanceFromStats ?: 0.0
            calculateMileageAllowance.calculateFromTotalDistance(totalDistance, workingDays)
        } else {
            val oneWayKm = state.oneWayDistanceKm.toDoubleOrNull() ?: 0.0
            calculateMileageAllowance.calculate(oneWayKm, workingDays)
        }

        _uiState.update { it.copy(result = result) }
    }

    private fun loadStatsForYear(year: Int) {
        viewModelScope.launch {
            calculateMileageAllowance.getBusinessDistanceForYear(year).collect { distance ->
                _uiState.update { it.copy(businessDistanceFromStats = distance) }
            }
        }
        viewModelScope.launch {
            calculateMileageAllowance.getBusinessTripCountForYear(year).collect { count ->
                _uiState.update { it.copy(businessTripCount = count) }
            }
        }
    }
}
