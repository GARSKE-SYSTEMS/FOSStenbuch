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

class GetAllTripsUseCaseTest {

    private lateinit var useCase: GetAllTripsUseCase
    private val mockRepository: TripRepository = mockk()

    @Before
    fun setup() {
        useCase = GetAllTripsUseCase(mockRepository)
    }

    @Test
    fun `invoke returns all trips from repository`() = runBlocking {
        val trips = listOf(
            Trip(1L, Date(), "Berlin", "Hamburg", 280.0, "Business", purposeId = 1L),
            Trip(2L, Date(), "Munich", "Stuttgart", 230.0, "Private", purposeId = 2L)
        )
        every { mockRepository.getAllTrips() } returns flowOf(trips)

        val result = useCase().first()

        assertEquals(2, result.size)
        assertEquals("Berlin", result[0].startLocation)
    }

    @Test
    fun `invoke returns empty list when no trips`() = runBlocking {
        every { mockRepository.getAllTrips() } returns flowOf(emptyList())

        val result = useCase().first()

        assertEquals(0, result.size)
    }
}
