package de.fosstenbuch.data.repository

import de.fosstenbuch.data.local.MonthlyDistance
import de.fosstenbuch.data.local.TripDao
import de.fosstenbuch.data.model.Trip
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `getTripById should delegate to dao`() = runBlocking {
        val trip = Trip(1, Date(), "Start", "End", 10.0, "Business", purposeId = 1L)
        every { mockTripDao.getTripById(1L) } returns flowOf(trip)

        val result = tripRepository.getTripById(1L).first()

        assertEquals("Start", result?.startLocation)
    }

    @Test
    fun `getBusinessTrips should delegate to dao`() = runBlocking {
        val trips = listOf(Trip(1, Date(), "S", "E", 10.0, "B", purposeId = 1L))
        every { mockTripDao.getBusinessTrips() } returns flowOf(trips)

        val result = tripRepository.getBusinessTrips().first()

        assertEquals(1, result.size)
    }

    @Test
    fun `getPrivateTrips should delegate to dao`() = runBlocking {
        val trips = listOf(Trip(1, Date(), "S", "E", 10.0, "P", purposeId = 2L))
        every { mockTripDao.getPrivateTrips() } returns flowOf(trips)

        val result = tripRepository.getPrivateTrips().first()

        assertEquals(1, result.size)
    }

    @Test
    fun `getTripsByDateRange should delegate to dao`() = runBlocking {
        val trips = listOf(Trip(1, Date(), "S", "E", 10.0, "B", purposeId = 1L))
        every { mockTripDao.getTripsByDateRange(100L, 200L) } returns flowOf(trips)

        val result = tripRepository.getTripsByDateRange(100L, 200L).first()

        assertEquals(1, result.size)
    }

    @Test
    fun `deleteAllTrips should delegate to dao`() = runBlocking {
        coEvery { mockTripDao.deleteAllTrips() } returns Unit

        tripRepository.deleteAllTrips()

        coVerify(exactly = 1) { mockTripDao.deleteAllTrips() }
    }

    @Test
    fun `getTotalBusinessDistance should delegate to dao`() = runBlocking {
        every { mockTripDao.getTotalBusinessDistance() } returns flowOf(5000.0)

        val result = tripRepository.getTotalBusinessDistance().first()

        assertEquals(5000.0, result!!, 0.001)
    }

    @Test
    fun `getTotalPrivateDistance should delegate to dao`() = runBlocking {
        every { mockTripDao.getTotalPrivateDistance() } returns flowOf(3000.0)

        val result = tripRepository.getTotalPrivateDistance().first()

        assertEquals(3000.0, result!!, 0.001)
    }

    @Test
    fun `getTotalDistance should delegate to dao`() = runBlocking {
        every { mockTripDao.getTotalDistance() } returns flowOf(8000.0)

        val result = tripRepository.getTotalDistance().first()

        assertEquals(8000.0, result!!, 0.001)
    }

    @Test
    fun `getTripCountByDateRange should delegate to dao`() = runBlocking {
        every { mockTripDao.getTripCountByDateRange(100L, 200L) } returns flowOf(5)

        val result = tripRepository.getTripCountByDateRange(100L, 200L).first()

        assertEquals(5, result)
    }

    @Test
    fun `getMonthlyDistanceSummary should delegate to dao`() = runBlocking {
        val summary = listOf(MonthlyDistance(1, 500.0))
        every { mockTripDao.getMonthlyDistanceSummary(2024) } returns flowOf(summary)

        val result = tripRepository.getMonthlyDistanceSummary(2024).first()

        assertEquals(1, result.size)
        assertEquals(500.0, result[0].totalDistance, 0.001)
    }

    @Test
    fun `getBusinessDistanceForYear should delegate to dao`() = runBlocking {
        every { mockTripDao.getBusinessDistanceForYear(2024) } returns flowOf(4000.0)

        val result = tripRepository.getBusinessDistanceForYear(2024).first()

        assertEquals(4000.0, result!!, 0.001)
    }

    @Test
    fun `getBusinessTripCountForYear should delegate to dao`() = runBlocking {
        every { mockTripDao.getBusinessTripCountForYear(2024) } returns flowOf(150)

        val result = tripRepository.getBusinessTripCountForYear(2024).first()

        assertEquals(150, result)
    }

    @Test
    fun `getTotalDistance returns null when no trips`() = runBlocking {
        every { mockTripDao.getTotalDistance() } returns flowOf(null)

        val result = tripRepository.getTotalDistance().first()

        assertNull(result)
    }

    @Test
    fun `getActiveTrip should return flow of active trip`() = runBlocking {
        val activeTrip = Trip(1, Date(), "Start", "End", 10.0, "Business", purposeId = 1L, isActive = true)
        every { mockTripDao.getActiveTrip() } returns flowOf(activeTrip)

        val result = tripRepository.getActiveTrip().first()

        assertEquals(1L, result?.id)
        assertEquals(true, result?.isActive)
    }

    @Test
    fun `getActiveTrip returns null when no active trip`() = runBlocking {
        every { mockTripDao.getActiveTrip() } returns flowOf(null)

        val result = tripRepository.getActiveTrip().first()

        assertNull(result)
    }

    @Test
    fun `getLastEndOdometerForVehicle should delegate to dao`() = runBlocking {
        coEvery { mockTripDao.getLastEndOdometerForVehicle(5L) } returns 50280

        val result = tripRepository.getLastEndOdometerForVehicle(5L)

        assertEquals(50280, result)
    }

    @Test
    fun `getLastEndOdometerForVehicle returns null when no trips`() = runBlocking {
        coEvery { mockTripDao.getLastEndOdometerForVehicle(5L) } returns null

        val result = tripRepository.getLastEndOdometerForVehicle(5L)

        assertNull(result)
    }

    @Test
    fun `getLastEndOdometer should delegate to dao`() = runBlocking {
        coEvery { mockTripDao.getLastEndOdometer() } returns 75000

        val result = tripRepository.getLastEndOdometer()

        assertEquals(75000, result)
    }

    @Test
    fun `getLastEndOdometer returns null when no trips`() = runBlocking {
        coEvery { mockTripDao.getLastEndOdometer() } returns null

        val result = tripRepository.getLastEndOdometer()

        assertNull(result)
    }

    @Test
    fun `getLastCompletedTrip should delegate to dao`() = runBlocking {
        val trip = Trip(1, Date(), "Start", "End", 10.0, "Business", purposeId = 1L)
        coEvery { mockTripDao.getLastCompletedTrip() } returns trip

        val result = tripRepository.getLastCompletedTrip()

        assertEquals(1L, result?.id)
    }

    @Test
    fun `getLastCompletedTrip returns null when no completed trips`() = runBlocking {
        coEvery { mockTripDao.getLastCompletedTrip() } returns null

        val result = tripRepository.getLastCompletedTrip()

        assertNull(result)
    }

    @Test
    fun `markTripsAsExported should delegate to dao`() = runBlocking {
        val tripIds = listOf(1L, 2L, 3L)
        coEvery { mockTripDao.markTripsAsExported(tripIds) } returns Unit

        tripRepository.markTripsAsExported(tripIds)

        coVerify(exactly = 1) { mockTripDao.markTripsAsExported(tripIds) }
    }

    @Test
    fun `getUnexportedTripsByDateRange should delegate to dao`() = runBlocking {
        val trips = listOf(Trip(1, Date(), "S", "E", 10.0, "B", purposeId = 1L))
        coEvery { mockTripDao.getUnexportedTripsByDateRange(100L, 200L) } returns trips

        val result = tripRepository.getUnexportedTripsByDateRange(100L, 200L)

        assertEquals(1, result.size)
        assertEquals("S", result[0].startLocation)
    }

    @Test
    fun `getUnexportedTripsByDateRange returns empty when no trips`() = runBlocking {
        coEvery { mockTripDao.getUnexportedTripsByDateRange(100L, 200L) } returns emptyList()

        val result = tripRepository.getUnexportedTripsByDateRange(100L, 200L)

        assertEquals(0, result.size)
    }

    @Test
    fun `getCompletedTripsByDateRange should delegate to dao`() = runBlocking {
        val trips = listOf(
            Trip(1, Date(), "S", "E", 10.0, "B", purposeId = 1L),
            Trip(2, Date(), "S2", "E2", 20.0, "P", purposeId = 2L)
        )
        coEvery { mockTripDao.getCompletedTripsByDateRange(100L, 200L) } returns trips

        val result = tripRepository.getCompletedTripsByDateRange(100L, 200L)

        assertEquals(2, result.size)
    }

    @Test
    fun `getTotalDistanceByDateRange should delegate to dao`() = runBlocking {
        every { mockTripDao.getTotalDistanceByDateRange(100L, 200L) } returns flowOf(1500.0)

        val result = tripRepository.getTotalDistanceByDateRange(100L, 200L).first()

        assertEquals(1500.0, result!!, 0.001)
    }

    @Test
    fun `getTotalDistanceByDateRange returns null when no trips`() = runBlocking {
        every { mockTripDao.getTotalDistanceByDateRange(100L, 200L) } returns flowOf(null)

        val result = tripRepository.getTotalDistanceByDateRange(100L, 200L).first()

        assertNull(result)
    }

    @Test
    fun `getBusinessDistanceByDateRange should delegate to dao`() = runBlocking {
        every { mockTripDao.getBusinessDistanceByDateRange(100L, 200L) } returns flowOf(800.0)

        val result = tripRepository.getBusinessDistanceByDateRange(100L, 200L).first()

        assertEquals(800.0, result!!, 0.001)
    }

    @Test
    fun `getPrivateDistanceByDateRange should delegate to dao`() = runBlocking {
        every { mockTripDao.getPrivateDistanceByDateRange(100L, 200L) } returns flowOf(700.0)

        val result = tripRepository.getPrivateDistanceByDateRange(100L, 200L).first()

        assertEquals(700.0, result!!, 0.001)
    }
}