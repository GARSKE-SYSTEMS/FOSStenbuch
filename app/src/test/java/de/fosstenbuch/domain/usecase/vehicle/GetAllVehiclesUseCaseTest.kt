package de.fosstenbuch.domain.usecase.vehicle

import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.data.repository.VehicleRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetAllVehiclesUseCaseTest {

    private lateinit var useCase: GetAllVehiclesUseCase
    private val mockRepository: VehicleRepository = mockk()

    @Before
    fun setup() {
        useCase = GetAllVehiclesUseCase(mockRepository)
    }

    @Test
    fun `invoke returns flow from repository`() = runBlocking {
        val vehicles = listOf(
            Vehicle(1L, "BMW", "320d", "B AB 1234", "Diesel"),
            Vehicle(2L, "Audi", "A4", "M CD 5678", "Benzin")
        )
        every { mockRepository.getAllVehicles() } returns flowOf(vehicles)

        val result = useCase().first()

        assertEquals(2, result.size)
        assertEquals("BMW", result[0].make)
        assertEquals("Audi", result[1].make)
    }

    @Test
    fun `invoke returns empty list when no vehicles`() = runBlocking {
        every { mockRepository.getAllVehicles() } returns flowOf(emptyList())

        val result = useCase().first()

        assertEquals(0, result.size)
    }
}
