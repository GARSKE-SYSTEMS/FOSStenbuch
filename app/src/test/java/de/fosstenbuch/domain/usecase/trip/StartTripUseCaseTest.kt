package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.repository.TripRepository
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

class StartTripUseCaseTest {

    private lateinit var useCase: StartTripUseCase
    private val mockRepository: TripRepository = mockk()
    private val validator = TripValidator()

    @Before
    fun setup() {
        useCase = StartTripUseCase(mockRepository, validator)
    }

    private fun validStartTrip() = Trip(
        date = Date(),
        startLocation = "Berlin",
        startOdometer = 50000
    )

    @Test
    fun `valid start trip is inserted with isActive true`() = runBlocking {
        val trip = validStartTrip()
        coEvery { mockRepository.insertTrip(any()) } returns 42L

        val result = useCase(trip)

        assertTrue(result is StartTripUseCase.Result.Success)
        assertEquals(42L, (result as StartTripUseCase.Result.Success).tripId)
        coVerify(exactly = 1) { mockRepository.insertTrip(match { it.isActive }) }
    }

    @Test
    fun `blank start location returns validation error`() = runBlocking {
        val trip = validStartTrip().copy(startLocation = "")

        val result = useCase(trip)

        assertTrue(result is StartTripUseCase.Result.ValidationError)
        coVerify(exactly = 0) { mockRepository.insertTrip(any()) }
    }

    @Test
    fun `null start odometer returns validation error`() = runBlocking {
        val trip = validStartTrip().copy(startOdometer = null)

        val result = useCase(trip)

        assertTrue(result is StartTripUseCase.Result.ValidationError)
        coVerify(exactly = 0) { mockRepository.insertTrip(any()) }
    }

    @Test
    fun `negative start odometer returns validation error`() = runBlocking {
        val trip = validStartTrip().copy(startOdometer = -100)

        val result = useCase(trip)

        assertTrue(result is StartTripUseCase.Result.ValidationError)
        coVerify(exactly = 0) { mockRepository.insertTrip(any()) }
    }

    @Test
    fun `repository exception returns error result`() = runBlocking {
        val trip = validStartTrip()
        coEvery { mockRepository.insertTrip(any()) } throws RuntimeException("DB error")

        val result = useCase(trip)

        assertTrue(result is StartTripUseCase.Result.Error)
        assertEquals("DB error", (result as StartTripUseCase.Result.Error).exception.message)
    }

    @Test
    fun `start location exceeding max length returns validation error`() = runBlocking {
        val trip = validStartTrip().copy(startLocation = "A".repeat(201))

        val result = useCase(trip)

        assertTrue(result is StartTripUseCase.Result.ValidationError)
        coVerify(exactly = 0) { mockRepository.insertTrip(any()) }
    }
}
