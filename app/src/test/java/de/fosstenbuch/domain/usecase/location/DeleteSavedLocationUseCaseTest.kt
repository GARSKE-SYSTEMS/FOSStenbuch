package de.fosstenbuch.domain.usecase.location

import de.fosstenbuch.data.model.SavedLocation
import de.fosstenbuch.data.repository.SavedLocationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class DeleteSavedLocationUseCaseTest {

    private lateinit var useCase: DeleteSavedLocationUseCase
    private val mockRepository: SavedLocationRepository = mockk()

    private val testLocation = SavedLocation(1L, "Büro", 52.52, 13.405)

    @Before
    fun setup() {
        useCase = DeleteSavedLocationUseCase(mockRepository)
    }

    @Test
    fun `invoke delegates to repository`() = runBlocking {
        coEvery { mockRepository.deleteSavedLocation(testLocation) } returns Unit

        useCase(testLocation)

        coVerify(exactly = 1) { mockRepository.deleteSavedLocation(testLocation) }
    }
}
