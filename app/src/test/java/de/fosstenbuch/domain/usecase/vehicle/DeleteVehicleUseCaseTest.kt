package de.fosstenbuch.domain.usecase.vehicle

import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.data.repository.VehicleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class DeleteVehicleUseCaseTest {

    private lateinit var useCase: DeleteVehicleUseCase
    private val mockRepository: VehicleRepository = mockk()

    private val testVehicle = Vehicle(
        id = 1L,
        make = "BMW",
        model = "320d",
        licensePlate = "B AB 1234",
        fuelType = "Diesel"
    )

    @Before
    fun setup() {
        useCase = DeleteVehicleUseCase(mockRepository)
    }

    @Test
    fun `invoke delegates to repository`() = runBlocking {
        coEvery { mockRepository.deleteVehicle(testVehicle) } returns Unit

        useCase(testVehicle)

        coVerify(exactly = 1) { mockRepository.deleteVehicle(testVehicle) }
    }
}
