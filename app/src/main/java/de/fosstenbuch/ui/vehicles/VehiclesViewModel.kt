package de.fosstenbuch.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.data.repository.TripRepository
import de.fosstenbuch.domain.usecase.vehicle.DeleteVehicleUseCase
import de.fosstenbuch.domain.usecase.vehicle.GetAllVehiclesUseCase
import de.fosstenbuch.domain.usecase.vehicle.SetPrimaryVehicleUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class VehiclesViewModel @Inject constructor(
    private val getAllVehiclesUseCase: GetAllVehiclesUseCase,
    private val deleteVehicleUseCase: DeleteVehicleUseCase,
    private val setPrimaryVehicleUseCase: SetPrimaryVehicleUseCase,
    private val tripRepository: TripRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VehiclesUiState())
    val uiState: StateFlow<VehiclesUiState> = _uiState.asStateFlow()

    init {
        loadVehicles()
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            try {
                // Audit-protected vehicles cannot be deleted if they have trips
                if (vehicle.auditProtected) {
                    val tripCount = tripRepository.getTripCountForVehicle(vehicle.id)
                    if (tripCount > 0) {
                        _uiState.update {
                            it.copy(error = "Änderungssicher geschütztes Fahrzeug kann nicht gelöscht werden, " +
                                "solange Fahrten zugeordnet sind ($tripCount Fahrten)")
                        }
                        return@launch
                    }
                }
                deleteVehicleUseCase(vehicle)
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete vehicle")
                _uiState.update { it.copy(error = "Fahrzeug konnte nicht gelöscht werden") }
            }
        }
    }

    fun setPrimaryVehicle(vehicleId: Long) {
        viewModelScope.launch {
            try {
                setPrimaryVehicleUseCase(vehicleId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to set primary vehicle")
                _uiState.update { it.copy(error = "Primärfahrzeug konnte nicht gesetzt werden") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun loadVehicles() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            getAllVehiclesUseCase()
                .catch { e ->
                    Timber.e(e, "Failed to load vehicles")
                    _uiState.update {
                        it.copy(isLoading = false, error = "Fahrzeuge konnten nicht geladen werden")
                    }
                }
                .collect { vehicles ->
                    _uiState.update {
                        it.copy(isLoading = false, vehicles = vehicles, error = null)
                    }
                }
        }
    }
}
