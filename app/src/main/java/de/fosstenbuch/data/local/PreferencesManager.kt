package de.fosstenbuch.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fosstenbuch_settings")

class PreferencesManager(private val context: Context) {

    enum class DarkMode {
        SYSTEM, LIGHT, DARK
    }

    enum class DistanceUnit(val label: String, val suffix: String) {
        KM("Kilometer", "km"),
        MILES("Meilen", "mi")
    }

    companion object {
        private val KEY_DARK_MODE = stringPreferencesKey("dark_mode")
        private val KEY_DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        private val KEY_DEFAULT_PURPOSE_ID = longPreferencesKey("default_purpose_id")
        private val KEY_DEFAULT_VEHICLE_ID = longPreferencesKey("default_vehicle_id")
        private val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        private val KEY_REMINDER_TIME = stringPreferencesKey("reminder_time")
    }

    // --- Dark Mode ---

    val darkMode: Flow<DarkMode> = context.dataStore.data.map { prefs ->
        try {
            DarkMode.valueOf(prefs[KEY_DARK_MODE] ?: DarkMode.SYSTEM.name)
        } catch (_: Exception) {
            DarkMode.SYSTEM
        }
    }

    suspend fun setDarkMode(mode: DarkMode) {
        context.dataStore.edit { it[KEY_DARK_MODE] = mode.name }
    }

    // --- Distance Unit ---

    val distanceUnit: Flow<DistanceUnit> = context.dataStore.data.map { prefs ->
        try {
            DistanceUnit.valueOf(prefs[KEY_DISTANCE_UNIT] ?: DistanceUnit.KM.name)
        } catch (_: Exception) {
            DistanceUnit.KM
        }
    }

    suspend fun setDistanceUnit(unit: DistanceUnit) {
        context.dataStore.edit { it[KEY_DISTANCE_UNIT] = unit.name }
    }

    // --- Default Purpose ---

    val defaultPurposeId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_PURPOSE_ID]?.takeIf { it > 0 }
    }

    suspend fun setDefaultPurposeId(id: Long?) {
        context.dataStore.edit {
            if (id != null && id > 0) {
                it[KEY_DEFAULT_PURPOSE_ID] = id
            } else {
                it.remove(KEY_DEFAULT_PURPOSE_ID)
            }
        }
    }

    // --- Default Vehicle ---

    val defaultVehicleId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_VEHICLE_ID]?.takeIf { it > 0 }
    }

    suspend fun setDefaultVehicleId(id: Long?) {
        context.dataStore.edit {
            if (id != null && id > 0) {
                it[KEY_DEFAULT_VEHICLE_ID] = id
            } else {
                it.remove(KEY_DEFAULT_VEHICLE_ID)
            }
        }
    }

    // --- Reminder ---

    val reminderEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_REMINDER_ENABLED] ?: false
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_REMINDER_ENABLED] = enabled }
    }

    val reminderTime: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_REMINDER_TIME] ?: "18:00"
    }

    suspend fun setReminderTime(time: String) {
        context.dataStore.edit { it[KEY_REMINDER_TIME] = time }
    }
}
