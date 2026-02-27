package de.fosstenbuch.domain.usecase.location

import de.fosstenbuch.data.model.SavedLocation
import de.fosstenbuch.data.repository.SavedLocationRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetAllSavedLocationsUseCaseTest {

    private lateinit var useCase: GetAllSavedLocationsUseCase
    private val mockRepository: SavedLocationRepository = mockk()

    @Before
    fun setup() {
        useCase = GetAllSavedLocationsUseCase(mockRepository)
    }

    @Test
    fun `invoke returns all locations`() = runBlocking {
        val locations = listOf(
            SavedLocation(1L, "Büro", 52.52, 13.405),
            SavedLocation(2L, "Home", 52.50, 13.380)
        )
        every { mockRepository.getAllSavedLocations() } returns flowOf(locations)

        val result = useCase().first()

        assertEquals(2, result.size)
        assertEquals("Büro", result[0].name)
    }

    @Test
    fun `invoke returns empty list when no locations`() = runBlocking {
        every { mockRepository.getAllSavedLocations() } returns flowOf(emptyList())

        val result = useCase().first()

        assertEquals(0, result.size)
    }
}
