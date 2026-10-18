package de.fosstenbuch.logic.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import de.fosstenbuch.data.Seal
import de.fosstenbuch.data.Trip
import de.fosstenbuch.logic.Fmt
import de.fosstenbuch.logic.Seals
import java.io.OutputStream
import java.net.URI
import java.util.Date
import java.util.Locale

// PDF-Ausgabe für "PDF" und "PDF (ausführlich, mit Änderungsprotokoll)": A4 quer, Maße in Punkt (1/72 Zoll).
// Alle Texte werden umbrochen statt gekürzt – Orte, Zweck, Geschäftspartner und Route bleiben vollständig lesbar.

private const val W = 842
private const val H = 595
private const val M = 36f              // Rand links, rechts und oben
private const val BOTTOM = H - 40f     // darunter steht nur noch die Fußzeile
private const val TEXT_W = W - 2 * M
private const val PAD = 2.5f           // Innenabstand der Tabellenzellen

/** Schrift für das PDF (feste Farben: Papier ist immer weiß, unabhängig vom Dunkelmodus) */
private fun paint(size: Float, color: Int = Color.BLACK, style: Int = Typeface.NORMAL) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.DEFAULT, style)
    }

private val title = paint(15f, style = Typeface.BOLD)
private val heading = paint(10f, Color.DKGRAY, Typeface.BOLD)
private val body = paint(7.5f)
private val bold = paint(7.5f, style = Typeface.BOLD)
private val headerText = paint(7.5f, Color.WHITE, Typeface.BOLD)
private val small = paint(6.5f, Color.DKGRAY)
private val italic = paint(7f, Color.DKGRAY, Typeface.ITALIC)
private val red = paint(7.5f, Color.RED)
private val green = paint(7.5f, Color.rgb(0x1B, 0x5E, 0x20), Typeface.ITALIC)
private val orange = paint(7.5f, Color.rgb(0xE6, 0x51, 0x00), Typeface.ITALIC)
private val grid = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }
private val fill = Paint()
private val STRIPE = Color.rgb(0xF5, 0xF5, 0xF5)  // Hintergrund jeder zweiten Zeile
private val SUM_BG = Color.rgb(0xE8, 0xEA, 0xF6)  // Summenzeilen

private fun lineHeight(p: Paint) = p.textSize * 1.3f

private fun odo(km: Int?) = km?.toString() ?: "-"

/** Schreibt beide PDF-Formate; Format, Fahrten usw. stehen in [d] */
internal fun writePdf(d: ExportData, out: OutputStream) {
    val detailed = d.options.format == Format.DETAILED_PDF
    val w = PdfWriter(
        footer = if (detailed) "FOSStenbuch — Fahrtenbuch" else "FOSStenbuch",
        created = "Erstellt: ${Fmt.date(d.created)}",
        headerColor = if (detailed) Color.rgb(0x37, 0x47, 0x4F) else Color.rgb(0x62, 0x00, 0xEE)
    )
    try {
        if (detailed) detailed(w, d) else standard(w, d)
        w.finish(out)
    } finally {
        w.close()
    }
}

/** Einfaches PDF: Kopf mit Summen, Fahrtentabelle, Änderungsprotokoll, Bestätigung */
private fun standard(w: PdfWriter, d: ExportData) {
    val s = d.summary()
    w.text("Fahrtenbuch", title)
    w.y += 4f
    if (d.driver.isNotEmpty()) w.text("Fahrer: ${d.driver}")
    w.text("Zeitraum: ${Export.range(d.options)}")
    w.text("Anzahl Fahrten: ${s.count}" + if (s.cancelled > 0) " (zzgl. ${s.cancelled} storniert)" else "")
    w.text("Gesamtstrecke: ${Fmt.km(s.km)}")
    w.text("Geschäftliche Strecke: ${Fmt.km(s.businessKm)}")
    w.text("Private Strecke: ${Fmt.km(s.privateKm)}")
    auditNotice(w, d)
    w.y += 8f

    val cw = floatArrayOf(72f, 72f, 80f, 80f, 50f, 80f, 60f, 75f, 60f, 50f, 50f, 32f)
    val headers = listOf("Start", "Ende", "Startort", "Zielort", "Distanz", "Zweck", "Kategorie", "Fahrzeug",
        "Kennzeichen", "Km Start", "Km Ende", "Storno")
    w.table(headers, cw) {
        d.trips.forEachIndexed { i, t ->
            val v = d.vehicle(t)
            w.tripRow(
                t, i, cw, listOf(
                    Fmt.dateTime(t.date), t.endTime?.let(Fmt::dateTime) ?: "-", t.startLocation, t.endLocation,
                    Fmt.km(t.distanceKm), t.purpose, d.purpose(t)?.name ?: "-", v?.let { "${it.make} ${it.model}" } ?: "-",
                    v?.licensePlate ?: "-", odo(t.startOdometer), odo(t.endOdometer), if (t.isCancelled) "Ja" else ""
                )
            )
        }
    }
    auditLog(w, d)

    w.y += 12f
    w.text("Ich bestätige hiermit, dass alle Angaben in diesem Fahrtenbuch der Wahrheit entsprechen.", italic)
    if (d.driver.isNotEmpty()) {
        w.y += 8f
        w.text(d.driver)
        w.text(Fmt.date(d.created), small)
    }
    d.publicKey?.let {
        w.y += 8f
        w.text("Schlüssel-Fingerabdruck: ${Seals.fingerprint(it)} · Dieses PDF ist digital signiert. Prüfen: ${Export.VERIFY_URL}", small)
    }
}

