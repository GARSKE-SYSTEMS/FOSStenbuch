package de.fosstenbuch.tracking

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import de.fosstenbuch.R
import de.fosstenbuch.data.Trip
import de.fosstenbuch.data.dao
import de.fosstenbuch.logic.Geo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * GPS-Aufzeichnung als Vordergrunddienst, für manuell gestartete und für automatisch (per Bluetooth)
 * erkannte Fahrten. Es läuft immer höchstens eine Aufzeichnung.
 *
 * - Manuelle Fahrt: Die Strecke steht nur in [live]; die UI übernimmt `live.km` beim Beenden.
 * - Automatische Fahrt: Jede Position landet sofort in der DB (übersteht Prozess-Tod),
 *   beim Stoppen wird die Fahrt über [Ghosts.finish] abgeschlossen.
 *
 * Endet die Fahrt anderswo (beendet, gelöscht, übernommen), hört der Dienst von selbst auf.
 */
class TrackingService : Service() {

    /** Live-Stand der laufenden GPS-Aufzeichnung für die Anzeige */
    data class Live(val tripId: Long, val km: Double)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val listener = LocationListenerCompat { onFix(it) }
    private lateinit var notifications: NotificationManager

    /** Die aufgezeichnete Fahrt (Stand beim Start der Aufzeichnung) oder null */
    private var trip: Trip? = null
    private var track = Track()
    /** Laufen nur während einer Aufzeichnung: Fahrt beobachten, Benachrichtigung aktualisieren */
    private var jobs = emptyList<Job>()
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Der Kanal muss vor startForeground() existieren, sonst stürzt die App ab
        notifications = notificationChannel(CHANNEL_ID, R.string.tracking_channel_name, R.string.tracking_channel_desc, NotificationManager.IMPORTANCE_LOW)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (intent?.action == ACTION_STOP) {
            stopRecording()
            return START_NOT_STICKY
        }
        // Nach startForegroundService() muss startForeground() binnen Sekunden folgen, sonst beendet
        // Android die App. Deshalb sofort – noch bevor die Fahrt aus der DB geladen ist.
        if (!Geo.hasPermission(this) || !enterForeground()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val id = intent?.getLongExtra(EXTRA_TRIP_ID, 0L) ?: 0L
        scope.launch {
            // Ohne Fahrt-ID hat Android den Dienst nach Prozess-Tod neu gestartet (START_STICKY):
            // dann die laufende Aufzeichnung aus der DB fortsetzen
            val t = if (id != 0L) dao.trip(id) else dao.activeTrip() ?: dao.recordingGhost()
            if (t != null && running(t)) record(t) else if (trip == null) stopSelf(startId)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTracking()
        scope.cancel()
        super.onDestroy()
    }

    /** Vordergrundbetrieb mit Standort-Typ; false, wenn Android ihn verweigert */
    private fun enterForeground(): Boolean = try {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        true
    } catch (e: RuntimeException) {
        // SecurityException: ab Android 14 Standort-Dienst aus dem Hintergrund ohne ACCESS_BACKGROUND_LOCATION.
        // ForegroundServiceStartNotAllowedException: ab Android 12 Start aus dem Hintergrund (z.B. nach
        // START_STICKY). Dann eben ohne GPS – die Fahrt selbst bleibt erhalten.
        false
    }

    /** Beginnt die Aufzeichnung von [t] (nichts zu tun, wenn sie schon läuft) */
    private fun record(t: Trip) {
        if (trip?.id == t.id) return
        // Eine laufende automatische Fahrt bleibt dabei offen und endet wie immer beim Bluetooth-Trennen
        stopTracking()
        trip = t
        // Automatische Fahrt: bisherige Strecke und letzte Position aus der DB übernehmen (Fortsetzen)
        track = if (t.isGhost) Track(t.gpsDistanceKm ?: 0.0, t.endLatitude, t.endLongitude) else Track()
        state.value = Live(t.id, track.km)
        requestUpdates()
        jobs = listOf(
            scope.launch {
                dao.tripFlow(t.id).first { it == null || !running(it) }
                stopRecording()
            },
            // Regelmäßig, weil die Minuten auch ohne neue Position weiterlaufen
            scope.launch {
                while (true) {
                    notifications.notify(NOTIFICATION_ID, notification())
                    delay(15_000)
                }
            }
        )
    }

    /** Beendet die Aufzeichnung; eine automatische Fahrt wird vorher abgeschlossen */
    private fun stopRecording() {
        val t = trip
        // Nur die bis jetzt eingegangenen Befehle zählen: Kommt währenddessen ein neuer Start
        // (z.B. Auto-Bluetooth sofort wieder verbunden), ignoriert Android stopSelf(startId)
        val startId = lastStartId
        stopTracking()
        scope.launch {
            // Der Dienst hält den Prozess am Leben, bis auch die Ortsnamen (Netz) nachgetragen sind
            if (t?.isGhost == true) Ghosts.finish(this@TrackingService, t.id)
            stopSelf(startId)
        }
    }

    private fun stopTracking() {
        jobs.forEach { it.cancel() }
        getSystemService(LocationManager::class.java)?.removeUpdates(listener)
        trip = null
        state.value = null
    }

    @SuppressLint("MissingPermission") // in onStartCommand per Geo.hasPermission() geprüft
    private fun requestUpdates() {
        val lm = getSystemService(LocationManager::class.java) ?: return
        // Netzwerkortung nur als Ersatz, wenn GPS aus ist (gibt es z.B. auf Geräten ohne Google-Dienste nicht)
        val provider = if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            LocationManager.NETWORK_PROVIDER
        } else {
            LocationManager.GPS_PROVIDER
        }
        try {
            lm.requestLocationUpdates(provider, 15_000L, 10f, listener, Looper.getMainLooper())
        } catch (e: RuntimeException) {
            // Kein GPS im Gerät, oder vor Android 12 nur "ungefähre" Berechtigung (GPS verlangt "genau"):
            // Die Fahrt läuft ohne Strecke weiter
        }
    }

