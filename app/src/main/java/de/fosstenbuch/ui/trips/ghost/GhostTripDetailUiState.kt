package de.fosstenbuch.ui.trips.ghost

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.model.Vehicle

data class GhostTripDetailUiState(
    val trip: Trip? = null,
    val vehicles: List<Vehicle> = emptyList(),
    val purposes: List<TripPurpose> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val acceptedSuccessfully: Boolean = false,
    val discardedSuccessfully: Boolean = false,
    val validationErrors: Map<String, String> = emptyMap(),
    val lastEndOdometer: Int? = null
)
