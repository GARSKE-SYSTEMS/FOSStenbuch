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
import java.util.Locale
import javax.inject.Inject

class PdfTripExporter @Inject constructor(
    @ApplicationContext private val context: Context
) : TripExporter {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)

    companion object {
        private const val PAGE_WIDTH = 842 // A4 landscape
        private const val PAGE_HEIGHT = 595
        private const val MARGIN = 40f
        private const val LINE_HEIGHT = 16f
        private const val HEADER_HEIGHT = 20f
        private const val FONT_SIZE_TITLE = 16f
        private const val FONT_SIZE_HEADER = 9f
        private const val FONT_SIZE_BODY = 8f
        private const val FONT_SIZE_SMALL = 7f
    }

    private val titlePaint = Paint().apply {
        color = Color.BLACK
        textSize = FONT_SIZE_TITLE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val headerPaint = Paint().apply {
        color = Color.WHITE
        textSize = FONT_SIZE_HEADER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val bodyPaint = Paint().apply {
        color = Color.BLACK
        textSize = FONT_SIZE_BODY
        isAntiAlias = true
    }

    private val smallPaint = Paint().apply {
        color = Color.DKGRAY
        textSize = FONT_SIZE_SMALL
        isAntiAlias = true
    }

    private val headerBgPaint = Paint().apply {
        color = Color.parseColor("#6200EE")
        style = Paint.Style.FILL
    }

    private val rowBgPaint = Paint().apply {
        color = Color.parseColor("#F5F5F5")
        style = Paint.Style.FILL
    }

    private val linePaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 0.5f
    }

    private val cancelledPaint = Paint().apply {
        color = Color.RED
        textSize = FONT_SIZE_BODY
        isAntiAlias = true
    }

    private val strikethroughPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 1f
    }

    // Column widths (landscape A4: 842 - 80 margin = 762 usable)
    private val colWidths = floatArrayOf(
        65f,  // Datum
        90f,  // Startort
        90f,  // Zielort
        55f,  // Distanz
        90f,  // Zweck
        70f,  // Kategorie
        90f,  // Fahrzeug
        70f,  // Kennzeichen
        55f,  // Km Start
        55f,  // Km Ende
        32f   // Storniert
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
        val fileName = "fahrtenbuch_${fromStr}_${toStr}.pdf"
        val file = File(context.cacheDir, fileName)

        val document = PdfDocument()
        var pageNumber = 1
        var currentPage: PdfDocument.Page
        var yPos: Float

        // Start first page immediately so canvas is initialized
        val firstPageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create()
        val firstPage = document.startPage(firstPageInfo)
        currentPage = firstPage
        var canvas: Canvas = firstPage.canvas

        fun startNewPage(): Pair<PdfDocument.Page, Canvas> {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create()
            val page = document.startPage(pageInfo)
            canvas = page.canvas
            return page to page.canvas
        }

        fun finishPage(page: PdfDocument.Page) {
            // Footer
            val footerText = "FOSStenbuch – Seite ${pageNumber - 1}"
            canvas.drawText(
                footerText,
                PAGE_WIDTH - MARGIN - bodyPaint.measureText(footerText),
                PAGE_HEIGHT - 15f,
                smallPaint
            )
            document.finishPage(page)
        }

        yPos = MARGIN

        // Title
        canvas.drawText("Fahrtenbuch", MARGIN, yPos + FONT_SIZE_TITLE, titlePaint)
        yPos += FONT_SIZE_TITLE + 8f

        // Driver name
        if (config.driverName.isNotBlank()) {
            canvas.drawText("Fahrer: ${config.driverName}", MARGIN, yPos + FONT_SIZE_BODY, bodyPaint)
            yPos += LINE_HEIGHT
        }

        // Date range
        val rangeText = "Zeitraum: ${config.dateFrom.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))} – " +
            config.dateTo.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        canvas.drawText(rangeText, MARGIN, yPos + FONT_SIZE_BODY, bodyPaint)
        yPos += LINE_HEIGHT

        // Trip count
        canvas.drawText("Anzahl Fahrten: ${trips.size}", MARGIN, yPos + FONT_SIZE_BODY, bodyPaint)
        yPos += LINE_HEIGHT

        // Total distance
        val totalDist = trips.sumOf { it.distanceKm }
        canvas.drawText("Gesamtstrecke: ${"%.1f".format(totalDist)} km", MARGIN, yPos + FONT_SIZE_BODY, bodyPaint)
        yPos += LINE_HEIGHT

        // Business distance
        val businessTrips = trips.filter { t ->
            t.purposeId?.let { purposes[it]?.isBusinessRelevant } == true
        }
        val businessDist = businessTrips.sumOf { it.distanceKm }
        canvas.drawText("Geschäftliche Strecke: ${"%.1f".format(businessDist)} km", MARGIN, yPos + FONT_SIZE_BODY, bodyPaint)
        yPos += LINE_HEIGHT

        val privateDist = totalDist - businessDist
        canvas.drawText("Private Strecke: ${"%.1f".format(privateDist)} km", MARGIN, yPos + FONT_SIZE_BODY, bodyPaint)
        yPos += LINE_HEIGHT + 8f

        // Check for audit-protected vehicles
        val hasAuditProtected = trips.any { t ->
            t.vehicleId?.let { vehicles[it]?.auditProtected } == true
        }
        if (hasAuditProtected) {
            val noticePaint = Paint(bodyPaint).apply {
                color = Color.parseColor("#1B5E20")
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }
            canvas.drawText(
                "✓ Unverändertes Fahrtenbuch gemäß §22 UStG – Änderungssichere Protokollierung aktiv",
                MARGIN, yPos + FONT_SIZE_BODY, noticePaint
            )
            yPos += LINE_HEIGHT + 4f
        }

        // Table header
        val headers = arrayOf(
            "Datum", "Startort", "Zielort", "Distanz", "Zweck",
            "Kategorie", "Fahrzeug", "Kennzeichen", "Km Start", "Km Ende", "Storno"
        )
        yPos = drawTableHeader(canvas, yPos, headers)

        // Table rows
        for ((index, trip) in trips.withIndex()) {
            if (yPos + LINE_HEIGHT > PAGE_HEIGHT - MARGIN - 20f) {
                finishPage(currentPage)
                val (newPage, newCanvas) = startNewPage()
                currentPage = newPage
                canvas = newCanvas
                yPos = MARGIN
                yPos = drawTableHeader(canvas, yPos, headers)
            }

            val purpose = trip.purposeId?.let { purposes[it] }
            val vehicle = trip.vehicleId?.let { vehicles[it] }

            // Alternating row background
            if (index % 2 == 0) {
                canvas.drawRect(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos + LINE_HEIGHT, rowBgPaint)
            }

            val rowData = arrayOf(
                dateFormat.format(trip.date),
                truncate(trip.startLocation, 14),
                truncate(trip.endLocation, 14),
                "%.1f km".format(trip.distanceKm),
                truncate(trip.purpose, 14),
                purpose?.name ?: "-",
                vehicle?.let { truncate("${it.make} ${it.model}", 14) } ?: "-",
                vehicle?.licensePlate ?: "-",
                trip.startOdometer?.toString() ?: "-",
                trip.endOdometer?.toString() ?: "-",
                if (trip.isCancelled) "Ja" else ""
            )

            var xPos = MARGIN + 4f
            for (i in rowData.indices) {
                val paint = if (trip.isCancelled && i < rowData.size - 1) cancelledPaint else bodyPaint
                canvas.drawText(rowData[i], xPos, yPos + LINE_HEIGHT - 4f, paint)
                xPos += colWidths[i]
            }

            // Strikethrough line for cancelled trips
            if (trip.isCancelled) {
                val lineY = yPos + LINE_HEIGHT / 2f
                canvas.drawLine(MARGIN + 4f, lineY, xPos - 4f, lineY, strikethroughPaint)
            }

            // Row bottom line
            canvas.drawLine(MARGIN, yPos + LINE_HEIGHT, PAGE_WIDTH - MARGIN, yPos + LINE_HEIGHT, linePaint)
            yPos += LINE_HEIGHT

            // Cancellation reason as sub-row
            if (trip.isCancelled && !trip.cancellationReason.isNullOrBlank()) {
                if (yPos + LINE_HEIGHT > PAGE_HEIGHT - MARGIN - 20f) {
                    finishPage(currentPage)
                    val (newPage, newCanvas) = startNewPage()
                    currentPage = newPage
                    canvas = newCanvas
                    yPos = MARGIN
                    yPos = drawTableHeader(canvas, yPos, headers)
                }
                val reasonText = "    Stornogrund: ${trip.cancellationReason}"
                canvas.drawText(reasonText, MARGIN + 4f, yPos + LINE_HEIGHT - 4f, cancelledPaint)
                canvas.drawLine(MARGIN, yPos + LINE_HEIGHT, PAGE_WIDTH - MARGIN, yPos + LINE_HEIGHT, linePaint)
                yPos += LINE_HEIGHT
            }
        }

        // Audit log section
        if (config.includeAuditLog && auditLogs.isNotEmpty()) {
            yPos += LINE_HEIGHT

            if (yPos + 60f > PAGE_HEIGHT - MARGIN) {
                finishPage(currentPage)
                val (newPage, newCanvas) = startNewPage()
                currentPage = newPage
                canvas = newCanvas
                yPos = MARGIN
            }

            canvas.drawText("Änderungsprotokoll", MARGIN, yPos + FONT_SIZE_TITLE, titlePaint)
            yPos += FONT_SIZE_TITLE + 8f

            for ((tripId, logs) in auditLogs) {
                for (log in logs) {
                    if (yPos + LINE_HEIGHT > PAGE_HEIGHT - MARGIN - 20f) {
                        finishPage(currentPage)
                        val (newPage, newCanvas) = startNewPage()
                        currentPage = newPage
                        canvas = newCanvas
                        yPos = MARGIN
                    }

                    val logText = "Fahrt #$tripId | ${log.fieldName}: " +
                        "\"${log.oldValue ?: "-"}\" → \"${log.newValue ?: "-"}\" " +
                        "(${dateFormat.format(log.changedAt)})"
                    canvas.drawText(logText, MARGIN, yPos + FONT_SIZE_BODY, smallPaint)
                    yPos += LINE_HEIGHT
                }
            }
        }

        // Truthfulness confirmation
        if (config.truthfulnessConfirmed) {
            yPos += LINE_HEIGHT

            if (yPos + 60f > PAGE_HEIGHT - MARGIN) {
                finishPage(currentPage)
                val (newPage, newCanvas) = startNewPage()
                currentPage = newPage
                canvas = newCanvas
                yPos = MARGIN
            }

            val confirmPaint = Paint(bodyPaint).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }
            canvas.drawText(
                "Ich bestätige hiermit, dass alle Angaben in diesem Fahrtenbuch der Wahrheit entsprechen.",
                MARGIN, yPos + FONT_SIZE_BODY, confirmPaint
            )
            yPos += LINE_HEIGHT

            if (config.driverName.isNotBlank()) {
                yPos += LINE_HEIGHT
                canvas.drawText(
                    config.driverName,
                    MARGIN, yPos + FONT_SIZE_BODY, bodyPaint
                )
                yPos += LINE_HEIGHT

                val dateStr = config.dateTo.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                canvas.drawText(
                    dateStr,
                    MARGIN, yPos + FONT_SIZE_BODY, smallPaint
                )
                yPos += LINE_HEIGHT
            }
        }

        finishPage(currentPage)

        FileOutputStream(file).use { fos ->
            document.writeTo(fos)
        }
        document.close()

        return file
    }

    private fun drawTableHeader(canvas: Canvas, yStart: Float, headers: Array<String>): Float {
        canvas.drawRect(MARGIN, yStart, PAGE_WIDTH - MARGIN, yStart + HEADER_HEIGHT, headerBgPaint)

        var xPos = MARGIN + 4f
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
