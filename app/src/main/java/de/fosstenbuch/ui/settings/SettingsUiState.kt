package de.fosstenbuch.ui.settings

/**
 * UI state for the Settings screen.
 * Will be expanded in Phase 7 with preferences, dark mode, language, etc.
 */
data class SettingsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val vehicleCount: Int = 0,
    val tripCount: Int = 0,
    val locationCount: Int = 0,
    val purposeCount: Int = 0
)
