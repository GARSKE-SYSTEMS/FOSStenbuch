package de.fosstenbuch.ui.trips

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.domain.validation.ValidationResult

/**
 * Represents the current phase of the trip form.
 */
enum class TripPhase {
    /** Starting a new trip — only start fields shown */
    START,
    /** Ending an active trip — end fields shown with start summary */
    END,
    /** Viewing/editing a completed trip — all fields shown */
    EDIT
}

/**
 * UI state for the Trip detail / add-edit screen.
 */
data class TripDetailUiState(
    val trip: Trip? = null,
    val phase: TripPhase = TripPhase.START,
    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val validationResult: ValidationResult? = null,
    val error: String? = null,
    val vehicles: List<Vehicle> = emptyList(),
    val purposes: List<TripPurpose> = emptyList(),
    val lastEndOdometer: Int? = null,
    val gpsDistanceKm: Double = 0.0,
    val isAuditLocked: Boolean = false
)
