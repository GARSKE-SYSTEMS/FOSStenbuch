package de.fosstenbuch.data.remote

/**
 * Represents a single result from the Nominatim (OpenStreetMap) geocoding API.
 */
data class NominatimResult(
    val displayName: String,
    val latitude: Double,
    val longitude: Double
)
