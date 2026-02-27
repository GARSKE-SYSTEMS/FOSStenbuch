package de.fosstenbuch.domain.usecase.audit

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

class IsVehicleAuditProtectedUseCaseTest {

    private lateinit var useCase: IsVehicleAuditProtectedUseCase
    private val mockRepository: VehicleRepository = mockk()

    @Before
    fun setup() {
        useCase = IsVehicleAuditProtectedUseCase(mockRepository)
    }

    @Test
    fun `returns true when vehicle is audit protected`() = runBlocking {
        val vehicle = Vehicle(1L, "BMW", "320d", "B AB 1234", "Diesel", auditProtected = true)
        every { mockRepository.getVehicleById(1L) } returns flowOf(vehicle)

        val result = useCase(1L).first()

        assertEquals(true, result)
    }

    @Test
    fun `returns false when vehicle is not audit protected`() = runBlocking {
        val vehicle = Vehicle(1L, "BMW", "320d", "B AB 1234", "Diesel", auditProtected = false)
        every { mockRepository.getVehicleById(1L) } returns flowOf(vehicle)

        val result = useCase(1L).first()

        assertEquals(false, result)
    }

    @Test
    fun `returns false when vehicle is null`() = runBlocking {
        every { mockRepository.getVehicleById(999L) } returns flowOf(null)

        val result = useCase(999L).first()

        assertEquals(false, result)
    }
}
