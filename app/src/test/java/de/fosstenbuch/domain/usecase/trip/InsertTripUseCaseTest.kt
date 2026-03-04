package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.repository.TripRepository
import de.fosstenbuch.domain.backup.TripChainService
import de.fosstenbuch.domain.validation.TripValidator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class InsertTripUseCaseTest {

    private lateinit var useCase: InsertTripUseCase
    private val mockRepository: TripRepository = mockk()
    private val validator = TripValidator()
    private val mockTripChainService: TripChainService = mockk(relaxed = true)

    @Before
    fun setup() {
        useCase = InsertTripUseCase(mockRepository, validator, mockTripChainService)
    }

    @Test
    fun `valid trip is inserted successfully`() = runBlocking {
        val trip = Trip(
            date = Date(),
            startLocation = "Berlin",
            endLocation = "Hamburg",
            distanceKm = 280.0,
            purpose = "Kundentermin",
            purposeId = 1L,
            vehicleId = 1L,
            startOdometer = 50000,
            endOdometer = 50280
        )
        coEvery { mockRepository.insertTrip(trip) } returns 42L

        val result = useCase(trip)

        assertTrue(result is InsertTripUseCase.Result.Success)
        assertEquals(42L, (result as InsertTripUseCase.Result.Success).tripId)
        coVerify(exactly = 1) { mockRepository.insertTrip(trip) }
    }

    @Test
    fun `invalid trip returns validation error without calling repository`() = runBlocking {
        val trip = Trip(
            date = Date(),
            startLocation = "",  // invalid
            endLocation = "Hamburg",
            distanceKm = 280.0,
            purpose = "Kundentermin",
            purposeId = 1L,
            vehicleId = 1L,
            startOdometer = 50000,
            endOdometer = 50280
        )

        val result = useCase(trip)

        assertTrue(result is InsertTripUseCase.Result.ValidationError)
        coVerify(exactly = 0) { mockRepository.insertTrip(any()) }
    }

    @Test
    fun `repository exception returns error result`() = runBlocking {
        val trip = Trip(
            date = Date(),
            startLocation = "Berlin",
            endLocation = "Hamburg",
            distanceKm = 280.0,
            purpose = "Kundentermin",
            purposeId = 1L,
            vehicleId = 1L,
            startOdometer = 50000,
            endOdometer = 50280
        )
        coEvery { mockRepository.insertTrip(trip) } throws RuntimeException("DB error")

        val result = useCase(trip)

        assertTrue(result is InsertTripUseCase.Result.Error)
    }
}
