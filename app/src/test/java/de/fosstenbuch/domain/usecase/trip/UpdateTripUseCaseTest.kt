package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.repository.TripRepository
import de.fosstenbuch.domain.validation.TripValidator
import de.fosstenbuch.domain.validation.ValidationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class UpdateTripUseCaseTest {

    private lateinit var useCase: UpdateTripUseCase
    private val mockRepository: TripRepository = mockk()
    private val validator = TripValidator()

    @Before
    fun setup() {
        useCase = UpdateTripUseCase(mockRepository, validator)
    }

    private fun validTrip() = Trip(
        id = 1L,
        date = Date(),
        startLocation = "Berlin",
        endLocation = "Hamburg",
        distanceKm = 280.0,
        purpose = "Kundentermin",
        purposeId = 1L,
        startOdometer = 50000,
        endOdometer = 50280
    )

    @Test
    fun `valid trip is updated successfully`() = runBlocking {
        val trip = validTrip()
        coEvery { mockRepository.updateTrip(trip) } returns Unit

        val result = useCase(trip)

        assertTrue(result is UpdateTripUseCase.Result.Success)
        coVerify(exactly = 1) { mockRepository.updateTrip(trip) }
    }

    @Test
    fun `invalid trip returns validation error`() = runBlocking {
        val trip = validTrip().copy(startLocation = "")

        val result = useCase(trip)

        assertTrue(result is UpdateTripUseCase.Result.ValidationError)
        coVerify(exactly = 0) { mockRepository.updateTrip(any()) }
    }

    @Test
    fun `repository exception returns error result`() = runBlocking {
        val trip = validTrip()
        coEvery { mockRepository.updateTrip(trip) } throws RuntimeException("DB error")

        val result = useCase(trip)

        assertTrue(result is UpdateTripUseCase.Result.Error)
    }

    @Test
    fun `trip with zero distance returns validation error`() = runBlocking {
        val trip = validTrip().copy(distanceKm = 0.0)

        val result = useCase(trip)

        assertTrue(result is UpdateTripUseCase.Result.ValidationError)
        val errors = (result as UpdateTripUseCase.Result.ValidationError).validation
        assertTrue(errors.errorFor(TripValidator.FIELD_DISTANCE) != null)
    }
}
