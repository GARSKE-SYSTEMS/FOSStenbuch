package de.fosstenbuch.ui.stats

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import de.fosstenbuch.R
import de.fosstenbuch.data.dao
import de.fosstenbuch.databinding.FragmentStatsBinding
import de.fosstenbuch.databinding.StatsCardBinding
import de.fosstenbuch.logic.Mileage
import de.fosstenbuch.logic.Fmt
import de.fosstenbuch.ui.common.collect
import de.fosstenbuch.ui.common.pickDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.util.Calendar
import java.util.Date

/**
 * Statistik: Kilometer und Fahrten eines Jahres, eines Monats oder eines frei gewählten Zeitraums.
 * Gezählt werden nur abgeschlossene, nicht stornierte Fahrten (siehe [Mileage.totals]).
 */
class StatsFragment : Fragment(R.layout.fragment_stats) {

    /**
     * Gewählter Zeitraum. [mode] ist die ID des gewählten Chips – das spart eine eigene
     * Aufzählung samt Umrechnung. [from]/[to] sind Tagesanfänge des freien Zeitraums.
     */
    private data class Filter(val mode: Int, val year: Int, val month: Int, val from: Long, val to: Long) {
        val range: LongRange
            get() = when (mode) {
                R.id.chip_month -> Mileage.month(year, month)
                R.id.chip_custom -> Mileage.days(from, to)
                else -> Mileage.year(year)
            }

        /** Monat vor/zurück, über die Jahresgrenze hinweg (Januar − 1 = Dezember des Vorjahres) */
        fun plusMonths(n: Int) = (year * 12 + month + n).let { copy(year = Math.floorDiv(it, 12), month = Math.floorMod(it, 12)) }
    }

    // Einziger Zustand des Bildschirms. Jede Änderung läuft durch denselben combine-Flow in
    // onViewCreated – es gibt also nie mehrere Abfragen, die sich gegenseitig überschreiben.
    // Standard: aktuelles Jahr; freier Zeitraum vom 1.1. bis einschließlich heute.
    private val filter = MutableStateFlow(
        Calendar.getInstance().let {
            val year = it.get(Calendar.YEAR)
            Filter(R.id.chip_year, year, it.get(Calendar.MONTH), Mileage.year(year).first, Mileage.startOfDay(it.timeInMillis))
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getLongArray(KEY_FILTER)?.let { (mode, year, month, from, to) ->
            filter.value = Filter(mode.toInt(), year.toInt(), month.toInt(), from, to)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        filter.value.run { outState.putLongArray(KEY_FILTER, longArrayOf(mode.toLong(), year.toLong(), month.toLong(), from, to)) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentStatsBinding.bind(view)
        b.chips.setOnCheckedStateChangeListener { _, ids -> ids.firstOrNull()?.let { id -> filter.update { it.copy(mode = id) } } }
        b.year.prev.setOnClickListener { filter.update { it.copy(year = it.year - 1) } }
        b.year.next.setOnClickListener { filter.update { it.copy(year = it.year + 1) } }
        b.month.prev.contentDescription = getString(R.string.stats_previous_month)
        b.month.next.contentDescription = getString(R.string.stats_next_month)
        b.month.prev.setOnClickListener { filter.update { it.plusMonths(-1) } }
        b.month.next.setOnClickListener { filter.update { it.plusMonths(1) } }
        // Von nach Bis (bzw. Bis vor Von) zieht das andere Datum mit – der Zeitraum ist nie verdreht
        b.dateFrom.setOnClickListener {
            pickDate(Date(filter.value.from)) { d -> filter.update { it.copy(from = d.time, to = maxOf(it.to, d.time)) } }
        }
        b.dateTo.setOnClickListener {
            pickDate(Date(filter.value.to)) { d -> filter.update { it.copy(to = d.time, from = minOf(it.from, d.time)) } }
        }

        b.cardTotal.setup(R.string.stats_total_distance)
        b.cardBusiness.setup(R.string.stats_business_distance, R.color.business)
        b.cardPrivate.setup(R.string.stats_private_distance, R.color.private_trip)
        b.cardShare.setup(R.string.stats_business_percentage, R.color.business)
        b.cardCount.setup(R.string.stats_total_trips)
        b.cardAverage.setup(R.string.stats_average_distance)

        // Summiert wird in Kotlin über alle Fahrten; ein Filterwechsel rechnet nur neu, ohne neue DB-Abfrage
        val dao = requireContext().dao
        collect(combine(filter, dao.trips(), dao.purposes()) { f, trips, purposes -> f to Mileage.totals(trips, purposes, f.range) }) { (f, t) ->
            b.chips.check(f.mode)
            b.year.root.isVisible = f.mode != R.id.chip_custom
            b.month.root.isVisible = f.mode == R.id.chip_month
            b.range.isVisible = f.mode == R.id.chip_custom
            b.year.value.text = f.year.toString()
            b.month.value.text = resources.getStringArray(R.array.month_names)[f.month]
            b.dateFrom.setText(Fmt.date(Date(f.from)))
            b.dateTo.setText(Fmt.date(Date(f.to)))

            b.empty.isVisible = t.trips == 0
            b.cards.isVisible = t.trips > 0
            b.cardTotal.value.text = Fmt.km(t.km)
            b.cardBusiness.value.text = Fmt.km(t.businessKm)
            b.cardPrivate.value.text = Fmt.km(t.privateKm)
            b.cardShare.root.isVisible = t.businessPercent != null
            b.cardShare.value.text = getString(R.string.stats_percent_format, t.businessPercent ?: 0.0)
            b.cardCount.value.text = t.trips.toString()
            b.cardAverage.value.text = Fmt.km(t.averageKm)
        }
    }

    /** Feste Beschriftung und optionaler Farbbalken in der Farbe der Kategorie-Art (Beruflich/Privat) */
    private fun StatsCardBinding.setup(label: Int, @ColorRes accentColor: Int? = null) {
        this.label.setText(label)
        accent.isVisible = accentColor != null
        // Tönen statt GradientDrawable.setColor(): setColor() ändert den mit allen Nutzern von
        // rounded_accent geteilten Drawable-Zustand, dann hätten alle Balken dieselbe Farbe.
        if (accentColor != null) accent.backgroundTintList = ContextCompat.getColorStateList(root.context, accentColor)
    }

    private companion object {
        const val KEY_FILTER = "filter"
    }
}
