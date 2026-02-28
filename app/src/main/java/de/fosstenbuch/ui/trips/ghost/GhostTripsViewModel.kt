package de.fosstenbuch.ui.trips.ghost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.fosstenbuch.domain.usecase.trip.GetGhostTripsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class GhostTripsViewModel @Inject constructor(
    private val getGhostTripsUseCase: GetGhostTripsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(GhostTripsUiState())
    val uiState: StateFlow<GhostTripsUiState> = _uiState.asStateFlow()

    init {
        loadGhostTrips()
    }

    private fun loadGhostTrips() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            getGhostTripsUseCase()
                .catch { e ->
                    Timber.e(e, "Failed to load ghost trips")
                    _uiState.update {
                        it.copy(isLoading = false, error = "Automatische Fahrten konnten nicht geladen werden")
                    }
                }
                .collect { trips ->
                    _uiState.update { it.copy(isLoading = false, ghostTrips = trips) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
