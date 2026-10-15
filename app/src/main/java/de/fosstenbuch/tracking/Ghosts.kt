package de.fosstenbuch.tracking

import android.content.Context
import android.os.SystemClock
import de.fosstenbuch.data.Trip
import de.fosstenbuch.data.dao
import de.fosstenbuch.logic.Geo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    /** Für [end]: Das Abschließen soll den auslösenden Bildschirm bzw. Empfänger überdauern */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Zuletzt in diesem Prozess per Bluetooth begonnene oder fortgesetzte Aufzeichnung. Der Prozess ist
     * nach dem letzten Boot entstanden – diese Aufzeichnung ist also nicht verwaist (siehe [closeStale]).
     */
    @Volatile private var connectedId = 0L

    /**
     * Ein Bluetooth-Gerät hat sich verbunden: Für das zugehörige Fahrzeug eine automatische Fahrt beginnen,
     * eine eben erst beendete fortsetzen (kurzer Abbruch) oder eine hier schon laufende weiterführen.
     */
    suspend fun onConnected(context: Context, address: String) {
        val dao = context.dao
        val vehicle = dao.vehicleByBluetooth(address) ?: return
        // Keine Doppelerfassung neben einer manuell gestarteten Fahrt
        if (dao.activeTrip() != null) return
        val recording = dao.recordingGhost()
        val now = System.currentTimeMillis()
        val id = if (recording != null && runsHere(recording.id)) {
            // Läuft schon (in diesem Prozess begonnen oder vom Dienst aufgezeichnet), z.B. zweite Verbindung desselben
            // Geräts per Bluetooth LE: nur bei demselben Fahrzeug weitermachen (unten ggf. GPS erneut starten)
            if (recording.vehicleId != vehicle.id) return
            recording.id
        } else {
            // Eine offene Aufzeichnung, die hier nicht läuft, ist verwaist: Die Trennung wurde verpasst (z.B. nach
            // "Beenden erzwingen"). Abschließen statt fortsetzen – sonst entstünde Tage später eine Fahrt mit
            // altem Startdatum. Die Ortsnamen brauchen ggf. Netz, darauf wartet die neue Fahrt nicht.
            if (recording != null) end(context, recording.id)
            val recent = dao.ghostTrips().first().firstOrNull {
                it.id != recording?.id && it.vehicleId == vehicle.id && now - (it.endTime?.time ?: 0) < REOPEN_MS
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
        connectedId = id
        if (TrackingService.live.value?.tripId != id) TrackingService.startGhost(context, id)
    }

    /** Ein Bluetooth-Gerät hat sich getrennt */
    suspend fun onDisconnected(context: Context, address: String) {
        val dao = context.dao
        val ghost = dao.recordingGhost() ?: return
        // Nur das Auto-Bluetooth dieses Fahrzeugs beendet die Fahrt, nicht Kopfhörer, Uhr usw.
        val vehicle = ghost.vehicleId?.let { dao.vehicle(it) }
        if (!address.equals(vehicle?.bluetoothDeviceAddress, ignoreCase = true)) return
        end(context, ghost.id).join()
    }

    /**
     * Beendet die automatische Aufzeichnung [id] von außen: Fahrzeug-Formular beim Wechseln oder Entfernen
     * des Bluetooth-Geräts (die Trennung des alten Geräts beendet sie sonst nicht mehr) und "Aufzeichnung
     * beenden" im Fahrtformular. Zeichnet der Dienst sie auf, wird er gestoppt und schließt sie ab, sonst [finish].
     * Kehrt sofort zurück; das Abschließen läuft weiter, auch wenn der Bildschirm geschlossen wird.
     */
    fun end(context: Context, id: Long): Job {
        val app = context.applicationContext
        return scope.launch {
            if (TrackingService.live.value?.tripId == id) TrackingService.stop(app) else finish(app, id)
        }
    }

    /** Nach einem Neustart: Eine schon vorher begonnene Aufzeichnung ist verwaist, die Verbindung ist längst weg */
    suspend fun closeStale(context: Context) {
        val ghost = context.dao.recordingGhost() ?: return
        val bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        // Ausnahme: Das Auto hat sich nach dem Boot schon wieder verbunden und die Aufzeichnung fortgesetzt.
        // BOOT_COMPLETED läuft über eine eigene, langsame Warteschlange und kann nach ACL_CONNECTED kommen.
        if (ghost.date.time < bootTime && !runsHere(ghost.id)) finish(context, ghost.id)
    }

    /** Läuft die Aufzeichnung [id] in diesem Prozess (hier per Bluetooth begonnen/fortgesetzt oder vom Dienst aufgezeichnet)? */
    private fun runsHere(id: Long) = id == connectedId || id == TrackingService.live.value?.tripId

    /**
     * Schließt eine automatische Aufzeichnung ab: Ende = jetzt, End-Kilometerstand aus der GPS-Strecke
     * geschätzt, Orte aus den Koordinaten. Zuerst wird das Ende gespeichert (schnell, ohne Netz),
     * danach werden die Ortsnamen nachgetragen – das braucht ggf. Netz und darf auch ausbleiben.
     */
    suspend fun finish(context: Context, id: Long) {
        val dao = context.dao
        val ghost = dao.trip(id)?.takeIf { it.isGhost && it.endTime == null } ?: return
        // Eine echte Fahrt desselben Fahrzeugs, die sich mit der Aufzeichnung überschneidet (läuft noch oder
        // endete erst nach deren Beginn), ist maßgeblich – der Ghost wäre ein Duplikat. Typisch: Das Auto-Bluetooth
        // verbindet sich beim Motorstart, erst danach startet der Nutzer die Fahrt in der App; deren Abfahrtszeit
        // kann dabei vor dem Ghost liegen (Formular schon vorher geöffnet). Geprüft wird hier, damit es für jede
        // Reihenfolge gilt und auch ohne laufenden Dienst (z.B. ohne Standortberechtigung).
        if (dao.allTrips().any { it.vehicleId == ghost.vehicleId && !it.isCancelled && (it.isActive || (it.endTime ?: it.date) >= ghost.date) }) {
            dao.deleteTrip(ghost)
            return
        }
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
