package de.fosstenbuch.ui.stats

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import de.fosstenbuch.R
import de.fosstenbuch.data.dao
import de.fosstenbuch.databinding.FragmentMileageBinding
import de.fosstenbuch.logic.Mileage
import de.fosstenbuch.logic.Fmt
import de.fosstenbuch.ui.common.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.util.Calendar

/**
 * Rechner für die Entfernungspauschale eines Jahres: aus einfacher Entfernung und Arbeitstagen
 * oder geschätzt aus den geschäftlichen km der Statistik (Formeln und Rechtsstand in [Mileage]).
 *
 * Das Ergebnis wird bei jeder Eingabe neu berechnet. Nach Drehen oder Theme-Wechsel stellt Android
 * die Eingabefelder selbst wieder her, deren Listener feuern – und das Ergebnis entsteht neu.
 */
class MileageFragment : Fragment(R.layout.fragment_mileage) {

    /** Alle Eingaben; [oneWay] und [days] spiegeln die Textfelder, Jahr und Modus sichert onSaveInstanceState */
    private data class Input(val year: Int, val fromStats: Boolean = false, val oneWay: String = "", val days: String = "")

    // Ein Zustand, ein combine-Flow: Ein Jahreswechsel ersetzt die Auswertung, statt eine zweite daneben zu starten
    private val input = MutableStateFlow(Input(Calendar.getInstance().get(Calendar.YEAR)))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { input.value = Input(it.getInt(KEY_YEAR), it.getBoolean(KEY_FROM_STATS)) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_YEAR, input.value.year)
        outState.putBoolean(KEY_FROM_STATS, input.value.fromStats)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentMileageBinding.bind(view)
        b.year.prev.setOnClickListener { input.update { it.copy(year = it.year - 1) } }
        b.year.next.setOnClickListener { input.update { it.copy(year = it.year + 1) } }
        b.useStats.setOnClickListener { input.update { it.copy(fromStats = !it.fromStats) } }
        val syncTexts = { input.update { it.copy(oneWay = b.oneWay.text.toString(), days = b.days.text.toString()) } }
        b.oneWay.doAfterTextChanged { syncTexts() }
        b.days.doAfterTextChanged { syncTexts() }
        syncTexts() // Startwerte aus dem Layout (z.B. 230 Arbeitstage)

        val dao = requireContext().dao
        collect(combine(input, dao.trips(), dao.purposes()) { i, trips, purposes -> i to Mileage.totals(trips, purposes, Mileage.year(i.year)) }) { (i, t) ->
            b.year.value.text = i.year.toString()
            b.statsKm.text = Fmt.km(t.businessKm)
            b.statsTrips.text = getString(R.string.trip_count_format, t.businessTrips)
            b.useStats.setText(if (i.fromStats) R.string.mileage_one_way_distance else R.string.mileage_use_stats)
            b.layoutOneWay.isVisible = !i.fromStats
            val (rateFirst, rateBeyond) = Mileage.commuteRates(i.year)
            b.rateInfo.text = if (rateFirst == rateBeyond) getString(R.string.stats_mileage_rate_flat, i.year, Fmt.euro(rateFirst))
            else getString(R.string.stats_mileage_rates, i.year, Fmt.euro(rateFirst), Fmt.euro(rateBeyond))

            // Ungültige Eingaben werden gemeldet statt still durch Ersatzwerte (früher 0 km bzw. 230 Tage) ersetzt
            val days = i.days.toIntOrNull()?.takeIf { it in 1..366 }
            b.layoutDays.error = if (days == null) getString(R.string.stats_mileage_error_days) else null
            val oneWay = i.oneWay.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
            // Ein leeres Entfernungsfeld ist noch kein Fehler – es gibt nur noch kein Ergebnis
            b.layoutOneWay.error = if (!i.fromStats && i.oneWay.isNotEmpty() && oneWay == null) getString(R.string.stats_mileage_error_km) else null

            val a = when {
                days == null -> null
                i.fromStats -> Mileage.commuteAllowanceFromYearKm(t.businessKm, days, i.year)
                else -> oneWay?.let { Mileage.commuteAllowance(it, days, i.year) }
            }
            b.result.isVisible = a != null
            if (a != null) {
                val noData = i.fromStats && t.businessKm <= 0
                b.total.text = Fmt.euro(a.total)
                b.first20.text = getString(R.string.stats_mileage_part, getString(R.string.mileage_rate_standard), Fmt.euro(a.first20))
                b.beyond20.text = getString(R.string.stats_mileage_part, getString(R.string.mileage_rate_extended), Fmt.euro(a.beyond20))
                b.details.text = if (noData) getString(R.string.mileage_no_data) else getString(R.string.stats_mileage_details, a.km, a.days)
                // Im Statistikmodus ist offen, ob die km Arbeitswege oder Dienstreisen sind – beides zeigen
                // (den Dienstreise-Betrag nur mit Bedingung im Text: eigener Pkw, abzüglich Erstattung)
                b.businessTrips.isVisible = i.fromStats && !noData
                b.businessTrips.text = getString(
                    R.string.stats_mileage_business_trips,
                    Fmt.km(t.businessKm), Fmt.euro(Mileage.BUSINESS_TRIP_RATE), Fmt.euro(Mileage.businessTripAllowance(t.businessKm))
                )
            }
        }
    }

    private companion object {
        const val KEY_YEAR = "year"
        const val KEY_FROM_STATS = "fromStats"
    }
}
