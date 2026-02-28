package de.fosstenbuch.domain.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import de.fosstenbuch.R
import de.fosstenbuch.data.local.VehicleDao
import de.fosstenbuch.domain.usecase.trip.CreateGhostTripUseCase
import de.fosstenbuch.utils.HaversineUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Date
import javax.inject.Inject

/**
 * Foreground service that monitors Bluetooth events to automatically create ghost trips.
 *
 * GPS tracking for ghost trips is handled **internally** — the service upgrades its own
 * foreground type to include LOCATION when recording begins. This avoids starting a
 * separate LocationTrackingService from background, which Android 12+ blocks from
 * accessing location.
 *
 * Triggers:
 *  - ACL_CONNECTED  : paired device connects (classic BT auto-connect)
 *  - ACTION_FOUND   : device visible during periodic discovery scan (no pairing required)
 *
 * While recording the notification shows elapsed time and GPS distance.
 * In standby the notification shows a "ready to record" status.
 */
@AndroidEntryPoint
class BluetoothTrackingService : Service() {

    @Inject lateinit var vehicleDao: VehicleDao
    @Inject lateinit var createGhostTripUseCase: CreateGhostTripUseCase

    companion object {
        const val ACTION_START = "de.fosstenbuch.ACTION_START_BT_MONITOR"
        const val ACTION_STOP  = "de.fosstenbuch.ACTION_STOP_BT_MONITOR"

        private const val CHANNEL_ID = "bt_tracking_channel"
        private const val NOTIFICATION_ID = 2002

        /** Interval between BT discovery scans when idle (ms). */
        private const val DISCOVERY_INTERVAL_MS = 60_000L

        /** End ghost trip if device not re-discovered within this window (ms). */
        private const val WATCHDOG_TIMEOUT_MS = 5 * 60_000L

        /** How often the notification ticks while recording (ms). */
        private const val NOTIFICATION_TICK_MS = 30_000L

        /** GPS: request interval (ms). */
        private const val GPS_INTERVAL_MS = 15_000L
        /** GPS: minimum displacement (m). */
        private const val GPS_MIN_DISPLACEMENT_M = 10f

        /**
         * Name of the BT device currently being recorded, or null when in standby.
         * Observed by TripsViewModel to drive the "recording" banner.
         */
        private val _activeDeviceName = MutableStateFlow<String?>(null)
        val activeDeviceName: StateFlow<String?> = _activeDeviceName.asStateFlow()

        /** Epoch ms when the current ghost-trip recording started; 0 when in standby. */
        private val _recordingStartTimeMs = MutableStateFlow(0L)
        val recordingStartTimeMs: StateFlow<Long> = _recordingStartTimeMs.asStateFlow()

        /** GPS distance accumulated during the current ghost trip (km). */
        private val _ghostGpsDistanceKm = MutableStateFlow(0.0)
        val ghostGpsDistanceKm: StateFlow<Double> = _ghostGpsDistanceKm.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BluetoothTrackingService::class.java).apply {
                    action = ACTION_START
                }
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, BluetoothTrackingService::class.java).apply {
                    action = ACTION_STOP
                }
            )
        }
    }

    private val serviceScope  = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler   = Handler(Looper.getMainLooper())

    // ── Active-trip state (guarded by @Synchronized) ───────────────────────
    private val lock = Any()
    @Volatile private var activeVehicleId      : Long?   = null
    @Volatile private var trackingStartTime    : Date?   = null
    @Volatile private var activeDeviceAddress  : String? = null
    /** true = trip started via ACTION_FOUND (scanning), false = via ACL_CONNECTED */
    @Volatile private var discoveryBasedTracking = false

    private var notificationUpdateJob: Job? = null

    // ── Internal GPS tracking state ────────────────────────────────────────
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var gpsLastLocation: Location? = null
    private var gpsTotalDistanceMeters: Double = 0.0
    private var gpsStartCoords: Pair<Double, Double>? = null
    private var gpsCurrentCoords: Pair<Double, Double>? = null

    // ── Handler runnables ──────────────────────────────────────────────────
    /** Triggers the next BT discovery cycle. */
    private val discoveryRunnable = Runnable { runDiscoveryCycle() }

    /** Ends a discovery-based trip when the device hasn't been seen for WATCHDOG_TIMEOUT_MS. */
    private val watchdogRunnable = Runnable {
        Timber.i("Watchdog: %s not re-discovered in %ds – ending ghost trip",
            activeDeviceAddress, WATCHDOG_TIMEOUT_MS / 1000)
        activeDeviceAddress?.let { endGhostTrip(it) }
    }

    // ── BroadcastReceiver ──────────────────────────────────────────────────
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

            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val addr = device?.address ?: return
                    handleConnected(addr, device)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val addr = device?.address ?: return
                    // Only handle disconnect for ACL-connected sessions
                    if (!discoveryBasedTracking) endGhostTrip(addr)
                }
                BluetoothDevice.ACTION_FOUND -> {
                    val addr = device?.address ?: return
                    handleDeviceFound(addr, device)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    // Schedule next scan only when not actively tracking
                    if (activeVehicleId == null) scheduleNextDiscovery()
                }
            }
        }
    }

    // ── Service lifecycle ──────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        createNotificationChannel()
        // Start with connectedDevice type only; location is added when recording begins
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        // RECEIVER_EXPORTED is required: these are system broadcasts from the BT stack,
        // not from our own app. RECEIVER_NOT_EXPORTED silently drops them all.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothReceiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(bluetoothReceiver, filter)
        }

        scheduleNextDiscovery()
        Timber.d("BluetoothTrackingService started – listening for BT events + scanning")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopGpsTracking()
        try { unregisterReceiver(bluetoothReceiver) } catch (_: Exception) {}
        mainHandler.removeCallbacksAndMessages(null)
        notificationUpdateJob?.cancel()
        serviceScope.cancel()
        _activeDeviceName.value = null
        _recordingStartTimeMs.value = 0L
        _ghostGpsDistanceKm.value = 0.0
        Timber.d("BluetoothTrackingService destroyed")
    }

    // ── BT Discovery ──────────────────────────────────────────────────────

    private fun scheduleNextDiscovery() {
        mainHandler.removeCallbacks(discoveryRunnable)
        mainHandler.postDelayed(discoveryRunnable, DISCOVERY_INTERVAL_MS)
    }

    private fun runDiscoveryCycle() {
        if (activeVehicleId != null) return // skip while tracking
        if (!hasBluetoothScanPermission()) { scheduleNextDiscovery(); return }

        val adapter = btAdapter() ?: run { scheduleNextDiscovery(); return }
        if (!adapter.isEnabled) { scheduleNextDiscovery(); return }

        if (adapter.isDiscovering) adapter.cancelDiscovery()
        val started = adapter.startDiscovery()
        Timber.d("BT discovery started: $started")

        // Fallback schedule in case ACTION_DISCOVERY_FINISHED never fires
        mainHandler.postDelayed(discoveryRunnable, DISCOVERY_INTERVAL_MS + 15_000L)
    }

    /** Called when BT discovery finds a device nearby (no ACL connection required). */
    private fun handleDeviceFound(address: String, device: BluetoothDevice?) {
        synchronized(lock) {
            if (activeVehicleId != null) {
                // If we see our currently tracked device → reset the watchdog
                if (discoveryBasedTracking && address == activeDeviceAddress) {
                    mainHandler.removeCallbacks(watchdogRunnable)
                    mainHandler.postDelayed(watchdogRunnable, WATCHDOG_TIMEOUT_MS)
                }
                return
            }
        }

        serviceScope.launch {
            val vehicle = vehicleDao.getVehicleByBluetoothAddress(address) ?: return@launch
            synchronized(lock) {
                if (activeVehicleId != null) return@launch  // double-check after DB query
                val name = readDeviceName(device)
                Timber.i("BT found (scan): %s → vehicle '%s %s'", address, vehicle.make, vehicle.model)
                startGhostTrip(vehicle.id, name, address, discoveryBased = true)
            }
        }
    }

    /** Called when a device establishes a full ACL connection. */
    private fun handleConnected(address: String, device: BluetoothDevice?) {
        if (!hasBluetoothPermission()) {
            Timber.w("BLUETOOTH_CONNECT permission missing – cannot process ACL_CONNECTED")
            return
        }
        serviceScope.launch {
            val vehicle = vehicleDao.getVehicleByBluetoothAddress(address) ?: return@launch
            synchronized(lock) {
                if (activeVehicleId != null) {
                    Timber.w("Already tracking vehicle %d – ignoring ACL_CONNECTED for %s",
                        activeVehicleId, address)
                    return@launch
                }
                val name = readDeviceName(device)
                Timber.i("BT connected (ACL): %s → vehicle '%s %s'", address, vehicle.make, vehicle.model)
                startGhostTrip(vehicle.id, name, address, discoveryBased = false)
            }
        }
    }

    // ── Ghost trip lifecycle ──────────────────────────────────────────────

    private fun startGhostTrip(
        vehicleId      : Long,
        deviceName     : String?,
        address        : String,
        discoveryBased : Boolean
    ) {
        activeVehicleId       = vehicleId
        trackingStartTime     = Date()
        activeDeviceAddress   = address
        discoveryBasedTracking = discoveryBased
        _activeDeviceName.value = deviceName ?: address
        _recordingStartTimeMs.value = System.currentTimeMillis()

        // Stop discovery while we are recording
        mainHandler.removeCallbacks(discoveryRunnable)
        btAdapter()?.cancelDiscovery()

        if (discoveryBased) {
            mainHandler.postDelayed(watchdogRunnable, WATCHDOG_TIMEOUT_MS)
        }

        // Upgrade foreground type to include LOCATION, then start internal GPS tracking.
        // This avoids starting a separate LocationTrackingService from background which
        // Android 12+ blocks from accessing location.
        upgradeToLocationForeground()
        startGpsTracking()

        startNotificationUpdates()
        mainHandler.post { postNotification() }
    }

    private fun endGhostTrip(address: String) {
        val vehicleId: Long
        val startTime: Date
        val distanceKm: Double
        val startCoords: Pair<Double, Double>?
        val endCoords: Pair<Double, Double>?

        synchronized(lock) {
            vehicleId = activeVehicleId ?: return
            startTime = trackingStartTime ?: Date()

            // Capture GPS state before resetting
            distanceKm  = gpsTotalDistanceMeters / 1000.0
            startCoords = gpsStartCoords
            endCoords   = gpsCurrentCoords

            // Reset tracking state immediately to prevent double-end
            activeVehicleId       = null
            trackingStartTime     = null
            activeDeviceAddress   = null
            discoveryBasedTracking = false
        }

        // Stop GPS tracking and reset companion flows
        stopGpsTracking()
        _activeDeviceName.value = null
        _recordingStartTimeMs.value = 0L
        _ghostGpsDistanceKm.value = 0.0

        notificationUpdateJob?.cancel()
        notificationUpdateJob = null
        mainHandler.removeCallbacks(watchdogRunnable)

        // Downgrade back to connectedDevice-only foreground
        downgradeFromLocationForeground()

        // Resume idle discovery
        scheduleNextDiscovery()

        serviceScope.launch {
            val vehicle = vehicleDao.getVehicleByBluetoothAddress(address)
            if (vehicle?.id != vehicleId) return@launch

            Timber.i("Ghost trip ending for vehicle %d (address %s)", vehicleId, address)

            val endTime = Date()

            if (startCoords == null) {
                Timber.w("No GPS fix during session – ghost trip not saved")
                return@launch
            }

            val (startLat, startLng) = startCoords
            val (endLat,   endLng  ) = endCoords ?: startCoords

            val input = CreateGhostTripUseCase.GhostTripInput(
                vehicleId     = vehicleId,
                startTime     = startTime,
                endTime       = endTime,
                startLat      = startLat,
                startLng      = startLng,
                endLat        = endLat,
                endLng        = endLng,
                gpsDistanceKm = distanceKm
            )

            when (val result = createGhostTripUseCase(input)) {
                is CreateGhostTripUseCase.Result.Success ->
                    Timber.i("Ghost trip persisted: id=%d", result.tripId)
                is CreateGhostTripUseCase.Result.Error   ->
                    Timber.e(result.exception, "Failed to persist ghost trip")
            }
        }
    }

    // ── Internal GPS tracking ─────────────────────────────────────────────

    @Suppress("MissingPermission")
    private fun startGpsTracking() {
        if (!hasLocationPermission()) {
            Timber.w("ACCESS_FINE_LOCATION not granted – ghost trip will have no GPS data")
            return
        }

        gpsTotalDistanceMeters = 0.0
        gpsLastLocation = null
        gpsStartCoords  = null
        gpsCurrentCoords = null
        _ghostGpsDistanceKm.value = 0.0

        val lm = locationManager ?: return
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                processGpsLocation(location)
            }
            @Deprecated("Deprecated in API level 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                Timber.w("GPS provider disabled during ghost trip tracking")
            }
        }
        locationListener = listener

        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)     -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> {
                Timber.w("No location provider available for ghost trip")
                return
            }
        }

        lm.requestLocationUpdates(provider, GPS_INTERVAL_MS, GPS_MIN_DISPLACEMENT_M, listener)
        Timber.d("Internal GPS tracking started (provider: %s)", provider)
    }

    private fun stopGpsTracking() {
        locationListener?.let {
            try { locationManager?.removeUpdates(it) } catch (_: Exception) {}
        }
        locationListener = null
        gpsLastLocation = null
        Timber.d("Internal GPS tracking stopped. Distance: %.1f km", gpsTotalDistanceMeters / 1000.0)
    }

    private fun processGpsLocation(location: Location) {
        val coords = Pair(location.latitude, location.longitude)
        if (gpsStartCoords == null) gpsStartCoords = coords
        gpsCurrentCoords = coords

        val prev = gpsLastLocation
        if (prev != null) {
            val meters = HaversineUtils.distanceInMeters(
                prev.latitude, prev.longitude,
                location.latitude, location.longitude
            )
            if (meters > 5.0) {
                gpsTotalDistanceMeters += meters
                _ghostGpsDistanceKm.value = gpsTotalDistanceMeters / 1000.0
            }
        }
        gpsLastLocation = location
    }

    // ── Foreground service type management ─────────────────────────────────

    /**
     * Re-calls startForeground with connectedDevice + location type so that
     * GPS access is allowed even when started from background.
     */
    private fun upgradeToLocationForeground() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not upgrade foreground type to include LOCATION")
        }
    }

    /** Downgrades back to connectedDevice-only after recording stops. */
    private fun downgradeFromLocationForeground() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                postNotification()
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not downgrade foreground type")
            mainHandler.post { postNotification() }
        }
    }

    // ── Notification live-updates ─────────────────────────────────────────

    private fun startNotificationUpdates() {
        notificationUpdateJob?.cancel()
        notificationUpdateJob = serviceScope.launch {
            _ghostGpsDistanceKm.collect { _ ->
                mainHandler.post { postNotification() }
            }
        }
        // Tick every 30 s for elapsed-time update
        serviceScope.launch {
            while (activeVehicleId != null) {
                delay(NOTIFICATION_TICK_MS)
                if (activeVehicleId != null) mainHandler.post { postNotification() }
            }
        }
    }

    private fun postNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
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
        val mainIntent   = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isRecording = activeVehicleId != null
        return if (isRecording) {
            val deviceName  = _activeDeviceName.value ?: ""
            val distKm      = _ghostGpsDistanceKm.value
            val elapsedMin  = trackingStartTime?.let {
                (System.currentTimeMillis() - it.time) / 60_000L
            } ?: 0L
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_location)
                .setContentTitle(getString(R.string.ghost_recording_notification_title, deviceName))
                .setContentText(getString(R.string.ghost_recording_notification_text, distKm, elapsedMin))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .build()
        } else {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_location)
                .setContentTitle(getString(R.string.bt_tracking_notification_title))
                .setContentText(getString(R.string.bt_standby_notification_text))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setContentIntent(pendingIntent)
                .build()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun btAdapter(): BluetoothAdapter? =
        (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private fun readDeviceName(device: BluetoothDevice?): String? {
        if (device == null) return null
        return try {
            if (hasBluetoothPermission()) device.name else null
        } catch (_: SecurityException) { null }
    }

    private fun hasBluetoothPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else { true }

    private fun hasBluetoothScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else { true }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
}
