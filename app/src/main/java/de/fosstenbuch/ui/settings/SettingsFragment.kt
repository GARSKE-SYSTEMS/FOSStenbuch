package de.fosstenbuch.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.CompoundButton
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.datastore.preferences.core.Preferences
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import de.fosstenbuch.App
import de.fosstenbuch.R
import de.fosstenbuch.data.Settings
import de.fosstenbuch.data.dao
import de.fosstenbuch.databinding.FragmentSettingsBinding
import de.fosstenbuch.logic.Seals
import de.fosstenbuch.logic.Tsa
import de.fosstenbuch.tracking.Reminder
import de.fosstenbuch.ui.common.germanButtons
import de.fosstenbuch.ui.common.collect
import de.fosstenbuch.ui.common.confirm
import de.fosstenbuch.ui.common.granted
import de.fosstenbuch.ui.common.launch
import de.fosstenbuch.ui.common.setDropdown
import de.fosstenbuch.ui.common.shareFile
import de.fosstenbuch.ui.common.textOrNull
import de.fosstenbuch.ui.common.toast
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Einstellungen: Dunkelmodus, Fahrername, Standard-Kategorie, tägliche Erinnerung,
 * Datenübersicht, Backup, Wiederherstellung und "Alle Daten löschen" sowie Signaturschlüssel und Zeitstempel.
 * Jede Änderung wird sofort gespeichert, es gibt keinen Speichern-Knopf.
 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val vm: SettingsViewModel by viewModels()

    /** Gespeicherte Werte zu R.array.dark_mode_options (gleiche Reihenfolge), siehe App.applyDarkMode */
    private val darkModes = listOf("SYSTEM", "LIGHT", "DARK")

    /** Erst lesen und prüfen (vm.checkRestore), dann nachfragen – siehe outcomes */
    private val pickBackup = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.checkRestore(uri)
    }

    /** Schlüssel-Export: erst Passwort (vm.exportPassword), dann Zieldatei */
    private val createKeyFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportKey(uri) else vm.exportPassword = null
    }

    /** Schlüssel-Import: erst Datei, dann Passwort */
    private val openKeyFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) askPassword(getString(R.string.key_import_title), null) { vm.importKey(uri, it) }
    }

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveReminder(Settings.REMINDER_ENABLED, true)
        else toast(R.string.notification_permission_required)
    }

    /** Reagiert nur auf Nutzeraktionen; programmatisch wird der Schalter über [setChecked] gesetzt */
    private val onReminderToggled: CompoundButton.OnCheckedChangeListener = CompoundButton.OnCheckedChangeListener { switch, on ->
        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !requireContext().granted(Manifest.permission.POST_NOTIFICATIONS)) {
            // Erst nach Zustimmung einschalten (siehe askNotifications), bis dahin bleibt der Schalter aus
            setChecked(switch, false, onReminderToggled)
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            saveReminder(Settings.REMINDER_ENABLED, on)
        }
    }

    /** Zeitstempel ein/aus; beim Einschalten gleich die Siegel ohne Zeitstempel nachholen */
    private val onTsaToggled = CompoundButton.OnCheckedChangeListener { _, on ->
        val app = requireContext().applicationContext
        launch {
            Settings.set(app, Settings.TSA_ENABLED, on)
            if (on) Tsa.stampPending(app)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentSettingsBinding.bind(view)
        val ctx = requireContext()
        val dao = ctx.dao

        // ── Datenübersicht (aktualisiert sich bei jeder Datenänderung) ──
        collect(combine(dao.trips(), dao.vehicles(), dao.locations(), dao.purposes()) { t, v, l, p -> listOf(t.size, v.size, l.size, p.size) }) {
            b.textSummary.text = listOf(
                getString(R.string.trip_count_format, it[0]), getString(R.string.vehicle_count_format, it[1]),
                getString(R.string.location_count_format, it[2]), getString(R.string.purpose_count_format, it[3])
            ).joinToString("\n")
        }

        // ── Dunkelmodus ──
        val darkModeLabels = resources.getStringArray(R.array.dark_mode_options).toList()
        collect(Settings.flow(ctx, Settings.DARK_MODE, "SYSTEM")) { mode ->
            b.dropdownDarkMode.setDropdown(darkModeLabels, darkModes.indexOf(mode).coerceAtLeast(0)) { i ->
                launch {
                    // Erst speichern, dann anwenden: applyDarkMode erzeugt die Activity neu und beendet diese Coroutine
                    Settings.set(ctx, Settings.DARK_MODE, darkModes[i])
                    App.applyDarkMode(darkModes[i])
                }
            }
        }

        // ── Fahrername ──
        launch {
            // Nur beim ersten Anzeigen laden; nach Drehung o.ä. stellt Android den Text selbst wieder her
            if (savedInstanceState == null) b.editDriverName.setText(Settings.get(ctx, Settings.DRIVER_NAME, ""))
            // Bei jeder Eingabe speichern, nicht erst bei Fokusverlust – sonst geht der Name verloren,
            // wenn das System die App im Hintergrund beendet
            b.editDriverName.doAfterTextChanged {
                launch { Settings.set(ctx, Settings.DRIVER_NAME, b.editDriverName.textOrNull() ?: "") }
            }
        }

        // ── Standard-Kategorie (für neue Fahrten) ──
        collect(combine(dao.purposes(), Settings.flow(ctx, Settings.DEFAULT_PURPOSE_ID, 0L), ::Pair)) { (purposes, selectedId) ->
            val ids = listOf(null) + purposes.map { it.id }
            val names = listOf(getString(R.string.no_default)) + purposes.map { it.name }
            b.dropdownDefaultPurpose.setDropdown(names, ids.indexOf(selectedId).coerceAtLeast(0)) { i ->
                launch { Settings.set(ctx, Settings.DEFAULT_PURPOSE_ID, ids[i]) }
            }
        }

        // ── Tägliche Erinnerung ──
        collect(Settings.flow(ctx, Settings.REMINDER_ENABLED, false)) { on ->
            setChecked(b.switchReminder, on, onReminderToggled)
            b.buttonReminderTime.isVisible = on
        }
        // Leer = noch nie gewählt: parseTime liefert dann dieselbe Standardzeit, mit der Reminder plant
        var reminderTime = Reminder.parseTime("")
        collect(Settings.flow(ctx, Settings.REMINDER_TIME, "")) {
            reminderTime = Reminder.parseTime(it)
            b.buttonReminderTime.text = getString(R.string.settings_reminder_time_format, reminderTime.first, reminderTime.second)
        }
        b.buttonReminderTime.setOnClickListener {
            val (hour, minute) = reminderTime
            TimePickerDialog(ctx, { _, h, m ->
                // Locale.ROOT: immer westliche Ziffern, Reminder.parseTime liest das Format beim Planen wieder
                saveReminder(Settings.REMINDER_TIME, String.format(Locale.ROOT, "%02d:%02d", h, m))
            }, hour, minute, true).germanButtons().show()
        }

        // ── Datenverwaltung ──
        b.buttonBackup.setOnClickListener { vm.backup() }
        // "*/*": Backups aus Mail, Messenger oder Cloud kommen oft nicht als application/json an
        b.buttonRestore.setOnClickListener { pickBackup.launch("*/*") }
        b.buttonDeleteAll.setOnClickListener {
            confirm(getString(R.string.settings_delete_all_title), getString(R.string.settings_delete_all_message), getString(R.string.delete)) {
                b.editDriverName.text = null // wird mitgelöscht
                vm.deleteAll()
            }
        }
        collect(vm.busy) { busy ->
            b.progress.isVisible = busy
            for (button in listOf(b.buttonBackup, b.buttonRestore, b.buttonDeleteAll)) button.isEnabled = !busy
        }
        collect(vm.outcomes.receiveAsFlow()) { o ->
            when {
                o.share != null -> ctx.shareFile(o.share, "application/json", o.message)
                o.confirmUri != null -> confirm(
                    getString(R.string.restore_warning_title),
                    listOf(o.message, getString(R.string.restore_warning_message)).filter { it.isNotEmpty() }.joinToString("\n\n"),
                    getString(R.string.settings_restore)
                ) { vm.restore(o.confirmUri) }
                o.retryUri != null -> confirm(
                    getString(R.string.settings_chain_broken_title), getString(R.string.settings_chain_broken_message, o.message),
                    getString(R.string.settings_restore_anyway)
                ) { vm.restore(o.retryUri, force = true) }
                else -> toast(o.message)
            }
        }

        // ── Signaturschlüssel ──
        collect(combine(vm.key, vm.keyBusy, ::Pair)) { (key, busy) ->
            val pub = key.publicKey
            b.textKey.text = when {
                pub == null -> getString(R.string.settings_key_none)
                key.usable -> getString(R.string.settings_key_fingerprint, Seals.fingerprint(pub))
                else -> getString(R.string.settings_key_unusable, Seals.fingerprint(pub))
            }
            b.buttonKeyExport.isVisible = key.usable
            b.buttonKeyRemove.isVisible = pub != null
            b.progressKey.isVisible = busy
            for (button in listOf(b.buttonKeyCreate, b.buttonKeyExport, b.buttonKeyImport, b.buttonKeyRemove)) button.isEnabled = !busy
        }
        // Bei vorhandenem Schlüssel erst nachfragen (bisherige Signaturen bleiben gültig)
        fun replacing(message: Int, action: () -> Unit) =
            if (vm.key.value.publicKey == null) action()
            else confirm(getString(R.string.key_replace_title), getString(message), getString(R.string.key_replace), action)
        b.buttonKeyCreate.setOnClickListener { replacing(R.string.key_create_replace_message) { vm.createKey() } }
        b.buttonKeyImport.setOnClickListener { replacing(R.string.key_import_replace_message) { openKeyFile.launch(arrayOf("*/*")) } }
        b.buttonKeyExport.setOnClickListener {
            askPassword(getString(R.string.key_export_title), getString(R.string.key_export_message)) {
                vm.exportPassword = it
                createKeyFile.launch("fosstenbuch-schluessel.json")
            }
        }
        b.buttonKeyRemove.setOnClickListener {
            confirm(getString(R.string.key_remove_title), getString(R.string.key_remove_message), getString(R.string.key_remove)) { vm.removeKey() }
        }

        // ── Zeitstempel ──
        collect(Settings.flow(ctx, Settings.TSA_ENABLED, false)) { on ->
            setChecked(b.switchTsa, on, onTsaToggled)
            b.groupTsa.isVisible = on
        }
        // Eigene URL = alles, was keiner der vorgegebenen Dienste ist (auch "" nach ungültiger Eingabe)
        var customTsa = false
        val tsaLabels = Tsa.PROVIDERS.map { "${it.name} (${it.country})" } + getString(R.string.settings_tsa_custom)
        collect(Settings.flow(ctx, Settings.TSA_URL, Tsa.DEFAULT_URL)) { url ->
            val index = Tsa.PROVIDERS.indexOfFirst { it.url == url }.let { if (it < 0) Tsa.PROVIDERS.size else it }
            customTsa = index == Tsa.PROVIDERS.size
            b.dropdownTsa.setDropdown(tsaLabels, index) { i ->
                launch { Settings.set(ctx, Settings.TSA_URL, Tsa.PROVIDERS.getOrNull(i)?.url ?: Tsa.normalizeUrl(b.editTsaUrl.text.toString()) ?: "") }
            }
            b.layoutTsaProvider.helperText = Tsa.PROVIDERS.getOrNull(index)?.let { getString(R.string.settings_tsa_operator, it.operator, it.country) }
            b.layoutTsaUrl.isVisible = customTsa
            b.layoutTsaUrl.helperText = if (Tsa.isCleartext(url)) getString(R.string.settings_tsa_http_warning) else null
        }
        launch {
            if (savedInstanceState == null) {
                val url = Settings.get(ctx, Settings.TSA_URL, Tsa.DEFAULT_URL)
                if (Tsa.PROVIDERS.none { it.url == url }) b.editTsaUrl.setText(url)
            }
            // Bei jeder Eingabe speichern (ungültig = ""), aber nur im Modus "Eigene URL"
            b.editTsaUrl.doAfterTextChanged { text ->
                if (!customTsa) return@doAfterTextChanged
                val url = Tsa.normalizeUrl(text.toString())
                b.layoutTsaUrl.error = if (url == null && !text.isNullOrBlank()) getString(R.string.settings_tsa_url_invalid) else null
                launch { Settings.set(ctx, Settings.TSA_URL, url ?: "") }
            }
        }
        collect(combine(dao.unstampedSealCount(), Tsa.running, Tsa.lastError, ::Triple)) { (count, running, error) ->
            b.textTsaStatus.text = listOfNotNull(
                if (count == 0) getString(R.string.settings_tsa_done) else getString(R.string.settings_tsa_pending, count),
                getString(R.string.settings_tsa_running).takeIf { running },
                error?.let { getString(R.string.settings_tsa_error, it) }
            ).joinToString("\n")
            b.buttonTsaNow.isEnabled = count > 0 && !running
        }
        b.buttonTsaNow.setOnClickListener { Tsa.stampPending(ctx) }
    }

    /** Setzt den Schalter, ohne dass sein [listener] das für eine Nutzeraktion hält */
    private fun setChecked(switch: CompoundButton, on: Boolean, listener: CompoundButton.OnCheckedChangeListener) {
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = on
        switch.setOnCheckedChangeListener(listener)
    }

    /**
     * Fragt ein Passwort ab. Mit [message] (Export) zweimal und mind. 8 Zeichen, sonst einmal (Import).
     * [onOk] läuft nur bei gültiger Eingabe, sonst kommt eine Meldung.
     */
    private fun askPassword(title: String, message: String?, onOk: (CharArray) -> Unit) {
        val ctx = requireContext()
        fun field(hint: Int) = TextInputLayout(ctx).apply {
            this.hint = getString(hint)
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            addView(TextInputEditText(context).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD })
        }
        val first = field(R.string.key_password)
        val second = field(R.string.key_password_repeat)
        val padding = (24 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            addView(first)
            if (message != null) addView(second)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setMessage(message)
            .setView(box)
            .setPositiveButton(R.string.ok) { _, _ ->
                val password = first.editText?.text.toString()
                when {
                    message != null && password.length < 8 -> toast(R.string.key_password_short)
                    message != null && password != second.editText?.text.toString() -> toast(R.string.key_password_mismatch)
                    else -> onOk(password.toCharArray())
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Speichert eine Erinnerungs-Einstellung und plant den Alarm neu. Nicht abbrechbar
     * (z.B. durch Drehen), damit gespeicherte Einstellung und geplanter Alarm nie auseinanderlaufen.
     */
    private fun <T> saveReminder(key: Preferences.Key<T>, value: T) {
        val app = requireContext().applicationContext
        launch {
            withContext(NonCancellable) {
                Settings.set(app, key, value)
                Reminder.sync(app)
            }
        }
    }
}
