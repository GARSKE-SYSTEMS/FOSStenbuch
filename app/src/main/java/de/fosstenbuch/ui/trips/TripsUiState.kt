package de.fosstenbuch.ui.trips

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.model.TripPurpose

/**
 * Represents the possible filter modes for the trips list.
 */
enum class TripFilter {
    ALL,
    BUSINESS,
    PRIVATE
}

/**
 * Represents the sort options for the trips list.
 */
enum class TripSort {
    DATE_DESC,
    DATE_ASC,
    DISTANCE_DESC,
    DISTANCE_ASC
}

/**
 * UI state for the Trips list screen.
 */
data class TripsUiState(
    val trips: List<Trip> = emptyList(),
    val purposes: List<TripPurpose> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val filter: TripFilter = TripFilter.ALL,
    val sort: TripSort = TripSort.DATE_DESC,
    val activeTrip: Trip? = null
) {
    val isEmpty: Boolean get() = !isLoading && error == null && trips.isEmpty()
    val hasActiveTrip: Boolean get() = activeTrip != null
}
