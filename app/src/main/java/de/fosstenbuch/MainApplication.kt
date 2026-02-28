package de.fosstenbuch

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import de.fosstenbuch.domain.service.BluetoothTrackingService
import de.fosstenbuch.utils.TimberDebugTree
import timber.log.Timber

@HiltAndroidApp
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(TimberDebugTree())
        }
        // Always ensure the Bluetooth monitoring service is running.
        // The service is a foreground service (START_STICKY) and handles
        // missing permissions gracefully, so this call is always safe.
        BluetoothTrackingService.start(this)
    }
}