/**
 * Ausführliches PDF (genau ein Fahrzeug, alle Kategorien): Deckblatt mit Stammdaten, Km-Ständen, Hinweisen,
 * Verlauf und Schlüssel, Übersicht und Monatssummen; danach Einzelnachweise mit Monats-Zwischensummen,
 * Änderungsprotokoll, Siegel-Anhang, Richtigkeitserklärung und Schlussvermerk.
 */
private fun detailed(w: PdfWriter, d: ExportData) {
    val s = d.summary()
    val months = Export.months(d.trips)
    w.text("Fahrtenbuch", title)
    w.text("Einzelnachweis aller Fahrten", italic)
    w.y += 10f

    // Stammdaten: Bezeichnung links, Wert daneben
    val meta = floatArrayOf(160f, TEXT_W - 160f)
    val v = d.usedVehicles.firstOrNull() // genau eins, siehe Options.needsVehicle
    if (d.options.company.isNotEmpty()) w.row(listOf("Firma/Betrieb:", d.options.company), meta)
    if (v != null) {
        w.row(listOf("Fahrzeug:", v.label), meta)
        w.row(listOf("Kraftstoff:", v.fuelType), meta)
    }
    w.row(listOf("Zeitraum:", Export.range(d.options)), meta)
    w.row(listOf("Fahrer laut Einstellung beim Export:", d.driver.ifEmpty { "nicht angegeben" }), meta)
    val (startKm, endKm) = Export.odometerRange(d.trips)
    w.row(listOf("Km-Stand zu Beginn:", "${odo(startKm)} (Start der ersten Fahrt im Zeitraum)"), meta)
    w.row(listOf("Km-Stand am Ende:", "${odo(endKm)} (Ende der letzten Fahrt im Zeitraum)"), meta)
    if (startKm != null && endKm != null) {
        val diff = (endKm - startKm).toDouble()
        w.row(listOf("Nicht erfasste km:", "${Fmt.km(diff - s.km)} (Differenz der Km-Stände ${Fmt.km(diff)} minus Summe der Fahrten ${Fmt.km(s.km)})"), meta)
    }
    if (v != null) {
        w.y += 6f
        w.text(Export.lockStatus(v), if (v.auditProtected) green else body)
    }

    // Hinweise auf Lücken – das Fahrtenbuch zeigt sie offen, statt sie zu verschweigen
    val gaps = Export.odometerGaps(d.trips)
    val noReason = d.trips.count { !it.isCancelled && d.isBusiness(it) && it.businessPartner.isNullOrBlank() && it.notes.isNullOrBlank() }
    val broken = d.sealMarks.values.count { it == "!" }
    if (gaps.isNotEmpty() || noReason > 0 || broken > 0 || d.pendingGhosts > 0) w.y += 6f
    if (gaps.isNotEmpty()) {
        w.text("⚠ Km-Lücken (Nr. = Nummer in den Einzelnachweisen):", orange)
        gaps.forEach { w.text("• $it", orange, indent = 12f) }
    }
    if (d.pendingGhosts > 0) {
        w.text("⚠ ${d.pendingGhosts} automatisch erkannte Fahrt(en) im Zeitraum noch nicht übernommen – sie fehlen in diesem Fahrtenbuch.", orange)
    }
    if (noReason > 0) w.text("⚠ $noReason geschäftliche Fahrt(en) ohne Geschäftspartner und Anlass.", orange)
    if (broken > 0) w.text("⚠ $broken Fahrt(en) passen nicht zu ihrem Siegel (nach dem Siegeln verändert, Spalte „Siegel“: !).", orange)

    // Verlauf aus den Einstellungen: übersteht "Alle Daten löschen" und das Einspielen eines Backups
    w.y += 14f
    w.text("Wiederherstellungen und Löschungen in der App", heading)
    w.text("Alle erfassten Einträge, unabhängig vom Zeitraum", small)
    if (d.history.isEmpty()) w.text("Keine erfasst.")
    for (e in d.history) {
        val mismatch = if (e.sealMismatches > 0) " – ${e.sealMismatches} Fahrt(en) passten nicht zu ihrem Siegel" else ""
        w.text("${Fmt.dateTime(Date(e.time))}  ${e.kind}$mismatch")
    }

    w.y += 14f
    w.text("Digitale Prüfung", heading)
    val stamped = d.seals.values.any { list -> list.any { it.tsTime != null } }
    w.text(
        when {
            d.seals.isEmpty() -> "Die Fahrten dieses Zeitraums haben keine Siegel (erfasst vor Einführung der Siegel)."
            stamped -> "Die PDF-Datei enthält die Siegel und Zeitstempel der Fahrten (siehe Anhang)."
            else -> "Die PDF-Datei enthält die Siegel der Fahrten (siehe Anhang), aber keine Zeitstempel."
        } + (if (d.publicKey != null) " Die Datei ist digital signiert." else "") +
            " Prüfen: die unveränderte Datei aus der App auf ${Export.VERIFY_URL} öffnen – im Browser, ohne Hochladen. " +
            "Ausgedruckt oder in einem PDF-Programm neu gespeichert ist das nicht prüfbar."
    )
    d.publicKey?.let { pub ->
        w.row(listOf("Schlüssel-Fingerabdruck:", Seals.fingerprint(pub)), meta)
        w.row(listOf("Öffentlicher Schlüssel:", pub), meta)
    }

    w.y += 14f
    w.text("Jahresübersicht", heading)
    w.y += 2f
    // Anteile nur bei Strecke > 0 – sonst stünde "100 %" neben zweimal "0 %"
    fun share(km: Double) = if (s.km > 0) String.format(Locale.GERMANY, "%.1f %%", km / s.km * 100) else "-"
    val sw = floatArrayOf(120f, 100f, 100f, 80f)
    w.table(listOf("", "Anzahl Fahrten", "Kilometer", "Anteil"), sw) {
        w.row(listOf("Geschäftlich", "${s.businessCount}", Fmt.km(s.businessKm), share(s.businessKm)), sw, bg = STRIPE)
        w.row(listOf("Privat", "${s.privateCount}", Fmt.km(s.privateKm), share(s.privateKm)), sw)
        w.row(listOf("Gesamt", "${s.count}", Fmt.km(s.km), share(s.km)), sw, bold, SUM_BG)
    }

    w.y += 14f
    w.text("Monatliche Zusammenfassung", heading)
    w.y += 2f
    val mw = floatArrayOf(100f, 60f, 90f, 90f, 90f)
    w.table(listOf("Monat", "Fahrten", "Geschäftl. km", "Privat km", "Gesamt km"), mw) {
        var i = 0
        for ((month, list) in months) {
            val m = d.summary(list)
            if (m.count == 0) continue
            w.row(
                listOf(Export.monthName(month), "${m.count}", Fmt.km(m.businessKm), Fmt.km(m.privateKm), Fmt.km(m.km)),
                mw, bg = if (i++ % 2 == 0) STRIPE else null
            )
        }
    }

    // Einzelnachweise immer ab einer neuen Seite
    w.newPage()
    w.text("Einzelnachweise", heading)
    w.text(
        "Nr. mit * = nach dem Abschluss geändert oder storniert (siehe Änderungsprotokoll; bei Fahrten aus der Zeit vor den " +
            "Siegeln auch Korrekturen beim Beenden/Übernehmen) · Zweck: gespeicherte Kategorie, (G) geschäftlich (darunter die Notiz), (P) privat · " +
            "Siegel: ✓ TT.MM. HH:MM mit Zeitstempel, ✓ sig. signiert, ✓ gesiegelt, – kein Siegel, ! nach dem Siegeln verändert (Signatur siehe Anhang)",
        small
    )
    w.y += 2f
    // Kein Kennzeichen (ein Fahrzeug, steht auf dem Deckblatt) und keine Kategorie (steckt im Zweck)
    val cw = floatArrayOf(24f, 44f, 28f, 28f, 90f, 90f, 80f, 100f, 70f, 40f, 40f, 46f, 32f, 58f)
    val headers = listOf("Nr.", "Datum", "Start", "Ende", "Startort", "Zielort", "Geschäftspartner", "Zweck", "Route",
        "Km-Start", "Km-Ende", "Distanz", "Storno", "Siegel")
    w.table(headers, cw) {
        var nr = 0
        for ((month, list) in months) {
            for (t in list) {
                nr++
                val business = d.isBusiness(t)
                val purpose = if (business) "${t.purpose} (G)" + t.notes?.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty() else "${t.purpose} (P)"
                w.tripRow(
                    t, nr, cw, listOf(
                        if (d.changes(t) > 0) "$nr*" else "$nr", Fmt.date(t.date), Fmt.time(t.date), t.endTime?.let(Fmt::time) ?: "-",
                        t.startLocation, t.endLocation, if (business) t.businessPartner.orEmpty() else "-", purpose, t.route.orEmpty(),
                        odo(t.startOdometer), odo(t.endOdometer), Fmt.km(t.distanceKm), if (t.isCancelled) "Ja" else "",
                        d.sealMarks[t.id] ?: "–"
                    ),
                    keep = 2
                )
            }
            val m = d.summary(list)
            if (m.count > 0) w.row(
                listOf(
                    "∑ ${Export.monthName(month)}: ${m.count} Fahrten | Geschäftl.: ${Fmt.km(m.businessKm)} | " +
                        "Privat: ${Fmt.km(m.privateKm)} | Gesamt: ${Fmt.km(m.km)}"
                ),
                floatArrayOf(cw.sum()), bold, SUM_BG
            )
        }
    }
    auditLog(w, d, numbered = true)
    sealAppendix(w, d)

    // Richtigkeitserklärung und Schlussvermerk als Block zusammenhalten
    w.y += 20f
    w.ensure(145f)
    w.canvas.drawLine(M, w.y, W - M, w.y, grid)
    w.y += 8f
    w.text("Richtigkeitserklärung", heading)
    w.text("Ich versichere hiermit, dass alle Angaben in diesem Fahrtenbuch vollständig und wahrheitsgemäß sind.", italic)
    w.text("Die Eintragungen wurden zeitnah und fortlaufend vorgenommen.", italic)
    w.y += 36f
    val signX = M + 280f
    for ((x, label) in listOf(M to "Datum", signX to "Unterschrift")) {
        w.canvas.drawLine(x, w.y, x + 200f, w.y, body)
        w.canvas.drawText(label, x, w.y + 10f, small)
    }
    if (d.driver.isNotEmpty()) w.canvas.drawText("Name: ${d.driver}", signX, w.y + 24f, body)
    d.publicKey?.let { w.canvas.drawText("Schlüssel-Fingerabdruck: ${Seals.fingerprint(it)}", signX, w.y + 36f, body) }
    w.y += 52f

    // Schlussvermerk auf der letzten Seite: So fällt eine fehlende letzte Seite auf
    w.text("Ende des Fahrtenbuchs – ${d.trips.size} ${if (d.trips.size == 1) "Fahrt" else "Fahrten"}", bold)
}

