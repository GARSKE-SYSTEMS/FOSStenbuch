package de.fosstenbuch.domain.usecase.location

import de.fosstenbuch.data.model.SavedLocation
import de.fosstenbuch.data.repository.SavedLocationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InsertSavedLocationUseCaseTest {

    private lateinit var useCase: InsertSavedLocationUseCase
    private val mockRepository: SavedLocationRepository = mockk()

    @Before
    fun setup() {
        useCase = InsertSavedLocationUseCase(mockRepository)
    }

    @Test
    fun `valid location is inserted successfully`() = runBlocking {
        val location = SavedLocation(name = "Office", latitude = 52.52, longitude = 13.405)
        coEvery { mockRepository.insertSavedLocation(location) } returns 1L

        val result = useCase(location)

        assertTrue(result is InsertSavedLocationUseCase.Result.Success)
        assertEquals(1L, (result as InsertSavedLocationUseCase.Result.Success).locationId)
    }

    @Test
    fun `blank name returns error`() = runBlocking {
        val location = SavedLocation(name = "", latitude = 52.52, longitude = 13.405)

        val result = useCase(location)

        assertTrue(result is InsertSavedLocationUseCase.Result.Error)
        coVerify(exactly = 0) { mockRepository.insertSavedLocation(any()) }
    }

    @Test
    fun `repository exception returns error`() = runBlocking {
        val location = SavedLocation(name = "Office", latitude = 52.52, longitude = 13.405)
        coEvery { mockRepository.insertSavedLocation(location) } throws RuntimeException("DB error")

        val result = useCase(location)

        assertTrue(result is InsertSavedLocationUseCase.Result.Error)
    }
}
