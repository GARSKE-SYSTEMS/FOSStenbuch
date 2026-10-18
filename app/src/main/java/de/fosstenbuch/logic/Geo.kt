package de.fosstenbuch.logic

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import de.fosstenbuch.data.SavedLocation
import de.fosstenbuch.data.dao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Standort-Hilfen: Entfernungen, gespeicherte Orte, aktuelle Position und Adresssuche (OpenStreetMap) */
object Geo {

    /** Ein Suchergebnis der Adresssuche */
    data class Place(val name: String, val latitude: Double, val longitude: Double)

    /** Entfernung zweier Koordinaten in Metern (Haversine-Formel, Erdradius 6371 km) */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 6_371_000.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Nächstgelegener gespeicherter Ort innerhalb von [radiusMeters] (Standard für alle Vorschläge: 500 m), sonst null */
    fun nearest(locations: List<SavedLocation>, lat: Double, lng: Double, radiusMeters: Double = 500.0): SavedLocation? =
        locations
            .map { it to distanceMeters(lat, lng, it.latitude, it.longitude) }
            .filter { it.second <= radiusMeters }
            .minByOrNull { it.second }?.first

    /**
     * Standortberechtigungen – immer GEMEINSAM anfragen: Ab Android 12 ignoriert das System eine
     * Anfrage nur nach "genau" ohne "ungefähr" (der Nutzer darf "ungefähr" wählen).
     */
    val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Hat die App irgendeine Standortberechtigung (genau oder ungefähr)? */
    fun hasPermission(context: Context): Boolean =
        PERMISSIONS.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** Standort auch im Hintergrund ("Immer zulassen")? Vor Android 10 gilt die normale Berechtigung immer. */
    fun hasBackgroundPermission(context: Context): Boolean = hasPermission(context) &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED)

    /** Aktuelle Position (max. 10 s warten, sonst letzte bekannte), null ohne Berechtigung/Empfang */
    @SuppressLint("MissingPermission") // geprüft über hasPermission()
    suspend fun currentLocation(context: Context): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { lm.isProviderEnabled(it) } ?: return null
        val fresh = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    lm.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) }
                }
            }
        } else null
        return fresh ?: lm.getLastKnownLocation(provider)
    }

    /** Lesbarer Name für Koordinaten: gespeicherter Ort (≤ 500 m) → Adresse (OpenStreetMap) → "lat, lng" */
    suspend fun placeName(context: Context, lat: Double, lng: Double): String =
        nearest(context.dao.allLocations(), lat, lng)?.name
            ?: reverse(lat, lng)
            ?: String.format(Locale.US, "%.4f, %.4f", lat, lng)

    /** Adresssuche, max. 5 Treffer. Wirft IOException bei Netzwerkfehlern. */
    suspend fun search(query: String): List<Place> {
        val text = get("search?format=json&limit=5&q=" + URLEncoder.encode(query, "UTF-8"))
        val json = try { JSONArray(text) } catch (e: JSONException) { throw IOException("Ungültige Antwort der Adresssuche", e) }
        return (0 until json.length()).mapNotNull { i ->
            val o = json.optJSONObject(i) ?: return@mapNotNull null
            val lat = o.optString("lat").toDoubleOrNull()
            val lon = o.optString("lon").toDoubleOrNull()
            val name = o.optString("display_name")
            if (lat != null && lon != null && name.isNotBlank()) Place(name, lat, lon) else null
        }
    }

    /**
     * Adresse zu Koordinaten als "Straße Nr, Ort", null bei Fehler. Gesendet wird auf 4 Nachkommastellen
     * (ca. 10 m) gerundet: genauer sind die gefilterten GPS-Positionen (bis 30 m) ohnehin nicht – Datensparsamkeit.
     */
    suspend fun reverse(lat: Double, lng: Double): String? = try {
        val json = JSONObject(get(String.format(Locale.US, "reverse?format=json&lat=%.4f&lon=%.4f", lat, lng)))
        val a = json.optJSONObject("address")
        val street = listOf(a?.optString("road"), a?.optString("house_number")).filterNot { it.isNullOrEmpty() }.joinToString(" ")
        val city = listOf("city", "town", "village", "municipality").map { a?.optString(it).orEmpty() }.firstOrNull { it.isNotEmpty() }
        listOfNotNull(street.ifEmpty { null }, city).joinToString(", ").ifEmpty {
            json.optString("display_name").split(",").take(2).joinToString(",").trim().ifEmpty { null }
        }
    } catch (e: IOException) {
        null
    } catch (e: JSONException) {
        null
    }

    /** Kennung der App bei Online-Anfragen (statt des Android-Standards mit Gerätemodell und Android-Version) */
    const val USER_AGENT = "FOSStenbuch-Android/1.0 (https://github.com/GARSKE-SYSTEMS/FOSStenbuch)"

    /** Taktet den Beginn der Anfragen auf mindestens 1 s Abstand */
    private val throttle = Mutex()
    private var lastRequestMs = 0L

    /**
     * GET-Anfrage an Nominatim. Die Nutzungsbedingungen verlangen einen eindeutigen User-Agent und
     * erlauben höchstens 1 Anfrage pro Sekunde – auch wenn z.B. Start- und Zielort direkt nacheinander
     * aufgelöst werden oder der Nutzer mehrmals schnell sucht. Gesperrt wird nur das Warten, nicht die
     * Anfrage selbst: Eine hängende Anfrage hält so keine spätere auf.
     */
    private suspend fun get(path: String): String {
        throttle.withLock {
            delay(lastRequestMs + 1_000 - System.nanoTime() / 1_000_000)
            lastRequestMs = System.nanoTime() / 1_000_000
        }
        return request(path)
    }

    private suspend fun request(path: String): String = withContext(Dispatchers.IO) {
        val conn = URL("https://nominatim.openstreetmap.org/$path").openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Adresssuche fehlgeschlagen (HTTP ${conn.responseCode})")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
