package de.fosstenbuch.logic.export

import de.fosstenbuch.data.Trip
import de.fosstenbuch.data.TripAuditLog
import de.fosstenbuch.data.TripPurpose
import de.fosstenbuch.data.Vehicle
import de.fosstenbuch.logic.Fmt
import java.io.File
import java.io.Serializable
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/** Exportformate mit MIME-Typ (für den Teilen-Dialog) und Dateiendung */
enum class Format(val mime: String, val ext: String) {
    CSV("text/csv", "csv"),
    PDF("application/pdf", "pdf"),
    FINANZAMT_PDF("application/pdf", "pdf")
}

/**
 * Filter und Optionen des Exports, so wie der Nutzer sie gewählt hat.
 * Serializable, damit der Bildschirm sie im SavedStateHandle über einen Prozess-Tod rettet
 * (LocalDate, Set und das enum sind selbst serialisierbar).
 */
data class Options(
    val from: LocalDate = LocalDate.now().withDayOfYear(1),
    // Jahresende statt "heute": Bleibt der Bildschirm über Mitternacht offen, fehlten sonst die heutigen Fahrten
    val to: LocalDate = LocalDate.now().withMonth(12).withDayOfMonth(31),
    val vehicleId: Long? = null,
    /** Abgewählte (nicht gewählte) Kategorien: So sind neu angelegte Kategorien automatisch dabei */
    val excludedPurposes: Set<Long> = emptySet(),
    val format: Format = Format.CSV,
    val onlyNew: Boolean = true,
    val markExported: Boolean = true,
    val includeAudit: Boolean = true,
    /** Firma/Betrieb, nur im Finanzamt-PDF */
    val company: String = ""
) : Serializable {
    /**
     * "Nur neue" und "Als exportiert markieren" gelten nur für CSV. Ein PDF muss immer den ganzen
     * Zeitraum zeigen (seine Summen sind Steuergrundlage) und darf den CSV-Stand nicht verbrauchen.
     */
    val incremental: Boolean get() = format == Format.CSV

    /** Das Finanzamt-PDF enthält das Änderungsprotokoll immer */
    val withAudit: Boolean get() = includeAudit || format == Format.FINANZAMT_PDF
}

/** Alles, was ein Exportformat zum Schreiben braucht */
class ExportData(
    val options: Options,
    /** Die zu exportierenden Fahrten, aufsteigend nach Datum (Ergebnis von [Export.filter]) */
    val trips: List<Trip>,
    purposes: List<TripPurpose>,
    vehicles: List<Vehicle>,
    /** Änderungsprotokoll je Fahrt-ID; leer = ohne Protokoll */
    val auditLogs: Map<Long, List<TripAuditLog>> = emptyMap(),
    val driver: String = "",
    /** Erstellungsdatum (Fußzeile, Unterschrift) */
    val today: LocalDate = LocalDate.now()
) {
    private val purposes = purposes.associateBy { it.id }
    private val vehicles = vehicles.associateBy { it.id }

    fun purpose(t: Trip): TripPurpose? = t.purposeId?.let { purposes[it] }
    fun vehicle(t: Trip): Vehicle? = t.vehicleId?.let { vehicles[it] }

    /** Geschäftlich = Kategorie ist geschäftsrelevant; ohne Kategorie gilt eine Fahrt als privat */
    fun isBusiness(t: Trip): Boolean = purpose(t)?.isBusinessRelevant == true

    /** Fahrzeuge der exportierten Fahrten (nur diese gehören z.B. in den Änderungssicher-Hinweis) */
    val usedVehicles: List<Vehicle> = trips.mapNotNull { vehicle(it) }.distinct()

    fun summary(list: List<Trip> = trips) = Summary(list, ::isBusiness)
}

