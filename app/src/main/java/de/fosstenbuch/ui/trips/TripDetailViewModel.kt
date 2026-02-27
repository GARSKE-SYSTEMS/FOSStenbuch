package de.fosstenbuch.ui.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.repository.TripRepository
import de.fosstenbuch.domain.usecase.purpose.GetAllPurposesUseCase
import de.fosstenbuch.domain.usecase.trip.EndTripUseCase
import de.fosstenbuch.domain.usecase.trip.GetTripByIdUseCase
import de.fosstenbuch.domain.usecase.trip.InsertTripUseCase
import de.fosstenbuch.domain.usecase.trip.StartTripUseCase
import de.fosstenbuch.domain.usecase.trip.UpdateTripUseCase
import de.fosstenbuch.domain.usecase.vehicle.GetAllVehiclesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getTripByIdUseCase: GetTripByIdUseCase,
    private val insertTripUseCase: InsertTripUseCase,
    private val updateTripUseCase: UpdateTripUseCase,
    private val startTripUseCase: StartTripUseCase,
    private val endTripUseCase: EndTripUseCase,
    private val getAllVehiclesUseCase: GetAllVehiclesUseCase,
    private val getAllPurposesUseCase: GetAllPurposesUseCase,
    private val tripRepository: TripRepository
) : ViewModel() {

    private val tripId: Long? = savedStateHandle.get<Long>("tripId")?.takeIf { it > 0 }

    private val _uiState = MutableStateFlow(TripDetailUiState(
        phase = if (tripId == null) TripPhase.START else TripPhase.EDIT,
        isEditing = tripId == null
    ))
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()

    init {
        loadVehicles()
        loadPurposes()
        if (tripId != null) {
            loadTrip(tripId)
        } else {
            loadLastEndOdometer()
        }
    }

    private fun loadVehicles() {
        viewModelScope.launch {
            getAllVehiclesUseCase()
                .catch { e -> Timber.e(e, "Failed to load vehicles") }
                .collect { vehicles ->
                    _uiState.update { it.copy(vehicles = vehicles) }
                }
        }
    }

    private fun loadPurposes() {
        viewModelScope.launch {
            getAllPurposesUseCase()
                .catch { e -> Timber.e(e, "Failed to load purposes") }
                .collect { purposes ->
                    _uiState.update { it.copy(purposes = purposes) }
                }
        }
    }

    private fun loadTrip(id: Long) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            getTripByIdUseCase(id)
                .catch { e ->
                    Timber.e(e, "Failed to load trip $id")
                    _uiState.update {
                        it.copy(isLoading = false, error = "Fahrt konnte nicht geladen werden")
                    }
                }
                .collect { trip ->
                    val phase = when {
                        trip?.isActive == true -> TripPhase.END
                        else -> TripPhase.EDIT
                    }
                    _uiState.update {
                        it.copy(isLoading = false, trip = trip, phase = phase)
                    }
                }
        }
    }

    private fun loadLastEndOdometer(vehicleId: Long? = null) {
        viewModelScope.launch {
            val lastOdometer = if (vehicleId != null) {
                tripRepository.getLastEndOdometerForVehicle(vehicleId)
            } else {
                tripRepository.getLastEndOdometer()
            }
            _uiState.update { it.copy(lastEndOdometer = lastOdometer) }
        }
    }

    fun onVehicleChanged(vehicleId: Long?) {
        if (vehicleId != null) {
            loadLastEndOdometer(vehicleId)
        }
    }

    fun setEditing(editing: Boolean) {
        _uiState.update { it.copy(isEditing = editing, validationResult = null) }
    }

    /**
     * Start a new trip (Phase: START).
     * Creates a partial trip with isActive=true and starts GPS tracking.
     */
    fun startTrip(trip: Trip) {
        _uiState.update { it.copy(isSaving = true, validationResult = null, error = null) }

        viewModelScope.launch {
            when (val result = startTripUseCase(trip)) {
                is StartTripUseCase.Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            savedSuccessfully = true,
                            trip = trip.copy(id = result.tripId, isActive = true)
                        )
                    }
                }
                is StartTripUseCase.Result.ValidationError -> {
                    _uiState.update {
                        it.copy(isSaving = false, validationResult = result.validation)
                    }
                }
                is StartTripUseCase.Result.Error -> {
                    Timber.e(result.exception, "Failed to start trip")
                    _uiState.update {
                        it.copy(isSaving = false, error = "Fahrt konnte nicht gestartet werden")
                    }
                }
            }
        }
    }

    /**
     * End an active trip (Phase: END).
     * Completes the trip with end fields and sets isActive=false.
     */
    fun endTrip(trip: Trip) {
        _uiState.update { it.copy(isSaving = true, validationResult = null, error = null) }

        viewModelScope.launch {
            when (val result = endTripUseCase(trip)) {
                is EndTripUseCase.Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            savedSuccessfully = true,
                            trip = trip.copy(isActive = false)
                        )
                    }
                }
                is EndTripUseCase.Result.ValidationError -> {
                    _uiState.update {
                        it.copy(isSaving = false, validationResult = result.validation)
                    }
                }
                is EndTripUseCase.Result.Error -> {
                    Timber.e(result.exception, "Failed to end trip")
                    _uiState.update {
                        it.copy(isSaving = false, error = "Fahrt konnte nicht beendet werden")
                    }
                }
            }
        }
    }

    /**
     * Save/update a completed trip (Phase: EDIT).
     */
    fun saveTrip(trip: Trip) {
        _uiState.update { it.copy(isSaving = true, validationResult = null, error = null) }

        viewModelScope.launch {
            val isNew = tripId == null
            if (isNew) {
                when (val result = insertTripUseCase(trip)) {
                    is InsertTripUseCase.Result.Success -> {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                savedSuccessfully = true,
                                isEditing = false,
                                trip = trip.copy(id = result.tripId)
                            )
                        }
                    }
                    is InsertTripUseCase.Result.ValidationError -> {
                        _uiState.update {
                            it.copy(isSaving = false, validationResult = result.validation)
                        }
                    }
                    is InsertTripUseCase.Result.Error -> {
                        Timber.e(result.exception, "Failed to insert trip")
                        _uiState.update {
                            it.copy(isSaving = false, error = "Fahrt konnte nicht gespeichert werden")
                        }
                    }
                }
            } else {
                when (val result = updateTripUseCase(trip)) {
                    is UpdateTripUseCase.Result.Success -> {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                savedSuccessfully = true,
                                isEditing = false,
                                trip = trip
                            )
                        }
                    }
                    is UpdateTripUseCase.Result.ValidationError -> {
                        _uiState.update {
                            it.copy(isSaving = false, validationResult = result.validation)
                        }
                    }
                    is UpdateTripUseCase.Result.Error -> {
                        Timber.e(result.exception, "Failed to update trip")
                        _uiState.update {
                            it.copy(isSaving = false, error = "Fahrt konnte nicht aktualisiert werden")
                        }
                    }
                }
            }
        }
    }

    fun updateGpsDistance(distanceKm: Double) {
        _uiState.update { it.copy(gpsDistanceKm = distanceKm) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun onSaveConsumed() {
        _uiState.update { it.copy(savedSuccessfully = false) }
    }
}
