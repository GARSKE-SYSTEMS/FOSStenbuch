package de.fosstenbuch.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Einstellungsdatei (Name nicht ändern, sonst sind bestehende Einstellungen weg) */
private val Context.dataStore by preferencesDataStore(name = "fosstenbuch_settings")

/**
 * App-Einstellungen. Verwendung: `Settings.get(context, Settings.DRIVER_NAME, "")`.
 */
object Settings {
    /** "SYSTEM", "LIGHT" oder "DARK" */
    val DARK_MODE = stringPreferencesKey("dark_mode")
    /** Kategorie, die bei neuen Fahrten vorausgewählt wird */
    val DEFAULT_PURPOSE_ID = longPreferencesKey("default_purpose_id")
    val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
    /** Uhrzeit der täglichen Erinnerung als "HH:mm" */
    val REMINDER_TIME = stringPreferencesKey("reminder_time")
    /** Fahrername für Export-Kopfzeilen */
    val DRIVER_NAME = stringPreferencesKey("driver_name")

    /** Liefert den Wert bei jeder Änderung neu (für die UI) */
    fun <T> flow(context: Context, key: Preferences.Key<T>, default: T): Flow<T> =
        context.dataStore.data.map { it[key] ?: default }

    suspend fun <T> get(context: Context, key: Preferences.Key<T>, default: T): T =
        flow(context, key, default).first()

    /** null entfernt den Eintrag (zurück zum Standardwert) */
    suspend fun <T> set(context: Context, key: Preferences.Key<T>, value: T?) {
        context.dataStore.edit { if (value == null) it.remove(key) else it[key] = value }
    }
}
