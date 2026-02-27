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

class GetPrivateTripsUseCaseTest {

    private lateinit var useCase: GetPrivateTripsUseCase
    private val mockRepository: TripRepository = mockk()

    @Before
    fun setup() {
        useCase = GetPrivateTripsUseCase(mockRepository)
    }

    @Test
    fun `invoke returns private trips from repository`() = runBlocking {
        val trips = listOf(
            Trip(1L, Date(), "Berlin", "Potsdam", 40.0, "Einkauf", purposeId = 2L)
        )
        every { mockRepository.getPrivateTrips() } returns flowOf(trips)

        val result = useCase().first()

        assertEquals(1, result.size)
        assertEquals("Einkauf", result[0].purpose)
    }

    @Test
    fun `invoke returns empty list when no private trips`() = runBlocking {
        every { mockRepository.getPrivateTrips() } returns flowOf(emptyList())

        val result = useCase().first()

        assertEquals(0, result.size)
    }
}
