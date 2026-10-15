package de.fosstenbuch

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import de.fosstenbuch.data.Settings
import kotlinx.coroutines.runBlocking

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        // Synchron lesen (winzige Datei): So steht der Dunkelmodus fest, bevor die erste
        // Activity erscheint, und sie muss nicht gleich wieder neu erstellt werden.
        applyDarkMode(runBlocking { Settings.get(this@App, Settings.DARK_MODE, "SYSTEM") })
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
