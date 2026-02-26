package de.fosstenbuch.ui.vehicles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.domain.usecase.vehicle.GetVehicleByIdUseCase
import de.fosstenbuch.domain.usecase.vehicle.InsertVehicleUseCase
import de.fosstenbuch.domain.usecase.vehicle.UpdateVehicleUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class VehicleDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getVehicleByIdUseCase: GetVehicleByIdUseCase,
    private val insertVehicleUseCase: InsertVehicleUseCase,
    private val updateVehicleUseCase: UpdateVehicleUseCase
) : ViewModel() {

    private val vehicleId: Long? = savedStateHandle.get<Long>("vehicleId")?.takeIf { it > 0 }

    private val _uiState = MutableStateFlow(VehicleDetailUiState(
        isEditing = vehicleId == null
    ))
    val uiState: StateFlow<VehicleDetailUiState> = _uiState.asStateFlow()

    init {
        vehicleId?.let { loadVehicle(it) }
    }

    private fun loadVehicle(id: Long) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            getVehicleByIdUseCase(id)
                .catch { e ->
                    Timber.e(e, "Failed to load vehicle $id")
                    _uiState.update {
                        it.copy(isLoading = false, error = "Fahrzeug konnte nicht geladen werden")
                    }
                }
                .collect { vehicle ->
                    _uiState.update {
                        it.copy(isLoading = false, vehicle = vehicle)
                    }
                }
        }
    }

    fun setEditing(editing: Boolean) {
        _uiState.update { it.copy(isEditing = editing, validationResult = null) }
    }

    fun saveVehicle(vehicle: Vehicle) {
        _uiState.update { it.copy(isSaving = true, validationResult = null, error = null) }

        viewModelScope.launch {
            val isNew = vehicleId == null
            if (isNew) {
                when (val result = insertVehicleUseCase(vehicle)) {
                    is InsertVehicleUseCase.Result.Success -> {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                savedSuccessfully = true,
                                isEditing = false,
                                vehicle = vehicle.copy(id = result.vehicleId)
                            )
                        }
                    }
                    is InsertVehicleUseCase.Result.ValidationError -> {
                        _uiState.update {
                            it.copy(isSaving = false, validationResult = result.validation)
                        }
                    }
                    is InsertVehicleUseCase.Result.Error -> {
                        Timber.e(result.exception, "Failed to insert vehicle")
                        _uiState.update {
                            it.copy(isSaving = false, error = "Fahrzeug konnte nicht gespeichert werden")
                        }
                    }
                }
            } else {
                when (val result = updateVehicleUseCase(vehicle)) {
                    is UpdateVehicleUseCase.Result.Success -> {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                savedSuccessfully = true,
                                isEditing = false,
                                vehicle = vehicle
                            )
                        }
                    }
                    is UpdateVehicleUseCase.Result.ValidationError -> {
                        _uiState.update {
                            it.copy(isSaving = false, validationResult = result.validation)
                        }
                    }
                    is UpdateVehicleUseCase.Result.Error -> {
                        Timber.e(result.exception, "Failed to update vehicle")
                        _uiState.update {
                            it.copy(isSaving = false, error = "Fahrzeug konnte nicht aktualisiert werden")
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
