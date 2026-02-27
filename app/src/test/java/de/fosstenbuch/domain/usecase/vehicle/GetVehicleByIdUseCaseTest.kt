package de.fosstenbuch.domain.usecase.vehicle

import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.data.repository.VehicleRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class GetVehicleByIdUseCaseTest {

    private lateinit var useCase: GetVehicleByIdUseCase
    private val mockRepository: VehicleRepository = mockk()

    @Before
    fun setup() {
        useCase = GetVehicleByIdUseCase(mockRepository)
    }

    @Test
    fun `invoke returns vehicle by id`() = runBlocking {
        val vehicle = Vehicle(1L, "BMW", "320d", "B AB 1234", "Diesel")
        every { mockRepository.getVehicleById(1L) } returns flowOf(vehicle)

        val result = useCase(1L).first()

        assertEquals("BMW", result?.make)
        assertEquals(1L, result?.id)
    }

    @Test
    fun `invoke returns null for non-existent id`() = runBlocking {
        every { mockRepository.getVehicleById(999L) } returns flowOf(null)

        val result = useCase(999L).first()

        assertNull(result)
    }
}
