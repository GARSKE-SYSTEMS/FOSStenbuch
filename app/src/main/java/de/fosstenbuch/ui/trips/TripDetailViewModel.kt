package de.fosstenbuch.ui.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.domain.usecase.trip.GetTripByIdUseCase
import de.fosstenbuch.domain.usecase.trip.InsertTripUseCase
import de.fosstenbuch.domain.usecase.trip.UpdateTripUseCase
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
    private val updateTripUseCase: UpdateTripUseCase
) : ViewModel() {

    private val tripId: Long? = savedStateHandle.get<Long>("tripId")?.takeIf { it > 0 }

    private val _uiState = MutableStateFlow(TripDetailUiState(
        isEditing = tripId == null  // New trip starts in edit mode
    ))
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()

    init {
        tripId?.let { loadTrip(it) }
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
                    _uiState.update {
                        it.copy(isLoading = false, trip = trip)
                    }
                }
        }
    }

    fun setEditing(editing: Boolean) {
        _uiState.update { it.copy(isEditing = editing, validationResult = null) }
    }

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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun onSaveConsumed() {
        _uiState.update { it.copy(savedSuccessfully = false) }
    }
}
