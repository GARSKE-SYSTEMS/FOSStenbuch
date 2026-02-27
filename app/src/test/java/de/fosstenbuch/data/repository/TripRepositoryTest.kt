package de.fosstenbuch.data.repository

import de.fosstenbuch.data.local.TripDao
import de.fosstenbuch.data.model.Trip
import io.mockk.coEvery
import io.mockk.coVerify
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
        val testTrips = listOf(
            Trip(1, Date(), "Start1", "End1", 10.0, "Business", purposeId = 1L),
            Trip(2, Date(), "Start2", "End2", 15.0, "Private", purposeId = 2L)
        )
        coEvery { mockTripDao.getAllTrips() } returns flowOf(testTrips)

        val result = tripRepository.getAllTrips()

        result.collect { trips ->
            assertEquals(2, trips.size)
            assertEquals("Start1", trips[0].startLocation)
            assertEquals("End2", trips[1].endLocation)
        }
    }

    @Test
    fun `insertTrip should return inserted trip id`() = runBlocking {
        val testTrip = Trip(1, Date(), "Start", "End", 10.0, "Business", purposeId = 1L)
        coEvery { mockTripDao.insertTrip(testTrip) } returns 1L

        val result = tripRepository.insertTrip(testTrip)

        assertEquals(1L, result)
    }

    @Test
    fun `deleteTrip should delegate to dao`() = runBlocking {
        val testTrip = Trip(1, Date(), "Start", "End", 10.0, "Business", purposeId = 1L)
        coEvery { mockTripDao.deleteTrip(testTrip) } returns Unit

        tripRepository.deleteTrip(testTrip)

        coVerify(exactly = 1) { mockTripDao.deleteTrip(testTrip) }
    }

    @Test
    fun `updateTrip should delegate to dao`() = runBlocking {
        val testTrip = Trip(1, Date(), "Start", "End", 10.0, "Business", purposeId = 1L)
        coEvery { mockTripDao.updateTrip(testTrip) } returns Unit

        tripRepository.updateTrip(testTrip)

        coVerify(exactly = 1) { mockTripDao.updateTrip(testTrip) }
    }

    @Test
    fun `getTripCountForVehicle should delegate to dao`() = runBlocking {
        coEvery { mockTripDao.getTripCountForVehicle(5L) } returns 3

        val result = tripRepository.getTripCountForVehicle(5L)

        assertEquals(3, result)
    }
}