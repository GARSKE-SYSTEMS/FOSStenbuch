package de.fosstenbuch.domain.backup

import de.fosstenbuch.data.model.Trip
import java.security.MessageDigest
import javax.inject.Inject

/**
 * Computes and verifies linked hash chains for trips of audit-protected vehicles.
 *
 * Each trip's [Trip.chainHash] is computed as:
 *   SHA-256(previousChainHash + canonicalTripData)
 *
 * This creates a linked hash list (similar to a blockchain) where modifying,
 * inserting, or removing any trip breaks the chain from that point forward.
 * During backup restore, the entire chain can be verified by recomputing
 * hashes from the first entry.
 *
 * Trips within a vehicle are ordered by [Trip.id] (insertion order) to
 * ensure determinism. The first trip uses an empty string as the genesis
 * previous hash.
 */
class TripChainHashCalculator @Inject constructor() {

    companion object {
        /** Sentinel used as "previous hash" for the very first trip in a chain. */
        const val GENESIS_HASH = ""
    }

    /**
     * Computes the chain hash for a single trip given the previous trip's chain hash.
     *
     * @param trip The trip whose chain hash to compute.
     *             The [Trip.chainHash] field of this trip is ignored in the computation.
     * @param previousChainHash The chain hash of the preceding trip, or null/empty for the first trip.
     * @return The SHA-256 hex string for this trip's chain hash.
     */
    fun computeChainHash(trip: Trip, previousChainHash: String?): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update((previousChainHash ?: GENESIS_HASH).toByteArray(Charsets.UTF_8))
        digest.update(canonicalTrip(trip))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Recomputes chain hashes for an ordered list of trips.
     *
     * @param trips Trips for a single vehicle, sorted by [Trip.id] ascending.
     * @return List of pairs (tripId, newChainHash) in the same order.
     */
    fun computeChainHashes(trips: List<Trip>): List<Pair<Long, String>> {
        var previousHash: String? = null
        return trips.map { trip ->
            val hash = computeChainHash(trip, previousHash)
            previousHash = hash
            trip.id to hash
        }
    }

    /**
     * Verifies the integrity of a chain of trips.
     *
     * @param trips Trips for a single vehicle, sorted by [Trip.id] ascending.
     *              Each trip must have a non-null [Trip.chainHash].
     * @return [ChainVerificationResult.Valid] if the chain is intact,
     *         or [ChainVerificationResult.Broken] with details about the first broken link.
     */
    fun verifyChain(trips: List<Trip>): ChainVerificationResult {
        if (trips.isEmpty()) return ChainVerificationResult.Valid

        var previousHash: String? = null
        for (trip in trips) {
            val expected = computeChainHash(trip, previousHash)
            if (trip.chainHash != expected) {
                return ChainVerificationResult.Broken(
                    trip = trip,
                    expectedHash = expected,
                    actualHash = trip.chainHash
                )
            }
            previousHash = trip.chainHash
        }
        return ChainVerificationResult.Valid
    }

    /**
     * Canonical byte representation of a trip for chain hashing.
     *
     * Includes all data fields that represent the trip record.
     * Excludes [Trip.chainHash] (circular) and [Trip.isExported] (metadata flag).
     */
    internal fun canonicalTrip(trip: Trip): ByteArray {
        return buildString {
            append("${trip.id}|")
            append("${trip.date.time}|")
            append("${trip.startLocation}|")
            append("${trip.endLocation}|")
            append("${trip.distanceKm}|")
            append("${trip.purpose}|")
            append("${trip.purposeId ?: "null"}|")
            append("${trip.notes ?: "null"}|")
            append("${trip.startOdometer ?: "null"}|")
            append("${trip.endOdometer ?: "null"}|")
            append("${trip.vehicleId ?: "null"}|")
            append("${trip.isCancelled}|")
            append("${trip.cancellationReason ?: "null"}|")
            append("${trip.isActive}|")
            append("${trip.endTime?.time ?: "null"}|")
            append("${trip.gpsDistanceKm ?: "null"}|")
            append("${trip.businessPartner ?: "null"}|")
            append(trip.route ?: "null")
        }.toByteArray(Charsets.UTF_8)
    }

    sealed class ChainVerificationResult {
        /** The entire chain is intact and all hashes match. */
        data object Valid : ChainVerificationResult()

        /** The chain is broken at the given trip. */
        data class Broken(
            val trip: Trip,
            val expectedHash: String,
            val actualHash: String?
        ) : ChainVerificationResult()
    }
}
