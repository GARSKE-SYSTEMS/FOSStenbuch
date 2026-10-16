package de.fosstenbuch.ui

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import de.fosstenbuch.R
import de.fosstenbuch.databinding.ActivityMainBinding
import de.fosstenbuch.ui.common.openUrl

/** Einzige Activity: Toolbar, Seitenmenü und Container für alle Bildschirme (Fragments) */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfig: AppBarConfiguration

    private val navController by lazy {
        (supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        // Dunkler Hintergrund hinter der Statusleiste (sonst nähme das DrawerLayout colorPrimary,
        // im Dunkelmodus hellviolett – die weißen Symbole wären unlesbar)
        binding.drawerLayout.setStatusBarBackgroundColor(getColor(R.color.status_bar))

        // Hauptbildschirme zeigen das Menü-Symbol statt des Zurück-Pfeils
        appBarConfig = AppBarConfiguration(
            setOf(
                R.id.navigation_trips, R.id.navigation_stats, R.id.navigation_vehicles,
                R.id.navigation_purposes, R.id.navigation_saved_locations, R.id.navigation_export,
                R.id.navigation_mileage_calculator, R.id.navigation_settings
            ),
            binding.drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfig)
        // Markiert den aktuellen Menüpunkt bei jedem Bildschirmwechsel
        binding.navView.setupWithNavController(navController)
        // Menü-Klicks: Links öffnen, alles andere navigiert ohne doppelte Einträge im Zurück-Stapel
        binding.navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.link_github -> openUrl("https://github.com/GARSKE-SYSTEMS/FOSStenbuch")
                R.id.link_website -> openUrl("https://garske-systems.de")
                R.id.link_osm -> openUrl("https://www.openstreetmap.org/copyright")
                else -> NavigationUI.onNavDestinationSelected(item, navController)
            }
            binding.drawerLayout.close()
            true
        }
        // Zurück schließt zuerst das offene Seitenmenü (DrawerLayout tut das nicht von selbst)
        val closeDrawer = onBackPressedDispatcher.addCallback(this, false) { binding.drawerLayout.close() }
        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) { closeDrawer.isEnabled = true }
            override fun onDrawerClosed(drawerView: View) { closeDrawer.isEnabled = false }
        })
    }

    override fun onSupportNavigateUp() = navController.navigateUp(appBarConfig) || super.onSupportNavigateUp()
}