/** Hinweis auf Fahrzeuge mit Bearbeitungssperre – nur die, deren Fahrten exportiert werden */
private fun auditNotice(w: PdfWriter, d: ExportData) {
    val protected = d.usedVehicles.filter { it.auditProtected }
    if (protected.isEmpty()) return
    w.y += 6f
    w.text("✓ Bearbeitungssperre aktiv für:", green)
    protected.forEach { w.text("• ${it.label}", green, indent = 12f) }
}

/** Änderungsprotokoll je Fahrt; die Fahrt wird über Datum und Strecke (mit [numbered] auch die Nr.) eindeutig benannt */
private fun auditLog(w: PdfWriter, d: ExportData, numbered: Boolean = false) {
    if (!d.options.withAudit || d.auditLogs.isEmpty()) return
    w.y += 12f
    w.ensure(40f)
    w.text("Änderungsprotokoll", heading)
    d.trips.forEachIndexed { i, t ->
        val logs = d.auditLogs[t.id] ?: return@forEachIndexed
        w.y += 3f
        w.text((if (numbered) "Nr. ${i + 1} · " else "") + "Fahrt ${Export.tripLabel(t)}", bold)
        for (log in logs) {
            w.text(
                "${Fmt.dateTime(log.changedAt)}  ${log.fieldName}: „${log.oldValue ?: "-"}“ → „${log.newValue ?: "-"}“",
                small, indent = 12f
            )
        }
    }
}

