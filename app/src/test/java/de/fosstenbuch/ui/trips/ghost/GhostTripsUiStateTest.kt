package de.fosstenbuch.ui.trips.ghost

import de.fosstenbuch.data.model.Trip
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class GhostTripsUiStateTest {

    private fun ghostTrip(id: Long = 1L) = Trip(
        id = id,
        date = Date(),
        startLocation = "Start",
        endLocation = "End",
        isGhost = true
    )

    // ── isEmpty ──────────────────────────────────────────────────────────────

    @Test
    fun `isEmpty is false while loading`() {
        val state = GhostTripsUiState(isLoading = true, ghostTrips = emptyList(), error = null)
        assertFalse(state.isEmpty)
    }

    @Test
    fun `isEmpty is false when there is an error`() {
        val state = GhostTripsUiState(isLoading = false, ghostTrips = emptyList(), error = "Fehler")
        assertFalse(state.isEmpty)
    }

    @Test
    fun `isEmpty is true when not loading, no error, and no ghost trips`() {
        val state = GhostTripsUiState(isLoading = false, ghostTrips = emptyList(), error = null)
        assertTrue(state.isEmpty)
    }

    @Test
    fun `isEmpty is false when ghost trips are present`() {
        val state = GhostTripsUiState(
            isLoading = false,
            ghostTrips = listOf(ghostTrip(1L), ghostTrip(2L)),
            error = null
        )
        assertFalse(state.isEmpty)
    }

    // ── Default state ────────────────────────────────────────────────────────

    @Test
    fun `default state is loading with no trips and no error`() {
        val state = GhostTripsUiState()
        assertTrue(state.isLoading)
        assertTrue(state.ghostTrips.isEmpty())
        assertFalse(state.isEmpty)
        assertTrue(state.error == null)
    }
}
