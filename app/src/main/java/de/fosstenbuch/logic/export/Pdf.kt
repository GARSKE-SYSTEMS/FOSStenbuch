package de.fosstenbuch.logic.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import de.fosstenbuch.data.Trip
import de.fosstenbuch.ui.common.Fmt
import java.io.OutputStream
import java.util.Locale

// PDF-Ausgabe für "PDF" und "PDF (Finanzamt)": A4 quer, Maße in Punkt (1/72 Zoll).
// Alle Texte werden umbrochen statt gekürzt – Ort, Zweck, Geschäftspartner und Route sind
// gesetzlich geforderte Angaben (§ 6 Abs. 1 Nr. 4 EStG) und dürfen nicht abgeschnitten werden.

private const val W = 842
private const val H = 595
private const val M = 36f              // Rand links, rechts und oben
private const val BOTTOM = H - 40f     // darunter steht nur noch die Fußzeile
private const val TEXT_W = W - 2 * M
private const val PAD = 2.5f           // Innenabstand der Tabellenzellen

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
    val finanzamt = d.options.format == Format.FINANZAMT_PDF
    val w = PdfWriter(
        footer = if (finanzamt) "FOSStenbuch — Fahrtenbuch gem. § 6 Abs. 1 Nr. 4 EStG" else "FOSStenbuch",
        created = "Erstellt: ${Export.day(d.today)}",
        headerColor = if (finanzamt) Color.rgb(0x37, 0x47, 0x4F) else Color.rgb(0x62, 0x00, 0xEE)
    )
    try {
        if (finanzamt) finanzamt(w, d) else standard(w, d)
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
        w.text(Export.day(d.today), small)
    }
}

/**
 * Finanzamt-PDF (§ 6 Abs. 1 Nr. 4 EStG): Deckblatt mit Stammdaten, Übersicht, Monatssummen und
 * Hinweisen; danach Einzelnachweise mit Monats-Zwischensummen, Änderungsprotokoll und Unterschrift.
 */
