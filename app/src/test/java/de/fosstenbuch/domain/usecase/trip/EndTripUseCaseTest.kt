package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.repository.TripRepository
import de.fosstenbuch.domain.backup.TripChainService
import de.fosstenbuch.domain.validation.TripValidator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class EndTripUseCaseTest {

    private lateinit var useCase: EndTripUseCase
    private val mockRepository: TripRepository = mockk()
    private val validator = TripValidator()
    private val mockTripChainService: TripChainService = mockk(relaxed = true)

    @Before
    fun setup() {
        useCase = EndTripUseCase(mockRepository, validator, mockTripChainService)
    }

    private fun validEndTrip() = Trip(
        id = 1L,
        date = Date(),
        startLocation = "Berlin",
        endLocation = "Hamburg",
        distanceKm = 280.0,
        purpose = "Kundentermin",
        purposeId = 1L,
        vehicleId = 1L,
        startOdometer = 50000,
        endOdometer = 50280,
        isActive = true
    )

    @Test
    fun `valid trip is ended successfully with isActive false`() = runBlocking {
        val trip = validEndTrip()
        coEvery { mockRepository.updateTrip(any()) } returns Unit

        val result = useCase(trip)

        assertTrue(result is EndTripUseCase.Result.Success)
        coVerify(exactly = 1) { mockRepository.updateTrip(match { !it.isActive }) }
    }

    @Test
    fun `blank end location returns validation error`() = runBlocking {
        val trip = validEndTrip().copy(endLocation = "")

        val result = useCase(trip)

        assertTrue(result is EndTripUseCase.Result.ValidationError)
        coVerify(exactly = 0) { mockRepository.updateTrip(any()) }
    }

    @Test
    fun `zero distance returns validation error`() = runBlocking {
        val trip = validEndTrip().copy(distanceKm = 0.0)

        val result = useCase(trip)

        assertTrue(result is EndTripUseCase.Result.ValidationError)
        coVerify(exactly = 0) { mockRepository.updateTrip(any()) }
    }

    @Test
    fun `blank purpose passes validation`() = runBlocking {
        val trip = validEndTrip().copy(purpose = "")
        coEvery { mockRepository.updateTrip(any()) } returns Unit

        val result = useCase(trip)

        assertTrue(result is EndTripUseCase.Result.Success)
    }

    @Test
    fun `null purposeId returns validation error`() = runBlocking {
        val trip = validEndTrip().copy(purposeId = null)

        val result = useCase(trip)

        assertTrue(result is EndTripUseCase.Result.ValidationError)
        coVerify(exactly = 0) { mockRepository.updateTrip(any()) }
    }

    @Test
    fun `repository exception returns error result`() = runBlocking {
        val trip = validEndTrip()
        coEvery { mockRepository.updateTrip(any()) } throws RuntimeException("DB error")

        val result = useCase(trip)

        assertTrue(result is EndTripUseCase.Result.Error)
    }

    @Test
    fun `multiple validation errors are returned`() = runBlocking {
        val trip = validEndTrip().copy(
            startLocation = "",
            endLocation = "",
            distanceKm = -1.0,
            purposeId = null
        )

        val result = useCase(trip)

        assertTrue(result is EndTripUseCase.Result.ValidationError)
        val validation = (result as EndTripUseCase.Result.ValidationError).validation
        assertTrue(validation.errors.size >= 4)
    }
}
