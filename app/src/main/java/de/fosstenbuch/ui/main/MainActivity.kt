package de.fosstenbuch.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        footerView.findViewById<android.view.View>(R.id.layout_info).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://garske-systems.de"))
            startActivity(intent)
            drawerLayout.closeDrawers()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}