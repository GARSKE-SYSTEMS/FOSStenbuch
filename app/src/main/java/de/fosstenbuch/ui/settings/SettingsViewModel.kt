package de.fosstenbuch.ui.settings

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.fosstenbuch.R
import de.fosstenbuch.logic.Backup
import de.fosstenbuch.logic.Keys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Führt Backup, Wiederherstellung, "Alle Daten löschen" und die Schlüsselverwaltung aus. Als ViewModel, damit
 * die Arbeit eine Drehung des Geräts übersteht. Wird der Bildschirm ganz verlassen, bricht sie ab –
 * dank Transaktion bleiben die Daten dann unverändert.
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    /** Läuft gerade eine Aktion? Dann sind die Knöpfe gesperrt und die Ladeanzeige sichtbar. */
    val busy = MutableStateFlow(false)

    /** Gespeicherter öffentlicher Schlüssel (null = keiner) und ob er auf diesem Gerät nutzbar ist */
    class KeyState(val publicKey: String? = null, val usable: Boolean = false)

    val key = MutableStateFlow(KeyState())

    /** Wie [busy], für die Schlüssel-Knöpfe */
    val keyBusy = MutableStateFlow(false)

    /** Passwort für den Schlüssel-Export zwischen Passwort-Dialog und Dateiauswahl (übersteht eine Drehung) */
    var exportPassword: CharArray? = null

    /** Ergebnisse für die Oberfläche. Gepuffert: Was während einer Drehung fertig wird, kommt danach genau einmal an. */
    val outcomes = Channel<Outcome>(Channel.UNLIMITED)

    /**
     * Ergebnis einer Aktion: [message] für Meldung bzw. Dialog; [share] = fertige Backup-Datei zum Teilen;
     * [retryUri] = Wiederherstellung scheiterte an der Hash-Kette und kann auf Wunsch erzwungen werden;
     * [confirmUri] = Backup gelesen, jetzt nachfragen ([message] = zusätzliche Warnung oder leer).
     */
    class Outcome(val message: String, val share: File? = null, val retryUri: Uri? = null, val confirmUri: Uri? = null)

    private val app: Application get() = getApplication()

    init {
        viewModelScope.launch { key.value = withContext(Dispatchers.IO) { keyState() } }
    }

    fun backup() = perform(R.string.backup_error) {
        Outcome(app.getString(R.string.settings_backup_share_title), share = Backup.export(app))
    }

    /**
     * Liest das Backup vor der Rückfrage: Unlesbare Dateien fallen sofort auf, und es wird gewarnt, wenn
     * Fahrzeuge dabei ihre Bearbeitungssperre verlören (Backup.unprotectedIn)
     */
    fun checkRestore(uri: Uri) = perform(R.string.restore_error) {
        val lost = Backup.unprotectedIn(app, uri)
        Outcome(if (lost.isEmpty()) "" else app.getString(R.string.restore_protection_warning, lost.joinToString { it.label }), confirmUri = uri)
    }

    /** Spielt das Backup aus [uri] ein; [force] übergeht eine gebrochene Hash-Kette (nur nach Rückfrage) */
    fun restore(uri: Uri, force: Boolean = false) = perform(R.string.restore_error) {
        try {
            val mismatches = Backup.restore(app, uri, force)
            Outcome(
                if (mismatches == 0) app.getString(R.string.restore_success)
                else app.resources.getQuantityString(R.plurals.restore_seal_mismatches, mismatches, mismatches)
            )
        } catch (e: Backup.BrokenChain) {
            Outcome(e.message.orEmpty(), retryUri = uri)
        }
    }

    fun deleteAll() = perform(R.string.settings_delete_error) {
        Backup.deleteAll(app)
        Outcome(app.getString(R.string.delete_all_success))
    }

    // ── Signaturschlüssel ──

    fun createKey() = keyAction(R.string.key_created) { Keys.save(app, Keys.generate()) }

    fun removeKey() = keyAction(R.string.key_removed) { Keys.remove(app) }

    /** Schreibt die Schlüsseldatei (mit [exportPassword] verschlüsselt) nach [uri] */
    fun exportKey(uri: Uri) {
        val password = exportPassword ?: return
        exportPassword = null
        keyAction(R.string.key_exported) {
            val text = Keys.exportFile(Keys.load(app) ?: error(app.getString(R.string.key_unusable)), password)
            app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: error("Datei konnte nicht geschrieben werden")
        }
    }

    /** Liest die Schlüsseldatei in [uri] mit [password] und ersetzt damit den bisherigen Schlüssel */
    fun importKey(uri: Uri, password: CharArray) = keyAction(R.string.key_imported) {
        val text = app.contentResolver.openInputStream(uri)?.use { it.reader().readText() } ?: error("Datei konnte nicht gelesen werden")
        Keys.save(app, Keys.importFile(text, password))
    }

    private fun keyState() = KeyState(Keys.storedPublicKey(app), Keys.load(app) != null)

    /** Schlüssel-Aktion im Hintergrund (Datei, KeyStore, PBKDF2 dauert spürbar), danach Anzeige aktualisieren */
    private fun keyAction(@StringRes success: Int, action: () -> Unit) = perform(R.string.key_error, keyBusy) {
        withContext(Dispatchers.IO) {
            try {
                action()
            } finally {
                key.value = keyState()
            }
        }
        Outcome(app.getString(success))
    }

    /** Startet [action], falls nicht schon eine läuft (nie zwei gleichzeitig je [flag]); Fehler werden zu [errorFormat] */
    private fun perform(@StringRes errorFormat: Int, flag: MutableStateFlow<Boolean> = busy, action: suspend () -> Outcome) {
        if (flag.value) return
        flag.value = true
        viewModelScope.launch {
            val outcome = try {
                action()
            } catch (e: CancellationException) {
                throw e // Abbruch (Bildschirm verlassen) ist kein Fehler und darf nicht gemeldet werden
            } catch (e: Exception) {
                // Datei, JSON, Datenbank, laufende Aufzeichnung: jeder Fehler wird zur Meldung statt zum Absturz
                Outcome(app.getString(errorFormat, e.message ?: e.javaClass.simpleName))
            } finally {
                flag.value = false
            }
            outcomes.send(outcome)
        }
    }
}
