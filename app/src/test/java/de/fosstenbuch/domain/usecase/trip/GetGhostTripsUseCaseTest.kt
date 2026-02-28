package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.local.TripDao
import de.fosstenbuch.data.model.Trip
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class GetGhostTripsUseCaseTest {

    private lateinit var useCase: GetGhostTripsUseCase
    private val mockTripDao: TripDao = mockk()

    @Before
    fun setup() {
        useCase = GetGhostTripsUseCase(mockTripDao)
    }

    private fun ghostTrip(id: Long) = Trip(
        id = id,
        date = Date(1700000000000L + id * 3_600_000L),
        startLocation = "Start $id",
        endLocation = "End $id",
        distanceKm = id * 10.0,
        vehicleId = 1L,
        isGhost = true,
        isActive = false
    )

    @Test
    fun `returns ghost trips emitted by dao`() = runBlocking {
        val ghosts = listOf(ghostTrip(2L), ghostTrip(1L))
        every { mockTripDao.getGhostTrips() } returns flowOf(ghosts)

        val result = useCase().first()

        assertEquals(2, result.size)
    }

    @Test
    fun `all returned trips have isGhost true`() = runBlocking {
        every { mockTripDao.getGhostTrips() } returns flowOf(listOf(ghostTrip(1L), ghostTrip(2L)))

        val result = useCase().first()

        assertTrue(result.all { it.isGhost })
    }

    @Test
    fun `returns empty list when no ghost trips exist`() = runBlocking {
        every { mockTripDao.getGhostTrips() } returns flowOf(emptyList())

        val result = useCase().first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `delegates directly to dao getGhostTrips`() {
        every { mockTripDao.getGhostTrips() } returns flowOf(emptyList())

        useCase()

        verify(exactly = 1) { mockTripDao.getGhostTrips() }
    }
}
