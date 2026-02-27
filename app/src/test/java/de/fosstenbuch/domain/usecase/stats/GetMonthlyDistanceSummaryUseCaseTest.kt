package de.fosstenbuch.domain.usecase.stats

import de.fosstenbuch.data.local.MonthlyDistance
import de.fosstenbuch.data.repository.TripRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetMonthlyDistanceSummaryUseCaseTest {

    private lateinit var useCase: GetMonthlyDistanceSummaryUseCase
    private val mockRepository: TripRepository = mockk()

    @Before
    fun setup() {
        useCase = GetMonthlyDistanceSummaryUseCase(mockRepository)
    }

    @Test
    fun `invoke returns monthly distance summary`() = runBlocking {
        val summary = listOf(
            MonthlyDistance(1, 500.0),
            MonthlyDistance(2, 300.0),
            MonthlyDistance(3, 450.0)
        )
        every { mockRepository.getMonthlyDistanceSummary(2024) } returns flowOf(summary)

        val result = useCase(2024).first()

        assertEquals(3, result.size)
        assertEquals(1, result[0].month)
        assertEquals(500.0, result[0].totalDistance, 0.001)
    }

    @Test
    fun `invoke returns empty list when no data`() = runBlocking {
        every { mockRepository.getMonthlyDistanceSummary(2024) } returns flowOf(emptyList())

        val result = useCase(2024).first()

        assertEquals(0, result.size)
    }
}
