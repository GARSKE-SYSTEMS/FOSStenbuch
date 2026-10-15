package de.fosstenbuch.logic

import de.fosstenbuch.data.Trip
import de.fosstenbuch.data.TripPurpose
import java.util.Calendar
import kotlin.math.floor

/**
 * Auswertungen für Statistik und Kilometerpauschale – reine Funktionen, ohne Datenbank und UI.
 *
 * Zeiträume sind [LongRange]s aus Epoch-Millisekunden in **lokaler Zeit** (Gerätezeitzone, so wie
 * die Fahrten angezeigt werden). Sie werden mit `until` gebildet, enden also direkt vor dem Beginn
 * des nächsten Zeitraums – so fällt keine Fahrt in eine Lücke wie früher bei "23:59:59.999".
 */
object Mileage {

    // ── Zeiträume ────────────────────────────────────────────────────────────

    /** Kalenderjahr [year] */
    fun year(year: Int): LongRange = period(year, Calendar.JANUARY, Calendar.YEAR)

    /** Monat [month] (0 = Januar, wie bei [Calendar]) des Jahres [year] */
    fun month(year: Int, month: Int): LongRange = period(year, month, Calendar.MONTH)

    /** Vom Beginn des Tages [from] bis einschließlich des ganzen Tages [to] */
    fun days(from: Long, to: Long): LongRange = startOfDay(from) until startOfDay(to, plusDays = 1)

