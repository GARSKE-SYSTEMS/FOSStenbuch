package de.fosstenbuch.domain.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.model.TripAuditLog
import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.model.Vehicle
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/**
 * Finanzamt-konformer PDF-Export gemäß § 6 Abs. 1 Nr. 4 EStG.
 *
 * Pflichtangaben je geschäftlicher Fahrt:
 * - Datum der Fahrt
 * - Abfahrtsort und Zielort
 * - Besuchter Geschäftspartner / Kunde
 * - Reisezweck
 * - Kilometerstand bei Beginn und Ende
 * - Gefahrene Kilometer
 * - Gefahrene Route (bei Umwegen)
 *
 * Zusätzlich:
 * - Monatliche Zusammenfassungen
 * - Jahresübersicht (Geschäftlich / Privat)
 * - Stornierte Einträge werden durchgestrichen, aber nicht entfernt
 * - Änderungsprotokoll (Audit-Log)
 * - Richtigkeitserklärung mit Unterschriftsfeld
 */
class FinanzamtPdfTripExporter @Inject constructor(
    @ApplicationContext private val context: Context
) : TripExporter {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
    private val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)

    companion object {
        private const val PAGE_WIDTH = 842  // A4 landscape
        private const val PAGE_HEIGHT = 595
        private const val MARGIN_LEFT = 36f
        private const val MARGIN_RIGHT = 36f
        private const val MARGIN_TOP = 36f
        private const val MARGIN_BOTTOM = 40f
        private const val LINE_HEIGHT = 14f
        private const val HEADER_HEIGHT = 18f
        private const val FONT_SIZE_TITLE = 14f
        private const val FONT_SIZE_SUBTITLE = 10f
        private const val FONT_SIZE_HEADER = 7.5f
        private const val FONT_SIZE_BODY = 7f
        private const val FONT_SIZE_SMALL = 6f
        private const val USABLE_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT
    }

    // Paints
    private val titlePaint = Paint().apply {
        color = Color.BLACK; textSize = FONT_SIZE_TITLE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
    }
    private val subtitlePaint = Paint().apply {
        color = Color.DKGRAY; textSize = FONT_SIZE_SUBTITLE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
    }
    private val headerPaint = Paint().apply {
        color = Color.WHITE; textSize = FONT_SIZE_HEADER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
    }
    private val bodyPaint = Paint().apply {
        color = Color.BLACK; textSize = FONT_SIZE_BODY; isAntiAlias = true
    }
    private val boldBodyPaint = Paint().apply {
        color = Color.BLACK; textSize = FONT_SIZE_BODY
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
    }
    private val smallPaint = Paint().apply {
        color = Color.DKGRAY; textSize = FONT_SIZE_SMALL; isAntiAlias = true
    }
    private val cancelledPaint = Paint().apply {
        color = Color.RED; textSize = FONT_SIZE_BODY; isAntiAlias = true
    }
    private val headerBgPaint = Paint().apply {
        color = Color.parseColor("#37474F"); style = Paint.Style.FILL
    }
    private val rowBgPaint = Paint().apply {
        color = Color.parseColor("#F5F5F5"); style = Paint.Style.FILL
    }
    private val summaryBgPaint = Paint().apply {
        color = Color.parseColor("#E8EAF6"); style = Paint.Style.FILL
    }
    private val linePaint = Paint().apply {
        color = Color.LTGRAY; strokeWidth = 0.5f
    }
    private val strikethroughPaint = Paint().apply {
        color = Color.RED; strokeWidth = 0.8f
    }
    private val legalPaint = Paint().apply {
        color = Color.parseColor("#424242"); textSize = FONT_SIZE_SMALL
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC); isAntiAlias = true
    }

    // Column widths for Finanzamt table (total ≈ USABLE_WIDTH = 770)
    // Nr | Datum | Start | Ende | Startort | Zielort | Geschäftspartner | Zweck | Route | Kategorie | Km-Start | Km-Ende | Distanz | Storno
    private val colWidths = floatArrayOf(
        24f,   // Nr.
        56f,   // Datum
        46f,   // Startzeit
        46f,   // Endzeit
        66f,   // Startort
        66f,   // Zielort
        78f,   // Geschäftspartner
        66f,   // Zweck
        56f,   // Route
        52f,   // Kategorie
        42f,   // Km-Start
        42f,   // Km-Ende
        42f,   // Distanz
        28f    // Storno
    )

    override suspend fun export(
        config: ExportConfig,
        trips: List<Trip>,
        purposes: Map<Long, TripPurpose>,
        vehicles: Map<Long, Vehicle>,
        auditLogs: Map<Long, List<TripAuditLog>>
    ): File {
        val fromStr = config.dateFrom.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val toStr = config.dateTo.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val fileName = "fahrtenbuch_finanzamt_${fromStr}_${toStr}.pdf"
        val file = File(context.cacheDir, fileName)

        val document = PdfDocument()
        var pageNumber = 1
        var currentPage: PdfDocument.Page
        var canvas: Canvas

        fun startNewPage(): Pair<PdfDocument.Page, Canvas> {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create()
            val page = document.startPage(pageInfo)
            return page to page.canvas
        }

        fun drawFooter(c: Canvas) {
            val footerY = PAGE_HEIGHT - 12f
            c.drawText(
                "FOSStenbuch — Fahrtenbuch gem. § 6 Abs. 1 Nr. 4 EStG",
                MARGIN_LEFT, footerY, smallPaint
            )
            val pageText = "Seite ${pageNumber - 1}"
            c.drawText(
                pageText,
                PAGE_WIDTH - MARGIN_RIGHT - smallPaint.measureText(pageText),
                footerY, smallPaint
            )
            val dateLine = "Erstellt: ${config.dateTo.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}"
            c.drawText(
                dateLine,
                PAGE_WIDTH / 2f - smallPaint.measureText(dateLine) / 2f,
                footerY, smallPaint
            )
        }

        fun finishPage(page: PdfDocument.Page, c: Canvas) {
            drawFooter(c)
            document.finishPage(page)
        }

        // ========== Page 1: COVER / SUMMARY ==========
        val (firstPage, firstCanvas) = startNewPage()
        currentPage = firstPage
        canvas = firstCanvas
        var yPos = MARGIN_TOP

        // Title
        canvas.drawText("Fahrtenbuch", MARGIN_LEFT, yPos + FONT_SIZE_TITLE, titlePaint)
        yPos += FONT_SIZE_TITLE + 4f
        canvas.drawText(
            "gemäß § 6 Abs. 1 Nr. 4 Satz 3 EStG",
            MARGIN_LEFT, yPos + FONT_SIZE_SMALL, legalPaint
        )
        yPos += FONT_SIZE_SMALL + 16f

        // Meta information box
        val metaBoxTop = yPos
        val metaLineHeight = LINE_HEIGHT + 2f

        if (config.companyName.isNotBlank()) {
            canvas.drawText("Firma/Betrieb:", MARGIN_LEFT, yPos + FONT_SIZE_BODY, boldBodyPaint)
            canvas.drawText(config.companyName, MARGIN_LEFT + 120f, yPos + FONT_SIZE_BODY, bodyPaint)
            yPos += metaLineHeight
        }
        if (config.driverName.isNotBlank()) {
            canvas.drawText("Fahrer/in:", MARGIN_LEFT, yPos + FONT_SIZE_BODY, boldBodyPaint)
            canvas.drawText(config.driverName, MARGIN_LEFT + 120f, yPos + FONT_SIZE_BODY, bodyPaint)
            yPos += metaLineHeight
        }

        val rangeText = "${config.dateFrom.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))} – " +
            config.dateTo.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        canvas.drawText("Zeitraum:", MARGIN_LEFT, yPos + FONT_SIZE_BODY, boldBodyPaint)
        canvas.drawText(rangeText, MARGIN_LEFT + 120f, yPos + FONT_SIZE_BODY, bodyPaint)
        yPos += metaLineHeight

        // Vehicle information
        val selectedVehicle = config.vehicleId?.let { vehicles[it] }
        if (selectedVehicle != null) {
            canvas.drawText("Fahrzeug:", MARGIN_LEFT, yPos + FONT_SIZE_BODY, boldBodyPaint)
            canvas.drawText(
                "${selectedVehicle.make} ${selectedVehicle.model} (${selectedVehicle.licensePlate})",
                MARGIN_LEFT + 120f, yPos + FONT_SIZE_BODY, bodyPaint
            )
            yPos += metaLineHeight
            canvas.drawText("Kraftstoff:", MARGIN_LEFT, yPos + FONT_SIZE_BODY, boldBodyPaint)
            canvas.drawText(selectedVehicle.fuelType, MARGIN_LEFT + 120f, yPos + FONT_SIZE_BODY, bodyPaint)
            yPos += metaLineHeight
        } else {
            // List all vehicles used
            val usedVehicleIds = trips.mapNotNull { it.vehicleId }.toSet()
            val usedVehicles = usedVehicleIds.mapNotNull { vehicles[it] }
            if (usedVehicles.isNotEmpty()) {
                canvas.drawText("Fahrzeuge:", MARGIN_LEFT, yPos + FONT_SIZE_BODY, boldBodyPaint)
                yPos += metaLineHeight
                for (v in usedVehicles) {
                    canvas.drawText(
                        "• ${v.make} ${v.model} (${v.licensePlate}) — ${v.fuelType}",
                        MARGIN_LEFT + 16f, yPos + FONT_SIZE_BODY, bodyPaint
                    )
                    yPos += metaLineHeight
                }
            }
        }

        // Draw border around meta box
        val metaBorderPaint = Paint().apply { color = Color.DKGRAY; style = Paint.Style.STROKE; strokeWidth = 0.5f }
        canvas.drawRect(MARGIN_LEFT - 4f, metaBoxTop - 6f, PAGE_WIDTH - MARGIN_RIGHT, yPos + 4f, metaBorderPaint)
        yPos += 20f

        // ========== YEARLY SUMMARY ==========
        canvas.drawText("Jahresübersicht", MARGIN_LEFT, yPos + FONT_SIZE_SUBTITLE, subtitlePaint)
        yPos += FONT_SIZE_SUBTITLE + 8f

        val activeTrips = trips.filter { !it.isCancelled }
        val totalDist = activeTrips.sumOf { it.distanceKm }
        val businessTrips = activeTrips.filter { t ->
            t.purposeId?.let { purposes[it]?.isBusinessRelevant } == true
        }
        val businessDist = businessTrips.sumOf { it.distanceKm }
        val privateDist = totalDist - businessDist
        val businessPct = if (totalDist > 0) (businessDist / totalDist * 100) else 0.0
        val privatePct = if (totalDist > 0) (privateDist / totalDist * 100) else 0.0

        // Summary table
        val summaryHeaders = arrayOf("", "Anzahl Fahrten", "Kilometer", "Anteil")
        val summaryData = arrayOf(
            arrayOf("Geschäftlich", "${businessTrips.size}", "${"%.1f".format(businessDist)} km", "${"%.1f".format(businessPct)} %"),
            arrayOf("Privat", "${activeTrips.size - businessTrips.size}", "${"%.1f".format(privateDist)} km", "${"%.1f".format(privatePct)} %"),
            arrayOf("Gesamt", "${activeTrips.size}", "${"%.1f".format(totalDist)} km", "100,0 %")
        )
        val summaryColWidths = floatArrayOf(120f, 100f, 100f, 80f)

        // Summary header row
        canvas.drawRect(MARGIN_LEFT, yPos, MARGIN_LEFT + summaryColWidths.sum(), yPos + HEADER_HEIGHT, headerBgPaint)
        var xPos = MARGIN_LEFT + 4f
        for (i in summaryHeaders.indices) {
            canvas.drawText(summaryHeaders[i], xPos, yPos + HEADER_HEIGHT - 5f, headerPaint)
            xPos += summaryColWidths[i]
        }
        yPos += HEADER_HEIGHT

        for ((idx, row) in summaryData.withIndex()) {
            val bgPaint = if (idx == summaryData.size - 1) summaryBgPaint else if (idx % 2 == 0) rowBgPaint else null
            bgPaint?.let {
                canvas.drawRect(MARGIN_LEFT, yPos, MARGIN_LEFT + summaryColWidths.sum(), yPos + LINE_HEIGHT, it)
            }
            xPos = MARGIN_LEFT + 4f
            val paint = if (idx == summaryData.size - 1) boldBodyPaint else bodyPaint
            for (i in row.indices) {
                canvas.drawText(row[i], xPos, yPos + LINE_HEIGHT - 4f, paint)
                xPos += summaryColWidths[i]
            }
            canvas.drawLine(MARGIN_LEFT, yPos + LINE_HEIGHT, MARGIN_LEFT + summaryColWidths.sum(), yPos + LINE_HEIGHT, linePaint)
            yPos += LINE_HEIGHT
        }
        yPos += 16f

        // ========== MONTHLY SUMMARY ==========
        canvas.drawText("Monatliche Zusammenfassung", MARGIN_LEFT, yPos + FONT_SIZE_SUBTITLE, subtitlePaint)
        yPos += FONT_SIZE_SUBTITLE + 8f

        val monthNames = arrayOf("Januar", "Februar", "März", "April", "Mai", "Juni",
            "Juli", "August", "September", "Oktober", "November", "Dezember")

        val tripsByMonth = activeTrips.groupBy { trip ->
            val cal = Calendar.getInstance()
            cal.time = trip.date
            cal.get(Calendar.MONTH)
        }

        val monthHeaders = arrayOf("Monat", "Fahrten", "Geschäftl. km", "Privat km", "Gesamt km")
        val monthColWidths = floatArrayOf(100f, 60f, 90f, 90f, 90f)

        canvas.drawRect(MARGIN_LEFT, yPos, MARGIN_LEFT + monthColWidths.sum(), yPos + HEADER_HEIGHT, headerBgPaint)
        xPos = MARGIN_LEFT + 4f
        for (i in monthHeaders.indices) {
            canvas.drawText(monthHeaders[i], xPos, yPos + HEADER_HEIGHT - 5f, headerPaint)
            xPos += monthColWidths[i]
        }
        yPos += HEADER_HEIGHT

        var rowIdx = 0
        for (month in 0..11) {
            val monthTrips = tripsByMonth[month] ?: continue
            val mTotal = monthTrips.sumOf { it.distanceKm }
            val mBusiness = monthTrips.filter { t ->
                t.purposeId?.let { purposes[it]?.isBusinessRelevant } == true
            }.sumOf { it.distanceKm }
            val mPrivate = mTotal - mBusiness

            if (rowIdx % 2 == 0) {
                canvas.drawRect(MARGIN_LEFT, yPos, MARGIN_LEFT + monthColWidths.sum(), yPos + LINE_HEIGHT, rowBgPaint)
            }

            val monthRow = arrayOf(
                monthNames[month],
                "${monthTrips.size}",
                "${"%.1f".format(mBusiness)} km",
                "${"%.1f".format(mPrivate)} km",
                "${"%.1f".format(mTotal)} km"
            )
            xPos = MARGIN_LEFT + 4f
            for (i in monthRow.indices) {
                canvas.drawText(monthRow[i], xPos, yPos + LINE_HEIGHT - 4f, bodyPaint)
                xPos += monthColWidths[i]
            }
            canvas.drawLine(MARGIN_LEFT, yPos + LINE_HEIGHT, MARGIN_LEFT + monthColWidths.sum(), yPos + LINE_HEIGHT, linePaint)
            yPos += LINE_HEIGHT
            rowIdx++
        }
        yPos += 16f

        // Audit protection notice
        val auditProtectedVehicles = vehicles.values.filter { it.auditProtected }
        if (auditProtectedVehicles.isNotEmpty()) {
            val noticePaint = Paint(bodyPaint).apply {
                color = Color.parseColor("#1B5E20")
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }
            canvas.drawText(
                "✓ Änderungssichere Protokollierung aktiv für:",
                MARGIN_LEFT, yPos + FONT_SIZE_BODY, noticePaint
            )
            yPos += LINE_HEIGHT
            for (v in auditProtectedVehicles) {
                canvas.drawText(
                    "    • ${v.make} ${v.model} (${v.licensePlate})",
                    MARGIN_LEFT, yPos + FONT_SIZE_BODY, noticePaint
                )
                yPos += LINE_HEIGHT
            }
            yPos += 8f
        }

        // Completeness warnings
        val businessTripsNoPartner = businessTrips.count { it.businessPartner.isNullOrBlank() }
        val tripsNoOdometer = activeTrips.count { it.startOdometer == null || it.endOdometer == null }
        if (businessTripsNoPartner > 0 || tripsNoOdometer > 0) {
            val warnPaint = Paint(bodyPaint).apply {
                color = Color.parseColor("#E65100")
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }
            if (businessTripsNoPartner > 0) {
                canvas.drawText(
                    "⚠ $businessTripsNoPartner geschäftliche Fahrt(en) ohne Geschäftspartner — ggf. nachtragen.",
                    MARGIN_LEFT, yPos + FONT_SIZE_BODY, warnPaint
                )
                yPos += LINE_HEIGHT
            }
            if (tripsNoOdometer > 0) {
                canvas.drawText(
                    "⚠ $tripsNoOdometer Fahrt(en) ohne lückenlose Km-Stände.",
                    MARGIN_LEFT, yPos + FONT_SIZE_BODY, warnPaint
                )
                yPos += LINE_HEIGHT
            }
        }

        finishPage(currentPage, canvas)

        // ========== TRIP DETAIL PAGES ==========
        val headers = arrayOf(
            "Nr.", "Datum", "Start", "Ende", "Startort", "Zielort",
            "Geschäftspartner", "Zweck", "Route", "Kategorie",
            "Km-Start", "Km-Ende", "Distanz", "Storno"
        )

        val (detailPage, detailCanvas) = startNewPage()
        currentPage = detailPage
        canvas = detailCanvas
        yPos = MARGIN_TOP

        canvas.drawText("Einzelnachweise", MARGIN_LEFT, yPos + FONT_SIZE_SUBTITLE, subtitlePaint)
        yPos += FONT_SIZE_SUBTITLE + 8f

        yPos = drawTableHeader(canvas, yPos, headers)

        // Track current month for monthly subtotals
        var currentMonth = -1
        var monthBusinessKm = 0.0
        var monthPrivateKm = 0.0
        var monthTripCount = 0

        for ((index, trip) in trips.withIndex()) {
            val cal = Calendar.getInstance()
            cal.time = trip.date
            val tripMonth = cal.get(Calendar.MONTH)

            // Monthly subtotal when month changes
            if (currentMonth >= 0 && tripMonth != currentMonth && monthTripCount > 0) {
                yPos = drawMonthSubtotal(
                    canvas, yPos, monthNames[currentMonth],
                    monthTripCount, monthBusinessKm, monthPrivateKm,
                    currentPage, document, headers, ::startNewPage, ::finishPage
                ).also {
                    if (it.second != null) {
                        currentPage = it.second!!.first
                        canvas = it.second!!.second
                    }
                }.first
                monthBusinessKm = 0.0
                monthPrivateKm = 0.0
                monthTripCount = 0
            }
            currentMonth = tripMonth

            // Check page overflow
            val neededHeight = LINE_HEIGHT + if (trip.isCancelled && !trip.cancellationReason.isNullOrBlank()) LINE_HEIGHT else 0f
            if (yPos + neededHeight > PAGE_HEIGHT - MARGIN_BOTTOM - 20f) {
                finishPage(currentPage, canvas)
                val (newPage, newCanvas) = startNewPage()
                currentPage = newPage
                canvas = newCanvas
                yPos = MARGIN_TOP
                yPos = drawTableHeader(canvas, yPos, headers)
            }

            val purpose = trip.purposeId?.let { purposes[it] }
            @Suppress("UNUSED_VARIABLE")
            val vehicle = trip.vehicleId?.let { vehicles[it] }
            val isBusiness = purpose?.isBusinessRelevant == true

            // Track monthly stats (only non-cancelled)
            if (!trip.isCancelled) {
                monthTripCount++
                if (isBusiness) monthBusinessKm += trip.distanceKm
                else monthPrivateKm += trip.distanceKm
            }

            // Alternating row background
            if (index % 2 == 0) {
                canvas.drawRect(MARGIN_LEFT, yPos, PAGE_WIDTH - MARGIN_RIGHT, yPos + LINE_HEIGHT, rowBgPaint)
            }

            val rowData = arrayOf(
                "${index + 1}",
                dateFormat.format(trip.date),
                trip.date.let { dateTimeFormat.format(it).substringAfter(" ") },
                trip.endTime?.let { dateTimeFormat.format(it).substringAfter(" ") } ?: "-",
                truncate(trip.startLocation, 10),
                truncate(trip.endLocation, 10),
                if (isBusiness) truncate(trip.businessPartner ?: "", 12) else "-",
                truncate(trip.purpose, 10),
                truncate(trip.route ?: "", 8),
                purpose?.name ?: "-",
                trip.startOdometer?.toString() ?: "-",
                trip.endOdometer?.toString() ?: "-",
                "${"%.1f".format(trip.distanceKm)} km",
                if (trip.isCancelled) "Ja" else ""
            )

            xPos = MARGIN_LEFT + 2f
            for (i in rowData.indices) {
                val paint = if (trip.isCancelled && i < rowData.size - 1) cancelledPaint else bodyPaint
                canvas.drawText(rowData[i], xPos, yPos + LINE_HEIGHT - 3f, paint)
                xPos += colWidths[i]
            }

            // Strikethrough for cancelled trips
            if (trip.isCancelled) {
                val lineY = yPos + LINE_HEIGHT / 2f
                canvas.drawLine(MARGIN_LEFT + 2f, lineY, xPos - 2f, lineY, strikethroughPaint)
            }

            canvas.drawLine(MARGIN_LEFT, yPos + LINE_HEIGHT, PAGE_WIDTH - MARGIN_RIGHT, yPos + LINE_HEIGHT, linePaint)
            yPos += LINE_HEIGHT

            // Cancellation reason sub-row
            if (trip.isCancelled && !trip.cancellationReason.isNullOrBlank()) {
                if (yPos + LINE_HEIGHT > PAGE_HEIGHT - MARGIN_BOTTOM - 20f) {
                    finishPage(currentPage, canvas)
                    val (newPage, newCanvas) = startNewPage()
                    currentPage = newPage
                    canvas = newCanvas
                    yPos = MARGIN_TOP
                    yPos = drawTableHeader(canvas, yPos, headers)
                }
                canvas.drawText(
                    "    Stornogrund: ${trip.cancellationReason}",
                    MARGIN_LEFT + 4f, yPos + LINE_HEIGHT - 3f, cancelledPaint
                )
                canvas.drawLine(MARGIN_LEFT, yPos + LINE_HEIGHT, PAGE_WIDTH - MARGIN_RIGHT, yPos + LINE_HEIGHT, linePaint)
                yPos += LINE_HEIGHT
            }
        }

        // Final monthly subtotal
        if (currentMonth >= 0 && monthTripCount > 0) {
            val result = drawMonthSubtotal(
                canvas, yPos, monthNames[currentMonth],
                monthTripCount, monthBusinessKm, monthPrivateKm,
                currentPage, document, headers, ::startNewPage, ::finishPage
            )
            yPos = result.first
            if (result.second != null) {
                currentPage = result.second!!.first
                canvas = result.second!!.second
            }
        }

        // ========== AUDIT LOG ==========
        if (config.includeAuditLog && auditLogs.isNotEmpty()) {
            yPos += LINE_HEIGHT

            if (yPos + 60f > PAGE_HEIGHT - MARGIN_BOTTOM) {
                finishPage(currentPage, canvas)
                val (newPage, newCanvas) = startNewPage()
                currentPage = newPage
                canvas = newCanvas
                yPos = MARGIN_TOP
            }

            canvas.drawText("Änderungsprotokoll", MARGIN_LEFT, yPos + FONT_SIZE_SUBTITLE, subtitlePaint)
            yPos += FONT_SIZE_SUBTITLE + 8f

            for ((tripId, logs) in auditLogs) {
                for (log in logs) {
                    if (yPos + LINE_HEIGHT > PAGE_HEIGHT - MARGIN_BOTTOM - 20f) {
                        finishPage(currentPage, canvas)
                        val (newPage, newCanvas) = startNewPage()
                        currentPage = newPage
                        canvas = newCanvas
                        yPos = MARGIN_TOP
                    }

                    val logText = "Fahrt #$tripId | ${log.fieldName}: " +
                        "\"${log.oldValue ?: "-"}\" → \"${log.newValue ?: "-"}\" " +
                        "(${dateFormat.format(log.changedAt)})"
                    canvas.drawText(logText, MARGIN_LEFT, yPos + FONT_SIZE_BODY, smallPaint)
                    yPos += LINE_HEIGHT
                }
            }
        }

        // ========== SIGNATURE / TRUTHFULNESS SECTION ==========
        if (config.truthfulnessConfirmed) {
            yPos += LINE_HEIGHT * 2

            if (yPos + 80f > PAGE_HEIGHT - MARGIN_BOTTOM) {
                finishPage(currentPage, canvas)
                val (newPage, newCanvas) = startNewPage()
                currentPage = newPage
                canvas = newCanvas
                yPos = MARGIN_TOP
            }

            // Legal notice
            canvas.drawLine(MARGIN_LEFT, yPos, PAGE_WIDTH - MARGIN_RIGHT, yPos, linePaint)
            yPos += 12f

            canvas.drawText(
                "Richtigkeitserklärung",
                MARGIN_LEFT, yPos + FONT_SIZE_SUBTITLE, subtitlePaint
            )
            yPos += FONT_SIZE_SUBTITLE + 8f

            canvas.drawText(
                "Ich versichere hiermit, dass alle Angaben in diesem Fahrtenbuch vollständig und wahrheitsgemäß sind.",
                MARGIN_LEFT, yPos + FONT_SIZE_BODY, legalPaint
            )
            yPos += LINE_HEIGHT
            canvas.drawText(
                "Die Eintragungen wurden zeitnah und fortlaufend vorgenommen.",
                MARGIN_LEFT, yPos + FONT_SIZE_BODY, legalPaint
            )
            yPos += LINE_HEIGHT * 3

            // Signature lines
            val signLineWidth = 200f
            val signLinePaint = Paint().apply { color = Color.BLACK; strokeWidth = 0.5f }

            // Date
            canvas.drawLine(MARGIN_LEFT, yPos, MARGIN_LEFT + signLineWidth, yPos, signLinePaint)
            canvas.drawText("Datum", MARGIN_LEFT, yPos + 12f, smallPaint)

            // Signature
            val signX = MARGIN_LEFT + signLineWidth + 80f
            canvas.drawLine(signX, yPos, signX + signLineWidth, yPos, signLinePaint)
            canvas.drawText("Unterschrift", signX, yPos + 12f, smallPaint)

            yPos += 20f

            if (config.driverName.isNotBlank()) {
                canvas.drawText(
                    "Name: ${config.driverName}",
                    signX, yPos + FONT_SIZE_BODY, bodyPaint
                )
            }
        }

        finishPage(currentPage, canvas)

        // Write PDF
        FileOutputStream(file).use { fos ->
            document.writeTo(fos)
        }
        document.close()

        return file
    }

    /**
     * Draws a monthly subtotal row. Returns updated yPos and optionally a new page/canvas pair
     * if a page break was needed.
     */
    private fun drawMonthSubtotal(
        canvas: Canvas,
        yStart: Float,
        monthName: String,
        tripCount: Int,
        businessKm: Double,
        privateKm: Double,
        currentPage: PdfDocument.Page,
        @Suppress("UNUSED_PARAMETER") document: PdfDocument,
        headers: Array<String>,
        startNewPage: () -> Pair<PdfDocument.Page, Canvas>,
        finishPage: (PdfDocument.Page, Canvas) -> Unit
    ): Pair<Float, Pair<PdfDocument.Page, Canvas>?> {
        var yPos = yStart
        var newPagePair: Pair<PdfDocument.Page, Canvas>? = null
        var activeCanvas = canvas
        var activePage = currentPage

        if (yPos + LINE_HEIGHT + 4f > PAGE_HEIGHT - MARGIN_BOTTOM - 20f) {
            finishPage(activePage, activeCanvas)
            val (np, nc) = startNewPage()
            activePage = np
            activeCanvas = nc
            newPagePair = np to nc
            yPos = MARGIN_TOP
            yPos = drawTableHeader(activeCanvas, yPos, headers)
        }

        // Summary row with background
        activeCanvas.drawRect(MARGIN_LEFT, yPos, PAGE_WIDTH - MARGIN_RIGHT, yPos + LINE_HEIGHT, summaryBgPaint)
        val totalKm = businessKm + privateKm
        val summaryText = "∑ $monthName: $tripCount Fahrten | " +
            "Geschäftl.: ${"%.1f".format(businessKm)} km | " +
            "Privat: ${"%.1f".format(privateKm)} km | " +
            "Gesamt: ${"%.1f".format(totalKm)} km"
        activeCanvas.drawText(summaryText, MARGIN_LEFT + 4f, yPos + LINE_HEIGHT - 3f, boldBodyPaint)
        activeCanvas.drawLine(MARGIN_LEFT, yPos + LINE_HEIGHT, PAGE_WIDTH - MARGIN_RIGHT, yPos + LINE_HEIGHT, linePaint)
        yPos += LINE_HEIGHT + 4f

        return yPos to newPagePair
    }

    private fun drawTableHeader(canvas: Canvas, yStart: Float, headers: Array<String>): Float {
        canvas.drawRect(MARGIN_LEFT, yStart, PAGE_WIDTH - MARGIN_RIGHT, yStart + HEADER_HEIGHT, headerBgPaint)

        var xPos = MARGIN_LEFT + 2f
        for (i in headers.indices) {
            canvas.drawText(headers[i], xPos, yStart + HEADER_HEIGHT - 5f, headerPaint)
            xPos += colWidths[i]
        }

        return yStart + HEADER_HEIGHT
    }

    private fun truncate(text: String, maxLen: Int): String {
        return if (text.length > maxLen) text.take(maxLen - 1) + "…" else text
    }
}
