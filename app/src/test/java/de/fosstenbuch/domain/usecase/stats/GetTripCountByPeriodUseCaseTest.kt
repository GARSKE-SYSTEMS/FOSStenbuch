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

class GetTripCountByPeriodUseCaseTest {

    private lateinit var useCase: GetTripCountByPeriodUseCase
    private val mockRepository: TripRepository = mockk()

    @Before
    fun setup() {
        useCase = GetTripCountByPeriodUseCase(mockRepository)
    }

    @Test
    fun `invoke returns trip count from repository`() = runBlocking {
        val startDate = 1704067200000L
        val endDate = 1706745600000L
        every { mockRepository.getTripCountByDateRange(startDate, endDate) } returns flowOf(15)

        val result = useCase(startDate, endDate).first()

        assertEquals(15, result)
    }

    @Test
    fun `invoke returns zero when no trips in range`() = runBlocking {
        val startDate = 1704067200000L
        val endDate = 1706745600000L
        every { mockRepository.getTripCountByDateRange(startDate, endDate) } returns flowOf(0)

        val result = useCase(startDate, endDate).first()

        assertEquals(0, result)
    }
}
