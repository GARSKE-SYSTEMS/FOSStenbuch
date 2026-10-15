package de.fosstenbuch.tracking

import android.app.Service
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// PLATZHALTER – wird vom Tracking-Teil implementiert. Die öffentliche API (companion) ist fest.
class TrackingService : Service() {

    /** Live-Stand der laufenden GPS-Aufzeichnung für die Anzeige */
    data class Live(val tripId: Long, val km: Double)

    companion object {
        private val state = MutableStateFlow<Live?>(null)

        /** Aktuelle Aufzeichnung oder null */
        val live: StateFlow<Live?> = state

        /** GPS-Aufzeichnung für eine manuell gestartete Fahrt (nur aus sichtbarer UI aufrufen) */
        fun startManual(context: Context, tripId: Long) {}

        /** GPS-Aufzeichnung für eine automatisch erkannte Fahrt */
        fun startGhost(context: Context, tripId: Long) {}

        /** Beendet die laufende Aufzeichnung */
        fun stop(context: Context) {}
    }

    override fun onBind(intent: Intent?) = null
}
