package de.fosstenbuch.ui.trips

import de.fosstenbuch.data.model.Trip
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class TripsUiStateTest {

    // ── hasGhostTrips ────────────────────────────────────────────────────────

    @Test
    fun `hasGhostTrips is false when ghostTripCount is zero`() {
        val state = TripsUiState(ghostTripCount = 0)
        assertFalse(state.hasGhostTrips)
    }

    @Test
    fun `hasGhostTrips is true when ghostTripCount is one`() {
        val state = TripsUiState(ghostTripCount = 1)
        assertTrue(state.hasGhostTrips)
    }

    @Test
    fun `hasGhostTrips is true when ghostTripCount is many`() {
        val state = TripsUiState(ghostTripCount = 42)
        assertTrue(state.hasGhostTrips)
    }

    @Test
    fun `default state has ghostTripCount zero and hasGhostTrips false`() {
        val state = TripsUiState()
        assertFalse(state.hasGhostTrips)
        assertTrue(state.ghostTripCount == 0)
    }

    // ── hasActiveTrip ────────────────────────────────────────────────────────

    @Test
    fun `hasActiveTrip is false when activeTrip is null`() {
        val state = TripsUiState(activeTrip = null)
        assertFalse(state.hasActiveTrip)
    }

    @Test
    fun `hasActiveTrip is true when activeTrip is set`() {
        val trip = Trip(date = Date(), startLocation = "Berlin", isActive = true)
        val state = TripsUiState(activeTrip = trip)
        assertTrue(state.hasActiveTrip)
    }

    // ── isEmpty ──────────────────────────────────────────────────────────────

    @Test
    fun `isEmpty is false while loading`() {
        val state = TripsUiState(isLoading = true, trips = emptyList(), error = null)
        assertFalse(state.isEmpty)
    }

    @Test
    fun `isEmpty is false when there is an error`() {
        val state = TripsUiState(isLoading = false, trips = emptyList(), error = "Oops")
        assertFalse(state.isEmpty)
    }

    @Test
    fun `isEmpty is true when not loading, no error, and no trips`() {
        val state = TripsUiState(isLoading = false, trips = emptyList(), error = null)
        assertTrue(state.isEmpty)
    }

    @Test
    fun `isEmpty is false when trips exist`() {
        val trip = Trip(date = Date(), startLocation = "Berlin")
        val state = TripsUiState(isLoading = false, trips = listOf(trip), error = null)
        assertFalse(state.isEmpty)
    }

    // ── Ghost + active trip interaction ──────────────────────────────────────

    @Test
    fun `state can have both ghost trips and an active trip simultaneously`() {
        val activeTrip = Trip(date = Date(), startLocation = "Berlin", isActive = true)
        val state = TripsUiState(activeTrip = activeTrip, ghostTripCount = 3)
        assertTrue(state.hasActiveTrip)
        assertTrue(state.hasGhostTrips)
    }
}
