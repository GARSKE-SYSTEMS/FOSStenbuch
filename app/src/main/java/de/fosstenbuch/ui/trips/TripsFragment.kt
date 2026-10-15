package de.fosstenbuch.ui.trips

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import de.fosstenbuch.R
import de.fosstenbuch.data.Trip
import de.fosstenbuch.data.dao
import de.fosstenbuch.databinding.FragmentTripsBinding
import de.fosstenbuch.databinding.ItemTripBinding
import de.fosstenbuch.logic.Fmt
import de.fosstenbuch.logic.Trips
import de.fosstenbuch.tracking.TrackingService
import de.fosstenbuch.ui.common.ARG_ID
import de.fosstenbuch.ui.common.SimpleAdapter
import de.fosstenbuch.ui.common.askText
import de.fosstenbuch.ui.common.choose
import de.fosstenbuch.ui.common.collect
import de.fosstenbuch.ui.common.launch
import de.fosstenbuch.ui.common.navigate
import de.fosstenbuch.ui.common.setDropdown
import de.fosstenbuch.ui.common.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

/**
 * Startbildschirm: alle echten Fahrten mit Filter und Sortierung. Darüber Karten für die
 * laufende Fahrt, eine laufende automatische Aufzeichnung und noch zu prüfende automatische Fahrten.
 * Langes Drücken auf eine Fahrt bietet Stornieren und Löschen (laufende: Verwerfen) an.
 */
class TripsFragment : Fragment(R.layout.fragment_trips) {

    // Als Flows, damit die Liste bei jeder Änderung über EINEN kombinierten Datenstrom neu berechnet wird.
    // Fragment-Felder überleben die Rückkehr aus dem Formular; für Rotation siehe onSaveInstanceState.
    private val filter = MutableStateFlow(TripFilter.ALL)
    private val sort = MutableStateFlow(0)

    private val chipIds = listOf(R.id.chip_all, R.id.chip_business, R.id.chip_private)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            filter.value = TripFilter.entries[it.getInt("filter")]
            sort.value = it.getInt("sort")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("filter", filter.value.ordinal)
        outState.putInt("sort", sort.value)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentTripsBinding.bind(view)
        val dao = requireContext().dao

        val adapter = SimpleAdapter(ItemTripBinding::inflate, { row: TripRow -> row.trip.id }) { item, row ->
            bindTrip(item, row.trip, row.purpose)
            item.root.setOnClickListener { navigate(R.id.navigation_trip_edit, ARG_ID to row.trip.id) }
            item.root.setOnLongClickListener { showActions(row.trip); true }
        }
        b.recycler.adapter = adapter

        b.chips.check(chipIds[filter.value.ordinal])
        b.chips.setOnCheckedStateChangeListener { _, ids ->
            filter.value = TripFilter.entries[chipIds.indexOf(ids.firstOrNull() ?: R.id.chip_all)]
        }
        b.inputSort.setDropdown(resources.getStringArray(R.array.sort_options).toList(), sort.value) { sort.value = it }

        val list = combine(dao.trips(), dao.purposes(), filter, sort) { trips, purposes, f, s ->
            trips.isEmpty() to tripRows(trips, purposes, f, s)
        }
        collect(list) { (noTripsAtAll, rows) ->
            adapter.submitList(rows)
            // Leer wegen Filter oder wirklich keine Fahrten? Nur im zweiten Fall auf "+" hinweisen.
            b.layoutEmpty.isVisible = rows.isEmpty()
            b.textEmpty.setText(if (noTripsAtAll) R.string.no_trips else R.string.trips_no_match)
            b.textEmptyHint.isVisible = noTripsAtAll
        }

        // Laufende Fahrt mit GPS-Strecke; solange sie läuft, kann keine zweite gestartet werden
        collect(combine(dao.activeTripFlow(), TrackingService.live) { trip, live -> trip to live }) { (trip, live) ->
            b.cardActive.isVisible = trip != null
            if (trip == null) b.fab.show() else b.fab.hide()
            if (trip == null) return@collect
            // GPS-Aufzeichnung fehlt (z.B. nach Prozess-Tod oder später erteilter Berechtigung): nachstarten.
            // Die Liste ist sichtbar, Android erlaubt den Vordergrunddienst also; ohne Standortberechtigung
            // startet startManual nichts, und eine schon laufende Aufzeichnung derselben Fahrt bleibt unberührt.
            if (live?.tripId != trip.id) TrackingService.startManual(requireContext(), trip.id)
            val km = live?.takeIf { it.tripId == trip.id }?.km
            b.textActiveInfo.text = getString(R.string.active_trip_banner_text, trip.startLocation, Fmt.dateTime(trip.date))
            b.textActiveGps.isVisible = km != null
            if (km != null) b.textActiveGps.text = getString(R.string.trips_gps_live, Fmt.km(km))
            b.buttonEndTrip.setOnClickListener { navigate(R.id.navigation_trip_edit, ARG_ID to trip.id) }
        }

