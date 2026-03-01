package de.fosstenbuch.ui.trips.ghost

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.repository.TripRepository
import de.fosstenbuch.domain.usecase.purpose.GetAllPurposesUseCase
import kotlin.math.roundToInt
import de.fosstenbuch.domain.usecase.trip.AcceptGhostTripUseCase
import de.fosstenbuch.domain.usecase.trip.DeleteTripUseCase
import de.fosstenbuch.domain.usecase.trip.GetTripByIdUseCase
import de.fosstenbuch.domain.usecase.vehicle.GetAllVehiclesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class GhostTripDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getTripByIdUseCase: GetTripByIdUseCase,
    private val acceptGhostTripUseCase: AcceptGhostTripUseCase,
    private val deleteTripUseCase: DeleteTripUseCase,
    private val getAllVehiclesUseCase: GetAllVehiclesUseCase,
    private val getAllPurposesUseCase: GetAllPurposesUseCase,
    private val tripRepository: TripRepository
) : ViewModel() {

    private val tripId: Long = savedStateHandle.get<Long>("tripId") ?: 0L

    private val _uiState = MutableStateFlow(GhostTripDetailUiState())
    val uiState: StateFlow<GhostTripDetailUiState> = _uiState.asStateFlow()

    init {
        loadTrip()
        loadVehicles()
        loadPurposes()
    }

    private fun loadTrip() {
        viewModelScope.launch {
            getTripByIdUseCase(tripId)
                .catch { e ->
                    Timber.e(e, "Failed to load ghost trip $tripId")
                    _uiState.update { it.copy(isLoading = false, error = "Fahrt konnte nicht geladen werden") }
                }
                .collect { trip ->
                    _uiState.update { it.copy(isLoading = false, trip = trip) }
                    trip?.vehicleId?.let { loadLastEndOdometer(it) }
                }
        }
    }

    private fun loadVehicles() {
        viewModelScope.launch {
            getAllVehiclesUseCase()
                .catch { e -> Timber.e(e, "Failed to load vehicles") }
                .collect { vehicles -> _uiState.update { it.copy(vehicles = vehicles) } }
        }
    }

    private fun loadPurposes() {
        viewModelScope.launch {
            getAllPurposesUseCase()
                .catch { e -> Timber.e(e, "Failed to load purposes") }
                .collect { purposes -> _uiState.update { it.copy(purposes = purposes) } }
        }
    }

    /**
     * Accepts the ghost trip with the user-provided fields merged in.
     * On success, the trip is converted to a regular trip.
     */
    fun accept(updatedTrip: Trip) {
        _uiState.update { it.copy(isSaving = true, error = null, validationErrors = emptyMap()) }
        viewModelScope.launch {
            when (val result = acceptGhostTripUseCase(updatedTrip)) {
                is AcceptGhostTripUseCase.Result.Success -> {
                    _uiState.update { it.copy(isSaving = false, acceptedSuccessfully = true) }
                }
                is AcceptGhostTripUseCase.Result.ValidationError -> {
                    val errors = result.validation.errors
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            validationErrors = errors
                        )
                    }
                }
                is AcceptGhostTripUseCase.Result.Error -> {
                    Timber.e(result.exception, "Failed to accept ghost trip")
                    _uiState.update {
                        it.copy(isSaving = false, error = "Fahrt konnte nicht übernommen werden")
                    }
                }
            }
        }
    }

    /** Permanently deletes the ghost trip (discard). */
    fun discard() {
        val trip = _uiState.value.trip ?: return
        viewModelScope.launch {
            when (deleteTripUseCase(trip)) {
                is DeleteTripUseCase.Result.Success ->
                    _uiState.update { it.copy(discardedSuccessfully = true) }
                is DeleteTripUseCase.Result.AuditProtected ->
                    _uiState.update { it.copy(error = "Diese Fahrt kann nicht verworfen werden (änderungssicher)") }
                is DeleteTripUseCase.Result.Error ->
                    _uiState.update { it.copy(error = "Fahrt konnte nicht verworfen werden") }
            }
        }
    }

    private fun loadLastEndOdometer(vehicleId: Long) {
        viewModelScope.launch {
            val lastOdometer = tripRepository.getLastEndOdometerForVehicle(vehicleId)
            _uiState.update { it.copy(lastEndOdometer = lastOdometer) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun onAcceptConsumed() {
        _uiState.update { it.copy(acceptedSuccessfully = false) }
    }

    fun onDiscardConsumed() {
        _uiState.update { it.copy(discardedSuccessfully = false) }
    }
}
