package de.fosstenbuch.ui.vehicles

import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.domain.validation.ValidationResult

/**
 * UI state for the Vehicle detail / add-edit screen.
 */
data class VehicleDetailUiState(
    val vehicle: Vehicle? = null,
    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val validationResult: ValidationResult? = null,
    val error: String? = null
)