    private fun onFix(location: Location) {
        val t = trip ?: return
        val accuracy = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE
        if (!track.add(location.latitude, location.longitude, accuracy)) return
        val km = track.km
        state.value = Live(t.id, km)
        if (t.isGhost) scope.launch { dao.updateGhostGps(t.id, km, location.latitude, location.longitude) }
    }

    private fun notification(): Notification {
        val t = trip
        val minutes = t?.let { ((System.currentTimeMillis() - it.date.time) / 60_000).toInt().coerceAtLeast(0) } ?: 0
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location)
            .setContentTitle(getString(if (t?.isGhost == true) R.string.tracking_ghost_title else R.string.tracking_notification_title))
            .setContentText(getString(R.string.ghost_recording_notification_text, track.km, minutes))
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .build()
    }

    companion object {
        private const val ACTION_STOP = "de.fosstenbuch.tracking.STOP"
        private const val EXTRA_TRIP_ID = "tripId"
        /** Kanal der früheren manuellen Aufzeichnung – wird weiterverwendet */
        private const val CHANNEL_ID = "location_tracking_channel"
        private const val NOTIFICATION_ID = 2001

        private val state = MutableStateFlow<Live?>(null)

        /** Aktuelle Aufzeichnung oder null */
        val live: StateFlow<Live?> = state

        /** Läuft die Aufzeichnung dieser Fahrt noch? (manuell: aktiv; automatisch: noch kein Ende) */
        private fun running(t: Trip) = if (t.isGhost) t.endTime == null else t.isActive

        /** GPS-Aufzeichnung für eine manuell gestartete Fahrt (nur aus sichtbarer UI aufrufen) */
        fun startManual(context: Context, tripId: Long) = start(context, tripId)

        /** GPS-Aufzeichnung für eine automatisch erkannte Fahrt */
        fun startGhost(context: Context, tripId: Long) = start(context, tripId)

        /** Beendet die laufende Aufzeichnung */
        fun stop(context: Context) {
            // Läuft keine, gibt es nichts zu stoppen. Läuft eine, ist der Dienst im Vordergrund –
            // dann ist startService() auch aus dem Hintergrund erlaubt.
            if (state.value != null) context.startService(Intent(context, TrackingService::class.java).setAction(ACTION_STOP))
        }

        private fun start(context: Context, tripId: Long) {
            // Ohne Standortberechtigung gar nicht erst starten: Ein per startForegroundService()
            // gestarteter Dienst, der nicht in den Vordergrund kommt, bringt die App zum Absturz
            if (!Geo.hasPermission(context)) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java).putExtra(EXTRA_TRIP_ID, tripId))
            } catch (e: IllegalStateException) {
                // Ab Android 12 darf ein Vordergrunddienst aus dem Hintergrund nur mit Ausnahme starten
                // (z.B. Akku-Optimierung für die App abgeschaltet). Die Fahrt bleibt ohne GPS erhalten.
            }
        }
    }
}

/**
 * Summiert die gefahrene Strecke aus GPS-Positionen (Luftlinie zwischen aufeinanderfolgenden Positionen)
 * und filtert Messrauschen, damit ein stehendes Auto keine Kilometer sammelt.
 * [lat]/[lng] = letzte gezählte Position (zum Fortsetzen einer Aufzeichnung).
 */
internal class Track(km: Double = 0.0, private var lat: Double? = null, private var lng: Double? = null) {

    var km = km
        private set

    /** Nimmt eine Position auf; false, wenn sie verworfen wurde (zu ungenau oder nur Rauschen) */
    fun add(lat: Double, lng: Double, accuracyMeters: Float): Boolean {
        if (accuracyMeters > MAX_ACCURACY_M) return false
        val lastLat = this.lat
        val lastLng = this.lng
        if (lastLat != null && lastLng != null) {
            val meters = Geo.distanceMeters(lastLat, lastLng, lat, lng)
            // Weniger als die Messgenauigkeit ist Rauschen (Auto steht), keine Bewegung. Die alte
            // Position bleibt Bezugspunkt – langsame echte Bewegung summiert sich so trotzdem auf.
            if (meters < accuracyMeters) return false
            km += meters / 1000
        }
        this.lat = lat
        this.lng = lng
        return true
    }

    private companion object {
        const val MAX_ACCURACY_M = 30f
    }
}
