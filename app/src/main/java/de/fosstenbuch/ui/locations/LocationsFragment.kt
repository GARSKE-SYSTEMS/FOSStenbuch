package de.fosstenbuch.ui.locations

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import de.fosstenbuch.R
import de.fosstenbuch.data.SavedLocation
import de.fosstenbuch.data.dao
import de.fosstenbuch.databinding.FragmentMasterdataListBinding
import de.fosstenbuch.databinding.ItemSavedLocationBinding
import de.fosstenbuch.ui.common.ARG_ID
import de.fosstenbuch.ui.common.SimpleAdapter
import de.fosstenbuch.ui.common.collect
import de.fosstenbuch.ui.common.confirm
import de.fosstenbuch.ui.common.launch
import de.fosstenbuch.ui.common.navigate
import java.util.Locale
import kotlin.math.abs

/** Koordinaten für die Anzeige, z.B. "52,5200°N, 13,4050°E" */
internal fun formatCoordinates(lat: Double, lng: Double): String =
    String.format(Locale.GERMANY, "%.4f°%s, %.4f°%s", abs(lat), if (lat >= 0) "N" else "S", abs(lng), if (lng >= 0) "E" else "W")

/**
 * Gespeicherte Orte, meistgenutzte zuerst. Tippen öffnet das Formular, langes Drücken löscht.
 * Löschen ändert keine Fahrten – die speichern Ortsnamen als Text.
 */
class LocationsFragment : Fragment(R.layout.fragment_masterdata_list) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentMasterdataListBinding.bind(view)
        val adapter = SimpleAdapter<SavedLocation, ItemSavedLocationBinding>(ItemSavedLocationBinding::inflate, { it.id }) { item, l ->
            item.name.text = l.name
            item.usage.text = getString(R.string.usage_count_format, l.usageCount)
            item.address.text = l.address
            item.address.isVisible = l.address != null
            item.coordinates.text = formatCoordinates(l.latitude, l.longitude)
            item.root.setOnClickListener { navigate(R.id.navigation_location_edit, ARG_ID to l.id) }
            item.root.setOnLongClickListener {
                confirm(getString(R.string.delete_location_title), getString(R.string.delete_location_message), getString(R.string.delete)) {
                    launch { requireContext().dao.deleteLocation(l) }
                }
                true
            }
        }
        b.list.adapter = adapter
        b.empty.text = getString(R.string.masterdata_two_lines, getString(R.string.no_saved_locations), getString(R.string.add_first_location))
        b.fab.contentDescription = getString(R.string.add_location)
        b.fab.setOnClickListener { navigate(R.id.navigation_location_edit, ARG_ID to 0L) }
        collect(requireContext().dao.locations()) {
            adapter.submitList(it)
            b.empty.isVisible = it.isEmpty()
        }
    }
}
