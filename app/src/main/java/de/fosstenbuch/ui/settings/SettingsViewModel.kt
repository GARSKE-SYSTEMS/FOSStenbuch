package de.fosstenbuch.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.fosstenbuch.data.local.PreferencesManager
import de.fosstenbuch.domain.backup.BackupManager
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
    private val getAllPurposesUseCase: GetAllPurposesUseCase,
    private val preferencesManager: PreferencesManager,
    private val backupManager: BackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSummary()
        loadPreferences()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun consumeBackupSuccess() {
        _uiState.update { it.copy(backupSuccess = false, backupFilePath = null) }
    }

    fun consumeRestoreSuccess() {
        _uiState.update { it.copy(restoreSuccess = false) }
    }

    fun consumeDeleteSuccess() {
        _uiState.update { it.copy(deleteSuccess = false) }
    }

    // --- Preferences ---

    fun setDarkMode(mode: PreferencesManager.DarkMode) {
        viewModelScope.launch {
            preferencesManager.setDarkMode(mode)
        }
    }

    fun setDistanceUnit(unit: PreferencesManager.DistanceUnit) {
        viewModelScope.launch {
            preferencesManager.setDistanceUnit(unit)
        }
    }

    fun setDefaultPurpose(purposeId: Long?) {
        viewModelScope.launch {
            preferencesManager.setDefaultPurposeId(purposeId)
        }
    }

    fun setDefaultVehicle(vehicleId: Long?) {
        viewModelScope.launch {
            preferencesManager.setDefaultVehicleId(vehicleId)
        }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setReminderEnabled(enabled)
            _uiState.update { it.copy(reminderEnabled = enabled) }
        }
    }

    fun setReminderTime(time: String) {
        viewModelScope.launch {
            preferencesManager.setReminderTime(time)
            _uiState.update { it.copy(reminderTime = time) }
        }
    }

    fun setDriverName(name: String) {
        viewModelScope.launch {
            preferencesManager.setDriverName(name)
            _uiState.update { it.copy(driverName = name) }
        }
    }

    // --- Backup / Restore / Delete ---

    fun performBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val file = backupManager.exportBackup()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        backupSuccess = true,
                        backupFilePath = file.absolutePath
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Backup failed")
                _uiState.update {
                    it.copy(isLoading = false, error = "Backup fehlgeschlagen: ${e.message}")
                }
            }
        }
    }

    fun performRestore(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                backupManager.importBackup(uri)
                _uiState.update {
                    it.copy(isLoading = false, restoreSuccess = true)
                }
                // Reload summary after restore
                loadSummary()
            } catch (e: Exception) {
                Timber.e(e, "Restore failed")
                _uiState.update {
                    it.copy(isLoading = false, error = "Wiederherstellung fehlgeschlagen: ${e.message}")
                }
            }
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                backupManager.deleteAllData()
                _uiState.update {
                    it.copy(isLoading = false, deleteSuccess = true)
                }
                // Reload summary after delete
                loadSummary()
            } catch (e: Exception) {
                Timber.e(e, "Delete all failed")
                _uiState.update {
                    it.copy(isLoading = false, error = "Löschen fehlgeschlagen: ${e.message}")
                }
            }
        }
    }

    // --- Data Loading ---

    private fun loadSummary() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            combine(
                getAllTripsUseCase(),
                getAllVehiclesUseCase(),
                getAllSavedLocationsUseCase(),
                getAllPurposesUseCase()
            ) { trips, vehicles, locations, purposes ->
                _uiState.value.copy(
                    isLoading = false,
                    tripCount = trips.size,
                    vehicleCount = vehicles.size,
                    locationCount = locations.size,
                    purposeCount = purposes.size,
                    purposes = purposes,
                    vehicles = vehicles
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

    private fun loadPreferences() {
        viewModelScope.launch {
            combine(
                preferencesManager.darkMode,
                preferencesManager.distanceUnit,
                preferencesManager.defaultPurposeId,
                preferencesManager.defaultVehicleId
            ) { darkMode, distanceUnit, defaultPurposeId, defaultVehicleId ->
                Triple(darkMode, distanceUnit, Pair(defaultPurposeId, defaultVehicleId))
            }
                .collect { (darkMode, distanceUnit, defaults) ->
                    _uiState.update {
                        it.copy(
                            darkMode = darkMode,
                            distanceUnit = distanceUnit,
                            defaultPurposeId = defaults.first,
                            defaultVehicleId = defaults.second
                        )
                    }
                }
        }
        viewModelScope.launch {
            combine(
                preferencesManager.reminderEnabled,
                preferencesManager.reminderTime
            ) { enabled, time -> Pair(enabled, time) }
                .collect { (enabled, time) ->
                    _uiState.update {
                        it.copy(reminderEnabled = enabled, reminderTime = time)
                    }
                }
        }
        viewModelScope.launch {
            preferencesManager.driverName.collect { name ->
                _uiState.update { it.copy(driverName = name) }
            }
        }
    }
}
