package de.fosstenbuch.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Einstellungsdatei (Name nicht ändern, sonst sind bestehende Einstellungen weg).
 * Ist die Datei beschädigt, startet sie leer – sonst stürzte die App bei jedem Start ab (App.onCreate liest sie).
 */
private val Context.dataStore by preferencesDataStore(
    name = "fosstenbuch_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

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
    /** Verlauf "Backup eingespielt" / "Alle Daten gelöscht" (siehe Backup.history) – überlebt "Alle Daten löschen" */
    val DATA_HISTORY = stringPreferencesKey("data_history")
    /** Zeitstempel (online) für Siegel, siehe logic/Tsa – standardmäßig aus */
    val TSA_ENABLED = booleanPreferencesKey("tsa_enabled")
    /** Adresse des Zeitstempeldienstes (Tsa.PROVIDERS oder eigene URL; "" = ungültige Eingabe) */
    val TSA_URL = stringPreferencesKey("tsa_url")

    /** Liefert den Wert bei jeder Änderung neu (für die UI) */
    fun <T> flow(context: Context, key: Preferences.Key<T>, default: T): Flow<T> =
        context.dataStore.data.map { it[key] ?: default }

    suspend fun <T> get(context: Context, key: Preferences.Key<T>, default: T): T =
        flow(context, key, default).first()

    /** Ändert einen Wert in einem Schritt (Lesen und Schreiben ohne Lücke dazwischen) */
    suspend fun <T> update(context: Context, key: Preferences.Key<T>, default: T, change: (T) -> T) {
        context.dataStore.edit { it[key] = change(it[key] ?: default) }
    }

    /** null entfernt den Eintrag (zurück zum Standardwert) */
    suspend fun <T> set(context: Context, key: Preferences.Key<T>, value: T?) {
        context.dataStore.edit { if (value == null) it.remove(key) else it[key] = value }
    }
}
