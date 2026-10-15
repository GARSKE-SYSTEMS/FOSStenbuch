package de.fosstenbuch.ui.export

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import de.fosstenbuch.R
import de.fosstenbuch.data.Settings
import de.fosstenbuch.data.dao
import de.fosstenbuch.data.db
import de.fosstenbuch.logic.export.Export
import de.fosstenbuch.logic.export.ExportData
import de.fosstenbuch.logic.export.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Filter und Export-Zustand des Exportbildschirms. Ein ViewModel ist hier nötig, damit ein
 * laufender Export (bei vielen Fahrten einige Sekunden) Drehen und Bildschirmwechsel übersteht.
 */
class ExportViewModel(private val app: Application) : AndroidViewModel(app) {

    /** Ergebnis eines Exports: die Datei und die Fahrten, die danach als exportiert markiert werden sollen */
    class Done(val file: File, val mime: String, val markIds: List<Long>)

    private val dao = app.dao

    /** Vom Nutzer gewählte Filter und Optionen (der Bildschirm ändert sie direkt per update) */
    val options = MutableStateFlow(Options())

    val purposes = dao.purposes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val vehicles = dao.vehicles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Vorschau: die Fahrten, die mit den aktuellen Optionen exportiert würden. Reagiert auf jede
     * Änderung von Fahrten, Kategorien und Optionen; combine liefert immer das zuletzt gültige Ergebnis.
     */
    val trips = combine(dao.trips(), purposes, options) { trips, purposes, o -> Export.filter(trips, o, purposes.map { it.id }) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val busy = MutableStateFlow(false)

    /** Ergebnis des letzten Exports; der Bildschirm verarbeitet es und setzt es wieder auf null */
    val result = MutableStateFlow<Result<Done>?>(null)

    /** Erzeugt die Exportdatei im Hintergrund; ein zweiter Aufruf während eines Exports wird ignoriert */
    fun export() {
        if (busy.value) return
        busy.value = true
        val o = options.value
        viewModelScope.launch {
            result.value = runCatching {
                withContext(Dispatchers.IO) {
                    // Frisch aus der DB lesen statt der Vorschau – gleiche Filterfunktion, aktueller Stand
                    val purposes = dao.allPurposes()
                    val trips = Export.filter(dao.allTrips(), o, purposes.map { it.id })
                    check(trips.isNotEmpty()) { app.getString(R.string.export_no_trips) }
                    val ids = trips.map { it.id }
                    val logs = if (o.withAudit) {
                        val wanted = ids.toSet()
                        dao.allAuditLogs().filter { it.tripId in wanted }.groupBy { it.tripId }
                    } else emptyMap()
                    val driver = Settings.get(app, Settings.DRIVER_NAME, "").trim()
                    val data = ExportData(o, trips, purposes, dao.allVehicles(), logs, driver)
                    val file = Export.write(File(app.cacheDir, "exports"), data)
                    Done(file, o.format.mime, if (o.incremental && o.markExported) ids else emptyList())
                }
            }
            busy.value = false
        }
    }

    /** Markiert Fahrten als exportiert – in Blöcken, weil SQLite bis Android 11 max. 999 Parameter erlaubt */
    fun markExported(ids: List<Long>) {
        viewModelScope.launch {
            app.db.withTransaction { ids.chunked(500).forEach { dao.markExported(it) } }
        }
    }
}