/** Summen über die nicht stornierten Fahrten – stornierte bleiben im Export sichtbar, zählen aber nicht */
class Summary(trips: List<Trip>, isBusiness: (Trip) -> Boolean) {
    private val valid = trips.filter { !it.isCancelled }
    private val business = valid.filter(isBusiness)
    val count = valid.size
    val km = valid.sumOf { it.distanceKm }
    val businessCount = business.size
    val businessKm = business.sumOf { it.distanceKm }
    val privateCount = count - businessCount
    val privateKm = km - businessKm
    val cancelled = trips.size - count
}

/** Reine Export-Logik (Filter, Dateiname, CSV, Gruppierung) – ohne Android, daher per JUnit testbar */
object Export {
    private val DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMANY)
    private const val KEEP_MS = 24 * 60 * 60 * 1000L

    /**
     * Fahrten für den Export: abgeschlossen (nicht laufend, nicht automatisch erkannt), im Zeitraum
     * (lokale Zeit, Bis-Tag einschließlich), passend zu Fahrzeug, Kategorien und "nur neue".
     * Stornierte bleiben enthalten. Ergebnis aufsteigend nach Datum.
     * [purposeIds] = IDs aller vorhandenen Kategorien.
     */
    fun filter(trips: List<Trip>, o: Options, purposeIds: Collection<Long>): List<Trip> {
        val zone = ZoneId.systemDefault()
        val start = o.from.atStartOfDay(zone).toInstant().toEpochMilli()
        // Ende exklusiv = Beginn des Folgetags, so zählt auch 23:59:59.999 noch mit
        val end = o.to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        // Nichts abgewählt: alle Fahrten, auch ohne Kategorie. Sonst nur die gewählten Kategorien –
        // alles abgewählt ergibt also bewusst keine Fahrten (nicht stillschweigend alle).
        val allPurposes = purposeIds.none { it in o.excludedPurposes }
        return trips.filter { t ->
            !t.isActive && !t.isGhost && t.date.time >= start && t.date.time < end &&
                (o.vehicleId == null || t.vehicleId == o.vehicleId) &&
                (allPurposes || (t.purposeId != null && t.purposeId !in o.excludedPurposes)) &&
                !(o.incremental && o.onlyNew && t.isExported)
        }.sortedBy { it.date }
    }

    /**
     * Schreibt die Exportdatei nach [dir] und räumt dabei alte Exporte auf.
     * Blockiert (Dateizugriff, PDF-Rendering) – nur im Hintergrund aufrufen.
     */
    fun write(dir: File, d: ExportData, now: Date = Date()): File {
        dir.mkdirs()
        // Exporte enthalten personenbezogene Daten, daher nach einem Tag löschen. Nicht sofort alle:
        // Eine Ziel-App (z.B. ein Mail-Entwurf) liest eine geteilte Datei evtl. erst später.
        dir.listFiles()?.forEach { if (now.time - it.lastModified() > KEEP_MS) it.delete() }
        val file = File(dir, fileName(d.options, now))
        try {
            file.outputStream().use { out ->
                if (d.options.format == Format.CSV) out.write(csv(d).toByteArray()) else writePdf(d, out)
            }
        } catch (e: Exception) {
            file.delete() // keine halbe Datei liegen lassen
            throw e
        }
        return file
    }

    /** Dateiname mit Zeitstempel: Ein neuer Export überschreibt nie eine Datei, die gerade geteilt wird */
    fun fileName(o: Options, now: Date): String {
        val kind = if (o.format == Format.FINANZAMT_PDF) "fahrtenbuch_finanzamt" else "fahrtenbuch"
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(now)
        return "${kind}_${o.from}_${o.to}_$stamp.${o.format.ext}"
    }

    /** CSV für Excel: UTF-8 mit BOM, Trennzeichen ";", deutsche Zahlen, Zeilenende CRLF (RFC 4180) */
    fun csv(d: ExportData): String = buildString {
        fun row(vararg cells: Any?) {
            cells.joinTo(this, ";") { cell(it?.toString() ?: "") }
            append("\r\n")
        }
        append('\uFEFF') // BOM: Sonst zeigt Excel die Umlaute falsch an
        if (d.driver.isNotEmpty()) {
            row("Fahrer", d.driver)
            row()
        }
        val protected = d.usedVehicles.filter { it.auditProtected }
        if (protected.isNotEmpty()) {
            row("Änderungssicher geführte Fahrzeuge")
            protected.forEach { row("${it.make} ${it.model}", it.licensePlate) }
            row()
        }
        row(
            "Startdatum/-zeit", "Enddatum/-zeit", "Startort", "Zielort", "Distanz (km)", "Zweck", "Kategorie",
            "Geschäftlich", "Geschäftspartner", "Route", "Fahrzeug", "Kennzeichen", "Km-Stand Start",
            "Km-Stand Ende", "Notizen", "Storniert", "Stornogrund"
        )
        for (t in d.trips) {
            val v = d.vehicle(t)
            row(
                Fmt.dateTime(t.date), t.endTime?.let(Fmt::dateTime), t.startLocation, t.endLocation,
                String.format(Locale.GERMANY, "%.2f", t.distanceKm), t.purpose, d.purpose(t)?.name,
                if (d.isBusiness(t)) "Ja" else "Nein", t.businessPartner, t.route, v?.let { "${it.make} ${it.model}" },
                v?.licensePlate, t.startOdometer, t.endOdometer, t.notes, if (t.isCancelled) "STORNIERT" else null,
                t.cancellationReason
            )
        }
        if (d.auditLogs.isNotEmpty()) {
            row()
            row("Änderungsprotokoll")
            row("Fahrt", "Feld", "Alter Wert", "Neuer Wert", "Geändert am")
            for (t in d.trips) {
                d.auditLogs[t.id]?.forEach { row(tripLabel(t), it.fieldName, it.oldValue, it.newValue, Fmt.dateTime(it.changedAt)) }
            }
        }
    }

    /** Ein CSV-Feld: Formelanfang entschärfen, bei Bedarf in Anführungszeichen setzen (RFC 4180) */
    fun cell(value: String): String {
        // Excel/LibreOffice werten Zellen, die mit = + - @ (oder Tab/CR) beginnen, als Formel aus –
        // aus Nutzer- oder Nominatim-Text würde sonst z.B. =HYPERLINK(...) ausführbar
        val s = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
        return if (s.any { it in ";\"\r\n" }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    /** Fahrten je Kalendermonat (mit Jahr, lokale Zeit), chronologisch sortiert */
    fun months(trips: List<Trip>): Map<YearMonth, List<Trip>> =
        trips.groupBy { YearMonth.from(it.date.toInstant().atZone(ZoneId.systemDefault())) }.toSortedMap()

    /** z.B. "Januar 2024" */
    fun monthName(ym: YearMonth): String = ym.format(MONTH)

    fun day(d: LocalDate): String = d.format(DAY)

    fun range(o: Options) = "${day(o.from)} – ${day(o.to)}"

    /** Eindeutige Bezeichnung einer Fahrt (für das Änderungsprotokoll), z.B. "05.02.2024 08:15 Berlin → Hamburg" */
    fun tripLabel(t: Trip) = "${Fmt.dateTime(t.date)} ${t.startLocation} → ${t.endLocation}"

    /**
     * Anzahl nicht stornierter Fahrten ohne lückenlose Km-Stände: Stand fehlt, oder der Start passt
     * nicht zum Ende der vorherigen Fahrt desselben Fahrzeugs.
     */
    fun odometerGaps(trips: List<Trip>): Int =
        trips.filter { !it.isCancelled }.groupBy { it.vehicleId }.values.sumOf { list ->
            var previousEnd: Int? = null
            list.sortedBy { it.date }.count { t ->
                val gap = t.startOdometer == null || t.endOdometer == null ||
                    (previousEnd != null && t.startOdometer != previousEnd)
                previousEnd = t.endOdometer
                gap
            }
        }
}
