package de.fosstenbuch

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import de.fosstenbuch.utils.TimberDebugTree
import timber.log.Timber

@HiltAndroidApp
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(TimberDebugTree())
        }
        // Initialize any app-wide components here
    }
}