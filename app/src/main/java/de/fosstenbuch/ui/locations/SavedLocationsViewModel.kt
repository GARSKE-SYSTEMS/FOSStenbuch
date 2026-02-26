package de.fosstenbuch.ui.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.fosstenbuch.data.model.SavedLocation
import de.fosstenbuch.domain.usecase.location.DeleteSavedLocationUseCase
import de.fosstenbuch.domain.usecase.location.GetAllSavedLocationsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SavedLocationsViewModel @Inject constructor(
    private val getAllSavedLocationsUseCase: GetAllSavedLocationsUseCase,
    private val deleteSavedLocationUseCase: DeleteSavedLocationUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedLocationsUiState())
    val uiState: StateFlow<SavedLocationsUiState> = _uiState.asStateFlow()

    init {
        loadLocations()
    }

    fun deleteLocation(location: SavedLocation) {
        viewModelScope.launch {
            try {
                deleteSavedLocationUseCase(location)
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete location")
                _uiState.update { it.copy(error = "Ort konnte nicht gelöscht werden") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun loadLocations() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            getAllSavedLocationsUseCase()
                .catch { e ->
                    Timber.e(e, "Failed to load saved locations")
                    _uiState.update {
                        it.copy(isLoading = false, error = "Orte konnten nicht geladen werden")
                    }
                }
                .collect { locations ->
                    _uiState.update {
                        it.copy(isLoading = false, locations = locations)
                    }
                }
        }
    }
}
