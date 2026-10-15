package de.fosstenbuch.logic

import de.fosstenbuch.data.Trip
import de.fosstenbuch.data.TripPurpose
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MileageTest {

    private val original = TimeZone.getDefault()

    // Feste Zeitzone mit Sommerzeit, damit lokale Grenzen und Zeitumstellung geprüft werden
    @Before fun setUp() = TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
    @After fun tearDown() = TimeZone.setDefault(original)

    /** Lokaler Zeitpunkt "yyyy-MM-dd HH:mm" in Millisekunden */
    private fun at(text: String) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.GERMANY).parse(text)!!.time

    private val business = TripPurpose(id = 1, name = "Beruflich", isBusinessRelevant = true)
    private val privat = TripPurpose(id = 2, name = "Privat", isBusinessRelevant = false)

    private fun trip(time: String, km: Double, purposeId: Long? = 1) =
        Trip(date = Date(at(time)), startLocation = "A", distanceKm = km, purposeId = purposeId)

    @Test
    fun `Jahr beginnt und endet um Mitternacht Ortszeit`() {
        val y = Mileage.year(2026)
        assertEquals(at("2026-01-01 00:00"), y.first)
        assertEquals(at("2027-01-01 00:00") - 1, y.last)
        // 00:30 MEZ am Neujahrstag ist in UTC noch Silvester – zählt trotzdem zu 2026
        assertTrue(at("2026-01-01 00:30") in y)
        assertFalse(at("2026-01-01 00:30") in Mileage.year(2025))
        assertTrue(at("2026-12-31 23:59") + 59_999 in y)
    }

    @Test
    fun `Monat Dezember endet am 1 Januar des Folgejahres`() {
        val m = Mileage.month(2025, 11)
        assertEquals(at("2025-12-01 00:00"), m.first)
        assertEquals(at("2026-01-01 00:00") - 1, m.last)
    }

    @Test
    fun `freier Zeitraum umfasst den ganzen Bis-Tag, auch bei Zeitumstellung`() {
        val r = Mileage.days(at("2026-03-29 00:00"), at("2026-03-29 15:00"))
        assertEquals(at("2026-03-29 00:00"), r.first)
        assertEquals(at("2026-03-30 00:00") - 1, r.last)
        // Tag der Umstellung auf Sommerzeit hat nur 23 Stunden
        assertEquals(23 * 3_600_000L, r.last + 1 - r.first)
        assertTrue(at("2026-03-29 23:59") in r)
    }

    @Test
    fun `Summen zählen nur abgeschlossene, nicht stornierte Fahrten im Zeitraum`() {
        val trips = listOf(
            trip("2026-01-01 00:30", 100.0),
            trip("2026-06-01 12:00", 50.0, purposeId = 2),
            trip("2026-07-01 12:00", 30.0, purposeId = null), // ohne Kategorie: nur Gesamt
            trip("2026-07-02 12:00", 999.0).copy(isCancelled = true),
            trip("2026-07-03 12:00", 999.0).copy(isActive = true),
            trip("2026-07-04 12:00", 999.0).copy(isGhost = true),
            trip("2025-12-31 23:59", 999.0)
        )
        val t = Mileage.totals(trips, listOf(business, privat), Mileage.year(2026))
        assertEquals(Mileage.Totals(trips = 3, km = 180.0, businessKm = 100.0, privateKm = 50.0, businessTrips = 1), t)
        assertEquals(60.0, t.averageKm, 1e-9)
        assertEquals(100.0 / 150 * 100, t.businessPercent!!, 1e-9)
    }

    @Test
    fun `ohne Fahrten gibt es weder Durchschnitt noch Geschäftsanteil`() {
        val t = Mileage.totals(emptyList(), listOf(business), Mileage.year(2026))
        assertEquals(0.0, t.averageKm, 0.0)
        assertNull(t.businessPercent)
    }

    @Test
    fun `Pauschalsätze je Jahr`() {
        assertEquals(0.30 to 0.30, Mileage.commuteRates(2020))
        assertEquals(0.30 to 0.35, Mileage.commuteRates(2021))
        assertEquals(0.30 to 0.38, Mileage.commuteRates(2022))
        assertEquals(0.30 to 0.38, Mileage.commuteRates(2025))
        assertEquals(0.38 to 0.38, Mileage.commuteRates(2026))
    }

    @Test
    fun `Entfernungspauschale mit Staffelung und nur vollen Kilometern`() {
        // 2026: 15 km × 0,38 € × 230 Tage
        assertEquals(1311.0, Mileage.commuteAllowance(15.0, 230, 2026).total, 1e-6)
        // 2025: 20 km × 0,30 € + 10 km × 0,38 €, je 230 Tage
        Mileage.commuteAllowance(30.0, 230, 2025).let {
            assertEquals(1380.0, it.first20, 1e-6)
            assertEquals(874.0, it.beyond20, 1e-6)
            assertEquals(2254.0, it.total, 1e-6)
        }
        // 2021: ab dem 21. km 0,35 €
        assertEquals(1380.0 + 805.0, Mileage.commuteAllowance(30.0, 230, 2021).total, 1e-6)
        // 15,7 km zählen als 15 km
        Mileage.commuteAllowance(15.7, 230, 2025).let {
            assertEquals(15, it.km)
            assertEquals(1035.0, it.total, 1e-6)
        }
        assertEquals(0.0, Mileage.commuteAllowance(0.9, 230, 2026).total, 0.0)
    }

    @Test
    fun `Statistikmodus deutet Jahres-km als Hin- und Rückweg`() {
        // 12.450 km / 2 / 230 Tage = 27,07 km → 27 volle km
        val a = Mileage.commuteAllowanceFromYearKm(12_450.0, 230, 2025)
        assertEquals(27, a.km)
        assertEquals(20 * 0.30 * 230 + 7 * 0.38 * 230, a.total, 1e-6)
        assertEquals(3735.0, Mileage.businessTripAllowance(12_450.0), 1e-6)
    }
}
