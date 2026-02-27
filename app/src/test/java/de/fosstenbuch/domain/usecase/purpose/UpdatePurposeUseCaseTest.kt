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

class UpdatePurposeUseCaseTest {

    private lateinit var useCase: UpdatePurposeUseCase
    private val mockRepository: TripPurposeRepository = mockk()

    private val testPurpose = TripPurpose(
        id = 1L,
        name = "Beruflich",
        isBusinessRelevant = true,
        color = "#6200EE"
    )

    @Before
    fun setup() {
        useCase = UpdatePurposeUseCase(mockRepository)
    }

    @Test
    fun `valid purpose is updated successfully`() = runBlocking {
        coEvery { mockRepository.getPurposeByName("Beruflich") } returns null
        coEvery { mockRepository.updatePurpose(testPurpose) } returns Unit

        val result = useCase(testPurpose)

        assertTrue(result is UpdatePurposeUseCase.Result.Success)
        coVerify(exactly = 1) { mockRepository.updatePurpose(testPurpose) }
    }

    @Test
    fun `blank name returns error`() = runBlocking {
        val blankPurpose = testPurpose.copy(name = "")

        val result = useCase(blankPurpose)

        assertTrue(result is UpdatePurposeUseCase.Result.Error)
        assertTrue((result as UpdatePurposeUseCase.Result.Error).message.contains("Name"))
    }

    @Test
    fun `whitespace-only name returns error`() = runBlocking {
        val blankPurpose = testPurpose.copy(name = "   ")

        val result = useCase(blankPurpose)

        assertTrue(result is UpdatePurposeUseCase.Result.Error)
    }

    @Test
    fun `duplicate name returns error`() = runBlocking {
        val existingPurpose = TripPurpose(id = 2L, name = "Beruflich", isBusinessRelevant = true)
        coEvery { mockRepository.getPurposeByName("Beruflich") } returns existingPurpose

        val result = useCase(testPurpose)

        assertTrue(result is UpdatePurposeUseCase.Result.Error)
        assertTrue((result as UpdatePurposeUseCase.Result.Error).message.contains("existiert"))
    }

    @Test
    fun `same purpose with same name is allowed`() = runBlocking {
        // Existing purpose has same ID - should be allowed (updating itself)
        coEvery { mockRepository.getPurposeByName("Beruflich") } returns testPurpose
        coEvery { mockRepository.updatePurpose(testPurpose) } returns Unit

        val result = useCase(testPurpose)

        assertTrue(result is UpdatePurposeUseCase.Result.Success)
    }

    @Test
    fun `repository exception returns error`() = runBlocking {
        coEvery { mockRepository.getPurposeByName("Beruflich") } returns null
        coEvery { mockRepository.updatePurpose(testPurpose) } throws RuntimeException("DB error")

        val result = useCase(testPurpose)

        assertTrue(result is UpdatePurposeUseCase.Result.Error)
        assertTrue((result as UpdatePurposeUseCase.Result.Error).message.contains("nicht aktualisiert"))
    }
}
