package de.fosstenbuch.ui.trips.ghost

import de.fosstenbuch.data.model.Trip

data class GhostTripsUiState(
    val ghostTrips: List<Trip> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val isEmpty: Boolean get() = !isLoading && error == null && ghostTrips.isEmpty()
}
