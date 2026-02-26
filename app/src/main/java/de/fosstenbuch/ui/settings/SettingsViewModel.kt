package de.fosstenbuch.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.fosstenbuch.domain.usecase.location.GetAllSavedLocationsUseCase
import de.fosstenbuch.domain.usecase.purpose.GetAllPurposesUseCase
import de.fosstenbuch.domain.usecase.trip.GetAllTripsUseCase
import de.fosstenbuch.domain.usecase.vehicle.GetAllVehiclesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getAllTripsUseCase: GetAllTripsUseCase,
    private val getAllVehiclesUseCase: GetAllVehiclesUseCase,
    private val getAllSavedLocationsUseCase: GetAllSavedLocationsUseCase,
    private val getAllPurposesUseCase: GetAllPurposesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSummary()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun loadSummary() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            combine(
                getAllTripsUseCase(),
                getAllVehiclesUseCase(),
                getAllSavedLocationsUseCase(),
                getAllPurposesUseCase()
            ) { trips, vehicles, locations, purposes ->
                SettingsUiState(
                    isLoading = false,
                    tripCount = trips.size,
                    vehicleCount = vehicles.size,
                    locationCount = locations.size,
                    purposeCount = purposes.size
                )
            }
                .catch { e ->
                    Timber.e(e, "Failed to load settings summary")
                    _uiState.update {
                        it.copy(isLoading = false, error = "Daten konnten nicht geladen werden")
                    }
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }
}
