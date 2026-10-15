package de.fosstenbuch.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.datastore.preferences.core.Preferences
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import de.fosstenbuch.App
import de.fosstenbuch.R
import de.fosstenbuch.data.Settings
import de.fosstenbuch.data.dao
import de.fosstenbuch.databinding.FragmentSettingsBinding
import de.fosstenbuch.tracking.Reminder
import de.fosstenbuch.ui.common.collect
import de.fosstenbuch.ui.common.confirm
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
 * Datenübersicht sowie Backup, Wiederherstellung und "Alle Daten löschen".
 * Jede Änderung wird sofort gespeichert, es gibt keinen Speichern-Knopf.
 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val vm: SettingsViewModel by viewModels()

    /** Gespeicherte Werte zu R.array.dark_mode_options (gleiche Reihenfolge), siehe App.applyDarkMode */
    private val darkModes = listOf("SYSTEM", "LIGHT", "DARK")

    private val pickBackup = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            confirm(getString(R.string.restore_warning_title), getString(R.string.restore_warning_message), getString(R.string.settings_restore)) {
                vm.restore(uri)
            }
        }
    }

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveReminder(Settings.REMINDER_ENABLED, true)
        else toast(getString(R.string.notification_permission_required))
    }

    /** Reagiert nur auf Nutzeraktionen; programmatisch wird der Schalter über [setChecked] gesetzt */
    private val onReminderToggled = CompoundButton.OnCheckedChangeListener { switch, on ->
        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // Erst nach Zustimmung einschalten (siehe askNotifications), bis dahin bleibt der Schalter aus
            setChecked(switch, false)
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            saveReminder(Settings.REMINDER_ENABLED, on)
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
            setChecked(b.switchReminder, on)
            b.buttonReminderTime.isVisible = on
        }
        var reminderTime = DEFAULT_REMINDER_TIME
        collect(Settings.flow(ctx, Settings.REMINDER_TIME, DEFAULT_REMINDER_TIME)) {
            reminderTime = it
            b.buttonReminderTime.text = getString(R.string.settings_reminder_time_format, it)
        }
        b.buttonReminderTime.setOnClickListener {
            val hour = reminderTime.substringBefore(":").toIntOrNull() ?: 18
            val minute = reminderTime.substringAfter(":", "").toIntOrNull() ?: 0
            TimePickerDialog(ctx, { _, h, m ->
                // Locale.ROOT: immer westliche Ziffern, das Format wird beim Planen wieder gelesen
                saveReminder(Settings.REMINDER_TIME, String.format(Locale.ROOT, "%02d:%02d", h, m))
            }, hour, minute, true).show()
        }

        // ── Datenverwaltung ──
        b.buttonBackup.setOnClickListener { vm.backup() }
        // "*/*": Backups aus Mail, Messenger oder Cloud kommen oft nicht als application/json an
        b.buttonRestore.setOnClickListener { pickBackup.launch("*/*") }
        b.buttonDeleteAll.setOnClickListener {
            confirm(getString(R.string.settings_delete_all_title), getString(R.string.settings_delete_all_message), getString(R.string.delete)) {
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
                o.retryUri != null -> confirm(
                    getString(R.string.settings_chain_broken_title), getString(R.string.settings_chain_broken_message, o.message),
                    getString(R.string.settings_restore_anyway)
                ) { vm.restore(o.retryUri, force = true) }
                else -> toast(o.message)
            }
        }
    }

    /** Setzt den Schalter, ohne dass [onReminderToggled] das für eine Nutzeraktion hält */
    private fun setChecked(switch: CompoundButton, on: Boolean) {
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = on
        switch.setOnCheckedChangeListener(onReminderToggled)
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

    private companion object {
        const val DEFAULT_REMINDER_TIME = "18:00"
    }
}
