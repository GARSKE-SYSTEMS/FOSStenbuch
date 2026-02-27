package de.fosstenbuch.domain.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.fosstenbuch.R
import de.fosstenbuch.utils.HaversineUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.Locale

/**
 * Foreground service that tracks GPS location during an active trip.
 * Accumulates driven distance using Haversine formula between consecutive GPS fixes.
 */
class LocationTrackingService : Service() {

    companion object {
        const val ACTION_START = "de.fosstenbuch.ACTION_START_TRACKING"
        const val ACTION_STOP = "de.fosstenbuch.ACTION_STOP_TRACKING"
        const val EXTRA_TRIP_ID = "EXTRA_TRIP_ID"

        private const val CHANNEL_ID = "location_tracking_channel"
        private const val NOTIFICATION_ID = 2001
        private const val LOCATION_INTERVAL_MS = 15_000L
        private const val LOCATION_MIN_DISPLACEMENT_M = 10f

        private val _gpsDistanceKm = MutableStateFlow(0.0)
        val gpsDistanceKm: StateFlow<Double> = _gpsDistanceKm.asStateFlow()

        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

        fun start(context: Context, tripId: Long) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TRIP_ID, tripId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private lateinit var locationManager: LocationManager
    private var locationListener: LocationListener? = null
    private var lastLocation: Location? = null
    private var totalDistanceMeters: Double = 0.0
    private var activeTripId: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                activeTripId = intent.getLongExtra(EXTRA_TRIP_ID, 0L)
                startTracking()
            }
            ACTION_STOP -> {
                stopTracking()
            }
        }
        return START_STICKY
    }

    @Suppress("MissingPermission")
    private fun startTracking() {
        if (!hasLocationPermission()) {
            Timber.w("No location permission for tracking service")
            stopSelf()
            return
        }

        totalDistanceMeters = 0.0
        lastLocation = null
        _gpsDistanceKm.value = 0.0
        _isTracking.value = true

        val notification = buildNotification(0.0)
        startForeground(NOTIFICATION_ID, notification)

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                processNewLocation(location)
            }

            @Deprecated("Deprecated in API level 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {
                // Required for API < 29
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                Timber.w("Location provider $provider disabled")
            }
        }
        locationListener = listener

        // Prefer GPS, fall back to network provider
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            else -> {
                Timber.w("No location provider available")
                stopSelf()
                return
            }
        }

        locationManager.requestLocationUpdates(
            provider,
            LOCATION_INTERVAL_MS,
            LOCATION_MIN_DISPLACEMENT_M,
            listener
        )

        Timber.d("Location tracking started for trip $activeTripId (provider: $provider)")
    }

    private fun processNewLocation(location: Location) {
        val previous = lastLocation
        if (previous != null) {
            val distanceMeters = HaversineUtils.distanceInMeters(
                previous.latitude, previous.longitude,
                location.latitude, location.longitude
            )
            // Only count movements > 5m to filter GPS jitter
            if (distanceMeters > 5.0) {
                totalDistanceMeters += distanceMeters
                val distanceKm = totalDistanceMeters / 1000.0
                _gpsDistanceKm.value = distanceKm
                updateNotification(distanceKm)
            }
        }
        lastLocation = location
    }

    private fun stopTracking() {
        _isTracking.value = false
        locationListener?.let { locationManager.removeUpdates(it) }
        locationListener = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Timber.d("Location tracking stopped. Total distance: %.1f km", totalDistanceMeters / 1000.0)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tracking_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.tracking_channel_desc)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(distanceKm: Double): Notification {
        val mainIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(
                String.format(Locale.GERMANY, getString(R.string.tracking_notification_text), distanceKm)
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(distanceKm: Double) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(distanceKm))
    }

    override fun onDestroy() {
        super.onDestroy()
        _isTracking.value = false
        locationListener?.let { locationManager.removeUpdates(it) }
        locationListener = null
    }
}
