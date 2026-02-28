package de.fosstenbuch.ui.trips.ghost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.domain.usecase.trip.DeleteTripUseCase
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
    private val getGhostTripsUseCase: GetGhostTripsUseCase,
    private val deleteTripUseCase: DeleteTripUseCase
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

    /**
     * Deletes a ghost trip. Only valid while the trip is still a ghost (isGhost = true).
     * After acceptance the trip is a regular trip and must be managed via the trips screen.
     */
    fun deleteGhostTrip(trip: Trip) {
        if (!trip.isGhost) return
        viewModelScope.launch {
            when (val result = deleteTripUseCase(trip)) {
                is DeleteTripUseCase.Result.Success -> { /* list updates automatically via Flow */ }
                is DeleteTripUseCase.Result.AuditProtected ->
                    _uiState.update { it.copy(error = "Fahrzeug ist änderungssicher – Fahrt kann nicht gelöscht werden") }
                is DeleteTripUseCase.Result.Error -> {
                    Timber.e(result.exception, "Failed to delete ghost trip")
                    _uiState.update { it.copy(error = "Fahrt konnte nicht gelöscht werden") }
                }
            }
        }
    }
}
