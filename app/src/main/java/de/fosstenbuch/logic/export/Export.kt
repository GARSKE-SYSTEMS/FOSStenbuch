package de.fosstenbuch.logic.export

import de.fosstenbuch.data.Seal
import de.fosstenbuch.data.Trip
import de.fosstenbuch.data.TripAuditLog
import de.fosstenbuch.data.TripPurpose
import de.fosstenbuch.data.Vehicle
import de.fosstenbuch.logic.Backup
import de.fosstenbuch.logic.Fmt
import de.fosstenbuch.logic.Keys
import de.fosstenbuch.logic.Seals
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.Serializable
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.zip.DeflaterOutputStream

/** Exportformate mit MIME-Typ (für den Teilen-Dialog) und Dateiendung */
enum class Format(val mime: String, val ext: String) {
    CSV("text/csv", "csv"),
    PDF("application/pdf", "pdf"),
    /** "PDF (ausführlich, mit Änderungsprotokoll)": genau ein Fahrzeug, immer alle Kategorien */
    DETAILED_PDF("application/pdf", "pdf")
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
    /** Firma/Betrieb, nur im ausführlichen PDF */
    val company: String = ""
) : Serializable {
    /**
     * "Nur neue" und "Als exportiert markieren" gelten nur für CSV. Ein PDF muss immer den ganzen
     * Zeitraum zeigen (seine Summen sind Steuergrundlage) und darf den CSV-Stand nicht verbrauchen.
     */
    val incremental: Boolean get() = format == Format.CSV

    /** Das ausführliche PDF enthält das Änderungsprotokoll immer */
    val withAudit: Boolean get() = includeAudit || format == Format.DETAILED_PDF

    /** Das ausführliche PDF gilt genau einem Fahrzeug – ohne Auswahl wird es verweigert */
    val needsVehicle: Boolean get() = format == Format.DETAILED_PDF && vehicleId == null
}

