package de.fosstenbuch.domain.backup

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.model.TripAuditLog
import de.fosstenbuch.data.model.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class IntegrityHashCalculatorTest {

    private lateinit var calculator: IntegrityHashCalculator

    @Before
    fun setup() {
        calculator = IntegrityHashCalculator()
    }

    // --- Test helpers ---

    private fun auditVehicle(id: Long = 1L) = Vehicle(
        id = id,
        make = "BMW",
        model = "320d",
        licensePlate = "B AB 1234",
        fuelType = "Diesel",
        auditProtected = true
    )

    private fun normalVehicle(id: Long = 2L) = Vehicle(
        id = id,
        make = "VW",
        model = "Golf",
        licensePlate = "M XY 5678",
        fuelType = "Benzin",
        auditProtected = false
    )

    private fun trip(id: Long = 1L, vehicleId: Long? = 1L) = Trip(
        id = id,
        date = Date(1700000000000L),
        startLocation = "Berlin",
        endLocation = "Hamburg",
        distanceKm = 280.0,
        purpose = "Kundentermin",
        purposeId = 1L,
        vehicleId = vehicleId,
        startOdometer = 50000,
        endOdometer = 50280
    )

    private fun auditLog(id: Long = 1L, tripId: Long = 1L) = TripAuditLog(
        id = id,
        tripId = tripId,
        fieldName = "distanceKm",
        oldValue = "100.0",
        newValue = "280.0",
        changedAt = Date(1700000100000L)
    )

    // --- computeHashes tests ---

    @Test
    fun `computeHashes returns empty map when no audit-protected vehicles`() {
        val vehicles = listOf(normalVehicle())
        val trips = listOf(trip(vehicleId = 2L))
        val result = calculator.computeHashes(vehicles, trips, emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `computeHashes returns hash only for audit-protected vehicles`() {
        val vehicles = listOf(auditVehicle(1L), normalVehicle(2L))
        val trips = listOf(trip(1L, 1L), trip(2L, 2L))
        val result = calculator.computeHashes(vehicles, trips, emptyList())

        assertEquals(1, result.size)
        assertTrue(result.containsKey(1L))
    }

    @Test
    fun `computeHashes produces valid SHA-256 hex string`() {
        val vehicles = listOf(auditVehicle())
        val trips = listOf(trip())
        val result = calculator.computeHashes(vehicles, trips, emptyList())

        val hash = result[1L]!!
        assertEquals(64, hash.length) // SHA-256 = 32 bytes = 64 hex chars
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `computeHashes is deterministic`() {
        val vehicles = listOf(auditVehicle())
        val trips = listOf(trip())
        val logs = listOf(auditLog())

        val hash1 = calculator.computeHashes(vehicles, trips, logs)
        val hash2 = calculator.computeHashes(vehicles, trips, logs)

        assertEquals(hash1, hash2)
    }

    @Test
    fun `computeHashes changes when trip data is modified`() {
        val vehicles = listOf(auditVehicle())
        val originalTrip = trip()
        val modifiedTrip = originalTrip.copy(distanceKm = 999.0)

        val hash1 = calculator.computeHashes(vehicles, listOf(originalTrip), emptyList())
        val hash2 = calculator.computeHashes(vehicles, listOf(modifiedTrip), emptyList())

        assertNotEquals(hash1[1L], hash2[1L])
    }

    @Test
    fun `computeHashes changes when audit log is modified`() {
        val vehicles = listOf(auditVehicle())
        val trips = listOf(trip())
        val originalLog = auditLog()
        val modifiedLog = originalLog.copy(newValue = "999.0")

        val hash1 = calculator.computeHashes(vehicles, trips, listOf(originalLog))
        val hash2 = calculator.computeHashes(vehicles, trips, listOf(modifiedLog))

        assertNotEquals(hash1[1L], hash2[1L])
    }

    @Test
    fun `computeHashes changes when audit log is removed`() {
        val vehicles = listOf(auditVehicle())
        val trips = listOf(trip())
        val log = auditLog()

        val hashWithLog = calculator.computeHashes(vehicles, trips, listOf(log))
        val hashWithoutLog = calculator.computeHashes(vehicles, trips, emptyList())

        assertNotEquals(hashWithLog[1L], hashWithoutLog[1L])
    }

    @Test
    fun `computeHashes changes when trip is added`() {
        val vehicles = listOf(auditVehicle())
        val trip1 = trip(1L, 1L)
        val trip2 = trip(2L, 1L)

        val hash1 = calculator.computeHashes(vehicles, listOf(trip1), emptyList())
        val hash2 = calculator.computeHashes(vehicles, listOf(trip1, trip2), emptyList())

        assertNotEquals(hash1[1L], hash2[1L])
    }

    @Test
    fun `computeHashes changes when trip is removed`() {
        val vehicles = listOf(auditVehicle())
        val trip1 = trip(1L, 1L)
        val trip2 = trip(2L, 1L)

        val hashBoth = calculator.computeHashes(vehicles, listOf(trip1, trip2), emptyList())
        val hashOne = calculator.computeHashes(vehicles, listOf(trip1), emptyList())

        assertNotEquals(hashBoth[1L], hashOne[1L])
    }

    @Test
    fun `computeHashes includes vehicle identity in hash`() {
        val vehicle1 = auditVehicle(1L).copy(licensePlate = "B AB 1234")
        val vehicle2 = auditVehicle(1L).copy(licensePlate = "M XY 9999")
        val trips = listOf(trip())

        val hash1 = calculator.computeHashes(listOf(vehicle1), trips, emptyList())
        val hash2 = calculator.computeHashes(listOf(vehicle2), trips, emptyList())

        assertNotEquals(hash1[1L], hash2[1L])
    }

    @Test
    fun `computeHashes handles vehicle with no trips`() {
        val vehicles = listOf(auditVehicle())
        val result = calculator.computeHashes(vehicles, emptyList(), emptyList())

        assertEquals(1, result.size)
        assertTrue(result[1L]!!.isNotEmpty())
    }

    @Test
    fun `computeHashes handles multiple audit-protected vehicles independently`() {
        val vehicle1 = auditVehicle(1L)
        val vehicle2 = auditVehicle(2L).copy(make = "Audi", model = "A4", licensePlate = "M CD 5678")
        val trip1 = trip(1L, 1L)
        val trip2 = trip(2L, 2L)

        val result = calculator.computeHashes(
            listOf(vehicle1, vehicle2),
            listOf(trip1, trip2),
            emptyList()
        )

        assertEquals(2, result.size)
        assertNotEquals(result[1L], result[2L])
    }

    // --- verifyHashes tests ---

    @Test
    fun `verifyHashes returns Success when hashes match`() {
        val vehicles = listOf(auditVehicle())
        val trips = listOf(trip())
        val logs = listOf(auditLog())

        val hashes = calculator.computeHashes(vehicles, trips, logs)
        val result = calculator.verifyHashes(hashes, vehicles, trips, logs)

        assertTrue(result is IntegrityHashCalculator.VerificationResult.Success)
    }

    @Test
    fun `verifyHashes returns Success when storedHashes is empty`() {
        val result = calculator.verifyHashes(
            emptyMap(),
            listOf(auditVehicle()),
            listOf(trip()),
            emptyList()
        )
        assertTrue(result is IntegrityHashCalculator.VerificationResult.Success)
    }

    @Test
    fun `verifyHashes detects tampered trip data`() {
        val vehicles = listOf(auditVehicle())
        val originalTrip = trip()
        val logs = listOf(auditLog())

        // Compute hash with original data
        val hashes = calculator.computeHashes(vehicles, listOf(originalTrip), logs)

        // Verify with modified data
        val tamperedTrip = originalTrip.copy(distanceKm = 999.0)
        val result = calculator.verifyHashes(hashes, vehicles, listOf(tamperedTrip), logs)

        assertTrue(result is IntegrityHashCalculator.VerificationResult.TamperingDetected)
        val detected = result as IntegrityHashCalculator.VerificationResult.TamperingDetected
        assertEquals(1, detected.affectedVehicles.size)
        assertEquals(1L, detected.affectedVehicles[0].id)
    }

    @Test
    fun `verifyHashes detects removed audit log`() {
        val vehicles = listOf(auditVehicle())
        val trips = listOf(trip())
        val log = auditLog()

        val hashes = calculator.computeHashes(vehicles, trips, listOf(log))

        // Verify without the audit log
        val result = calculator.verifyHashes(hashes, vehicles, trips, emptyList())

        assertTrue(result is IntegrityHashCalculator.VerificationResult.TamperingDetected)
    }

    @Test
    fun `verifyHashes detects removed trip`() {
        val vehicles = listOf(auditVehicle())
        val trip1 = trip(1L, 1L)
        val trip2 = trip(2L, 1L)

        val hashes = calculator.computeHashes(vehicles, listOf(trip1, trip2), emptyList())

        // Verify with one trip missing
        val result = calculator.verifyHashes(hashes, vehicles, listOf(trip1), emptyList())

        assertTrue(result is IntegrityHashCalculator.VerificationResult.TamperingDetected)
    }

    @Test
    fun `verifyHashes detects added trip`() {
        val vehicles = listOf(auditVehicle())
        val trip1 = trip(1L, 1L)
        val trip2 = trip(2L, 1L)

        val hashes = calculator.computeHashes(vehicles, listOf(trip1), emptyList())

        // Verify with extra trip
        val result = calculator.verifyHashes(hashes, vehicles, listOf(trip1, trip2), emptyList())

        assertTrue(result is IntegrityHashCalculator.VerificationResult.TamperingDetected)
    }

    @Test
    fun `verifyHashes reports only affected vehicles`() {
        val vehicle1 = auditVehicle(1L)
        val vehicle2 = auditVehicle(2L).copy(make = "Audi", model = "A4", licensePlate = "M CD 5678")
        val trip1 = trip(1L, 1L)
        val trip2 = trip(2L, 2L)

        val hashes = calculator.computeHashes(
            listOf(vehicle1, vehicle2),
            listOf(trip1, trip2),
            emptyList()
        )

        // Tamper only vehicle 2's trip
        val tamperedTrip2 = trip2.copy(distanceKm = 999.0)
        val result = calculator.verifyHashes(
            hashes,
            listOf(vehicle1, vehicle2),
            listOf(trip1, tamperedTrip2),
            emptyList()
        )

        assertTrue(result is IntegrityHashCalculator.VerificationResult.TamperingDetected)
        val detected = result as IntegrityHashCalculator.VerificationResult.TamperingDetected
        assertEquals(1, detected.affectedVehicles.size)
        assertEquals(2L, detected.affectedVehicles[0].id)
    }

    @Test
    fun `verifyHashes handles hash for vehicle that no longer exists`() {
        val vehicles = listOf(auditVehicle(1L))
        val trips = listOf(trip(1L, 1L))

        val hashes = calculator.computeHashes(vehicles, trips, emptyList())

        // Add a fake hash for a non-existent vehicle
        val allHashes = hashes + (999L to "fakehash")

        // Non-existent vehicle hash should not crash, but is ignored (no vehicle to report)
        val result = calculator.verifyHashes(allHashes, vehicles, trips, emptyList())
        assertTrue(result is IntegrityHashCalculator.VerificationResult.Success)
    }
}
