package de.fosstenbuch.domain.usecase.location

import de.fosstenbuch.data.model.SavedLocation
import de.fosstenbuch.data.repository.SavedLocationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class UpdateSavedLocationUseCaseTest {

    private lateinit var useCase: UpdateSavedLocationUseCase
    private val mockRepository: SavedLocationRepository = mockk()

    private val testLocation = SavedLocation(1L, "Büro", 52.52, 13.405, address = "Berlin")

    @Before
    fun setup() {
        useCase = UpdateSavedLocationUseCase(mockRepository)
    }

    @Test
    fun `invoke delegates to repository`() = runBlocking {
        coEvery { mockRepository.updateSavedLocation(testLocation) } returns Unit

        useCase(testLocation)

        coVerify(exactly = 1) { mockRepository.updateSavedLocation(testLocation) }
    }
}