    /** 00:00 Uhr des Tages von [millis], um [plusDays] Tage verschoben (Kalendertage, also auch bei Zeitumstellung richtig) */
    fun startOfDay(millis: Long, plusDays: Int = 0): Long = Calendar.getInstance().run {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, plusDays)
        timeInMillis
    }

    private fun period(year: Int, month: Int, length: Int): LongRange {
        val cal = Calendar.getInstance().apply { clear(); set(year, month, 1) }
        val start = cal.timeInMillis
        cal.add(length, 1)
        return start until cal.timeInMillis
    }

    // ── Statistik ────────────────────────────────────────────────────────────

    /** Kennzahlen eines Zeitraums. Fahrten ohne Kategorie zählen nur in [trips] und [km] mit. */
    data class Totals(val trips: Int, val km: Double, val businessKm: Double, val privateKm: Double, val businessTrips: Int) {
        val averageKm: Double get() = if (trips > 0) km / trips else 0.0

        /** Geschäftsanteil in Prozent an den km mit Kategorie; null, wenn es keine solchen km gibt */
        val businessPercent: Double? get() = (businessKm + privateKm).takeIf { it > 0 }?.let { businessKm / it * 100 }
    }

    /**
     * Summiert alle echten, abgeschlossenen, nicht stornierten Fahrten in [range]. Geschäftlich bzw.
     * privat entscheidet die *aktuelle* Einstellung der Kategorie ([TripPurpose.isBusinessRelevant]).
     */
    fun totals(trips: List<Trip>, purposes: List<TripPurpose>, range: LongRange): Totals {
        val isBusiness = purposes.associate { it.id to it.isBusinessRelevant }
        val counted = trips.filter { !it.isGhost && !it.isActive && !it.isCancelled && it.date.time in range }
        // true = geschäftlich, false = privat, null = ohne (gelöschte) Kategorie
        val groups = counted.groupBy { t -> t.purposeId?.let { isBusiness[it] } }
        val business = groups[true].orEmpty()
        return Totals(
            trips = counted.size,
            km = counted.sumOf { it.distanceKm },
            businessKm = business.sumOf { it.distanceKm },
            privateKm = groups[false].orEmpty().sumOf { it.distanceKm },
            businessTrips = business.size
        )
    }

    // ── Kilometerpauschale ───────────────────────────────────────────────────
    //
    // Rechtsstand (Tabelle zuletzt geprüft 2026, neue Jahre in commuteRates ergänzen):
    //  - Entfernungspauschale ("Pendlerpauschale") für Wege zwischen Wohnung und erster
    //    Tätigkeitsstätte, § 9 Abs. 1 Satz 3 Nr. 4 EStG: je Arbeitstag und je *vollem* km der
    //    *einfachen* Entfernung. Der Höchstbetrag von 4.500 € gilt nicht bei eigenem oder
    //    überlassenem Pkw – also nicht für ein Fahrtenbuch – und wird deshalb nicht angewandt.
    //  - Dienstreisen (Auswärtstätigkeit) mit dem Pkw: 0,30 € je *gefahrenem* km, Hin- und
    //    Rückweg zählen (§ 9 Abs. 1 Satz 3 Nr. 4a EStG i. V. m. § 5 Abs. 1 BRKG). Annahmen:
    //    *eigener* Pkw (Motorrad/Motorroller: 0,20 €/km, nicht abgebildet) und *keine* Erstattung
    //    durch den Arbeitgeber – eine steuerfreie Erstattung mindert den absetzbaren Betrag. Beim
    //    Firmenwagen (dem typischen Fahrtenbuch-Fall) trägt der Arbeitgeber die Kosten, dann ist
    //    für Dienstreisen gar nichts absetzbar. Die App kennt weder Halter noch Erstattung und
    //    zeigt den Betrag deshalb nur als bedingten Vergleich.

    /** Reisekostenpauschale für Dienstreisen mit dem *eigenen* Pkw in €/gefahrenem km (nicht Motorrad, nicht Firmenwagen) */
    const val BUSINESS_TRIP_RATE = 0.30

    /**
     * Sätze der Entfernungspauschale in € je km: (erste 20 km, ab dem 21. km).
     *  - ab 2026: 0,38 € ab dem ersten km (Steueränderungsgesetz 2025)
     *  - 2022–2025: 0,30 € / 0,38 € (Steuerentlastungsgesetz 2022)
     *  - 2021: 0,30 € / 0,35 €
     *  - davor: 0,30 € ab dem ersten km (so seit 2004; ältere Rechtsstände sind nicht abgebildet)
     */
    fun commuteRates(year: Int): Pair<Double, Double> = when {
        year >= 2026 -> 0.38 to 0.38
        year >= 2022 -> 0.30 to 0.38
        year == 2021 -> 0.30 to 0.35
        else -> 0.30 to 0.30
    }

    /** Ergebnis der Entfernungspauschale; [km] = angesetzte volle km der einfachen Entfernung */
    data class Allowance(val km: Int, val days: Int, val first20: Double, val beyond20: Double) {
        val total: Double get() = first20 + beyond20
    }

    /** Entfernungspauschale für [oneWayKm] einfache Entfernung an [days] Arbeitstagen im Jahr [year] */
    fun commuteAllowance(oneWayKm: Double, days: Int, year: Int): Allowance {
        // Nur volle Kilometer zählen (15,7 km → 15 km)
        val km = floor(oneWayKm).toInt().coerceAtLeast(0)
        val (rateFirst, rateBeyond) = commuteRates(year)
        return Allowance(km, days, minOf(km, 20) * rateFirst * days, maxOf(km - 20, 0) * rateBeyond * days)
    }

    /**
     * Statistikmodus (Verhalten der alten App): Die geschäftlichen km eines Jahres werden als
     * Hin- und Rückweg zur Arbeit an [days] Arbeitstagen gedeutet, daraus folgt die mittlere
     * einfache Entfernung. Das ist eine Annahme – sind es Dienstreisen, gilt [businessTripAllowance]
     * (nur mit eigenem Pkw).
     */
    fun commuteAllowanceFromYearKm(yearKm: Double, days: Int, year: Int): Allowance =
        commuteAllowance(yearKm / 2 / days, days, year)

    /**
     * Reisekostenpauschale für [km] gefahrene Dienstreise-km. Gilt nur für einen *eigenen* Pkw und
     * nur, soweit der Arbeitgeber nichts erstattet; beim Firmenwagen ist nichts absetzbar.
     */
    fun businessTripAllowance(km: Double): Double = km * BUSINESS_TRIP_RATE
}
