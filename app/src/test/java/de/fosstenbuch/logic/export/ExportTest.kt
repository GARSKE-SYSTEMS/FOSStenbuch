package de.fosstenbuch.logic.export

import de.fosstenbuch.data.Trip
import de.fosstenbuch.data.TripAuditLog
import de.fosstenbuch.data.TripPurpose
import de.fosstenbuch.data.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date

class ExportTest {

    /** Zeitpunkt in lokaler Zeit (wie auf dem Gerät) */
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0, s: Int = 0, ms: Int = 0): Date =
        Date.from(LocalDateTime.of(y, m, d, h, min, s, ms * 1_000_000).atZone(ZoneId.systemDefault()).toInstant())

    private fun trip(id: Long, date: Date, purposeId: Long? = 1, vehicleId: Long? = 1, km: Double = 10.0, start: Int? = null, end: Int? = null) =
        Trip(id = id, date = date, startLocation = "A", endLocation = "B", distanceKm = km, purposeId = purposeId, vehicleId = vehicleId,
            startOdometer = start, endOdometer = end)

    private val business = TripPurpose(id = 1, name = "Beruflich", isBusinessRelevant = true)
    private val private = TripPurpose(id = 2, name = "Privat", isBusinessRelevant = false)
    private val golf = Vehicle(id = 1, make = "VW", model = "Golf", licensePlate = "B-AB 123", fuelType = "Benzin", auditProtected = true)
    private val bmw = Vehicle(id = 2, make = "BMW", model = "320d", licensePlate = "M-XY 9", fuelType = "Diesel", auditProtected = true)
    private val year2024 = Options(from = LocalDate.of(2024, 1, 1), to = LocalDate.of(2024, 12, 31))

    @Test
    fun `Zeitraum in lokaler Zeit, Bis-Tag vollständig, sortiert, ohne laufende und automatische Fahrten`() {
        val trips = listOf(
            trip(1, at(2024, 12, 31, 23, 59, 59, 999)),
            trip(2, at(2024, 1, 1, 0, 0)),
            trip(3, at(2025, 1, 1, 0, 0)),
            trip(4, at(2023, 12, 31, 23, 59, 59, 999)),
            trip(5, at(2024, 6, 1)).copy(isActive = true),
            trip(6, at(2024, 6, 1)).copy(isGhost = true),
            trip(7, at(2024, 6, 1)).copy(isCancelled = true)
        )
        assertEquals(listOf(2L, 7L, 1L), Export.filter(trips, year2024, listOf(1, 2)).map { it.id })
        // Von nach Bis ergibt nichts
        assertTrue(Export.filter(trips, year2024.copy(from = LocalDate.of(2025, 1, 1)), listOf(1, 2)).isEmpty())
    }

    @Test
    fun `Fahrzeug- und Kategoriefilter, leere Auswahl ergibt keine Fahrten`() {
        val trips = listOf(trip(1, at(2024, 3, 1), purposeId = 1), trip(2, at(2024, 3, 2), purposeId = 2, vehicleId = 2),
            trip(3, at(2024, 3, 3), purposeId = null))
        val ids = listOf(1L, 2L)
        fun result(o: Options) = Export.filter(trips, o, ids).map { it.id }
        // Nichts abgewählt: alles, auch Fahrten ohne Kategorie
        assertEquals(listOf(1L, 2L, 3L), result(year2024))
        assertEquals(listOf(1L), result(year2024.copy(excludedPurposes = setOf(2))))
        assertEquals(emptyList<Long>(), result(year2024.copy(excludedPurposes = setOf(1, 2))))
        // Abgewählte, inzwischen gelöschte Kategorie zählt nicht als Einschränkung
        assertEquals(listOf(1L, 2L, 3L), result(year2024.copy(excludedPurposes = setOf(99))))
        assertEquals(listOf(2L), result(year2024.copy(vehicleId = 2)))
    }

    @Test
    fun `Nur neue Fahrten gilt nur für CSV`() {
        val trips = listOf(trip(1, at(2024, 3, 1)).copy(isExported = true), trip(2, at(2024, 3, 2)))
        assertEquals(listOf(2L), Export.filter(trips, year2024, listOf(1)).map { it.id })
        assertEquals(listOf(1L, 2L), Export.filter(trips, year2024.copy(onlyNew = false), listOf(1)).map { it.id })
        assertEquals(listOf(1L, 2L), Export.filter(trips, year2024.copy(format = Format.FINANZAMT_PDF), listOf(1)).map { it.id })
        assertTrue(year2024.copy(format = Format.FINANZAMT_PDF, includeAudit = false).withAudit)
    }

    @Test
    fun `CSV-Felder werden nach RFC 4180 gequotet und Formeln entschärft`() {
        assertEquals("Berlin", Export.cell("Berlin"))
        assertEquals("\"a;b\"", Export.cell("a;b"))
        assertEquals("\"sagt \"\"hi\"\"\"", Export.cell("sagt \"hi\""))
        assertEquals("\"a\rb\"", Export.cell("a\rb"))
        assertEquals("\"a\nb\"", Export.cell("a\nb"))
        assertEquals("'=HYPERLINK(\"x\")".let { "\"" + it.replace("\"", "\"\"") + "\"" }, Export.cell("=HYPERLINK(\"x\")"))
        assertEquals("'- Rückfahrt", Export.cell("- Rückfahrt"))
        assertEquals("'+49", Export.cell("+49"))
        assertEquals("'@x", Export.cell("@x"))
        assertEquals("", Export.cell(""))
    }

    @Test
    fun `CSV enthält Kopf, Fahrten, Storno und eindeutig zugeordnetes Änderungsprotokoll`() {
        val t1 = trip(1, at(2024, 2, 5, 8, 15), km = 1234.5, start = 100, end = 1334)
            .copy(startLocation = "Berlin", endLocation = "Hamburg", purpose = "Kunde; Termin", businessPartner = "ACME", endTime = at(2024, 2, 5, 9, 0))
        val t2 = trip(2, at(2024, 2, 6, 10, 0), purposeId = 2, vehicleId = 2, km = 5.0)
            .copy(isCancelled = true, cancellationReason = "Doppelt")
        val log = TripAuditLog(tripId = 1, fieldName = "Zielort", oldValue = "Hamburg-Nord", newValue = "Hamburg", changedAt = at(2024, 2, 7, 10, 30))
        // Der Polo ist änderungssicher, wird aber nicht exportiert → darf nicht im Hinweis stehen
        val polo = Vehicle(id = 3, make = "VW", model = "Polo", licensePlate = "P-1", fuelType = "Benzin", auditProtected = true)
        val data = ExportData(year2024, listOf(t1, t2), listOf(business, private), listOf(golf, bmw.copy(auditProtected = false), polo),
            mapOf(1L to listOf(log)), driver = "Max Muster")

        val csv = Export.csv(data)
        assertTrue(csv.startsWith("\uFEFF"))
        assertEquals(
            listOf(
                "Fahrer;Max Muster",
                "",
                "Änderungssicher geführte Fahrzeuge",
                "VW Golf;B-AB 123",
                "",
                "Startdatum/-zeit;Enddatum/-zeit;Startort;Zielort;Distanz (km);Zweck;Kategorie;Geschäftlich;Geschäftspartner;Route;" +
                    "Fahrzeug;Kennzeichen;Km-Stand Start;Km-Stand Ende;Notizen;Storniert;Stornogrund",
                "05.02.2024 08:15;05.02.2024 09:00;Berlin;Hamburg;1234,50;\"Kunde; Termin\";Beruflich;Ja;ACME;;VW Golf;B-AB 123;100;1334;;;",
                "06.02.2024 10:00;;A;B;5,00;;Privat;Nein;;;BMW 320d;M-XY 9;;;;STORNIERT;Doppelt",
                "",
                "Änderungsprotokoll",
                "Fahrt;Feld;Alter Wert;Neuer Wert;Geändert am",
                "05.02.2024 08:15 Berlin → Hamburg;Zielort;Hamburg-Nord;Hamburg;07.02.2024 10:30",
                ""
            ),
            csv.removePrefix("\uFEFF").split("\r\n")
        )
    }

    @Test
    fun `Summen ohne stornierte Fahrten, Fahrten ohne Kategorie sind privat`() {
        val trips = listOf(
            trip(1, at(2024, 1, 1), purposeId = 1, km = 100.0),
            trip(2, at(2024, 1, 2), purposeId = 2, km = 20.0),
            trip(3, at(2024, 1, 3), purposeId = null, km = 5.0),
            trip(4, at(2024, 1, 4), purposeId = 1, km = 300.0).copy(isCancelled = true)
        )
        val s = ExportData(year2024, trips, listOf(business, private), emptyList()).summary()
        assertEquals(3, s.count)
        assertEquals(125.0, s.km, 0.001)
        assertEquals(1, s.businessCount)
        assertEquals(100.0, s.businessKm, 0.001)
        assertEquals(2, s.privateCount)
        assertEquals(25.0, s.privateKm, 0.001)
        assertEquals(1, s.cancelled)
    }

    @Test
    fun `Monate werden mit Jahr gruppiert und chronologisch sortiert`() {
        val trips = listOf(trip(1, at(2023, 7, 3)), trip(2, at(2024, 1, 2)), trip(3, at(2023, 1, 5)), trip(4, at(2024, 1, 31, 23, 59)))
        val months = Export.months(trips)
        assertEquals(listOf(YearMonth.of(2023, 1), YearMonth.of(2023, 7), YearMonth.of(2024, 1)), months.keys.toList())
        assertEquals(listOf(2L, 4L), months[YearMonth.of(2024, 1)]!!.map { it.id })
        assertEquals("Januar 2024", Export.monthName(YearMonth.of(2024, 1)))
        assertEquals("März 2023", Export.monthName(YearMonth.of(2023, 3)))
    }

    @Test
    fun `Km-Lücken je Fahrzeug, fehlende Stände und stornierte Fahrten`() {
        val trips = listOf(
            trip(1, at(2024, 1, 1), vehicleId = 1, start = 100, end = 150),
            trip(2, at(2024, 1, 2), vehicleId = 2, start = 900, end = 950),  // anderes Fahrzeug: keine Lücke
            trip(3, at(2024, 1, 3), vehicleId = 1, start = 150, end = 200),  // lückenlos
            trip(4, at(2024, 1, 4), vehicleId = 1, start = 210, end = 220),  // Lücke 200 → 210
            trip(5, at(2024, 1, 5), vehicleId = 1, start = 220, end = null), // Stand fehlt
            trip(6, at(2024, 1, 6), vehicleId = 1, start = 1, end = 2).copy(isCancelled = true)
        )
        assertEquals(2, Export.odometerGaps(trips))
    }

    @Test
    fun `Dateiname enthält Zeitraum und Zeitstempel, Anzeige des Zeitraums deutsch`() {
        assertEquals("01.01.2024 – 31.12.2024", Export.range(year2024))
        val now = at(2026, 10, 15, 14, 30, 5)
        assertEquals("fahrtenbuch_2024-01-01_2024-12-31_20261015-143005.csv", Export.fileName(year2024, now))
        assertEquals(
            "fahrtenbuch_finanzamt_2024-01-01_2024-12-31_20261015-143005.pdf",
            Export.fileName(year2024.copy(format = Format.FINANZAMT_PDF), now)
        )
    }
}
