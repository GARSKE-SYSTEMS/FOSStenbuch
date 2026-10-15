package de.fosstenbuch.tracking

import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Empfängt Systemereignisse und den Erinnerungs-Alarm. Als Manifest-Receiver läuft er auch, wenn die
 * App gar nicht gestartet ist – ein dauerhaft laufender Bluetooth-Dienst ist dadurch überflüssig.
 *
 * - Auto-Bluetooth verbunden/getrennt → automatische Fahrt ([Ghosts]). ACL_CONNECTED/ACL_DISCONNECTED
 *   sind von der Sperre impliziter Broadcasts ausgenommen; ab Android 12 kommen sie nur mit der
 *   Berechtigung BLUETOOTH_CONNECT an.
 * - Erinnerungs-Alarm, Neustart, App-Update → [Reminder].
 */
class SystemReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val address = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)?.address
        // goAsync(): Android hält den Prozess für die DB-Arbeit am Leben, bis finish() kommt, und stellt
        // diesem Empfänger bis dahin nichts Neues zu – die Ereignisse laufen also der Reihe nach ab.
        val pending = goAsync()
        val work = scope.launch {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> if (address != null) Ghosts.onConnected(app, address)
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (address != null) Ghosts.onDisconnected(app, address)
                Reminder.ACTION -> Reminder.onAlarm(app)
                Intent.ACTION_BOOT_COMPLETED -> {
                    // Zuerst das Schnelle: Alarme überleben keinen Neustart
                    Reminder.sync(app)
                    Ghosts.closeStale(app)
                }
                Intent.ACTION_MY_PACKAGE_REPLACED -> {
                    // Kanal des früheren Bluetooth-Dauerdienstes aus den System-Einstellungen entfernen
                    app.getSystemService(NotificationManager::class.java)?.deleteNotificationChannel("bt_tracking_channel")
                    Reminder.sync(app)
                }
            }
        }
        // Android erwartet finish() nach spätestens etwa 10 s. Das Wichtige (DB) ist dann längst erledigt;
        // nur langsame Ortsnamen-Abfragen (Netz) laufen ggf. weiter, solange der Prozess lebt.
        scope.launch {
            withTimeoutOrNull(9_000) { work.join() }
            pending.finish()
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
