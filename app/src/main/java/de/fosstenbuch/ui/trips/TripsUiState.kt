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
    val activeTrip: Trip? = null,
    val auditProtectedVehicleIds: Set<Long> = emptySet(),
    val ghostTripCount: Int = 0,
    /** Non-null while a ghost trip is being auto-recorded; contains the BT device name. */
    val activeGhostTripDeviceName: String? = null,
    val activeGhostTripDistanceKm: Double = 0.0,
    val activeGhostTripElapsedMin: Long = 0L
) {
    val isEmpty: Boolean get() = !isLoading && error == null && trips.isEmpty()
    val hasActiveTrip: Boolean get() = activeTrip != null
    val hasGhostTrips: Boolean get() = ghostTripCount > 0
    val isGhostTripRecording: Boolean get() = activeGhostTripDeviceName != null
}