/**
 * Anhang "Siegel": je Fahrt alle Siegel. Prüfbar ist nur das jüngste (gegen die heutigen Daten der Fahrt);
 * frühere gehören zu einem früheren Stand, den das Änderungsprotokoll zeigt.
 */
private fun sealAppendix(w: PdfWriter, d: ExportData) {
    if (d.seals.isEmpty()) return
    w.y += 12f
    w.ensure(60f)
    w.text("Anhang: Siegel", heading)
    w.text(
        "Ein Siegel ist ein SHA-256-Fingerabdruck der Fahrtdaten beim Beenden, Übernehmen, Ändern und Stornieren. Daten: ob die " +
            "heutigen Fahrtdaten noch zum jüngsten Siegel passen (ältere gehören zu einem früheren Stand). Signatur: Schlüssel, mit dem " +
            "gesiegelt wurde – „keine“, wenn beim Siegeln kein Schlüssel eingerichtet war. Zeitstempel: Zeitpunkt laut Dienst – er " +
            "belegt, dass es die Daten da schon so gab; ohne Zeitstempel erkennt ein Siegel nur versehentliche oder einfache " +
            "Änderungen, denn die Formel ist öffentlich. Alles prüft " +
            "die Prüfseite mit dieser PDF-Datei – bis auf Privatfahrten mit Notiz oder Geschäftspartner (diese Angaben stehen nicht in " +
            "den Prüfdaten) und Siegel früherer Stände: Die sind nur mit dem Backup prüfbar.",
        small
    )
    w.y += 2f
    val cw = floatArrayOf(30f, 60f, 80f, 190f, 110f, 70f)
    w.table(listOf("Nr.", "Art", "Gerätezeit", "Zeitstempel", "Signatur", "Daten"), cw) {
        d.trips.forEachIndexed { i, t ->
            val seals = d.seals[t.id] ?: return@forEachIndexed
            val latest = seals.maxBy { it.id }
            for (s in seals) {
                val current = s === latest && Seals.dataMatches(t, s)
                val data = if (s !== latest) "früherer Stand" else if (current) "unverändert" else "verändert"
                // Gültig/ungültig nur, wo sich die Signatur gegen die heutigen Daten prüfen lässt
                val signature = when {
                    s.signature == null -> "keine"
                    s.publicKey == null -> "ungültig"
                    !current -> Seals.fingerprint(s.publicKey).take(9)
                    else -> Seals.fingerprint(s.publicKey).take(9) + if (Seals.matches(t, s)) " gültig" else " ungültig"
                }
                w.row(listOf("${i + 1}", s.kind, Fmt.dateTime(Date(s.createdAt)), stamp(s), signature, data), cw, bg = if (i % 2 == 0) STRIPE else null)
            }
        }
    }
}

