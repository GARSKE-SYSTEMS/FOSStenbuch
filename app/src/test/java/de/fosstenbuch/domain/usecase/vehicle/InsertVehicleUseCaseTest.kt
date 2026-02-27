package de.fosstenbuch.domain.usecase.vehicle

import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.data.repository.VehicleRepository
import de.fosstenbuch.domain.validation.VehicleValidator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InsertVehicleUseCaseTest {

    private lateinit var useCase: InsertVehicleUseCase
    private val mockRepository: VehicleRepository = mockk()
    private val validator = VehicleValidator()

    @Before
    fun setup() {
        useCase = InsertVehicleUseCase(mockRepository, validator)
    }

    private fun validVehicle() = Vehicle(
        make = "Volkswagen",
        model = "Golf",
        licensePlate = "B AB 1234",
        fuelType = "Diesel"
    )

    @Test
    fun `valid vehicle is inserted successfully`() = runBlocking {
        val vehicle = validVehicle()
        coEvery { mockRepository.insertVehicle(vehicle) } returns 1L

        val result = useCase(vehicle)

        assertTrue(result is InsertVehicleUseCase.Result.Success)
        assertEquals(1L, (result as InsertVehicleUseCase.Result.Success).vehicleId)
    }

    @Test
    fun `invalid vehicle returns validation error`() = runBlocking {
        val vehicle = validVehicle().copy(make = "")

        val result = useCase(vehicle)

        assertTrue(result is InsertVehicleUseCase.Result.ValidationError)
        coVerify(exactly = 0) { mockRepository.insertVehicle(any()) }
    }

    @Test
    fun `primary vehicle clears existing primary first`() = runBlocking {
        val vehicle = validVehicle().copy(isPrimary = true)
        coEvery { mockRepository.clearPrimaryVehicle() } returns Unit
        coEvery { mockRepository.insertVehicle(vehicle) } returns 5L

        val result = useCase(vehicle)

        assertTrue(result is InsertVehicleUseCase.Result.Success)
        coVerify(exactly = 1) { mockRepository.clearPrimaryVehicle() }
        coVerify(exactly = 1) { mockRepository.insertVehicle(vehicle) }
    }

    @Test
    fun `non-primary vehicle does not clear existing primary`() = runBlocking {
        val vehicle = validVehicle().copy(isPrimary = false)
        coEvery { mockRepository.insertVehicle(vehicle) } returns 5L

        useCase(vehicle)

        coVerify(exactly = 0) { mockRepository.clearPrimaryVehicle() }
    }

    @Test
    fun `repository exception returns error result`() = runBlocking {
        val vehicle = validVehicle()
        coEvery { mockRepository.insertVehicle(vehicle) } throws RuntimeException("DB error")

        val result = useCase(vehicle)

        assertTrue(result is InsertVehicleUseCase.Result.Error)
    }

    @Test
    fun `invalid license plate format returns validation error`() = runBlocking {
        val vehicle = validVehicle().copy(licensePlate = "12345")

        val result = useCase(vehicle)

        assertTrue(result is InsertVehicleUseCase.Result.ValidationError)
    }
}
