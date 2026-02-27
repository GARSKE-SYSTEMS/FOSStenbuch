package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.data.repository.TripRepository
import de.fosstenbuch.data.repository.VehicleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class DeleteTripUseCaseTest {

    private lateinit var useCase: DeleteTripUseCase
    private val mockTripRepository: TripRepository = mockk()
    private val mockVehicleRepository: VehicleRepository = mockk()

    private val auditProtectedVehicle = Vehicle(
        id = 10L,
        make = "BMW",
        model = "320d",
        licensePlate = "B AB 1234",
        fuelType = "Diesel",
        auditProtected = true
    )

    private val normalVehicle = Vehicle(
        id = 20L,
        make = "VW",
        model = "Golf",
        licensePlate = "M XY 5678",
        fuelType = "Benzin",
        auditProtected = false
    )

    private val tripWithAuditProtectedVehicle = Trip(
        id = 1L,
        date = Date(),
        startLocation = "Berlin",
        endLocation = "Hamburg",
        distanceKm = 280.0,
        purpose = "Kundentermin",
        purposeId = 1L,
        vehicleId = auditProtectedVehicle.id
    )

    private val tripWithNormalVehicle = Trip(
        id = 2L,
        date = Date(),
        startLocation = "München",
        endLocation = "Stuttgart",
        distanceKm = 230.0,
        purpose = "Privat",
        purposeId = 2L,
        vehicleId = normalVehicle.id
    )

    private val tripWithoutVehicle = Trip(
        id = 3L,
        date = Date(),
        startLocation = "Köln",
        endLocation = "Bonn",
        distanceKm = 30.0,
        purpose = "Einkauf",
        vehicleId = null
    )

    @Before
    fun setup() {
        useCase = DeleteTripUseCase(mockTripRepository, mockVehicleRepository)
    }

    @Test
    fun `deleting trip with audit-protected vehicle returns AuditProtected`() = runBlocking {
        every { mockVehicleRepository.getVehicleById(auditProtectedVehicle.id) } returns flowOf(auditProtectedVehicle)

        val result = useCase(tripWithAuditProtectedVehicle)

        assertTrue(result is DeleteTripUseCase.Result.AuditProtected)
    }

    @Test
    fun `deleting trip with audit-protected vehicle does not call repository delete`() = runBlocking {
        every { mockVehicleRepository.getVehicleById(auditProtectedVehicle.id) } returns flowOf(auditProtectedVehicle)

        useCase(tripWithAuditProtectedVehicle)

        coVerify(exactly = 0) { mockTripRepository.deleteTrip(any()) }
    }

    @Test
    fun `deleting trip with normal vehicle returns Success`() = runBlocking {
        every { mockVehicleRepository.getVehicleById(normalVehicle.id) } returns flowOf(normalVehicle)
        coEvery { mockTripRepository.deleteTrip(tripWithNormalVehicle) } returns Unit

        val result = useCase(tripWithNormalVehicle)

        assertTrue(result is DeleteTripUseCase.Result.Success)
    }

    @Test
    fun `deleting trip with normal vehicle calls repository delete`() = runBlocking {
        every { mockVehicleRepository.getVehicleById(normalVehicle.id) } returns flowOf(normalVehicle)
        coEvery { mockTripRepository.deleteTrip(tripWithNormalVehicle) } returns Unit

        useCase(tripWithNormalVehicle)

        coVerify(exactly = 1) { mockTripRepository.deleteTrip(tripWithNormalVehicle) }
    }

    @Test
    fun `deleting trip without vehicle returns Success`() = runBlocking {
        coEvery { mockTripRepository.deleteTrip(tripWithoutVehicle) } returns Unit

        val result = useCase(tripWithoutVehicle)

        assertTrue(result is DeleteTripUseCase.Result.Success)
    }

    @Test
    fun `deleting trip without vehicle calls repository delete`() = runBlocking {
        coEvery { mockTripRepository.deleteTrip(tripWithoutVehicle) } returns Unit

        useCase(tripWithoutVehicle)

        coVerify(exactly = 1) { mockTripRepository.deleteTrip(tripWithoutVehicle) }
    }

    @Test
    fun `deleting trip when vehicle not found in repository returns Success`() = runBlocking {
        every { mockVehicleRepository.getVehicleById(999L) } returns flowOf(null)
        val tripWithMissingVehicle = tripWithNormalVehicle.copy(vehicleId = 999L)
        coEvery { mockTripRepository.deleteTrip(tripWithMissingVehicle) } returns Unit

        val result = useCase(tripWithMissingVehicle)

        assertTrue(result is DeleteTripUseCase.Result.Success)
    }

    @Test
    fun `deleting trip when repository throws exception returns Error`() = runBlocking {
        every { mockVehicleRepository.getVehicleById(normalVehicle.id) } returns flowOf(normalVehicle)
        val exception = RuntimeException("Database error")
        coEvery { mockTripRepository.deleteTrip(tripWithNormalVehicle) } throws exception

        val result = useCase(tripWithNormalVehicle)

        assertTrue(result is DeleteTripUseCase.Result.Error)
        assertEquals(exception, (result as DeleteTripUseCase.Result.Error).exception)
    }
}