/** Zeit laut Zeitstempel und Rechnername des Dienstes, z.B. "05.02.2024 09:01 (freetsa.org)" */
private fun stamp(s: Seal): String {
    val time = s.tsTime ?: return "keiner"
    val host = s.tsUrl?.let { runCatching { URI(it).host }.getOrNull() ?: it }
    return Fmt.dateTime(Date(time)) + (host?.let { " ($it)" } ?: "")
}

/**
 * Zeile einer Fahrt; stornierte rot und durchgestrichen (bis auf die letzten [keep] Zellen: Storno, ggf. Siegel),
 * mit Stornogrund in einer eigenen Zeile darunter
 */
private fun PdfWriter.tripRow(t: Trip, index: Int, widths: FloatArray, cells: List<String>, keep: Int = 1) {
    val bg = if (index % 2 == 0) STRIPE else null
    row(cells, widths, if (t.isCancelled) red else body, bg, strike = if (t.isCancelled) cells.size - keep else 0)
    val reason = t.cancellationReason
    if (t.isCancelled && !reason.isNullOrBlank()) row(listOf("Stornogrund: $reason"), floatArrayOf(widths.sum()), red, bg)
}

/** Bricht [text] in Zeilen, die in [width] passen – an Leerzeichen, notfalls mitten im Wort */
private fun wrap(text: String, paint: Paint, width: Float): List<String> = text.trim().lines().flatMap { paragraph ->
    val lines = mutableListOf<String>()
    var rest = paragraph.trimEnd()
    while (true) {
        val n = paint.breakText(rest, true, width, null)
        if (n >= rest.length) {
            lines += rest
            break
        }
        val cut = rest.lastIndexOf(' ', n).takeIf { it > 0 } ?: maxOf(n, 1)
        lines += rest.substring(0, cut).trimEnd()
        rest = rest.substring(cut).trimStart()
    }
    lines
}

