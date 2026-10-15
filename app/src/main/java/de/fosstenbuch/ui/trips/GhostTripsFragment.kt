package de.fosstenbuch.ui.trips

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import de.fosstenbuch.R
import de.fosstenbuch.data.Trip
import de.fosstenbuch.data.dao
import de.fosstenbuch.databinding.FragmentGhostTripsBinding
import de.fosstenbuch.databinding.ItemTripBinding
import de.fosstenbuch.ui.common.ARG_ID
import de.fosstenbuch.ui.common.SimpleAdapter
import de.fosstenbuch.ui.common.collect
import de.fosstenbuch.ui.common.navigate
import kotlinx.coroutines.flow.map

/**
 * Automatisch erkannte Fahrten, die noch geprüft werden müssen. Tippen öffnet das Fahrtformular
 * zum Übernehmen, langes Drücken verwirft die Fahrt (nach Rückfrage).
 */
class GhostTripsFragment : Fragment(R.layout.fragment_ghost_trips) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentGhostTripsBinding.bind(view)
        val adapter = SimpleAdapter(ItemTripBinding::inflate, { trip: Trip -> trip.id }) { item, trip ->
            bindTrip(item, trip, null)
            item.root.setOnClickListener { navigate(R.id.navigation_trip_edit, ARG_ID to trip.id) }
            item.root.setOnLongClickListener { confirmDelete(trip); true }
        }
        b.recycler.adapter = adapter
        // Noch laufende Aufzeichnungen (ohne Ende) zeigt die Fahrtenliste als eigene Karte
        collect(requireContext().dao.ghostTrips().map { all -> all.filter { it.endTime != null } }) { trips ->
            adapter.submitList(trips)
            b.layoutEmpty.isVisible = trips.isEmpty()
        }
    }
}
