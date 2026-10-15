package de.fosstenbuch.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import de.fosstenbuch.ui.MainActivity

// Gemeinsame Helfer für die Benachrichtigungen von Aufzeichnung und Erinnerung.

/**
 * Legt einen Benachrichtigungskanal an und liefert den NotificationManager. Beliebig oft aufrufbar:
 * Einen bestehenden Kanal ändert Android dabei nur in Name/Beschreibung, die Einstellungen des Nutzers bleiben.
 */
internal fun Context.notificationChannel(id: String, @StringRes name: Int, @StringRes description: Int, importance: Int): NotificationManager {
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel(id, getString(name), importance).also { it.description = getString(description) })
    return manager
}

/**
 * Antippen öffnet die App. Derselbe Intent wie vom Launcher (MAIN/LAUNCHER), damit Android eine
 * bereits laufende App nur nach vorne holt, statt eine zweite MainActivity darüberzulegen.
 */
internal fun Context.openAppIntent(): PendingIntent = PendingIntent.getActivity(
    this,
    0,
    Intent.makeMainActivity(ComponentName(this, MainActivity::class.java)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    PendingIntent.FLAG_IMMUTABLE
)
