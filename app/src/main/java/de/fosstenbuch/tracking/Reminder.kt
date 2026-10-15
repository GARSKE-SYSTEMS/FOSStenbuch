package de.fosstenbuch.tracking

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.fosstenbuch.R
import de.fosstenbuch.data.Settings
import java.util.Calendar

/**
 * Tägliche Erinnerung "Fahrten eintragen". Ein einmaliger, ungenauer Alarm (ohne Sonderberechtigung
 * für exakte Alarme) weckt den [SystemReceiver]; der zeigt die Benachrichtigung und plant den
 * nächsten Tag über [sync] – so gilt immer der aktuelle Stand der Einstellungen.
 */
object Reminder {

    internal const val ACTION = "de.fosstenbuch.tracking.REMINDER"
    private const val CHANNEL_ID = "trip_reminder_channel"
    private const val NOTIFICATION_ID = 1001
    private const val DEFAULT_TIME = "18:00"

    /** Plant oder storniert die tägliche Erinnerung gemäß den Einstellungen */
    suspend fun sync(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val alarm = PendingIntent.getBroadcast(
            context, 0, Intent(context, SystemReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Settings.get(context, Settings.REMINDER_ENABLED, false)) {
            val (hour, minute) = parseTime(Settings.get(context, Settings.REMINDER_TIME, DEFAULT_TIME))
            // "AllowWhileIdle": kommt auch im Doze-Modus, ggf. einige Minuten später
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger(System.currentTimeMillis(), hour, minute), alarm)
        } else {
            alarms.cancel(alarm)
        }
    }

    /** Der Alarm ist da: nächsten Tag planen (einmaliger Alarm) und – falls noch gewünscht – erinnern */
    internal suspend fun onAlarm(context: Context) {
        sync(context)
        if (!Settings.get(context, Settings.REMINDER_ENABLED, false)) return
        val manager = context.notificationChannel(
            CHANNEL_ID, R.string.notification_channel_name, R.string.notification_channel_desc, NotificationManager.IMPORTANCE_DEFAULT
        )
        // Ab Android 13 nur mit Benachrichtigungs-Berechtigung (ohne sie wären Benachrichtigungen blockiert)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_trips)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_message))
            .setAutoCancel(true)
            .setContentIntent(context.openAppIntent())
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    /** "HH:mm" → Stunde und Minute; ungültige Teile fallen auf 18 bzw. 0 zurück */
    fun parseTime(time: String): Pair<Int, Int> {
        val parts = time.split(":")
        val hour = parts[0].trim().toIntOrNull()?.takeIf { it in 0..23 } ?: 18
        val minute = parts.getOrNull(1)?.trim()?.toIntOrNull()?.takeIf { it in 0..59 } ?: 0
        return hour to minute
    }

    /** Nächster Zeitpunkt [hour]:[minute] Ortszeit nach [now] – heute oder morgen (auch über Zeitumstellungen) */
    fun nextTrigger(now: Long, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis
    }
}
