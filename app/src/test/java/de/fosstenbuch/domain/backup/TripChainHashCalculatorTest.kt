package de.fosstenbuch.domain.backup

import de.fosstenbuch.data.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class TripChainHashCalculatorTest {

    private lateinit var calculator: TripChainHashCalculator

    @Before
    fun setup() {
        calculator = TripChainHashCalculator()
    }

    // --- Test helpers ---

    private fun trip(
        id: Long = 1L,
        vehicleId: Long = 10L,
        startLocation: String = "Berlin",
        endLocation: String = "Hamburg",
        distanceKm: Double = 280.0,
        chainHash: String? = null
    ) = Trip(
        id = id,
        date = Date(1700000000000L),
        startLocation = startLocation,
        endLocation = endLocation,
        distanceKm = distanceKm,
        purpose = "Kundentermin",
        purposeId = 1L,
        vehicleId = vehicleId,
        startOdometer = 50000,
        endOdometer = 50280,
        chainHash = chainHash
    )

    // --- computeChainHash tests ---

    @Test
    fun `computeChainHash produces valid SHA-256 hex string`() {
        val hash = calculator.computeChainHash(trip(), null)
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `computeChainHash is deterministic`() {
        val t = trip()
        val hash1 = calculator.computeChainHash(t, null)
        val hash2 = calculator.computeChainHash(t, null)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `computeChainHash with null previous hash equals empty string previous hash`() {
        val t = trip()
        val hashNull = calculator.computeChainHash(t, null)
        val hashEmpty = calculator.computeChainHash(t, "")
        assertEquals(hashNull, hashEmpty)
    }

    @Test
    fun `computeChainHash differs with different previous hash`() {
        val t = trip()
        val hash1 = calculator.computeChainHash(t, null)
        val hash2 = calculator.computeChainHash(t, "abc123")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `computeChainHash differs when trip data changes`() {
        val original = trip()
        val modified = trip(distanceKm = 999.0)
        val hash1 = calculator.computeChainHash(original, null)
        val hash2 = calculator.computeChainHash(modified, null)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `computeChainHash ignores chainHash field of trip`() {
        val t1 = trip(chainHash = null)
        val t2 = trip(chainHash = "somehash")
        val hash1 = calculator.computeChainHash(t1, null)
        val hash2 = calculator.computeChainHash(t2, null)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `computeChainHash differs for different trip IDs`() {
        val t1 = trip(id = 1L)
        val t2 = trip(id = 2L)
        val hash1 = calculator.computeChainHash(t1, null)
        val hash2 = calculator.computeChainHash(t2, null)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `computeChainHash differs for different locations`() {
        val t1 = trip(startLocation = "Berlin")
        val t2 = trip(startLocation = "München")
        val hash1 = calculator.computeChainHash(t1, null)
        val hash2 = calculator.computeChainHash(t2, null)
        assertNotEquals(hash1, hash2)
    }

    // --- computeChainHashes (batch) tests ---

    @Test
    fun `computeChainHashes returns empty list for empty input`() {
        val result = calculator.computeChainHashes(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `computeChainHashes returns correct number of entries`() {
        val trips = listOf(trip(id = 1L), trip(id = 2L), trip(id = 3L))
        val result = calculator.computeChainHashes(trips)
        assertEquals(3, result.size)
    }

    @Test
    fun `computeChainHashes chains hashes correctly`() {
        val t1 = trip(id = 1L)
        val t2 = trip(id = 2L)
        val t3 = trip(id = 3L)

        val result = calculator.computeChainHashes(listOf(t1, t2, t3))

        // First hash uses genesis (null previous)
        val expectedFirst = calculator.computeChainHash(t1, null)
        assertEquals(expectedFirst, result[0].second)

        // Second hash uses first hash as previous
        val expectedSecond = calculator.computeChainHash(t2, result[0].second)
        assertEquals(expectedSecond, result[1].second)

        // Third hash uses second hash as previous
        val expectedThird = calculator.computeChainHash(t3, result[1].second)
        assertEquals(expectedThird, result[2].second)
    }

    @Test
    fun `computeChainHashes preserves trip IDs in output`() {
        val trips = listOf(trip(id = 5L), trip(id = 10L), trip(id = 15L))
        val result = calculator.computeChainHashes(trips)
        assertEquals(5L, result[0].first)
        assertEquals(10L, result[1].first)
        assertEquals(15L, result[2].first)
    }

    @Test
    fun `computeChainHashes is deterministic`() {
        val trips = listOf(trip(id = 1L), trip(id = 2L))
        val result1 = calculator.computeChainHashes(trips)
        val result2 = calculator.computeChainHashes(trips)
        assertEquals(result1, result2)
    }

    // --- verifyChain tests ---

    @Test
    fun `verifyChain returns Valid for empty list`() {
        val result = calculator.verifyChain(emptyList())
        assertTrue(result is TripChainHashCalculator.ChainVerificationResult.Valid)
    }

    @Test
    fun `verifyChain returns Valid for correctly chained single trip`() {
        val t = trip(id = 1L)
        val hash = calculator.computeChainHash(t, null)
        val tripWithHash = t.copy(chainHash = hash)

        val result = calculator.verifyChain(listOf(tripWithHash))
        assertTrue(result is TripChainHashCalculator.ChainVerificationResult.Valid)
    }

    @Test
    fun `verifyChain returns Valid for correctly chained multiple trips`() {
        val trips = buildValidChain(3)
        val result = calculator.verifyChain(trips)
        assertTrue(result is TripChainHashCalculator.ChainVerificationResult.Valid)
    }

    @Test
    fun `verifyChain detects tampered first trip`() {
        val trips = buildValidChain(3)
        // Tamper with the first trip's data
        val tampered = trips.toMutableList()
        tampered[0] = tampered[0].copy(distanceKm = 999.0)

        val result = calculator.verifyChain(tampered)
        assertTrue(result is TripChainHashCalculator.ChainVerificationResult.Broken)
        val broken = result as TripChainHashCalculator.ChainVerificationResult.Broken
        assertEquals(tampered[0].id, broken.trip.id)
    }

    @Test
    fun `verifyChain detects tampered middle trip`() {
        val trips = buildValidChain(5)
        val tampered = trips.toMutableList()
        tampered[2] = tampered[2].copy(startLocation = "Manipuliert")

        val result = calculator.verifyChain(tampered)
        assertTrue(result is TripChainHashCalculator.ChainVerificationResult.Broken)
        val broken = result as TripChainHashCalculator.ChainVerificationResult.Broken
        assertEquals(tampered[2].id, broken.trip.id)
    }

    @Test
    fun `verifyChain detects tampered last trip`() {
        val trips = buildValidChain(3)
        val tampered = trips.toMutableList()
        tampered[2] = tampered[2].copy(endLocation = "Manipuliert")

        val result = calculator.verifyChain(tampered)
        assertTrue(result is TripChainHashCalculator.ChainVerificationResult.Broken)
        val broken = result as TripChainHashCalculator.ChainVerificationResult.Broken
        assertEquals(tampered[2].id, broken.trip.id)
    }

    @Test
    fun `verifyChain detects removed trip in chain`() {
        val trips = buildValidChain(4)
        // Remove the second trip - the third trip's chain hash won't match
        val withRemoved = listOf(trips[0], trips[2], trips[3])

        val result = calculator.verifyChain(withRemoved)
        assertTrue(result is TripChainHashCalculator.ChainVerificationResult.Broken)
        val broken = result as TripChainHashCalculator.ChainVerificationResult.Broken
        // The break is at the trip that followed the removed one
        assertEquals(trips[2].id, broken.trip.id)
    }

    @Test
    fun `verifyChain detects inserted trip in chain`() {
        val trips = buildValidChain(3)
        // Insert a foreign trip between trip 1 and trip 2
        val foreign = trip(id = 99L, startLocation = "Inserted").copy(
            chainHash = "fakehash"
        )
        val withInserted = listOf(trips[0], foreign, trips[1], trips[2])

        val result = calculator.verifyChain(withInserted)
        assertTrue(result is TripChainHashCalculator.ChainVerificationResult.Broken)
    }

    @Test
    fun `verifyChain detects swapped chain hash`() {
        val trips = buildValidChain(3)
        // Swap chain hashes between trip 1 and trip 2
        val swapped = trips.toMutableList()
        swapped[0] = swapped[0].copy(chainHash = trips[1].chainHash)
        swapped[1] = swapped[1].copy(chainHash = trips[0].chainHash)

        val result = calculator.verifyChain(swapped)
        assertTrue(result is TripChainHashCalculator.ChainVerificationResult.Broken)
    }

    @Test
    fun `verifyChain detects null chain hash`() {
        val trips = buildValidChain(2)
        val withNull = trips.toMutableList()
        withNull[1] = withNull[1].copy(chainHash = null)

        val result = calculator.verifyChain(withNull)
        assertTrue(result is TripChainHashCalculator.ChainVerificationResult.Broken)
        val broken = result as TripChainHashCalculator.ChainVerificationResult.Broken
        assertEquals(withNull[1].id, broken.trip.id)
    }

    @Test
    fun `verifyChain reports expected and actual hash in Broken result`() {
        val trips = buildValidChain(2)
        val tampered = trips.toMutableList()
        tampered[0] = tampered[0].copy(distanceKm = 999.0)

        val result = calculator.verifyChain(tampered)
        assertTrue(result is TripChainHashCalculator.ChainVerificationResult.Broken)
        val broken = result as TripChainHashCalculator.ChainVerificationResult.Broken
        assertNotEquals(broken.expectedHash, broken.actualHash)
        // expectedHash should be a valid SHA-256
        assertTrue(broken.expectedHash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `chain of 100 trips verifies correctly`() {
        val trips = buildValidChain(100)
        val result = calculator.verifyChain(trips)
        assertTrue(result is TripChainHashCalculator.ChainVerificationResult.Valid)
    }

    @Test
    fun `tampering trip 50 in chain of 100 is detected at trip 50`() {
        val trips = buildValidChain(100)
        val tampered = trips.toMutableList()
        tampered[49] = tampered[49].copy(notes = "manipulated")

        val result = calculator.verifyChain(tampered)
        assertTrue(result is TripChainHashCalculator.ChainVerificationResult.Broken)
        val broken = result as TripChainHashCalculator.ChainVerificationResult.Broken
        assertEquals(50L, broken.trip.id)
    }

    // --- Helper ---

    /**
     * Builds a list of trips with valid chain hashes.
     */
    private fun buildValidChain(count: Int): List<Trip> {
        val trips = (1..count).map { i ->
            trip(id = i.toLong(), startLocation = "City$i", endLocation = "City${i + 1}")
        }
        val hashes = calculator.computeChainHashes(trips)
        return trips.zip(hashes) { t, (_, hash) -> t.copy(chainHash = hash) }
    }
}
