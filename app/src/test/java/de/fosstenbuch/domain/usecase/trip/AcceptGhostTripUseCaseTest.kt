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

class AcceptGhostTripUseCaseTest {

    private lateinit var useCase: AcceptGhostTripUseCase
    private val mockTripRepository: TripRepository = mockk()
    private val validator = TripValidator()
    private val mockTripChainService: TripChainService = mockk(relaxed = true)

    @Before
    fun setup() {
        useCase = AcceptGhostTripUseCase(mockTripRepository, validator, mockTripChainService)
    }

    private fun validGhostTrip() = Trip(
        id = 1L,
        date = Date(1700000000000L),
        endTime = Date(1700003600000L),
        startLocation = "Berlin Mitte",
        endLocation = "Hamburg Altstadt",
        distanceKm = 280.0,
        purpose = "Kundentermin",
        purposeId = 1L,
        vehicleId = 1L,
        startOdometer = 50000,
        endOdometer = 50280,
        isGhost = true,
        isActive = false
    )

    // ── Happy path ──────────────────────────────────────────────────────────

    @Test
    fun `valid ghost trip is accepted successfully`() = runBlocking {
        val trip = validGhostTrip()
        coEvery { mockTripRepository.updateTrip(any()) } returns Unit

        val result = useCase(trip)

        assertTrue(result is AcceptGhostTripUseCase.Result.Success)
    }

    @Test
    fun `accepted trip is saved with isGhost false and isActive false`() = runBlocking {
        val trip = validGhostTrip()
        coEvery { mockTripRepository.updateTrip(any()) } returns Unit

        useCase(trip)

        coVerify { mockTripRepository.updateTrip(match { !it.isGhost && !it.isActive }) }
    }

    @Test
    fun `accepting a ghost trip triggers chain hash update`() = runBlocking {
        val trip = validGhostTrip()
        coEvery { mockTripRepository.updateTrip(any()) } returns Unit

        useCase(trip)

        coVerify { mockTripChainService.updateChainHash(trip.id) }
    }

    // ── Validation failures ──────────────────────────────────────────────────

    @Test
    fun `blank start location returns ValidationError`() = runBlocking {
        val trip = validGhostTrip().copy(startLocation = "")

        val result = useCase(trip)

        assertTrue(result is AcceptGhostTripUseCase.Result.ValidationError)
        coVerify(exactly = 0) { mockTripRepository.updateTrip(any()) }
    }

    @Test
    fun `blank end location returns ValidationError`() = runBlocking {
        val trip = validGhostTrip().copy(endLocation = "  ")

        val result = useCase(trip)

        assertTrue(result is AcceptGhostTripUseCase.Result.ValidationError)
        coVerify(exactly = 0) { mockTripRepository.updateTrip(any()) }
    }

    @Test
    fun `null purposeId returns ValidationError`() = runBlocking {
        val trip = validGhostTrip().copy(purposeId = null)

        val result = useCase(trip)

        assertTrue(result is AcceptGhostTripUseCase.Result.ValidationError)
        coVerify(exactly = 0) { mockTripRepository.updateTrip(any()) }
    }

    @Test
    fun `zero distance returns ValidationError`() = runBlocking {
        val trip = validGhostTrip().copy(distanceKm = 0.0)

        val result = useCase(trip)

        assertTrue(result is AcceptGhostTripUseCase.Result.ValidationError)
    }

    @Test
    fun `negative distance returns ValidationError`() = runBlocking {
        val trip = validGhostTrip().copy(distanceKm = -5.0)

        val result = useCase(trip)

        assertTrue(result is AcceptGhostTripUseCase.Result.ValidationError)
    }

    @Test
    fun `multiple validation errors accumulate`() = runBlocking {
        val trip = validGhostTrip().copy(
            startLocation = "",
            endLocation = "",
            purposeId = null,
            distanceKm = -1.0
        )

        val result = useCase(trip)

        assertTrue(result is AcceptGhostTripUseCase.Result.ValidationError)
        val errors = (result as AcceptGhostTripUseCase.Result.ValidationError).validation.errors
        assertTrue("Expected ≥3 errors, got ${errors.size}", errors.size >= 3)
    }

    @Test
    fun `chain hash is NOT updated when validation fails`() = runBlocking {
        val trip = validGhostTrip().copy(startLocation = "")

        useCase(trip)

        coVerify(exactly = 0) { mockTripChainService.updateChainHash(any()) }
    }

    // ── Error handling ───────────────────────────────────────────────────────

    @Test
    fun `repository exception returns Error result`() = runBlocking {
        val trip = validGhostTrip()
        coEvery { mockTripRepository.updateTrip(any()) } throws RuntimeException("DB error")

        val result = useCase(trip)

        assertTrue(result is AcceptGhostTripUseCase.Result.Error)
    }
}
