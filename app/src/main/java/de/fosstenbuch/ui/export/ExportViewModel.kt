package de.fosstenbuch.ui.export

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import de.fosstenbuch.R
import de.fosstenbuch.data.Settings
import de.fosstenbuch.data.dao
import de.fosstenbuch.data.db
import de.fosstenbuch.logic.export.Export
import de.fosstenbuch.logic.export.ExportData
import de.fosstenbuch.logic.export.Options
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Filter und Export-Zustand des Exportbildschirms. Ein ViewModel ist hier nötig, damit ein
 * laufender Export (bei vielen Fahrten einige Sekunden) Drehen und Bildschirmwechsel übersteht.
 * Optionen und offene Rückfrage liegen zusätzlich im [SavedStateHandle], damit sie auch einen
 * Prozess-Tod überstehen (z.B. während der Nutzer in der Ziel-App des Teilen-Dialogs ist).
 */
class ExportViewModel(private val app: Application, private val state: SavedStateHandle) : AndroidViewModel(app) {

    /** Ergebnis eines Exports: die Datei und die Fahrten, die danach als exportiert markiert werden sollen */
    class Done(val file: File, val mime: String, val markIds: LongArray)

    private val dao = app.dao

    /** Vom Nutzer gewählte Filter und Optionen (der Bildschirm ändert sie direkt per update) */
    val options = MutableStateFlow(state.get<Options>(KEY_OPTIONS) ?: Options())

    val purposes = dao.purposes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Wurde das gewählte Fahrzeug inzwischen gelöscht, zurück auf "Alle Fahrzeuge" – sonst zeigte die
    // Auswahl "Alle", gefiltert würde aber weiter auf die gelöschte ID (Vorschau 0 Fahrten).
    // Bewusst am Room-Flow: Erst er liefert den echten Stand (auch "kein Fahrzeug mehr").
    val vehicles = dao.vehicles()
        .onEach { list -> options.update { o -> if (o.vehicleId != null && list.none { it.id == o.vehicleId }) o.copy(vehicleId = null) else o } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /**
     * Fahrten der geteilten Datei, für die die Rückfrage "Als exportiert markieren?" noch offen ist
     * (leer = keine). Als LongArray, das hält das gesicherte Bundle auch bei vielen Fahrten klein.
     */
    val pendingMark: StateFlow<LongArray> = state.getStateFlow(KEY_MARK, LongArray(0))

    init {
        options.onEach { state[KEY_OPTIONS] = it }.launchIn(viewModelScope)
    }

    /** Erzeugt die Exportdatei im Hintergrund; ein zweiter Aufruf während eines Exports wird ignoriert */
    fun export() {
        if (busy.value) return
        busy.value = true
        val o = options.value
        viewModelScope.launch {
            result.value = try {
                Result.success(withContext(Dispatchers.IO) {
                    // Die Vorversion legte Exporte direkt im Cache ab, wo sie über den FileProvider
                    // weiter teilbar blieben – diese Altdateien mit personenbezogenen Daten wegräumen
                    app.cacheDir.listFiles { f -> f.isFile && f.name.startsWith("fahrtenbuch_") && f.extension in LEGACY_EXT }
                        ?.forEach { it.delete() }
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
                    Done(file, o.format.mime, if (o.incremental && o.markExported) ids.toLongArray() else LongArray(0))
                })
            } catch (e: CancellationException) {
                throw e // ViewModel wird verworfen – kein Fehler, den jemand anzeigen müsste
            } catch (e: Exception) {
                Result.failure(e)
            }
            busy.value = false
        }
    }

    /** Merkt die Fahrten einer gerade geteilten Datei für die Rückfrage vor */
    fun askMark(ids: LongArray) {
        state[KEY_MARK] = ids
    }

    /** Beantwortet die Rückfrage: bei [mark] die vorgemerkten Fahrten als exportiert markieren */
    fun answerMark(mark: Boolean) {
        val ids = pendingMark.value
        state[KEY_MARK] = LongArray(0)
        if (!mark || ids.isEmpty()) return
        viewModelScope.launch {
            // NonCancellable: Verlässt der Nutzer direkt danach den Bildschirm, würde die Transaktion
            // sonst abgebrochen (Rollback) und die Fahrten blieben unmarkiert
            withContext(NonCancellable) {
                try {
                    // In Blöcken, weil SQLite bis Android 11 max. 999 Parameter erlaubt
                    app.db.withTransaction { ids.asList().chunked(500).forEach { dao.markExported(it) } }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    state[KEY_MARK] = ids // Rückfrage erneut zeigen, so lässt sich das Markieren nachholen
                    result.value = Result.failure(IllegalStateException(app.getString(R.string.export_mark_failed), e))
                }
            }
        }
    }

    private companion object {
        const val KEY_OPTIONS = "options"
        const val KEY_MARK = "pendingMark"
        val LEGACY_EXT = setOf("csv", "pdf")
    }
}
