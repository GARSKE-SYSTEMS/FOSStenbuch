package de.fosstenbuch.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import de.fosstenbuch.R
import de.fosstenbuch.data.local.PreferencesManager
import de.fosstenbuch.databinding.ActivityMainBinding
import de.fosstenbuch.domain.service.BluetoothTrackingService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration

    @Inject
    lateinit var preferencesManager: PreferencesManager

    /**
     * Launcher for all permissions needed by the BT ghost-trip feature.
     * On API 31+ we need BLUETOOTH_CONNECT + BLUETOOTH_SCAN.
     * On API 33+ we also need POST_NOTIFICATIONS.
     * ACCESS_FINE_LOCATION is always needed for GPS tracking.
     */
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { (perm, granted) ->
            Timber.d("Permission %s granted: %s", perm, granted)
        }
        // (Re)start BT service now that permissions may have been granted
        if (results[Manifest.permission.BLUETOOTH_CONNECT] == true ||
            results[Manifest.permission.BLUETOOTH_SCAN] == true) {
            BluetoothTrackingService.start(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request all permissions needed for ghost-trip BT tracking + GPS + notifications
        requestRequiredPermissions()

        // Apply dark mode preference
        lifecycleScope.launch {
            val darkMode = preferencesManager.darkMode.first()
            val nightMode = when (darkMode) {
                PreferencesManager.DarkMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                PreferencesManager.DarkMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                PreferencesManager.DarkMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        // All top-level drawer destinations — these show the burger icon
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_trips,
                R.id.navigation_stats,
                R.id.navigation_vehicles,
                R.id.navigation_purposes,
                R.id.navigation_saved_locations,
                R.id.navigation_export,
                R.id.navigation_mileage_calculator,
                R.id.navigation_settings
            ),
            drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Close drawer after navigation
        navView.setNavigationItemSelectedListener { menuItem ->
            navController.navigate(menuItem.itemId)
            drawerLayout.closeDrawers()
            true
        }

        // Add sticky footer with company info
        setupDrawerFooter(navView, drawerLayout)
    }

    private fun setupDrawerFooter(navView: NavigationView, drawerLayout: DrawerLayout) {
        val footerView = LayoutInflater.from(this).inflate(R.layout.nav_footer, navView, false)

        // Make footer stick to the bottom
        val params = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM
        }
        navView.addView(footerView, params)

        footerView.findViewById<android.view.View>(R.id.layout_github).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/GARSKE-SYSTEMS/FOSStenbuch"))
            startActivity(intent)
            drawerLayout.closeDrawers()
        }

        footerView.findViewById<android.view.View>(R.id.layout_info).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://garske-systems.de"))
            startActivity(intent)
            drawerLayout.closeDrawers()
        }
    }

    /**
     * Requests every runtime permission the ghost-trip feature needs:
     *  - ACCESS_FINE_LOCATION   (GPS tracking during ghost trips)
     *  - BLUETOOTH_CONNECT      (read device name, API 31+)
     *  - BLUETOOTH_SCAN          (BT discovery, API 31+)
     *  - POST_NOTIFICATIONS     (foreground-service notification, API 33+)
     *
     * Only permissions that are NOT yet granted are requested.
     */
    private fun requestRequiredPermissions() {
        val needed = mutableListOf<String>()

        // Location — always needed for GPS recording
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
        }

        // Bluetooth — API 31+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.BLUETOOTH_CONNECT
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.BLUETOOTH_SCAN
            }
        }

        // Notifications — API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.POST_NOTIFICATIONS
            }
        }

        if (needed.isNotEmpty()) {
            Timber.d("Requesting permissions: %s", needed)
            permissionsLauncher.launch(needed.toTypedArray())
        } else {
            Timber.d("All required permissions already granted")
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}