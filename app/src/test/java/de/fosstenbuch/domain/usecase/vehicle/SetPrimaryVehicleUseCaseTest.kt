package de.fosstenbuch.domain.usecase.vehicle

import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.data.repository.VehicleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class SetPrimaryVehicleUseCaseTest {

    private lateinit var useCase: SetPrimaryVehicleUseCase
    private val mockRepository: VehicleRepository = mockk()

    private val testVehicle = Vehicle(
        id = 1L,
        make = "BMW",
        model = "320d",
        licensePlate = "B AB 1234",
        fuelType = "Diesel",
        isPrimary = false
    )

    @Before
    fun setup() {
        useCase = SetPrimaryVehicleUseCase(mockRepository)
    }

    @Test
    fun `sets vehicle as primary after clearing existing`() = runBlocking {
        coEvery { mockRepository.clearPrimaryVehicle() } returns Unit
        coEvery { mockRepository.getVehicleById(1L) } returns flowOf(testVehicle)
        coEvery { mockRepository.updateVehicle(any()) } returns Unit

        useCase(1L)

        coVerifyOrder {
            mockRepository.clearPrimaryVehicle()
            mockRepository.getVehicleById(1L)
            mockRepository.updateVehicle(testVehicle.copy(isPrimary = true))
        }
    }

    @Test
    fun `clears primary vehicle before setting new one`() = runBlocking {
        coEvery { mockRepository.clearPrimaryVehicle() } returns Unit
        coEvery { mockRepository.getVehicleById(1L) } returns flowOf(testVehicle)
        coEvery { mockRepository.updateVehicle(any()) } returns Unit

        useCase(1L)

        coVerify(exactly = 1) { mockRepository.clearPrimaryVehicle() }
    }

    @Test
    fun `throws when vehicle not found`(): Unit = runBlocking {
        coEvery { mockRepository.clearPrimaryVehicle() } returns Unit
        coEvery { mockRepository.getVehicleById(999L) } returns flowOf(null)

        try {
            useCase(999L)
            assert(false) { "Should have thrown IllegalArgumentException" }
        } catch (e: IllegalArgumentException) {
            assertEquals("Vehicle with ID 999 not found", e.message)
        }
    }

    @Test
    fun `updates vehicle with isPrimary set to true`() = runBlocking {
        coEvery { mockRepository.clearPrimaryVehicle() } returns Unit
        coEvery { mockRepository.getVehicleById(1L) } returns flowOf(testVehicle)
        coEvery { mockRepository.updateVehicle(any()) } returns Unit

        useCase(1L)

        coVerify { mockRepository.updateVehicle(testVehicle.copy(isPrimary = true)) }
    }
}
