package de.fosstenbuch.ui.trips

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import de.fosstenbuch.R
import de.fosstenbuch.data.Trip
import de.fosstenbuch.data.TripPurpose
import de.fosstenbuch.databinding.ItemTripBinding
import de.fosstenbuch.logic.Fmt
import de.fosstenbuch.logic.Trips
import de.fosstenbuch.ui.common.confirm
import de.fosstenbuch.ui.common.hexColor
import de.fosstenbuch.ui.common.launch
import de.fosstenbuch.ui.common.toast

// Gemeinsame Teile der Fahrten-Bildschirme (Liste, automatische Fahrten, Formular).

/** Eine Zeile der Fahrtenliste: die Fahrt mit ihrer Kategorie (für Badge und Filter) */
data class TripRow(val trip: Trip, val purpose: TripPurpose?)

/** Filter-Chips der Fahrtenliste (Reihenfolge = Reihenfolge der Chips) */
enum class TripFilter { ALL, BUSINESS, PRIVATE }

/**
 * Filtert und sortiert die Fahrtenliste. Geschäftlich/privat richtet sich nach der Kategorie;
 * Fahrten ohne Kategorie (z.B. laufende) erscheinen nur unter "Alle".
 * [sort] ist der Index in R.array.sort_options: Datum ↓, Datum ↑, Distanz ↓, Distanz ↑.
 */
internal fun tripRows(trips: List<Trip>, purposes: List<TripPurpose>, filter: TripFilter, sort: Int): List<TripRow> {
    val byId = purposes.associateBy { it.id }
    val rows = trips.map { TripRow(it, it.purposeId?.let(byId::get)) }.filter {
        when (filter) {
            TripFilter.ALL -> true
            TripFilter.BUSINESS -> it.purpose?.isBusinessRelevant == true
            TripFilter.PRIVATE -> it.purpose?.isBusinessRelevant == false
        }
    }
    return when (sort) {
        1 -> rows.sortedBy { it.trip.date }
        2 -> rows.sortedByDescending { it.trip.distanceKm }
        3 -> rows.sortedBy { it.trip.distanceKm }
        else -> rows.sortedByDescending { it.trip.date }
    }
}

/** Befüllt eine Listenzeile (item_trip.xml); [purpose] = Kategorie der Fahrt oder null */
fun bindTrip(b: ItemTripBinding, trip: Trip, purpose: TripPurpose?) {
    val ctx = b.root.context
    b.textDate.text = Fmt.dateTime(trip.date) +
        trip.endTime?.let { " " + ctx.getString(R.string.ghost_duration_format, Fmt.time(it)) }.orEmpty()
    b.textRoute.text = ctx.getString(R.string.trip_route_format, trip.startLocation, if (trip.isActive) "…" else trip.endLocation)
    // Automatische Fahrten haben noch keine Km-Stände – dort zählt nur die GPS-Strecke
    val gps = trip.gpsDistanceKm ?: 0.0
    b.textDistance.text = when {
        !trip.isGhost -> Fmt.km(trip.distanceKm)
        gps > 0 -> ctx.getString(R.string.trips_gps, Fmt.km(gps))
        else -> ctx.getString(R.string.ghost_no_distance)
    }
    // Alter Freitext-Zweck nur zeigen, wenn er nicht ohnehin der Kategoriename ist
    b.textDetails.text = listOfNotNull(trip.purpose.takeIf { it.isNotBlank() && it != purpose?.name }, trip.businessPartner)
        .joinToString(" · ")
    b.textActive.isVisible = trip.isActive

    // Kategorie in ihrer Farbe; die Schrift wird je nach Helligkeit schwarz oder weiß, damit sie lesbar bleibt
    b.textCategory.isVisible = !trip.isGhost
    val color = hexColor(purpose?.color) ?: Color.GRAY
    b.textCategory.text = purpose?.name ?: ctx.getString(R.string.no_category)
    b.textCategory.backgroundTintList = ColorStateList.valueOf(color)
    b.textCategory.setTextColor(if (ColorUtils.calculateLuminance(color) > 0.5) Color.BLACK else Color.WHITE)

    // Stornierte Fahrten bleiben sichtbar, aber durchgestrichen und blass.
    // Flags immer auch zurücksetzen: Die Zeilen-Views werden für andere Fahrten wiederverwendet.
    b.textCancelled.isVisible = trip.isCancelled
    b.textCancelled.text = trip.cancellationReason?.takeIf { it.isNotBlank() }
        ?.let { ctx.getString(R.string.cancel_trip_reason_format, it) } ?: ctx.getString(R.string.trip_cancelled)
    for (v in listOf(b.textRoute, b.textDistance, b.textDetails)) {
        v.paintFlags = if (trip.isCancelled) v.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        else v.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        v.alpha = if (trip.isCancelled) 0.5f else 1f
    }
}

/**
 * Verwirft eine automatische oder gerade erst gestartete Fahrt nach Rückfrage (abgeschlossene Fahrten werden
 * nur storniert). Regelverstöße (z.B. Fahrt läuft schon länger) erscheinen als Meldung; [onDone] läuft nach Erfolg.
 */
fun Fragment.confirmDelete(trip: Trip, onDone: () -> Unit = {}) {
    val message = if (trip.isGhost) R.string.ghost_discard_message else R.string.trips_discard_active_message
    confirm(getString(R.string.ghost_discard_title), getString(message), getString(R.string.ghost_discard)) {
        launch {
            try {
                Trips.delete(requireContext(), trip)
                onDone()
            } catch (e: Trips.Rejected) {
                toast(e.message.orEmpty())
            }
        }
    }
}
