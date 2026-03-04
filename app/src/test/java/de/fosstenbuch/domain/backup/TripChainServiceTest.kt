package de.fosstenbuch.domain.backup

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
import org.junit.Before
import org.junit.Test
import java.util.Date

class TripChainServiceTest {

    private lateinit var service: TripChainService
    private val mockTripRepository: TripRepository = mockk(relaxed = true)
    private val mockVehicleRepository: VehicleRepository = mockk()
    private val chainHashCalculator = TripChainHashCalculator()

    private val auditVehicle = Vehicle(
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

    private fun trip(id: Long, vehicleId: Long? = auditVehicle.id) = Trip(
        id = id,
        date = Date(1700000000000L + id * 86400000),
        startLocation = "City$id",
        endLocation = "City${id + 1}",
        distanceKm = 100.0 + id * 10,
        purpose = "Test",
        vehicleId = vehicleId
    )

    @Before
    fun setup() {
        service = TripChainService(mockTripRepository, mockVehicleRepository, chainHashCalculator)
    }

    @Test
    fun `updateChainHash computes and stores chain hash for audit-protected vehicle`() = runBlocking {
        val t = trip(1L)
        every { mockTripRepository.getTripById(1L) } returns flowOf(t)
        every { mockVehicleRepository.getVehicleById(auditVehicle.id) } returns flowOf(auditVehicle)
        coEvery { mockTripRepository.getTripsForVehicleOrdered(auditVehicle.id) } returns listOf(t)

        service.updateChainHash(1L)

        coVerify(exactly = 1) { mockTripRepository.updateTripChainHash(1L, any()) }
    }

    @Test
    fun `updateChainHash is no-op for non-audit-protected vehicle`() = runBlocking {
        val t = trip(1L, normalVehicle.id)
        every { mockTripRepository.getTripById(1L) } returns flowOf(t)
        every { mockVehicleRepository.getVehicleById(normalVehicle.id) } returns flowOf(normalVehicle)

        service.updateChainHash(1L)

        coVerify(exactly = 0) { mockTripRepository.updateTripChainHash(any(), any()) }
    }

    @Test
    fun `updateChainHash is no-op for trip without vehicle`() = runBlocking {
        val t = trip(1L, vehicleId = null)
        every { mockTripRepository.getTripById(1L) } returns flowOf(t)

        service.updateChainHash(1L)

        coVerify(exactly = 0) { mockTripRepository.updateTripChainHash(any(), any()) }
    }

    @Test
    fun `updateChainHash is no-op for non-existent trip`() = runBlocking {
        every { mockTripRepository.getTripById(999L) } returns flowOf(null)

        service.updateChainHash(999L)

        coVerify(exactly = 0) { mockTripRepository.updateTripChainHash(any(), any()) }
    }

    @Test
    fun `recomputeFullChain updates all trips in vehicle chain`() = runBlocking {
        val trips = listOf(trip(1L), trip(2L), trip(3L))
        coEvery { mockTripRepository.getTripsForVehicleOrdered(auditVehicle.id) } returns trips

        service.recomputeFullChain(auditVehicle.id)

        coVerify(exactly = 3) { mockTripRepository.updateTripChainHash(any(), any()) }
    }

    @Test
    fun `recomputeFullChain skips update when hash already matches`() = runBlocking {
        val t = trip(1L)
        val hash = chainHashCalculator.computeChainHash(t, null)
        val tripWithCorrectHash = t.copy(chainHash = hash)
        coEvery { mockTripRepository.getTripsForVehicleOrdered(auditVehicle.id) } returns listOf(tripWithCorrectHash)

        service.recomputeFullChain(auditVehicle.id)

        coVerify(exactly = 0) { mockTripRepository.updateTripChainHash(any(), any()) }
    }

    @Test
    fun `recomputeFullChain produces verifiable chain`() = runBlocking {
        val trips = listOf(trip(1L), trip(2L), trip(3L))
        val storedHashes = mutableMapOf<Long, String>()

        coEvery { mockTripRepository.getTripsForVehicleOrdered(auditVehicle.id) } returns trips
        coEvery { mockTripRepository.updateTripChainHash(any(), any()) } answers {
            storedHashes[firstArg()] = secondArg()
        }

        service.recomputeFullChain(auditVehicle.id)

        // Verify the stored chain is valid
        val chainedTrips = trips.map { t -> t.copy(chainHash = storedHashes[t.id]) }
        val result = chainHashCalculator.verifyChain(chainedTrips)
        assert(result is TripChainHashCalculator.ChainVerificationResult.Valid)
    }

    @Test
    fun `recomputeFullChain is no-op for vehicle with no trips`() = runBlocking {
        coEvery { mockTripRepository.getTripsForVehicleOrdered(auditVehicle.id) } returns emptyList()

        service.recomputeFullChain(auditVehicle.id)

        coVerify(exactly = 0) { mockTripRepository.updateTripChainHash(any(), any()) }
    }

    @Test
    fun `updateChainHash recomputes entire chain when middle trip changes`() = runBlocking {
        // Simulate updating trip 2 in a chain of 3
        val t1 = trip(1L)
        val t2 = trip(2L).copy(distanceKm = 999.0) // modified
        val t3 = trip(3L)
        val allTrips = listOf(t1, t2, t3)

        every { mockTripRepository.getTripById(2L) } returns flowOf(t2)
        every { mockVehicleRepository.getVehicleById(auditVehicle.id) } returns flowOf(auditVehicle)
        coEvery { mockTripRepository.getTripsForVehicleOrdered(auditVehicle.id) } returns allTrips

        service.updateChainHash(2L)

        // All 3 trips should get updated since none have matching hashes
        coVerify(exactly = 3) { mockTripRepository.updateTripChainHash(any(), any()) }
    }
}
