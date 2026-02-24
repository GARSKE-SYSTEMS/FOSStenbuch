package de.fosstenbuch.data.repository

import de.fosstenbuch.data.local.TripDao
import de.fosstenbuch.data.model.Trip
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Date

class TripRepositoryTest {

    private lateinit var tripRepository: TripRepository
    private val mockTripDao: TripDao = mockk()

    @Before
    fun setup() {
        tripRepository = TripRepositoryImpl(mockTripDao)
    }

    @Test
    fun `getAllTrips should return flow of trips`() = runBlocking {
        // Given
        val testTrips = listOf(
            Trip(1, Date(), "Start1", "End1", 10.0, "Business", true),
            Trip(2, Date(), "Start2", "End2", 15.0, "Private", false)
        )
        coEvery { mockTripDao.getAllTrips() } returns flowOf(testTrips)

        // When
        val result = tripRepository.getAllTrips()

        // Then
        result.collect { trips ->
            assertEquals(2, trips.size)
            assertEquals("Start1", trips[0].startLocation)
            assertEquals("End2", trips[1].endLocation)
        }
    }

    @Test
    fun `insertTrip should return inserted trip id`() = runBlocking {
        // Given
        val testTrip = Trip(1, Date(), "Start", "End", 10.0, "Business", true)
        coEvery { mockTripDao.insertTrip(testTrip) } returns 1L

        // When
        val result = tripRepository.insertTrip(testTrip)

        // Then
        assertEquals(1L, result)
    }
}