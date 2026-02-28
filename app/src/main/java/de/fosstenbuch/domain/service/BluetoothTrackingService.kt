package de.fosstenbuch.domain.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import de.fosstenbuch.R
import de.fosstenbuch.data.local.VehicleDao
import de.fosstenbuch.domain.usecase.trip.CreateGhostTripUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Date
import javax.inject.Inject

/**
 * Foreground service that monitors Bluetooth ACL connection events.
 * When a device matching a configured vehicle connects, GPS tracking starts automatically.
 * When the device disconnects, tracking stops and a ghost trip is persisted.
 */
@AndroidEntryPoint
class BluetoothTrackingService : Service() {

    @Inject lateinit var vehicleDao: VehicleDao
    @Inject lateinit var createGhostTripUseCase: CreateGhostTripUseCase

    companion object {
        const val ACTION_START = "de.fosstenbuch.ACTION_START_BT_MONITOR"
        const val ACTION_STOP = "de.fosstenbuch.ACTION_STOP_BT_MONITOR"

        private const val CHANNEL_ID = "bt_tracking_channel"
        private const val NOTIFICATION_ID = 2002

        fun start(context: Context) {
            val intent = Intent(context, BluetoothTrackingService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BluetoothTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // State of the current auto-tracking session
    private var activeVehicleId: Long? = null
    private var trackingStartTime: Date? = null

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device: BluetoothDevice? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

            val address = device?.address ?: return

            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> handleConnected(address)
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> handleDisconnected(address)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(bluetoothReceiver, filter)
        Timber.d("BluetoothTrackingService started – listening for ACL events")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    // ── Connection handlers ────────────────────────────────────────────────

    private fun handleConnected(address: String) {
        if (!hasBluetoothPermission()) {
            Timber.w("BLUETOOTH_CONNECT permission missing – cannot check device")
            return
        }

        serviceScope.launch {
            val vehicle = vehicleDao.getVehicleByBluetoothAddress(address) ?: return@launch
            Timber.i("BT connected: %s → vehicle '%s %s' (id=%d)",
                address, vehicle.make, vehicle.model, vehicle.id)

            if (activeVehicleId != null) {
                Timber.w("Already tracking vehicle %d – ignoring new connection", activeVehicleId)
                return@launch
            }

            // Insert a placeholder active trip so LocationTrackingService has a tripId
            activeVehicleId = vehicle.id
            trackingStartTime = Date()

            // Start GPS tracking – we use a sentinel ID (0L) for ghost trips;
            // the real DB entry is created on disconnect.
            LocationTrackingService.start(this@BluetoothTrackingService, 0L)
        }
    }

    private fun handleDisconnected(address: String) {
        val vehicleId = activeVehicleId ?: return

        serviceScope.launch {
            val vehicle = vehicleDao.getVehicleByBluetoothAddress(address)
            if (vehicle?.id != vehicleId) return@launch

            Timber.i("BT disconnected: %s → stopping tracking for vehicle %d", address, vehicleId)

            val endTime = Date()
            val startTime = trackingStartTime ?: endTime
            val distanceKm = LocationTrackingService.gpsDistanceKm.value
            val startCoords = LocationTrackingService.startLocation.value
            val endCoords = LocationTrackingService.currentLocation.value

            // Stop GPS tracking first
            LocationTrackingService.stop(this@BluetoothTrackingService)

            // Reset state
            activeVehicleId = null
            trackingStartTime = null

            // Create ghost trip only if we have at least a rough start position
            if (startCoords == null) {
                Timber.w("No GPS fix during session – ghost trip not created")
                return@launch
            }

            val (startLat, startLng) = startCoords
            val (endLat, endLng) = endCoords ?: startCoords // use last known if end unavailable

            val input = CreateGhostTripUseCase.GhostTripInput(
                vehicleId = vehicleId,
                startTime = startTime,
                endTime = endTime,
                startLat = startLat,
                startLng = startLng,
                endLat = endLat,
                endLng = endLng,
                gpsDistanceKm = distanceKm
            )

            when (val result = createGhostTripUseCase(input)) {
                is CreateGhostTripUseCase.Result.Success ->
                    Timber.i("Ghost trip persisted: id=%d", result.tripId)
                is CreateGhostTripUseCase.Result.Error ->
                    Timber.e(result.exception, "Failed to persist ghost trip")
            }
        }
    }

    // ── Permissions ───────────────────────────────────────────────────────

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Pre-Android 12: permission not required at runtime
        }
    }

    // ── Notification ──────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.bt_tracking_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.bt_tracking_channel_desc)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val mainIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location)
            .setContentTitle(getString(R.string.bt_tracking_notification_title))
            .setContentText(getString(R.string.bt_tracking_notification_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(bluetoothReceiver) } catch (_: Exception) {}
        serviceScope.cancel()
        Timber.d("BluetoothTrackingService destroyed")
    }
}
