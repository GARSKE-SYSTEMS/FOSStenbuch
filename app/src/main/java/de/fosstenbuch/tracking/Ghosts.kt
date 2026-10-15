package de.fosstenbuch.tracking

import android.content.Context
import android.os.SystemClock
import de.fosstenbuch.data.Trip
import de.fosstenbuch.data.dao
import de.fosstenbuch.logic.Geo
import kotlinx.coroutines.flow.first
import java.util.Date
import kotlin.math.roundToInt

/**
 * Automatische Fahrterkennung: Verbindet sich das Handy mit dem Auto-Bluetooth eines Fahrzeugs,
 * beginnt eine automatische Fahrt (Ghost); trennt es sich, wird sie abgeschlossen.
 *
 * Der Zustand steht vollständig in der DB (laufende Aufzeichnung = Ghost ohne endTime) und übersteht
 * so Prozess-Tod. Die GPS-Strecke liefert der [TrackingService] – sofern Android ihn starten lässt;
 * sonst hat die Fahrt eben nur Start- und Endzeit.
 */
internal object Ghosts {

    /** Kürzere Bluetooth-Abbrüche (Telefonat, Neuverbinden der Freisprechanlage) setzen dieselbe Fahrt fort */
    private const val REOPEN_MS = 3 * 60_000L
    private const val UNKNOWN = "Unbekannt"

    /** Ein Bluetooth-Gerät hat sich verbunden */
    suspend fun onConnected(context: Context, address: String) {
        val dao = context.dao
        val vehicle = dao.vehicleByBluetooth(address) ?: return
        // Keine Doppelerfassung neben einer manuell gestarteten Fahrt
        if (dao.activeTrip() != null) return
        val recording = dao.recordingGhost()
        val now = System.currentTimeMillis()
        val id = if (recording != null) {
            // Es wird schon aufgezeichnet: nur bei demselben Fahrzeug weitermachen (dann wurde die
            // Trennung verpasst oder die GPS-Aufzeichnung fehlt nach Prozess-Tod)
            if (recording.vehicleId != vehicle.id) return
            recording.id
        } else {
            val recent = dao.ghostTrips().first().firstOrNull {
                it.vehicleId == vehicle.id && it.endTime != null && now - it.endTime.time < REOPEN_MS
            }
            if (recent != null) {
                dao.updateTrip(recent.copy(endTime = null))
                recent.id
            } else {
                dao.insertTrip(
                    Trip(date = Date(now), startLocation = "", vehicleId = vehicle.id, startOdometer = dao.lastOdometer(vehicle.id), isGhost = true)
                )
            }
        }
        if (TrackingService.live.value?.tripId != id) TrackingService.startGhost(context, id)
    }

    /** Ein Bluetooth-Gerät hat sich getrennt */
    suspend fun onDisconnected(context: Context, address: String) {
        val dao = context.dao
        val ghost = dao.recordingGhost() ?: return
        // Nur das Auto-Bluetooth dieses Fahrzeugs beendet die Fahrt, nicht Kopfhörer, Uhr usw.
        val vehicle = ghost.vehicleId?.let { dao.vehicle(it) }
        if (!address.equals(vehicle?.bluetoothDeviceAddress, ignoreCase = true)) return
        if (TrackingService.live.value?.tripId == ghost.id) TrackingService.stop(context) else finish(context, ghost.id)
    }

    /** Nach einem Neustart: Eine schon vorher begonnene Aufzeichnung ist verwaist, die Verbindung ist längst weg */
    suspend fun closeStale(context: Context) {
        val ghost = context.dao.recordingGhost() ?: return
        val bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        if (ghost.date.time < bootTime) finish(context, ghost.id)
    }

    /**
     * Schließt eine automatische Aufzeichnung ab: Ende = jetzt, End-Kilometerstand aus der GPS-Strecke
     * geschätzt, Orte aus den Koordinaten. Zuerst wird das Ende gespeichert (schnell, ohne Netz),
     * danach werden die Ortsnamen nachgetragen – das braucht ggf. Netz und darf auch ausbleiben.
     */
    suspend fun finish(context: Context, id: Long) {
        val dao = context.dao
        val ghost = dao.trip(id)?.takeIf { it.isGhost && it.endTime == null } ?: return
        val ended = ghost.copy(
            endTime = Date(),
            endOdometer = endOdometer(ghost.startOdometer, ghost.gpsDistanceKm),
            startLocation = UNKNOWN,
            endLocation = UNKNOWN
        )
        dao.updateTrip(ended)
        val start = placeName(context, ghost.startLatitude, ghost.startLongitude)
        val end = placeName(context, ghost.endLatitude, ghost.endLongitude)
        // Nur, wenn die Fahrt seitdem unverändert ist (nicht übernommen, gelöscht oder wieder geöffnet)
        if (dao.trip(id) == ended) dao.updateTrip(ended.copy(startLocation = start, endLocation = end))
    }

    private suspend fun placeName(context: Context, lat: Double?, lng: Double?): String =
        if (lat != null && lng != null) Geo.placeName(context, lat, lng) else UNKNOWN

    /** Geschätzter End-Kilometerstand: Start + gerundete GPS-Strecke; ohne GPS-Strecke = Start */
    fun endOdometer(startOdometer: Int?, gpsKm: Double?): Int? =
        if (startOdometer != null && gpsKm != null && gpsKm > 0) startOdometer + gpsKm.roundToInt() else startOdometer
}
