package de.fosstenbuch.domain.usecase.location

import de.fosstenbuch.data.local.SavedLocationDao
import de.fosstenbuch.utils.HaversineUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.URL
import javax.inject.Inject

/**
 * Resolves a human-readable location name from GPS coordinates.
 *
 * Resolution order:
 * 1. Nearest saved location within [SAVED_LOCATION_RADIUS_M] metres
 * 2. Nominatim reverse-geocoding (OpenStreetMap)
 * 3. Raw coordinate string as last-resort fallback
 */
class ResolveLocationNameUseCase @Inject constructor(
    private val savedLocationDao: SavedLocationDao
) {
    companion object {
        private const val SAVED_LOCATION_RADIUS_M = 500.0
        private const val NOMINATIM_URL =
            "https://nominatim.openstreetmap.org/reverse?format=json&lat=%s&lon=%s"
        private const val USER_AGENT = "FOSStenbuch/1.0 (Android)"
    }

    suspend operator fun invoke(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        // 1. Check saved locations
        val nearest = findNearestSavedLocation(lat, lng)
        if (nearest != null) {
            Timber.d("Resolved (%.5f, %.5f) → saved location '%s'", lat, lng, nearest)
            return@withContext nearest
        }

        // 2. Nominatim reverse geocoding
        try {
            val url = NOMINATIM_URL.format(lat, lng)
            val response = URL(url).openConnection().apply {
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 5_000
                readTimeout = 5_000
            }.getInputStream().bufferedReader().readText()

            val json = JSONObject(response)
            val address = json.optJSONObject("address")
            if (address != null) {
                val road = address.optString("road", "")
                val houseNumber = address.optString("house_number", "")
                val city = address.optString("city", "")
                    .ifEmpty { address.optString("town", "") }
                    .ifEmpty { address.optString("village", "") }
                    .ifEmpty { address.optString("municipality", "") }

                val street = if (houseNumber.isNotEmpty() && road.isNotEmpty()) {
                    "$road $houseNumber"
                } else {
                    road
                }

                val resolved = listOf(street, city).filter { it.isNotEmpty() }.joinToString(", ")
                if (resolved.isNotEmpty()) {
                    Timber.d("Resolved (%.5f, %.5f) → Nominatim '%s'", lat, lng, resolved)
                    return@withContext resolved
                }
            }

            // Fallback: use display_name from Nominatim (truncated)
            val displayName = json.optString("display_name", "")
            if (displayName.isNotEmpty()) {
                val short = displayName.split(",").take(2).joinToString(",").trim()
                return@withContext short
            }
        } catch (e: Exception) {
            Timber.w(e, "Nominatim reverse geocoding failed for (%.5f, %.5f)", lat, lng)
        }

        // 3. Coordinate fallback (Locale.US ensures decimal dots, not commas)
        val fallback = String.format(java.util.Locale.US, "%.4f, %.4f", lat, lng)
        Timber.d("Resolved (%.5f, %.5f) → coordinate fallback '%s'", lat, lng, fallback)
        fallback
    }

    private suspend fun findNearestSavedLocation(lat: Double, lng: Double): String? {
        val locations = savedLocationDao.getAllSavedLocationsSync()
        var nearest: String? = null
        var nearestDistance = Double.MAX_VALUE

        for (loc in locations) {
            val distance = HaversineUtils.distanceInMeters(lat, lng, loc.latitude, loc.longitude)
            if (distance < SAVED_LOCATION_RADIUS_M && distance < nearestDistance) {
                nearestDistance = distance
                nearest = loc.name
            }
        }
        return nearest
    }
}