        // Laufende automatische Aufzeichnung. Der Takt alle 30 s lässt die Minuten weiterzählen,
        // auch wenn sich Strecke und Datenbank gerade nicht ändern (z.B. im Stau).
        val tick = flow { while (true) { emit(Unit); delay(30_000) } }
        collect(combine(dao.recordingGhostFlow(), TrackingService.live, tick) { ghost, live, _ -> ghost to live }) { (ghost, live) ->
            b.cardRecording.isVisible = ghost != null
            if (ghost == null) return@collect
            val vehicle = ghost.vehicleId?.let { dao.vehicle(it) }
            b.textRecordingDevice.text = getString(
                R.string.ghost_recording_banner_device, vehicle?.bluetoothDeviceName ?: vehicle?.label.orEmpty()
            )
            val km = live?.takeIf { it.tripId == ghost.id }?.km ?: ghost.gpsDistanceKm ?: 0.0
            val minutes = ((System.currentTimeMillis() - ghost.date.time) / 60_000).coerceAtLeast(0)
            b.textRecordingDistance.text = getString(R.string.trips_recording_progress, Fmt.km(km), minutes)
            // Das Formular bietet "Aufzeichnung beenden" an, z.B. wenn die Bluetooth-Trennung verpasst wurde
            b.cardRecording.setOnClickListener { navigate(R.id.navigation_trip_edit, ARG_ID to ghost.id) }
        }

        // Abgeschlossene automatische Fahrten, die noch übernommen oder verworfen werden müssen
        collect(dao.ghostTrips()) { ghosts ->
            b.cardGhosts.isVisible = ghosts.isNotEmpty()
            b.textGhosts.text = resources.getQuantityString(R.plurals.ghost_trips_banner_text, ghosts.size, ghosts.size)
        }
        b.buttonShowGhosts.setOnClickListener { navigate(R.id.navigation_ghost_trips) }

        b.fab.setOnClickListener { navigate(R.id.navigation_trip_edit, ARG_ID to 0L) }
    }

    /**
     * Aktionen zu einer Fahrt: Stornieren (nicht bei laufenden/stornierten), Löschen (nicht bei
     * änderungssicheren). Eine laufende Fahrt (z.B. versehentlich gestartet) lässt sich immer verwerfen –
     * Trips.delete erlaubt das; Menü und Rückfrage heißen dann gleich "Verwerfen".
     */
    private fun showActions(trip: Trip) = launch {
        val ctx = requireContext()
        val locked = !trip.isActive && trip.vehicleId?.let { ctx.dao.vehicle(it) }?.auditProtected == true
        val actions = buildList<Pair<Int, () -> Unit>> {
            if (!trip.isCancelled && !trip.isActive) add(R.string.cancel_trip_action to { cancel(trip) })
            if (!locked) add((if (trip.isActive) R.string.ghost_discard else R.string.delete) to { confirmDelete(trip) })
        }
        if (actions.isEmpty()) return@launch
        val title = "${getString(R.string.trip_route_format, trip.startLocation, trip.endLocation)} (${Fmt.date(trip.date)})"
        choose(title, actions.map { getString(it.first) }) { actions[it].second() }
    }

    /** Storniert nach Eingabe eines optionalen Grunds (auch bei änderungssicheren Fahrzeugen erlaubt) */
    private fun cancel(trip: Trip) = askText(
        getString(R.string.cancel_trip_title), getString(R.string.cancel_trip_message), getString(R.string.cancel_trip_action)
    ) { reason ->
        launch {
            try {
                Trips.cancel(requireContext(), trip, reason)
            } catch (e: Trips.Rejected) {
                toast(e.message.orEmpty())
            }
        }
    }
}
