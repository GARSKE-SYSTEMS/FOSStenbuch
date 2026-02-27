package de.fosstenbuch.domain.usecase.purpose

import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.repository.TripPurposeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeletePurposeUseCaseTest {

    private lateinit var useCase: DeletePurposeUseCase
    private val mockRepository: TripPurposeRepository = mockk()

    @Before
    fun setup() {
        useCase = DeletePurposeUseCase(mockRepository)
    }

    @Test
    fun `non-default purpose with no trips is deleted successfully`() = runBlocking {
        val purpose = TripPurpose(id = 3L, name = "Custom", isBusinessRelevant = true, isDefault = false)
        coEvery { mockRepository.getTripCountForPurpose(3L) } returns 0
        coEvery { mockRepository.deletePurpose(purpose) } returns Unit

        val result = useCase(purpose)

        assertTrue(result is DeletePurposeUseCase.Result.Success)
        coVerify(exactly = 1) { mockRepository.deletePurpose(purpose) }
    }

    @Test
    fun `default purpose cannot be deleted`() = runBlocking {
        val purpose = TripPurpose(id = 1L, name = "Beruflich", isBusinessRelevant = true, isDefault = true)

        val result = useCase(purpose)

        assertTrue(result is DeletePurposeUseCase.Result.Error)
        assertTrue((result as DeletePurposeUseCase.Result.Error).message.contains("Standard"))
        coVerify(exactly = 0) { mockRepository.deletePurpose(any()) }
    }

    @Test
    fun `purpose with trips cannot be deleted`() = runBlocking {
        val purpose = TripPurpose(id = 3L, name = "Custom", isBusinessRelevant = true, isDefault = false)
        coEvery { mockRepository.getTripCountForPurpose(3L) } returns 5

        val result = useCase(purpose)

        assertTrue(result is DeletePurposeUseCase.Result.Error)
        assertTrue((result as DeletePurposeUseCase.Result.Error).message.contains("5"))
        coVerify(exactly = 0) { mockRepository.deletePurpose(any()) }
    }

    @Test
    fun `repository exception returns error`() = runBlocking {
        val purpose = TripPurpose(id = 3L, name = "Custom", isBusinessRelevant = true, isDefault = false)
        coEvery { mockRepository.getTripCountForPurpose(3L) } returns 0
        coEvery { mockRepository.deletePurpose(purpose) } throws RuntimeException("error")

        val result = useCase(purpose)

        assertTrue(result is DeletePurposeUseCase.Result.Error)
    }
}
