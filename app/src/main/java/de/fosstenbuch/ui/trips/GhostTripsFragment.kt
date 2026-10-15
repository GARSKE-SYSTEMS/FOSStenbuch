package de.fosstenbuch.ui.trips

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import de.fosstenbuch.R
import de.fosstenbuch.data.Trip
import de.fosstenbuch.data.dao
import de.fosstenbuch.databinding.FragmentMasterdataListBinding
import de.fosstenbuch.databinding.ItemTripBinding
import de.fosstenbuch.ui.common.ARG_ID
import de.fosstenbuch.ui.common.SimpleAdapter
import de.fosstenbuch.ui.common.collect
import de.fosstenbuch.ui.common.navigate

/**
 * Automatisch erkannte Fahrten, die noch geprüft werden müssen. Tippen öffnet das Fahrtformular
 * zum Übernehmen, langes Drücken verwirft die Fahrt (nach Rückfrage).
 * Eine noch laufende Aufzeichnung zeigt die Fahrtenliste als eigene Karte.
 */
class GhostTripsFragment : Fragment(R.layout.fragment_masterdata_list) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentMasterdataListBinding.bind(view)
        b.fab.isVisible = false // Automatische Fahrten legt nur die Bluetooth-Erkennung an
        b.empty.text = getString(R.string.masterdata_two_lines, getString(R.string.ghost_trips_empty), getString(R.string.ghost_trips_empty_hint))
        val adapter = SimpleAdapter(ItemTripBinding::inflate, { trip: Trip -> trip.id }) { item, trip ->
            bindTrip(item, trip, null)
            item.root.setOnClickListener { navigate(R.id.navigation_trip_edit, ARG_ID to trip.id) }
            item.root.setOnLongClickListener { confirmDelete(trip); true }
        }
        b.list.adapter = adapter
        collect(requireContext().dao.ghostTrips()) { trips ->
            adapter.submitList(trips)
            b.empty.isVisible = trips.isEmpty()
        }
    }
}
