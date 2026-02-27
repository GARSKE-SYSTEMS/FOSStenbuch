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

class GetPrimaryVehicleUseCaseTest {

    private lateinit var useCase: GetPrimaryVehicleUseCase
    private val mockRepository: VehicleRepository = mockk()

    @Before
    fun setup() {
        useCase = GetPrimaryVehicleUseCase(mockRepository)
    }

    @Test
    fun `invoke returns primary vehicle`() = runBlocking {
        val vehicle = Vehicle(1L, "BMW", "320d", "B AB 1234", "Diesel", isPrimary = true)
        every { mockRepository.getPrimaryVehicle() } returns flowOf(vehicle)

        val result = useCase().first()

        assertEquals("BMW", result?.make)
        assertEquals(true, result?.isPrimary)
    }

    @Test
    fun `invoke returns null when no primary vehicle`() = runBlocking {
        every { mockRepository.getPrimaryVehicle() } returns flowOf(null)

        val result = useCase().first()

        assertNull(result)
    }
}
