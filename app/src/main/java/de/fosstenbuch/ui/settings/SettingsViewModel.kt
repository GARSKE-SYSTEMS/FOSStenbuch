package de.fosstenbuch.ui.settings

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.fosstenbuch.R
import de.fosstenbuch.logic.Backup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Führt Backup, Wiederherstellung und "Alle Daten löschen" aus. Als ViewModel, damit die Arbeit
 * eine Drehung des Geräts übersteht. Wird der Bildschirm ganz verlassen, bricht sie ab –
 * dank Transaktion bleiben die Daten dann unverändert.
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    /** Läuft gerade eine Aktion? Dann sind die Knöpfe gesperrt und die Ladeanzeige sichtbar. */
    val busy = MutableStateFlow(false)

    /** Ergebnisse für die Oberfläche. Gepuffert: Was während einer Drehung fertig wird, kommt danach genau einmal an. */
    val outcomes = Channel<Outcome>(Channel.UNLIMITED)

    /**
     * Ergebnis einer Aktion: [message] für Meldung bzw. Dialog; [share] = fertige Backup-Datei zum Teilen;
     * [retryUri] = Wiederherstellung scheiterte an der Hash-Kette und kann auf Wunsch erzwungen werden.
     */
    class Outcome(val message: String, val share: File? = null, val retryUri: Uri? = null)

    private val app: Application get() = getApplication()

    fun backup() = perform(R.string.backup_error) {
        Outcome(app.getString(R.string.settings_backup_share_title), share = Backup.export(app))
    }

    /** Spielt das Backup aus [uri] ein; [force] übergeht eine gebrochene Hash-Kette (nur nach Rückfrage) */
    fun restore(uri: Uri, force: Boolean = false) = perform(R.string.restore_error) {
        try {
            Backup.restore(app, uri, force)
            Outcome(app.getString(R.string.restore_success))
        } catch (e: Backup.BrokenChain) {
            Outcome(e.message.orEmpty(), retryUri = uri)
        }
    }

    fun deleteAll() = perform(R.string.settings_delete_error) {
        Backup.deleteAll(app)
        Outcome(app.getString(R.string.delete_all_success))
    }

    /** Startet [action], falls nicht schon eine läuft (nie zwei gleichzeitig); Fehler werden zu [errorFormat] */
    private fun perform(@StringRes errorFormat: Int, action: suspend () -> Outcome) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            val outcome = try {
                action()
            } catch (e: CancellationException) {
                throw e // Abbruch (Bildschirm verlassen) ist kein Fehler und darf nicht gemeldet werden
            } catch (e: Exception) {
                // Datei, JSON, Datenbank, laufende Aufzeichnung: jeder Fehler wird zur Meldung statt zum Absturz
                Outcome(app.getString(errorFormat, e.message ?: e.javaClass.simpleName))
            } finally {
                busy.value = false
            }
            outcomes.send(outcome)
        }
    }
}