/** Alles, was ein Exportformat zum Schreiben braucht */
class ExportData(
    val options: Options,
    /** Die zu exportierenden Fahrten, aufsteigend nach Datum (Ergebnis von [Export.filter]) */
    val trips: List<Trip>,
    purposes: List<TripPurpose>,
    vehicles: List<Vehicle>,
    /** Änderungsprotokoll je Fahrt-ID (gedruckt nur bei [Options.withAudit], gezählt immer) */
    val auditLogs: Map<Long, List<TripAuditLog>> = emptyMap(),
    val driver: String = "",
    /** Erstellungszeitpunkt (Dateiname, Fußzeile, Unterschrift) */
    val created: Date = Date(),
    /** Siegel je Fahrt-ID, älteste zuerst (Abschlusszeitpunkt für [changes]; Spalte und Anhang nur im ausführlichen PDF) */
    val seals: Map<Long, List<Seal>> = emptyMap(),
    /** Verlauf "Backup eingespielt" / "Alle Daten gelöscht", unabhängig vom Zeitraum (nur ausführliches PDF) */
    val history: List<Backup.Event> = emptyList(),
    /** Öffentlicher Schlüssel, mit dem das PDF signiert wird (SPKI, base64); null = unsigniert */
    val publicKey: String? = null,
    /** Noch nicht übernommene automatische Fahrten im Zeitraum (fehlen im Export) */
    val pendingGhosts: Int = 0
) {
    private val purposes = purposes.associateBy { it.id }
    private val vehicles = vehicles.associateBy { it.id }

    /** Kategorie bzw. Fahrzeug einer Fahrt; null, wenn keins zugeordnet (oder inzwischen gelöscht) */
    fun purpose(t: Trip): TripPurpose? = t.purposeId?.let { purposes[it] }
    fun vehicle(t: Trip): Vehicle? = t.vehicleId?.let { vehicles[it] }

    /** Geschäftlich = Kategorie ist geschäftsrelevant; ohne Kategorie gilt eine Fahrt als privat */
    fun isBusiness(t: Trip): Boolean = purpose(t)?.isBusinessRelevant == true

    /** Fahrzeuge der exportierten Fahrten (nur diese gehören z.B. in den Hinweis zur Bearbeitungssperre) */
    val usedVehicles: List<Vehicle> = trips.mapNotNull { vehicle(it) }.distinct()

    fun summary(list: List<Trip> = trips) = Summary(list, ::isBusiness)

    /**
     * Einträge im Änderungsprotokoll nach dem Abschluss (Siegel "Beendet"/"Übernommen"). Korrekturen beim Beenden
     * oder Übernehmen (z.B. "Unbekannt" → "Büro") stehen davor bzw. in derselben Millisekunde. Fahrten aus der
     * Zeit vor den Siegeln zählen alle Einträge.
     */
    fun changes(t: Trip): Int {
        val done = seals[t.id]?.firstOrNull { it.kind == Seals.ENDED || it.kind == Seals.ACCEPTED }?.createdAt
        return auditLogs[t.id].orEmpty().count { done == null || it.changedAt.time > done }
    }

    /** Siegel-Spalte je Fahrt-ID (einmal berechnet: Prüfung mit SHA-256 und ggf. Signatur) */
    val sealMarks: Map<Long, String> by lazy { trips.associate { it.id to Export.sealMark(it, seals[it.id].orEmpty()) } }
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

/** Tagesbeginn in lokaler Zeit – Brücke von den LocalDate-Filtern zu [Fmt] und dem Datumsdialog */
fun LocalDate.toDate(): Date = Date.from(atStartOfDay(ZoneId.systemDefault()).toInstant())

/** Reine Export-Logik (Filter, Dateiname, CSV, Gruppierung) – ohne Android, daher per JUnit testbar */
object Export {
    // Fmt kennt keinen Monatsnamen, daher hier ein eigenes Format nur dafür
    private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMANY)
    /** Siegel-Spalte, Zeit laut Zeitstempel: "✓ TT.MM. HH:MM" */
    private const val SEAL_TIME = "dd.MM. HH:mm"
    /** Prüfseite für PDFs und Backups (docs/pruefen.html) */
    const val VERIFY_URL = "https://garske-systems.github.io/FOSStenbuch/pruefen.html"
    /** Anfang der Prüfdaten am Ende des ausführlichen PDFs, danach base64-Kommentarzeilen (siehe [pdfTrailer]) */
    const val PROOF_MARKER = "\n%FOSStenbuch-Pruefdaten\n"
    private const val KEEP_MS = 24 * 60 * 60 * 1000L

    /**
     * Fahrten für den Export: abgeschlossen (nicht laufend, nicht automatisch erkannt), im Zeitraum
     * (lokale Zeit, Bis-Tag einschließlich), passend zu Fahrzeug, Kategorien und "nur neue".
     * Stornierte bleiben enthalten. Ergebnis aufsteigend nach Datum.
     * [purposeIds] = IDs aller vorhandenen Kategorien. Das ausführliche PDF ignoriert den Kategoriefilter.
     */
    fun filter(trips: List<Trip>, o: Options, purposeIds: Collection<Long>): List<Trip> {
        // Nichts abgewählt: alle Fahrten, auch ohne Kategorie. Sonst nur die gewählten Kategorien –
        // alles abgewählt ergibt also bewusst keine Fahrten (nicht stillschweigend alle).
        val allPurposes = o.format == Format.DETAILED_PDF || purposeIds.none { it in o.excludedPurposes }
        return trips.filter { t ->
            !t.isActive && !t.isGhost && inRange(t, o) &&
                (allPurposes || (t.purposeId != null && t.purposeId !in o.excludedPurposes)) &&
                !(o.incremental && o.onlyNew && t.isExported)
        }.sortedBy { it.date }
    }

    /** Abgeschlossene automatische Fahrten (noch nicht übernommen) im Zeitraum und beim Fahrzeug von [o] */
    fun pendingGhosts(trips: List<Trip>, o: Options): Int = trips.count { it.isGhost && it.endTime != null && inRange(it, o) }

    /** Im Zeitraum (lokale Zeit, Bis-Tag einschließlich) und beim gewählten Fahrzeug */
    private fun inRange(t: Trip, o: Options): Boolean {
        // Ende exklusiv = Beginn des Folgetags, so zählt auch 23:59:59.999 noch mit
        val time = t.date.time
        return time >= o.from.toDate().time && time < o.to.plusDays(1).toDate().time &&
            (o.vehicleId == null || t.vehicleId == o.vehicleId)
    }

    /**
     * Schreibt die Exportdatei nach [dir] und räumt dabei alte Exporte auf.
     * Blockiert (Dateizugriff, PDF-Rendering) – nur im Hintergrund aufrufen.
     */
    fun write(dir: File, d: ExportData): File {
        dir.mkdirs()
        // Exporte enthalten personenbezogene Daten, daher nach einem Tag löschen. Nicht sofort alle:
        // Eine Ziel-App (z.B. ein Mail-Entwurf) liest eine geteilte Datei evtl. erst später.
        dir.listFiles()?.forEach { if (d.created.time - it.lastModified() > KEEP_MS) it.delete() }
        val file = File(dir, fileName(d.options, d.created))
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

    /**
     * Prüfdaten des ausführlichen PDFs für docs/pruefen.js: die Fahrten mit den gesiegelten Feldern und ihre Siegel,
     * Schlüssel wie im Backup – damit prüft die Prüfseite jede Fahrt allein mit dem PDF. Keine GPS-Daten und nichts,
     * woraus sich Ungedrucktes erraten ließe: Bei einer Privatfahrt mit Notiz oder Geschäftspartner fehlen diese
     * beiden und ihre Siegel ("withheld", nur mit dem Backup prüfbar), siehe auch [proofSeals].
     */
    fun proofData(d: ExportData): String {
        val trips = d.trips.map { t -> t to (d.isBusiness(t) || (t.businessPartner.isNullOrEmpty() && t.notes.isNullOrEmpty())) }
        return Backup.toText(
            mapOf(
                "format" to "FOSStenbuch-Pruefdaten", "version" to 1, "exportedAt" to d.created.time,
                "trips" to trips.map { (t, printed) ->
                    mapOf(
                        "id" to t.id, "vehicleId" to t.vehicleId, "date" to t.date.time, "endTime" to t.endTime?.time,
                        "startLocation" to t.startLocation, "endLocation" to t.endLocation, "route" to t.route,
                        "purpose" to t.purpose, "startOdometer" to t.startOdometer, "endOdometer" to t.endOdometer,
                        "isCancelled" to t.isCancelled, "cancellationReason" to t.cancellationReason
                    ) + when {
                        printed -> mapOf("businessPartner" to t.businessPartner, "notes" to t.notes)
                        d.seals[t.id].isNullOrEmpty() -> emptyMap() // ohne Siegel gibt es auch mit dem Backup nichts zu prüfen
                        else -> mapOf("withheld" to true)
                    }
                },
                // Ohne tsUrl: Eine eigene Adresse könnte z.B. einen Zugangsschlüssel enthalten
                "seals" to trips.filter { it.second }.flatMap { (t, _) -> proofSeals(t, d.seals[t.id].orEmpty()) }
                    .map { Backup.encodeSeal(it) - "tsUrl" }
            )
        )
    }

    /**
     * Siegel einer Fahrt für die Prüfdaten: das jüngste und frühere, die zu den heutigen Daten oder zum Stand vor dem
     * Stornieren passen. Andere gehören zu einem früheren Stand – prüfen lassen sie sich ohne ihn nicht, und ihr Hash
     * könnte frühere Angaben verraten.
     */
    private fun proofSeals(t: Trip, seals: List<Seal>): List<Seal> {
        val latest = seals.maxByOrNull { it.id }
        val uncancelled = t.copy(isCancelled = false, cancellationReason = null)
        return seals.filter { it === latest || Seals.dataMatches(t, it) || Seals.dataMatches(uncancelled, it) }
    }

    /**
     * Anhang ans fertige PDF: die Prüfdaten [data] (zlib, base64, je 76 Zeichen eine Kommentarzeile), mit [key] eine
     * Signaturzeile über alle Bytes davor, zuletzt startxref und %%EOF wie im PDF selbst. PDF-Programme überspringen
     * Kommentare; das wiederholte startxref zeigt auf die unveränderte xref-Tabelle, so finden es auch Programme, die
     * nur im letzten KB suchen. Ohne Prüfdaten und Schlüssel bleibt das PDF, wie es ist.
     */
    fun pdfTrailer(pdf: ByteArray, data: String?, key: Keys.Key?): ByteArray {
        if (data == null && key == null) return ByteArray(0)
        val tail = String(pdf.copyOfRange(maxOf(0, pdf.size - 64), pdf.size), Charsets.ISO_8859_1)
        val xref = Regex("startxref\\s+(\\d+)\\s+%%EOF\\s*$").find(tail)?.groupValues?.get(1) ?: error("PDF ohne startxref")
        val proof = data?.let {
            val zipped = ByteArrayOutputStream().also { out -> DeflaterOutputStream(out).use { z -> z.write(it.toByteArray()) } }
            PROOF_MARKER + Base64.getEncoder().encodeToString(zipped.toByteArray()).chunked(76).joinToString("") { line -> "%$line\n" }
        }.orEmpty().toByteArray()
        val signature = key?.let { Keys.pdfSignature(pdf + proof, it) }.orEmpty()
        return proof + "$signature\nstartxref\n$xref\n%%EOF\n".toByteArray()
    }

    /** Hängt [pdfTrailer] an die fertige PDF-Datei an. Blockiert. */
    fun finishPdf(file: File, data: String?, key: Keys.Key?) = file.appendBytes(pdfTrailer(file.readBytes(), data, key))

    /** Dateiname mit Zeitstempel: Ein neuer Export überschreibt nie eine Datei, die gerade geteilt wird */
    fun fileName(o: Options, now: Date): String {
        val kind = if (o.format == Format.DETAILED_PDF) "fahrtenbuch_ausfuehrlich" else "fahrtenbuch"
        // Sortierbar und ohne Doppelpunkt (in Dateinamen z.B. unter Windows unzulässig), daher nicht Fmt
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
        // Eine CSV-Datei lässt sich beliebig bearbeiten – vorzeigbar ist nur das PDF
        row("Datenexport – kein Fahrtenbuch zur Vorlage")
        row()
        if (d.driver.isNotEmpty()) {
            row("Fahrer", d.driver)
            row()
        }
        val protected = d.usedVehicles.filter { it.auditProtected }
        if (protected.isNotEmpty()) {
            row("Fahrzeuge mit Bearbeitungssperre in der App (gilt nicht für diese Datei)")
            protected.forEach { row("${it.make} ${it.model}", it.licensePlate) }
            row()
        }
        row(
            "Fahrt-ID", "Startdatum/-zeit", "Enddatum/-zeit", "Startort", "Zielort", "Distanz (km)", "Zweck", "Kategorie",
            "Geschäftlich", "Geschäftspartner", "Route", "Fahrzeug", "Kennzeichen", "Km-Stand Start",
            "Km-Stand Ende", "Notizen", "Storniert", "Stornogrund", "Änderungen"
        )
        for (t in d.trips) {
            val v = d.vehicle(t)
            row(
                t.id, Fmt.dateTime(t.date), t.endTime?.let(Fmt::dateTime), t.startLocation, t.endLocation,
                // Reine Zahl ohne Einheit/Tausenderpunkt (nicht Fmt.km), damit Excel damit rechnen kann
                String.format(Locale.GERMANY, "%.2f", t.distanceKm), t.purpose, d.purpose(t)?.name,
                if (d.isBusiness(t)) "Ja" else "Nein", t.businessPartner, t.route, v?.let { "${it.make} ${it.model}" },
                v?.licensePlate, t.startOdometer, t.endOdometer, t.notes, if (t.isCancelled) "STORNIERT" else null,
                t.cancellationReason, d.changes(t)
            )
        }
        if (d.options.withAudit && d.auditLogs.isNotEmpty()) {
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

    /** Zeitraum für die PDF-Köpfe, z.B. "01.01.2024 – 31.12.2024" */
    fun range(o: Options) = "${Fmt.date(o.from.toDate())} – ${Fmt.date(o.to.toDate())}"

    /** Eindeutige Bezeichnung einer Fahrt (für das Änderungsprotokoll), z.B. "05.02.2024 08:15 Berlin → Hamburg" */
    fun tripLabel(t: Trip) = "${Fmt.dateTime(t.date)} ${t.startLocation} → ${t.endLocation}"

    /**
     * Nicht stornierte Fahrten ohne lückenlose Km-Stände, mit ihrer Nr. (Position in [trips], nach Datum sortiert
     * wie aus [filter]): Stand fehlt, oder der Start passt nicht zum Ende der vorherigen Fahrt desselben Fahrzeugs.
     */
    fun odometerGaps(trips: List<Trip>): List<String> {
        val previousEnd = mutableMapOf<Long?, Int?>()
        return trips.mapIndexedNotNull { i, t ->
            if (t.isCancelled) return@mapIndexedNotNull null
            val before = previousEnd[t.vehicleId]
            previousEnd[t.vehicleId] = t.endOdometer
            when {
                t.startOdometer == null || t.endOdometer == null -> "Nr. ${i + 1}: Km-Stand fehlt"
                before != null && t.startOdometer != before -> "Nr. ${i + 1}: beginnt bei ${t.startOdometer}, die vorherige Fahrt endete bei $before"
                else -> null
            }
        }
    }

    /** Km-Stand zu Beginn der ersten und am Ende der letzten nicht stornierten Fahrt (null = unbekannt) */
    fun odometerRange(trips: List<Trip>): Pair<Int?, Int?> = trips.filter { !it.isCancelled }.let {
        it.firstOrNull()?.startOdometer to it.lastOrNull()?.endOdometer
    }

    /** Stand der Bearbeitungssperre eines Fahrzeugs (Deckblatt des ausführlichen PDFs) */
    fun lockStatus(v: Vehicle): String = when {
        !v.auditProtected -> "Keine Bearbeitungssperre – abgeschlossene Fahrten bleiben bearbeitbar; Änderungen werden protokolliert"
        v.protectedSince == null -> "Bearbeitungssperre aktiv (Zeitpunkt der Aktivierung nicht erfasst)"
        else -> "Bearbeitungssperre aktiv seit ${Fmt.dateTime(Date(v.protectedSince))}"
    }

    /**
     * Siegel-Spalte einer Fahrt nach ihrem jüngsten Siegel: "!" passt nicht (nach dem Siegeln verändert),
     * "✓ TT.MM. HH:MM" mit Zeitstempel, "✓ sig." signiert, "✓" nur Siegel, "–" kein Siegel
     */
    fun sealMark(t: Trip, seals: List<Seal>): String {
        val s = seals.maxByOrNull { it.id } ?: return "–"
        return when {
            !Seals.matches(t, s) -> "!"
            s.tsTime != null -> "✓ " + SimpleDateFormat(SEAL_TIME, Locale.GERMANY).format(Date(s.tsTime))
            s.signature != null -> "✓ sig."
            else -> "✓"
        }
    }
}
