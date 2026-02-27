package de.fosstenbuch.domain.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.fosstenbuch.R
import de.fosstenbuch.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar

class TripReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "trip_reminder_channel"
        const val NOTIFICATION_ID = 1001
        private const val REQUEST_CODE = 2001

        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        fun scheduleReminder(context: Context, hour: Int, minute: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // On API 31+ check if exact alarms are permitted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Timber.w("Cannot schedule exact alarms – permission not granted, falling back to inexact alarm")
                    scheduleInexactReminder(context, alarmManager, hour, minute)
                    return
                }
            }

            val pendingIntent = getReminderPendingIntent(context)

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            // Use setExactAndAllowWhileIdle for reliable delivery through Doze mode
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )

            Timber.d("Trip reminder scheduled (exact) for %02d:%02d on %tF", hour, minute, calendar)
        }

        private fun scheduleInexactReminder(
            context: Context,
            alarmManager: AlarmManager,
            hour: Int,
            minute: Int
        ) {
            val pendingIntent = getReminderPendingIntent(context)

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )

            Timber.d("Trip reminder scheduled (inexact) for %02d:%02d on %tF", hour, minute, calendar)
        }

        fun cancelReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(getReminderPendingIntent(context))
            Timber.d("Trip reminder cancelled")
        }

        private fun getReminderPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, TripReminderReceiver::class.java)
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /**
         * Checks whether the POST_NOTIFICATIONS runtime permission has been granted.
         * Always returns true on API < 33 where the runtime permission does not exist.
         */
        fun hasNotificationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("TripReminderReceiver.onReceive triggered")
        createNotificationChannel(context)

        // Show notification only if we have the POST_NOTIFICATIONS permission
        if (hasNotificationPermission(context)) {
            showNotification(context)
        } else {
            Timber.w("POST_NOTIFICATIONS permission not granted – skipping notification")
        }

        // Re-schedule for the next day (setExactAndAllowWhileIdle is one-shot)
        rescheduleForNextDay(context)
    }

    private fun showNotification(context: Context) {
        val mainIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_trips)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
        Timber.d("Trip reminder notification shown")
    }

    private fun rescheduleForNextDay(context: Context) {
        val preferencesManager = PreferencesManager(context)
        CoroutineScope(Dispatchers.IO).launch {
            val enabled = preferencesManager.reminderEnabled.first()
            if (enabled) {
                val time = preferencesManager.reminderTime.first()
                val parts = time.split(":")
                if (parts.size == 2) {
                    val hour = parts[0].toIntOrNull() ?: 18
                    val minute = parts[1].toIntOrNull() ?: 0
                    scheduleReminder(context, hour, minute)
                }
            }
        }
    }
}
