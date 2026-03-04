package de.fosstenbuch.domain.usecase.location

import de.fosstenbuch.data.local.SavedLocationDao
import de.fosstenbuch.data.model.SavedLocation
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ResolveLocationNameUseCase].
 *
 * Resolution priority:
 *   1. Nearest saved location within 500 m
 *   2. Nominatim reverse geocoding (network – not available in unit tests → always falls through)
 *   3. Raw coordinate string fallback
 */
class ResolveLocationNameUseCaseTest {

    private lateinit var useCase: ResolveLocationNameUseCase
    private val mockSavedLocationDao: SavedLocationDao = mockk()

    @Before
    fun setup() {
        useCase = ResolveLocationNameUseCase(mockSavedLocationDao)
    }

    // ── Saved location resolution ────────────────────────────────────────────

    @Test
    fun `returns name of saved location within 500m radius`() = runBlocking {
        coEvery { mockSavedLocationDao.getAllSavedLocationsSync() } returns listOf(
            // ~50 m away from the query point
            SavedLocation(id = 1L, name = "Office", latitude = 52.5200, longitude = 13.4050)
        )

        val result = useCase(52.5204, 13.4056)

        assertEquals("Office", result)
    }

    @Test
    fun `returns closest saved location when multiple are within radius`() = runBlocking {
        coEvery { mockSavedLocationDao.getAllSavedLocationsSync() } returns listOf(
            SavedLocation(id = 1L, name = "Nearby Cafe", latitude = 52.5210, longitude = 13.4060),  // ~150 m
            SavedLocation(id = 2L, name = "Office",      latitude = 52.5200, longitude = 13.4050)   // ~50 m
        )

        val result = useCase(52.5204, 13.4056)

        assertEquals("Office", result)
    }

    @Test
    fun `ignores saved location beyond 500m radius`() = runBlocking {
        coEvery { mockSavedLocationDao.getAllSavedLocationsSync() } returns listOf(
            // Munich – ~500 km from a Berlin query point
            SavedLocation(id = 1L, name = "Munich Office", latitude = 48.1351, longitude = 11.5820)
        )

        // Query point in Berlin
        val result = useCase(52.5200, 13.4050)

        assertNotEquals("Munich Office", result)
    }

    // ── Coordinate fallback ──────────────────────────────────────────────────
    // In a unit-test environment no real network is available, so Nominatim will
    // always throw an IOException and the use case must fall back to coordinates.

    @Test
    fun `falls back to coordinate string when no saved locations and no network`() = runBlocking {
        coEvery { mockSavedLocationDao.getAllSavedLocationsSync() } returns emptyList()

        val result = useCase(52.5200, 13.4050)

        // Must contain the latitude and longitude digits
        assertTrue("Expected coordinate fallback, got: $result", result.contains("52") && result.contains("13"))
    }

    @Test
    fun `coordinate fallback format is four-decimal lat and lon separated by comma`() = runBlocking {
        coEvery { mockSavedLocationDao.getAllSavedLocationsSync() } returns emptyList()

        val result = useCase(48.1351, 11.5820)

        assertEquals("48.1351, 11.5820", result)
    }

    @Test
    fun `coordinate fallback is used when saved location is out of range and no network`() = runBlocking {
        coEvery { mockSavedLocationDao.getAllSavedLocationsSync() } returns listOf(
            SavedLocation(id = 1L, name = "Far Place", latitude = 40.7128, longitude = -74.0060) // New York
        )

        // Query point in Berlin
        val result = useCase(52.5200, 13.4050)

        assertNotEquals("Far Place", result)
        // Should be the coordinate fallback
        assertTrue(result.contains("52") && result.contains("13"))
    }

    // ── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `empty saved location list does not throw`() = runBlocking {
        coEvery { mockSavedLocationDao.getAllSavedLocationsSync() } returns emptyList()

        // Should not throw; returns coordinate fallback
        val result = useCase(0.0, 0.0)

        assertEquals("0.0000, 0.0000", result)
    }

    @Test
    fun `saved location exactly at query coordinates is returned`() = runBlocking {
        coEvery { mockSavedLocationDao.getAllSavedLocationsSync() } returns listOf(
            SavedLocation(id = 1L, name = "Exact Spot", latitude = 51.0000, longitude = 9.0000)
        )

        val result = useCase(51.0000, 9.0000)

        assertEquals("Exact Spot", result)
    }
}
