package de.fosstenbuch.domain.usecase.purpose

import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.repository.TripPurposeRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetAllPurposesUseCaseTest {

    private lateinit var useCase: GetAllPurposesUseCase
    private val mockRepository: TripPurposeRepository = mockk()

    @Before
    fun setup() {
        useCase = GetAllPurposesUseCase(mockRepository)
    }

    @Test
    fun `invoke returns all purposes`() = runBlocking {
        val purposes = listOf(
            TripPurpose(1L, "Beruflich", true),
            TripPurpose(2L, "Privat", false)
        )
        every { mockRepository.getAllPurposes() } returns flowOf(purposes)

        val result = useCase().first()

        assertEquals(2, result.size)
        assertEquals("Beruflich", result[0].name)
    }

    @Test
    fun `invoke returns empty list when no purposes`() = runBlocking {
        every { mockRepository.getAllPurposes() } returns flowOf(emptyList())

        val result = useCase().first()

        assertEquals(0, result.size)
    }
}
