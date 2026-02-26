package de.fosstenbuch.ui.trips

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.domain.validation.ValidationResult

/**
 * UI state for the Trip detail / add-edit screen.
 */
data class TripDetailUiState(
    val trip: Trip? = null,
    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val validationResult: ValidationResult? = null,
    val error: String? = null
)
