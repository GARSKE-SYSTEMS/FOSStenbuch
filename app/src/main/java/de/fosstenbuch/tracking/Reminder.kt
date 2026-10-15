package de.fosstenbuch.tracking

import android.content.Context

// PLATZHALTER – wird vom Tracking-Teil implementiert. Die öffentliche API ist fest.
object Reminder {
    /** Plant oder storniert die tägliche Erinnerung gemäß den Einstellungen */
    suspend fun sync(context: Context) {}
}
