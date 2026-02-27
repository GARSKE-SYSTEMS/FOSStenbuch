package de.fosstenbuch.domain.usecase.location

import de.fosstenbuch.data.model.SavedLocation
import de.fosstenbuch.data.repository.SavedLocationRepository
import de.fosstenbuch.utils.HaversineUtils
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FindNearestSavedLocationUseCaseTest {

    private lateinit var useCase: FindNearestSavedLocationUseCase
    private val mockRepository: SavedLocationRepository = mockk()

    @Before
    fun setup() {
        useCase = FindNearestSavedLocationUseCase(mockRepository)
    }

    @Test
    fun `returns nearest location within radius`() = runBlocking {
        val locations = listOf(
            SavedLocation(1L, "Office", 52.5200, 13.4050),   // Berlin center
            SavedLocation(2L, "Home", 52.5230, 13.4100),     // ~350m away
            SavedLocation(3L, "Far Away", 48.1351, 11.5820)  // Munich, very far
        )
        coEvery { mockRepository.getAllSavedLocationsSync() } returns locations

        // Query point near Office (within 100m)
        val result = useCase(52.5201, 13.4051, 1000.0)

        assertNotNull(result)
        assertEquals("Office", result!!.name)
    }

    @Test
    fun `returns null when no location within radius`() = runBlocking {
        val locations = listOf(
            SavedLocation(1L, "Munich", 48.1351, 11.5820)
        )
        coEvery { mockRepository.getAllSavedLocationsSync() } returns locations

        // Query in Berlin - Munich is far away
        val result = useCase(52.5200, 13.4050, 1000.0)

        assertNull(result)
    }

    @Test
    fun `returns null when no locations exist`() = runBlocking {
        coEvery { mockRepository.getAllSavedLocationsSync() } returns emptyList()

        val result = useCase(52.5200, 13.4050)

        assertNull(result)
    }

    @Test
    fun `returns closest of multiple locations within radius`() = runBlocking {
        val locations = listOf(
            SavedLocation(1L, "Closer", 52.5201, 13.4051),   // ~15m
            SavedLocation(2L, "Further", 52.5230, 13.4100)   // ~350m
        )
        coEvery { mockRepository.getAllSavedLocationsSync() } returns locations

        val result = useCase(52.5200, 13.4050, 1000.0)

        assertNotNull(result)
        assertEquals("Closer", result!!.name)
    }
}
