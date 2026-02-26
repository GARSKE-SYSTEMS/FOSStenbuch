package de.fosstenbuch.domain.usecase.location

import de.fosstenbuch.data.model.SavedLocation
import de.fosstenbuch.data.repository.SavedLocationRepository
import de.fosstenbuch.utils.HaversineUtils
import javax.inject.Inject

/**
 * Finds the nearest saved location within a given radius.
 * Uses the Haversine formula for distance calculation — no external API calls.
 */
class FindNearestSavedLocationUseCase @Inject constructor(
    private val savedLocationRepository: SavedLocationRepository
) {
    /**
     * @param latitude Current GPS latitude
     * @param longitude Current GPS longitude
     * @param radiusMeters Search radius in meters (default: 1000m = 1km)
     * @return The nearest SavedLocation within the radius, or null
     */
    suspend operator fun invoke(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double = 1000.0
    ): SavedLocation? {
        val allLocations = savedLocationRepository.getAllSavedLocationsSync()
        var nearest: SavedLocation? = null
        var minDistance = Double.MAX_VALUE

        for (location in allLocations) {
            val distance = HaversineUtils.distanceInMeters(
                latitude, longitude,
                location.latitude, location.longitude
            )
            if (distance <= radiusMeters && distance < minDistance) {
                minDistance = distance
                nearest = location
            }
        }

        return nearest
    }
}
