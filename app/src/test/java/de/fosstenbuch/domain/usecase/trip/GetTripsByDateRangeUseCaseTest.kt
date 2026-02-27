package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.repository.TripRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Date

class GetTripsByDateRangeUseCaseTest {

    private lateinit var useCase: GetTripsByDateRangeUseCase
    private val mockRepository: TripRepository = mockk()

    @Before
    fun setup() {
        useCase = GetTripsByDateRangeUseCase(mockRepository)
    }

    @Test
    fun `invoke returns trips within date range`() = runBlocking {
        val trips = listOf(
            Trip(1L, Date(), "Berlin", "Hamburg", 280.0, "Business", purposeId = 1L)
        )
        val startDate = 1704067200000L // 2024-01-01
        val endDate = 1706745600000L   // 2024-02-01
        every { mockRepository.getTripsByDateRange(startDate, endDate) } returns flowOf(trips)

        val result = useCase(startDate, endDate).first()

        assertEquals(1, result.size)
    }

    @Test
    fun `invoke returns empty list when no trips in range`() = runBlocking {
        val startDate = 1704067200000L
        val endDate = 1706745600000L
        every { mockRepository.getTripsByDateRange(startDate, endDate) } returns flowOf(emptyList())

        val result = useCase(startDate, endDate).first()

        assertEquals(0, result.size)
    }
}
