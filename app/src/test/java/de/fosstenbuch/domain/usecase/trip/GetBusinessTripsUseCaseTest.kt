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

class GetBusinessTripsUseCaseTest {

    private lateinit var useCase: GetBusinessTripsUseCase
    private val mockRepository: TripRepository = mockk()

    @Before
    fun setup() {
        useCase = GetBusinessTripsUseCase(mockRepository)
    }

    @Test
    fun `invoke returns business trips from repository`() = runBlocking {
        val trips = listOf(
            Trip(1L, Date(), "Berlin", "Hamburg", 280.0, "Kundentermin", purposeId = 1L)
        )
        every { mockRepository.getBusinessTrips() } returns flowOf(trips)

        val result = useCase().first()

        assertEquals(1, result.size)
        assertEquals("Kundentermin", result[0].purpose)
    }

    @Test
    fun `invoke returns empty list when no business trips`() = runBlocking {
        every { mockRepository.getBusinessTrips() } returns flowOf(emptyList())

        val result = useCase().first()

        assertEquals(0, result.size)
    }
}
