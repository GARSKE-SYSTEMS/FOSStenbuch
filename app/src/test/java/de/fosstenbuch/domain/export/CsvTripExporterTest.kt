package de.fosstenbuch.domain.export

import android.content.Context
import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.model.TripAuditLog
import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.model.Vehicle
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.Date

class CsvTripExporterTest {

    private lateinit var exporter: CsvTripExporter
    private val mockContext: Context = mockk()
    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "fosstenbuch_test")
        tempDir.mkdirs()
        every { mockContext.cacheDir } returns tempDir
        exporter = CsvTripExporter(mockContext)
    }

    private fun createConfig(
        dateFrom: LocalDate = LocalDate.of(2024, 1, 1),
        dateTo: LocalDate = LocalDate.of(2024, 12, 31),
        includeAuditLog: Boolean = false
    ) = ExportConfig(
        dateFrom = dateFrom,
        dateTo = dateTo,
        selectedPurposeIds = emptySet(),
        vehicleId = null,
        includeAuditLog = includeAuditLog
    )

    private fun createTrip(
        id: Long = 1L,
        startLocation: String = "Berlin",
        endLocation: String = "Hamburg",
        distanceKm: Double = 280.0,
        purpose: String = "Kundentermin",
        purposeId: Long? = 1L,
        vehicleId: Long? = 1L,
        isCancelled: Boolean = false,
        notes: String? = null,
        startOdometer: Int? = null,
        endOdometer: Int? = null
    ) = Trip(
        id = id,
        date = Date(),
        startLocation = startLocation,
        endLocation = endLocation,
        distanceKm = distanceKm,
        purpose = purpose,
        purposeId = purposeId,
        vehicleId = vehicleId,
        isCancelled = isCancelled,
        notes = notes,
        startOdometer = startOdometer,
        endOdometer = endOdometer
    )

    @Test
    fun `export creates CSV file with correct name`() = runBlocking {
        val config = createConfig()
        val file = exporter.export(config, emptyList(), emptyMap(), emptyMap(), emptyMap())

        assertTrue(file.name.startsWith("fahrtenbuch_"))
        assertTrue(file.name.endsWith(".csv"))
        assertTrue(file.name.contains("2024-01-01"))
        assertTrue(file.name.contains("2024-12-31"))
    }

    @Test
    fun `export writes header row`() = runBlocking {
        val config = createConfig()
        val file = exporter.export(config, emptyList(), emptyMap(), emptyMap(), emptyMap())

        val content = file.readText()
        assertTrue(content.contains("Datum"))
        assertTrue(content.contains("Startort"))
        assertTrue(content.contains("Zielort"))
        assertTrue(content.contains("Distanz (km)"))
        assertTrue(content.contains("Zweck"))
        assertTrue(content.contains("Fahrzeug"))
        assertTrue(content.contains("Kennzeichen"))
        assertTrue(content.contains("Storniert"))
    }

    @Test
    fun `export writes trip data`() = runBlocking {
        val trip = createTrip()
        val purposes = mapOf(1L to TripPurpose(1L, "Beruflich", true))
        val vehicles = mapOf(1L to Vehicle(1L, "BMW", "320d", "B AB 1234", "Diesel"))

        val file = exporter.export(createConfig(), listOf(trip), purposes, vehicles, emptyMap())

        val content = file.readText()
        assertTrue(content.contains("Berlin"))
        assertTrue(content.contains("Hamburg"))
        assertTrue(content.contains("280"))
        assertTrue(content.contains("Beruflich"))
        assertTrue(content.contains("BMW 320d"))
        assertTrue(content.contains("B AB 1234"))
        assertTrue(content.contains("Ja")) // business relevant
    }

    @Test
    fun `export handles cancelled trip`() = runBlocking {
        val trip = createTrip(isCancelled = true)

        val file = exporter.export(createConfig(), listOf(trip), emptyMap(), emptyMap(), emptyMap())

        val lines = file.readLines()
        // Last field of data row should be "Ja" (cancelled)
        val dataLine = lines[1]
        assertTrue(dataLine.endsWith("Ja"))
    }

    @Test
    fun `export handles trip without vehicle`() = runBlocking {
        val trip = createTrip(vehicleId = null)
        val purposes = mapOf(1L to TripPurpose(1L, "Beruflich", true))

        val file = exporter.export(createConfig(), listOf(trip), purposes, emptyMap(), emptyMap())

        val content = file.readText()
        assertTrue(content.contains("Berlin"))
    }

    @Test
    fun `export handles trip without purpose category`() = runBlocking {
        val trip = createTrip(purposeId = null)

        val file = exporter.export(createConfig(), listOf(trip), emptyMap(), emptyMap(), emptyMap())

        val content = file.readText()
        assertTrue(content.contains("Berlin"))
        assertTrue(content.contains("Nein")) // not business relevant when no purpose
    }

    @Test
    fun `export with audit log appends audit section`() = runBlocking {
        val trip = createTrip()
        val auditLogs = mapOf(
            1L to listOf(
                TripAuditLog(1L, 1L, "distanceKm", "100.0", "280.0", Date())
            )
        )

        val file = exporter.export(
            createConfig(includeAuditLog = true),
            listOf(trip),
            emptyMap(),
            emptyMap(),
            auditLogs
        )

        val content = file.readText()
        assertTrue(content.contains("Änderungsprotokoll"))
        assertTrue(content.contains("distanceKm"))
        assertTrue(content.contains("100.0"))
        assertTrue(content.contains("280.0"))
    }

    @Test
    fun `export without audit log does not include audit section`() = runBlocking {
        val file = exporter.export(
            createConfig(includeAuditLog = false),
            emptyList(),
            emptyMap(),
            emptyMap(),
            emptyMap()
        )

        val content = file.readText()
        assertFalse(content.contains("Änderungsprotokoll"))
    }

    @Test
    fun `export with odometer readings includes them`() = runBlocking {
        val trip = createTrip(startOdometer = 50000, endOdometer = 50280)

        val file = exporter.export(createConfig(), listOf(trip), emptyMap(), emptyMap(), emptyMap())

        val content = file.readText()
        assertTrue(content.contains("50000"))
        assertTrue(content.contains("50280"))
    }

    @Test
    fun `export with notes includes them`() = runBlocking {
        val trip = createTrip(notes = "Wichtiger Termin")

        val file = exporter.export(createConfig(), listOf(trip), emptyMap(), emptyMap(), emptyMap())

        val content = file.readText()
        assertTrue(content.contains("Wichtiger Termin"))
    }

    @Test
    fun `CSV escapes fields with semicolons`() = runBlocking {
        val trip = createTrip(notes = "Hin;Rück")

        val file = exporter.export(createConfig(), listOf(trip), emptyMap(), emptyMap(), emptyMap())

        val content = file.readText()
        // Field with semicolon should be quoted
        assertTrue(content.contains("\"Hin;Rück\""))
    }

    @Test
    fun `CSV escapes fields with quotes`() = runBlocking {
        val trip = createTrip(notes = "Er sagte \"Hallo\"")

        val file = exporter.export(createConfig(), listOf(trip), emptyMap(), emptyMap(), emptyMap())

        val content = file.readText()
        // Quotes inside should be doubled
        assertTrue(content.contains("\"\"Hallo\"\""))
    }

    @Test
    fun `export multiple trips writes multiple data rows`() = runBlocking {
        val trips = listOf(
            createTrip(id = 1L, startLocation = "Berlin"),
            createTrip(id = 2L, startLocation = "München"),
            createTrip(id = 3L, startLocation = "Köln")
        )

        val file = exporter.export(createConfig(), trips, emptyMap(), emptyMap(), emptyMap())

        val lines = file.readLines().filter { it.isNotBlank() }
        // 1 header + 3 data rows
        assertEquals(4, lines.size)
    }

    private fun assertFalse(condition: Boolean) {
        org.junit.Assert.assertFalse(condition)
    }
}
