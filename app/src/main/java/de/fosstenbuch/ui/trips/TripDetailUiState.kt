package de.fosstenbuch.ui.trips

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.model.Vehicle
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
    val error: String? = null,
    val vehicles: List<Vehicle> = emptyList(),
    val purposes: List<TripPurpose> = emptyList()
)
