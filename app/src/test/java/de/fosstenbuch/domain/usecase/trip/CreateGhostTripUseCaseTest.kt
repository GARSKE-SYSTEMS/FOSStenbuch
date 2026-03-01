package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.repository.TripRepository
import de.fosstenbuch.domain.usecase.location.ResolveLocationNameUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class CreateGhostTripUseCaseTest {

    private lateinit var useCase: CreateGhostTripUseCase
    private val mockTripRepository: TripRepository = mockk()
    private val mockResolveLocationName: ResolveLocationNameUseCase = mockk()

    @Before
    fun setup() {
        useCase = CreateGhostTripUseCase(mockTripRepository, mockResolveLocationName)
        coEvery { mockTripRepository.getLastEndOdometerForVehicle(any()) } returns null
    }

    private fun validInput() = CreateGhostTripUseCase.GhostTripInput(
        vehicleId = 1L,
        startTime = Date(1700000000000L),
        endTime = Date(1700003600000L),
        startLat = 52.5200,
        startLng = 13.4050,
        endLat = 53.5753,
        endLng = 10.0153,
        gpsDistanceKm = 280.0
    )

    @Test
    fun `creates ghost trip with resolved location names`() = runBlocking {
        val input = validInput()
        coEvery { mockResolveLocationName(input.startLat!!, input.startLng!!) } returns "Berlin Mitte"
        coEvery { mockResolveLocationName(input.endLat!!, input.endLng!!) } returns "Hamburg Altstadt"
        coEvery { mockTripRepository.insertTrip(any()) } returns 42L

        val result = useCase(input)

        assertTrue(result is CreateGhostTripUseCase.Result.Success)
        assertEquals(42L, (result as CreateGhostTripUseCase.Result.Success).tripId)
    }

    @Test
    fun `ghost trip is inserted with isGhost true and isActive false`() = runBlocking {
        val input = validInput()
        coEvery { mockResolveLocationName(any(), any()) } returns "somewhere"
        coEvery { mockTripRepository.insertTrip(any()) } returns 1L

        useCase(input)

        coVerify {
            mockTripRepository.insertTrip(match { it.isGhost && !it.isActive })
        }
    }

    @Test
    fun `ghost trip has correct resolved start and end location names`() = runBlocking {
        val input = validInput()
        coEvery { mockResolveLocationName(input.startLat!!, input.startLng!!) } returns "Start City"
        coEvery { mockResolveLocationName(input.endLat!!, input.endLng!!) } returns "End City"
        coEvery { mockTripRepository.insertTrip(any()) } returns 1L

        useCase(input)

        coVerify {
            mockTripRepository.insertTrip(match {
                it.startLocation == "Start City" && it.endLocation == "End City"
            })
        }
    }

    @Test
    fun `ghost trip stores GPS coordinates`() = runBlocking {
        val input = validInput()
        coEvery { mockResolveLocationName(any(), any()) } returns "somewhere"
        coEvery { mockTripRepository.insertTrip(any()) } returns 1L

        useCase(input)

        coVerify {
            mockTripRepository.insertTrip(match {
                it.startLatitude == input.startLat &&
                    it.startLongitude == input.startLng &&
                    it.endLatitude == input.endLat &&
                    it.endLongitude == input.endLng
            })
        }
    }

    @Test
    fun `ghost trip stores GPS distance`() = runBlocking {
        val input = validInput()
        coEvery { mockResolveLocationName(any(), any()) } returns "somewhere"
        coEvery { mockTripRepository.insertTrip(any()) } returns 1L

        useCase(input)

        coVerify {
            mockTripRepository.insertTrip(match { it.gpsDistanceKm == input.gpsDistanceKm })
        }
    }

    @Test
    fun `ghost trip assigns correct vehicleId`() = runBlocking {
        val input = validInput().copy(vehicleId = 99L)
        coEvery { mockResolveLocationName(any(), any()) } returns "somewhere"
        coEvery { mockTripRepository.insertTrip(any()) } returns 1L

        useCase(input)

        coVerify { mockTripRepository.insertTrip(match { it.vehicleId == 99L }) }
    }

    @Test
    fun `ghost trip stores start and end time`() = runBlocking {
        val input = validInput()
        coEvery { mockResolveLocationName(any(), any()) } returns "somewhere"
        coEvery { mockTripRepository.insertTrip(any()) } returns 1L

        useCase(input)

        coVerify {
            mockTripRepository.insertTrip(match {
                it.date == input.startTime && it.endTime == input.endTime
            })
        }
    }

    @Test
    fun `repository exception returns Error result`() = runBlocking {
        val input = validInput()
        coEvery { mockResolveLocationName(any(), any()) } returns "somewhere"
        coEvery { mockTripRepository.insertTrip(any()) } throws RuntimeException("DB error")

        val result = useCase(input)

        assertTrue(result is CreateGhostTripUseCase.Result.Error)
        assertEquals("DB error", (result as CreateGhostTripUseCase.Result.Error).exception.message)
    }

    @Test
    fun `location resolver exception returns Error result without inserting trip`() = runBlocking {
        val input = validInput()
        coEvery { mockResolveLocationName(any(), any()) } throws RuntimeException("Network error")

        val result = useCase(input)

        assertTrue(result is CreateGhostTripUseCase.Result.Error)
        coVerify(exactly = 0) { mockTripRepository.insertTrip(any()) }
    }

    @Test
    fun `zero GPS distance is stored as-is`() = runBlocking {
        val input = validInput().copy(gpsDistanceKm = 0.0)
        coEvery { mockResolveLocationName(any(), any()) } returns "somewhere"
        coEvery { mockTripRepository.insertTrip(any()) } returns 1L

        val result = useCase(input)

        assertTrue(result is CreateGhostTripUseCase.Result.Success)
        coVerify { mockTripRepository.insertTrip(match { it.gpsDistanceKm == 0.0 }) }
    }
}
