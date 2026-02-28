package de.fosstenbuch.domain.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.fosstenbuch.data.local.PreferencesManager
import de.fosstenbuch.domain.service.BluetoothTrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Timber.d("BootReceiver triggered by: $action")

            // Start the Bluetooth monitoring service unconditionally;
            // it will silently do nothing if no vehicle has a BT device configured.
            BluetoothTrackingService.start(context)

            val preferencesManager = PreferencesManager(context)
            CoroutineScope(Dispatchers.IO).launch {
                val enabled = preferencesManager.reminderEnabled.first()
                if (enabled) {
                    val time = preferencesManager.reminderTime.first()
                    val parts = time.split(":")
                    if (parts.size == 2) {
                        val hour = parts[0].toIntOrNull() ?: 18
                        val minute = parts[1].toIntOrNull() ?: 0
                        TripReminderReceiver.scheduleReminder(context, hour, minute)
                    }
                }
            }
        }
    }
}

