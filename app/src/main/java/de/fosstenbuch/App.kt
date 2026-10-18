package de.fosstenbuch

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import de.fosstenbuch.data.Settings
import de.fosstenbuch.logic.Tsa
import de.fosstenbuch.tracking.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Application: setzt den Dunkelmodus vor der ersten Activity, plant die Erinnerung neu und holt Zeitstempel nach */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        // Synchron lesen (winzige Datei): So steht der Dunkelmodus fest, bevor die erste
        // Activity erscheint, und sie muss nicht gleich wieder neu erstellt werden.
        applyDarkMode(runBlocking { Settings.get(this@App, Settings.DARK_MODE, "SYSTEM") })
        // Erinnerung neu planen: "Beenden erzwingen" löscht alle Alarme der App
        CoroutineScope(Dispatchers.IO).launch { Reminder.sync(this@App) }
        // Zeitstempel nachholen, die offline oder wegen eines Fehlers ausblieben (nur falls eingeschaltet)
        Tsa.stampPending(this)
    }

    companion object {
        /** "LIGHT", "DARK" oder "SYSTEM" (folgt der Systemeinstellung) */
        fun applyDarkMode(mode: String) = AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                "LIGHT" -> AppCompatDelegate.MODE_NIGHT_NO
                "DARK" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
