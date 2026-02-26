package de.fosstenbuch.ui.settings

import de.fosstenbuch.data.local.PreferencesManager
import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.model.Vehicle

/**
 * UI state for the Settings screen.
 */
data class SettingsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val vehicleCount: Int = 0,
    val tripCount: Int = 0,
    val locationCount: Int = 0,
    val purposeCount: Int = 0,

    // Preferences
    val darkMode: PreferencesManager.DarkMode = PreferencesManager.DarkMode.SYSTEM,
    val distanceUnit: PreferencesManager.DistanceUnit = PreferencesManager.DistanceUnit.KM,
    val defaultPurposeId: Long? = null,
    val defaultVehicleId: Long? = null,

    // Lists for dropdown selections
    val purposes: List<TripPurpose> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),

    // Operation feedback
    val backupSuccess: Boolean = false,
    val restoreSuccess: Boolean = false,
    val deleteSuccess: Boolean = false,
    val backupFilePath: String? = null
)
