package de.fosstenbuch.domain.usecase.purpose

import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.repository.TripPurposeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InsertPurposeUseCaseTest {

    private lateinit var useCase: InsertPurposeUseCase
    private val mockRepository: TripPurposeRepository = mockk()

    @Before
    fun setup() {
        useCase = InsertPurposeUseCase(mockRepository)
    }

    @Test
    fun `valid purpose is inserted successfully`() = runBlocking {
        val purpose = TripPurpose(name = "Dienstreise", isBusinessRelevant = true)
        coEvery { mockRepository.getPurposeByName("Dienstreise") } returns null
        coEvery { mockRepository.insertPurpose(purpose) } returns 1L

        val result = useCase(purpose)

        assertTrue(result is InsertPurposeUseCase.Result.Success)
        assertEquals(1L, (result as InsertPurposeUseCase.Result.Success).purposeId)
    }

    @Test
    fun `blank name returns error`() = runBlocking {
        val purpose = TripPurpose(name = "", isBusinessRelevant = true)

        val result = useCase(purpose)

        assertTrue(result is InsertPurposeUseCase.Result.Error)
        coVerify(exactly = 0) { mockRepository.insertPurpose(any()) }
    }

    @Test
    fun `duplicate name returns error`() = runBlocking {
        val existing = TripPurpose(id = 1L, name = "Dienstreise", isBusinessRelevant = true)
        val purpose = TripPurpose(name = "Dienstreise", isBusinessRelevant = false)
        coEvery { mockRepository.getPurposeByName("Dienstreise") } returns existing

        val result = useCase(purpose)

        assertTrue(result is InsertPurposeUseCase.Result.Error)
        assertTrue((result as InsertPurposeUseCase.Result.Error).message.contains("existiert bereits"))
    }

    @Test
    fun `repository exception returns error`() = runBlocking {
        val purpose = TripPurpose(name = "Test", isBusinessRelevant = true)
        coEvery { mockRepository.getPurposeByName("Test") } returns null
        coEvery { mockRepository.insertPurpose(purpose) } throws RuntimeException("DB error")

        val result = useCase(purpose)

        assertTrue(result is InsertPurposeUseCase.Result.Error)
    }
}
