package de.fosstenbuch.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for geocoding addresses via the Nominatim (OpenStreetMap) API.
 *
 * Usage policy: https://operations.osmfoundation.org/policies/nominatim/
 * - Max 1 request per second
 * - Custom User-Agent required
 */
@Singleton
class NominatimService @Inject constructor() {

    companion object {
        private const val BASE_URL = "https://nominatim.openstreetmap.org/search"
        private const val USER_AGENT = "FOSStenbuch-Android/1.0"
        private const val RESULT_LIMIT = 5
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
    }

    /**
     * Search for places matching the given [query] string.
     * Returns a list of [NominatimResult] or throws on network/parse errors.
     */
    suspend fun search(query: String): List<NominatimResult> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = URL("$BASE_URL?q=$encodedQuery&format=json&addressdetails=0&limit=$RESULT_LIMIT")

        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw NominatimException("Nominatim API returned HTTP $responseCode")
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            parseResults(responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResults(json: String): List<NominatimResult> {
        val results = mutableListOf<NominatimResult>()
        val jsonArray = JSONArray(json)

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val displayName = obj.optString("display_name", "")
            val lat = obj.optString("lat", "").toDoubleOrNull()
            val lon = obj.optString("lon", "").toDoubleOrNull()

            if (lat != null && lon != null && displayName.isNotBlank()) {
                results.add(
                    NominatimResult(
                        displayName = displayName,
                        latitude = lat,
                        longitude = lon
                    )
                )
            }
        }

        Timber.d("Nominatim search returned ${results.size} results")
        return results
    }
}

class NominatimException(message: String, cause: Throwable? = null) : Exception(message, cause)