/**
 * Mehrseitiges PDF mit Seitenumbruch an jeder Stelle (auch auf dem Deckblatt):
 * Vor jedem Element prüft [ensure], ob es noch auf die Seite passt. [y] ist die Schreibposition.
 */
private class PdfWriter(private val footer: String, private val created: String, private val headerColor: Int) {
    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var pageNo = 0
    var y = 0f

    /** Wird nach jedem Seitenwechsel gezeichnet (wiederholter Tabellenkopf) */
    private var onNewPage: (() -> Unit)? = null

    val canvas: Canvas get() = page!!.canvas

    /** Beginnt eine neue Seite, falls [height] nicht mehr auf die aktuelle passt */
    fun ensure(height: Float) {
        if (page == null || y + height > BOTTOM) newPage()
    }

    /** Schließt die aktuelle Seite ab und beginnt eine neue (mit wiederholtem Tabellenkopf) */
    fun newPage() {
        finishPage()
        page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, ++pageNo).create())
        y = M
        onNewPage?.invoke()
    }

    /** Fußzeile (Programm und Seitenzahl links, Erstellungsdatum rechts) zeichnen und Seite abschließen */
    private fun finishPage() {
        val p = page ?: return
        val baseline = H - 14f
        p.canvas.drawText("$footer · Seite $pageNo", M, baseline, small)
        p.canvas.drawText(created, W - M - small.measureText(created), baseline, small)
        doc.finishPage(p)
        page = null
    }

    /** Fließtext; lange Texte werden umbrochen und laufen bei Bedarf auf der nächsten Seite weiter */
    fun text(s: String, paint: Paint = body, indent: Float = 0f) {
        for (line in wrap(s, paint, TEXT_W - indent)) {
            ensure(lineHeight(paint))
            canvas.drawText(line, M + indent, y + paint.textSize, paint)
            y += lineHeight(paint)
        }
    }

    /**
     * Tabellenzeile: Jede Zelle wird auf ihre Spaltenbreite umgebrochen, die Zeile ist so hoch wie die
     * längste Zelle. Genau diese Höhe zählt für den Seitenumbruch – eine Zeile wird nie zerteilt.
     * Die ersten [strike] Zellen werden durchgestrichen.
     */
    fun row(cells: List<String>, widths: FloatArray, paint: Paint = body, bg: Int? = null, strike: Int = 0) {
        val lines = cells.mapIndexed { i, c -> wrap(c, paint, widths[i] - 2 * PAD) }
        val height = lines.maxOf { it.size } * lineHeight(paint) + 2 * PAD
        ensure(height)
        val right = M + widths.sum()
        if (bg != null) canvas.drawRect(M, y, right, y + height, fill.apply { color = bg })
        var x = M
        lines.forEachIndexed { i, cellLines ->
            cellLines.forEachIndexed { j, line ->
                val baseline = y + PAD + paint.textSize + j * lineHeight(paint)
                canvas.drawText(line, x + PAD, baseline, paint)
                if (i < strike) {
                    val strikeY = baseline - paint.textSize / 3
                    canvas.drawLine(x + PAD, strikeY, x + PAD + paint.measureText(line), strikeY, paint)
                }
            }
            x += widths[i]
        }
        canvas.drawLine(M, y + height, right, y + height, grid)
        y += height
    }

    /** Tabelle: Kopfzeile jetzt und nach jedem Seitenwechsel innerhalb von [rows] erneut */
    fun table(headers: List<String>, widths: FloatArray, rows: () -> Unit) {
        val header = { row(headers, widths, headerText, headerColor) }
        ensure(48f) // Kopf nicht allein unten auf einer Seite stehen lassen
        header()
        onNewPage = header
        rows()
        onNewPage = null
    }

    /** Letzte Seite abschließen und das Dokument nach [out] schreiben */
    fun finish(out: OutputStream) {
        finishPage()
        doc.writeTo(out)
    }

    fun close() = doc.close()
}
