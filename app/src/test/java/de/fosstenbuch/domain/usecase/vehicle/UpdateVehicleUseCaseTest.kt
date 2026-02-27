package de.fosstenbuch.domain.usecase.vehicle

import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.data.repository.VehicleRepository
import de.fosstenbuch.domain.validation.VehicleValidator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateVehicleUseCaseTest {

    private lateinit var useCase: UpdateVehicleUseCase
    private val mockRepository: VehicleRepository = mockk()
    private val validator = VehicleValidator()

    @Before
    fun setup() {
        useCase = UpdateVehicleUseCase(mockRepository, validator)
    }

    private fun validVehicle() = Vehicle(
        id = 1L,
        make = "Volkswagen",
        model = "Golf",
        licensePlate = "B AB 1234",
        fuelType = "Diesel"
    )

    @Test
    fun `valid vehicle is updated successfully`() = runBlocking {
        val vehicle = validVehicle()
        coEvery { mockRepository.updateVehicle(vehicle) } returns Unit

        val result = useCase(vehicle)

        assertTrue(result is UpdateVehicleUseCase.Result.Success)
        coVerify(exactly = 1) { mockRepository.updateVehicle(vehicle) }
    }

    @Test
    fun `invalid vehicle returns validation error`() = runBlocking {
        val vehicle = validVehicle().copy(model = "")

        val result = useCase(vehicle)

        assertTrue(result is UpdateVehicleUseCase.Result.ValidationError)
        coVerify(exactly = 0) { mockRepository.updateVehicle(any()) }
    }

    @Test
    fun `primary vehicle clears existing primary first`() = runBlocking {
        val vehicle = validVehicle().copy(isPrimary = true)
        coEvery { mockRepository.clearPrimaryVehicle() } returns Unit
        coEvery { mockRepository.updateVehicle(vehicle) } returns Unit

        val result = useCase(vehicle)

        assertTrue(result is UpdateVehicleUseCase.Result.Success)
        coVerify(exactly = 1) { mockRepository.clearPrimaryVehicle() }
    }

    @Test
    fun `repository exception returns error result`() = runBlocking {
        val vehicle = validVehicle()
        coEvery { mockRepository.updateVehicle(vehicle) } throws RuntimeException("DB error")

        val result = useCase(vehicle)

        assertTrue(result is UpdateVehicleUseCase.Result.Error)
    }
}
