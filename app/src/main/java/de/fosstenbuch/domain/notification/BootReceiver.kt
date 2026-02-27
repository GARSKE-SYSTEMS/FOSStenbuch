package de.fosstenbuch.domain.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.fosstenbuch.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.d("Boot completed, checking reminder schedule")
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
