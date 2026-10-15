package de.fosstenbuch.ui.locations

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.fosstenbuch.R
import de.fosstenbuch.data.SavedLocation
import de.fosstenbuch.data.dao
import de.fosstenbuch.databinding.DialogMasterdataSearchBinding
import de.fosstenbuch.databinding.FragmentLocationEditBinding
import de.fosstenbuch.logic.Geo
import de.fosstenbuch.ui.common.argId
import de.fosstenbuch.ui.common.choose
import de.fosstenbuch.ui.common.close
import de.fosstenbuch.ui.common.doubleOrNull
import de.fosstenbuch.ui.common.launch
import de.fosstenbuch.ui.common.textOrNull
import de.fosstenbuch.ui.common.toast
import java.io.IOException
import java.net.UnknownHostException
import java.util.Locale

/** Koordinate, wenn sie im gültigen Bereich ±[limit] liegt (90 für Breite, 180 für Länge), sonst null */
internal fun coordinate(value: Double?, limit: Double): Double? = value?.takeIf { it >= -limit && it <= limit }

/** Koordinate fürs Eingabefeld: immer mit Punkt und 6 Nachkommastellen (≈ 10 cm) */
private fun Double.toField() = String.format(Locale.US, "%.6f", this)

/**
 * Gespeicherten Ort anlegen/bearbeiten ([argId], 0 = neu). Koordinaten per Eingabe,
 * aktuellem Standort oder Adresssuche (OpenStreetMap).
 *
 * Mit [ARG_NAME] geöffnet (aus dem Fahrtformular), meldet der Bildschirm den gespeicherten Namen
 * per Fragment-Ergebnis "location_saved" zurück, damit das Formular den neuen Ort übernimmt.
 */
class LocationEditFragment : Fragment(R.layout.fragment_location_edit) {

    companion object {
        /** Optionaler Parameter: Name eines neuen Orts vorbelegen (z.B. aus dem Fahrtformular) */
        const val ARG_NAME = "name"
    }

    private var _b: FragmentLocationEditBinding? = null

    /** Formular schon befüllt? Danach stellt Android die Eingaben selbst wieder her. */
    private var loaded = false

    private val locationPermission = registerForActivityResult(RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) locate() else if (result.isNotEmpty()) toast(R.string.location_permission_denied)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loaded = savedInstanceState?.getBoolean("loaded") ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("loaded", loaded)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentLocationEditBinding.bind(view).also { _b = it }
        b.locate.setOnClickListener {
            if (Geo.hasPermission(requireContext())) locate() else locationPermission.launch(Geo.PERMISSIONS)
        }
        b.search.setOnClickListener { search() }
        b.save.setOnClickListener { save(b) }

        if (!loaded) launch {
            val l = requireContext().dao.location(argId)
            if (l == null) {
                b.name.setText(arguments?.getString(ARG_NAME))
            } else {
                b.name.setText(l.name)
                b.address.setText(l.address)
                b.partner.setText(l.businessPartner)
                b.latitude.setText(l.latitude.toField())
                b.longitude.setText(l.longitude.toField())
            }
            loaded = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    /**
     * Trägt den aktuellen Standort ein. Läuft im Lebenszyklus der Ansicht: Verlässt der Nutzer
     * den Bildschirm vor dem GPS-Fix, wird die Abfrage abgebrochen und nichts mehr angewendet.
     */
    private fun locate() {
        val b = _b ?: return
        b.locate.isEnabled = false // Doppel-Tipp startet keine zweite Abfrage
        launch {
            try {
                val location = Geo.currentLocation(requireContext())
                if (location == null) {
                    toast(R.string.location_not_available)
                } else {
                    b.latitude.setText(location.latitude.toField())
                    b.longitude.setText(location.longitude.toField())
                }
            } finally {
                b.locate.isEnabled = true
            }
        }
    }

    /** Adresssuche: erst Suchbegriff (Dialog bleibt bei Fehlern offen), dann Trefferauswahl */
    private fun search() {
        val b = _b ?: return
        val d = DialogMasterdataSearchBinding.inflate(layoutInflater)
        d.query.setText(b.address.textOrNull() ?: b.name.textOrNull())
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.search_address_title)
            .setView(d.root)
            .setPositiveButton(R.string.masterdata_search, null) // Listener unten, damit der Dialog offen bleibt
            .setNegativeButton(R.string.cancel, null)
            .show()
        val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        button.setOnClickListener {
            val query = d.query.textOrNull() ?: return@setOnClickListener
            // Während der Suche gesperrt: keine parallelen Anfragen (Nominatim erlaubt max. 1 pro Sekunde),
            // und eine langsame alte Antwort kann keine neuere überschreiben
            button.isEnabled = false
            d.queryLayout.error = null
            d.queryLayout.helperText = getString(R.string.search_address_searching)
            launch {
                d.queryLayout.error = try {
                    val places = Geo.search(query)
                    when {
                        !dialog.isShowing -> return@launch // inzwischen abgebrochen
                        places.isEmpty() -> getString(R.string.search_address_no_results)
                        else -> {
                            dialog.dismiss()
                            pick(places)
                            null
                        }
                    }
                } catch (e: UnknownHostException) {
                    getString(R.string.search_address_no_internet)
                } catch (e: IOException) {
                    getString(R.string.search_address_error, e.message.orEmpty())
                }
                d.queryLayout.helperText = null
                button.isEnabled = true
            }
        }
    }

    /** Treffer übernehmen: Koordinaten immer, Adresse nur in ein leeres Feld */
    private fun pick(places: List<Geo.Place>) = choose(getString(R.string.masterdata_search_pick), places.map { it.name }) { i ->
        val b = _b ?: return@choose
        b.latitude.setText(places[i].latitude.toField())
        b.longitude.setText(places[i].longitude.toField())
        if (b.address.textOrNull() == null) b.address.setText(places[i].name)
    }

    /** Prüft Name und Koordinaten und speichert */
    private fun save(b: FragmentLocationEditBinding) {
        if (!loaded) return
        val name = b.name.textOrNull()
        // Bereich prüfen: ein Tippfehler wie "481.12" würde sonst gespeichert und nie gefunden
        val lat = coordinate(b.latitude.doubleOrNull(), 90.0)
        val lng = coordinate(b.longitude.doubleOrNull(), 180.0)
        b.nameLayout.error = if (name == null) getString(R.string.error_name_required) else null
        b.latitudeLayout.error = if (lat == null) getString(R.string.masterdata_latitude_invalid) else null
        b.longitudeLayout.error = if (lng == null) getString(R.string.masterdata_longitude_invalid) else null
        if (name == null || lat == null || lng == null) return

        val address = b.address.textOrNull()
        val partner = b.partner.textOrNull()
        b.save.isEnabled = false // Doppel-Tipp würde den Ort sonst zweimal anlegen
        launch {
            val dao = requireContext().dao
            // Der Nutzungszähler bleibt beim Bearbeiten erhalten
            val usage = dao.location(argId)?.usageCount ?: 0
            dao.saveLocation(SavedLocation(argId, name, lat, lng, address, usage, partner))
            if (arguments?.containsKey(ARG_NAME) == true) setFragmentResult("location_saved", bundleOf(ARG_NAME to name))
            close()
        }
    }
}