private fun finanzamt(w: PdfWriter, d: ExportData) {
    val s = d.summary()
    val months = Export.months(d.trips)
    w.text("Fahrtenbuch", title)
    w.text("gemäß § 6 Abs. 1 Nr. 4 Satz 3 EStG", italic)
    w.y += 10f

    // Stammdaten: Bezeichnung links, Wert daneben
    val meta = floatArrayOf(120f, TEXT_W - 120f)
    if (d.options.company.isNotEmpty()) w.row(listOf("Firma/Betrieb:", d.options.company), meta)
    if (d.driver.isNotEmpty()) w.row(listOf("Fahrer/in:", d.driver), meta)
    w.row(listOf("Zeitraum:", Export.range(d.options)), meta)
    val vehicles = d.usedVehicles
    if (vehicles.size == 1) {
        w.row(listOf("Fahrzeug:", vehicles[0].label), meta)
        w.row(listOf("Kraftstoff:", vehicles[0].fuelType), meta)
    } else {
        vehicles.forEachIndexed { i, v -> w.row(listOf(if (i == 0) "Fahrzeuge:" else "", "• ${v.label} — ${v.fuelType}"), meta) }
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

    auditNotice(w, d)
    val noPartner = d.trips.count { !it.isCancelled && d.isBusiness(it) && it.businessPartner.isNullOrBlank() }
    val gaps = Export.odometerGaps(d.trips)
    if (noPartner > 0 || gaps > 0) w.y += 6f
    if (noPartner > 0) w.text("⚠ $noPartner geschäftliche Fahrt(en) ohne Geschäftspartner — ggf. nachtragen.", orange)
    if (gaps > 0) w.text("⚠ $gaps Fahrt(en) ohne lückenlose Km-Stände (Stand fehlt oder Lücke zur vorherigen Fahrt).", orange)

    // Einzelnachweise immer ab einer neuen Seite
    w.newPage()
    w.text("Einzelnachweise", heading)
    w.y += 2f
    val cw = floatArrayOf(22f, 44f, 28f, 28f, 54f, 81f, 81f, 81f, 81f, 56f, 56f, 40f, 40f, 46f, 32f)
    val headers = listOf("Nr.", "Datum", "Start", "Ende", "Kennzeichen", "Startort", "Zielort", "Geschäftspartner",
        "Zweck", "Route", "Kategorie", "Km-Start", "Km-Ende", "Distanz", "Storno")
    w.table(headers, cw) {
        var nr = 0
        for ((month, list) in months) {
            for (t in list) {
                nr++
                w.tripRow(
                    t, nr, cw, listOf(
                        "$nr", Fmt.date(t.date), Fmt.time(t.date), t.endTime?.let(Fmt::time) ?: "-",
                        d.vehicle(t)?.licensePlate ?: "-", t.startLocation, t.endLocation,
                        if (d.isBusiness(t)) t.businessPartner.orEmpty() else "-", t.purpose, t.route.orEmpty(),
                        d.purpose(t)?.name ?: "-", odo(t.startOdometer), odo(t.endOdometer), Fmt.km(t.distanceKm),
                        if (t.isCancelled) "Ja" else ""
                    )
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
    auditLog(w, d)

    // Richtigkeitserklärung als Block zusammenhalten
    w.y += 20f
    w.ensure(110f)
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
}

/** Hinweis auf änderungssicher geführte Fahrzeuge – nur die, deren Fahrten exportiert werden */
private fun auditNotice(w: PdfWriter, d: ExportData) {
    val protected = d.usedVehicles.filter { it.auditProtected }
    if (protected.isEmpty()) return
    w.y += 6f
    w.text("✓ Änderungssichere Protokollierung aktiv für:", green)
    protected.forEach { w.text("• ${it.label}", green, indent = 12f) }
}

/** Änderungsprotokoll je Fahrt; die Fahrt wird über Datum und Strecke eindeutig benannt */
private fun auditLog(w: PdfWriter, d: ExportData) {
    if (d.auditLogs.isEmpty()) return
    w.y += 12f
    w.ensure(40f)
    w.text("Änderungsprotokoll", heading)
    for (t in d.trips) {
        val logs = d.auditLogs[t.id] ?: continue
        w.y += 3f
        w.text("Fahrt ${Export.tripLabel(t)}", bold)
        for (log in logs) {
            w.text(
                "${Fmt.dateTime(log.changedAt)}  ${log.fieldName}: „${log.oldValue ?: "-"}“ → „${log.newValue ?: "-"}“",
                small, indent = 12f
            )
        }
    }
}

/** Zeile einer Fahrt; stornierte rot und durchgestrichen, mit Stornogrund in einer eigenen Zeile darunter */
private fun PdfWriter.tripRow(t: Trip, index: Int, widths: FloatArray, cells: List<String>) {
    val bg = if (index % 2 == 0) STRIPE else null
    row(cells, widths, if (t.isCancelled) red else body, bg, strike = t.isCancelled)
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

    fun newPage() {
        finishPage()
        page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, ++pageNo).create())
        y = M
        onNewPage?.invoke()
    }

    private fun finishPage() {
        val p = page ?: return
        val baseline = H - 14f
        p.canvas.drawText(footer, M, baseline, small)
        p.canvas.drawText(created, W / 2f - small.measureText(created) / 2f, baseline, small)
        val number = "Seite $pageNo"
        p.canvas.drawText(number, W - M - small.measureText(number), baseline, small)
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
     * [strike] streicht alle Zellen außer der letzten (Storno-Spalte) durch.
     */
    fun row(cells: List<String>, widths: FloatArray, paint: Paint = body, bg: Int? = null, strike: Boolean = false) {
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
                if (strike && i < cells.lastIndex) {
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

    fun finish(out: OutputStream) {
        finishPage()
        doc.writeTo(out)
    }

    fun close() = doc.close()
}
