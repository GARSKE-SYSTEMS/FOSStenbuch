package de.fosstenbuch.domain.usecase.stats

import de.fosstenbuch.data.repository.TripRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetTotalDistanceUseCaseTest {

    private lateinit var useCase: GetTotalDistanceUseCase
    private val mockRepository: TripRepository = mockk()

    @Before
    fun setup() {
        useCase = GetTotalDistanceUseCase(mockRepository)
    }

    @Test
    fun `invoke returns total distance`() = runBlocking {
        every { mockRepository.getTotalDistance() } returns flowOf(5000.0)

        val result = useCase().first()

        assertEquals(5000.0, result, 0.001)
    }

    @Test
    fun `invoke maps null to 0,0`() = runBlocking {
        every { mockRepository.getTotalDistance() } returns flowOf(null)

        val result = useCase().first()

        assertEquals(0.0, result, 0.001)
    }

    @Test
    fun `invoke returns zero distance`() = runBlocking {
        every { mockRepository.getTotalDistance() } returns flowOf(0.0)

        val result = useCase().first()

        assertEquals(0.0, result, 0.001)
    }
}
