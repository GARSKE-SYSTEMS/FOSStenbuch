package de.fosstenbuch.data.repository

import de.fosstenbuch.data.local.VehicleDao
import de.fosstenbuch.data.model.Vehicle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class VehicleRepositoryTest {

    private lateinit var repository: VehicleRepositoryImpl
    private val mockDao: VehicleDao = mockk()

    private val testVehicle = Vehicle(
        id = 1L,
        make = "BMW",
        model = "320d",
        licensePlate = "B AB 1234",
        fuelType = "Diesel",
        isPrimary = true
    )

    @Before
    fun setup() {
        repository = VehicleRepositoryImpl(mockDao)
    }

    @Test
    fun `getAllVehicles delegates to dao`() = runBlocking {
        val vehicles = listOf(testVehicle, testVehicle.copy(id = 2L, make = "Audi"))
        coEvery { mockDao.getAllVehicles() } returns flowOf(vehicles)

        val result = repository.getAllVehicles().first()

        assertEquals(2, result.size)
        assertEquals("BMW", result[0].make)
    }

    @Test
    fun `getVehicleById delegates to dao`() = runBlocking {
        coEvery { mockDao.getVehicleById(1L) } returns flowOf(testVehicle)

        val result = repository.getVehicleById(1L).first()

        assertEquals("BMW", result?.make)
    }

    @Test
    fun `getVehicleById returns null for non-existent id`() = runBlocking {
        coEvery { mockDao.getVehicleById(999L) } returns flowOf(null)

        val result = repository.getVehicleById(999L).first()

        assertNull(result)
    }

    @Test
    fun `getPrimaryVehicle delegates to dao`() = runBlocking {
        coEvery { mockDao.getPrimaryVehicle() } returns flowOf(testVehicle)

        val result = repository.getPrimaryVehicle().first()

        assertEquals(true, result?.isPrimary)
    }

    @Test
    fun `insertVehicle delegates to dao and returns id`() = runBlocking {
        coEvery { mockDao.insertVehicle(testVehicle) } returns 1L

        val result = repository.insertVehicle(testVehicle)

        assertEquals(1L, result)
        coVerify(exactly = 1) { mockDao.insertVehicle(testVehicle) }
    }

    @Test
    fun `updateVehicle delegates to dao`() = runBlocking {
        coEvery { mockDao.updateVehicle(testVehicle) } returns Unit

        repository.updateVehicle(testVehicle)

        coVerify(exactly = 1) { mockDao.updateVehicle(testVehicle) }
    }

    @Test
    fun `deleteVehicle delegates to dao`() = runBlocking {
        coEvery { mockDao.deleteVehicle(testVehicle) } returns Unit

        repository.deleteVehicle(testVehicle)

        coVerify(exactly = 1) { mockDao.deleteVehicle(testVehicle) }
    }

    @Test
    fun `deleteAllVehicles delegates to dao`() = runBlocking {
        coEvery { mockDao.deleteAllVehicles() } returns Unit

        repository.deleteAllVehicles()

        coVerify(exactly = 1) { mockDao.deleteAllVehicles() }
    }

    @Test
    fun `clearPrimaryVehicle delegates to dao`() = runBlocking {
        coEvery { mockDao.clearPrimaryVehicle() } returns Unit

        repository.clearPrimaryVehicle()

        coVerify(exactly = 1) { mockDao.clearPrimaryVehicle() }
    }
}
