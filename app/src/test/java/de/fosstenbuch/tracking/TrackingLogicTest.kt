package de.fosstenbuch.tracking

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class TrackingLogicTest {

    // ── GPS-Strecke ──────────────────────────────────────────────────────────

    @Test
    fun `ungenaue Positionen werden verworfen`() {
        val track = Track()
        assertFalse(track.add(52.0, 13.0, 31f))
        assertTrue(track.add(52.0, 13.0, 30f))
        assertFalse(track.add(52.1, 13.0, 50f))
        assertEquals(0.0, track.km, 0.0)
    }

    @Test
    fun `Strecke wird aus aufeinanderfolgenden Positionen summiert`() {
        val track = Track()
        track.add(52.0, 13.0, 5f)
        track.add(52.01, 13.0, 5f) // 0,01° Breite ≈ 1,112 km
        track.add(52.02, 13.0, 5f)
        assertEquals(2.224, track.km, 0.01)
    }

    @Test
    fun `Rauschen im Stand zählt nicht, langsame Bewegung schon`() {
        // Bezugspunkt und neue Position je ±10 m: erst ab 20 m Abstand ist es sicher Bewegung
        val track = Track()
        track.add(52.0, 13.0, 10f)
        assertFalse(track.add(52.000045, 13.0, 10f)) // ≈ 5 m: Rauschen
        assertFalse(track.add(52.000162, 13.0, 10f)) // ≈ 18 m vom Bezugspunkt: noch Rauschen
        assertEquals(0.0, track.km, 0.0)
        assertTrue(track.add(52.000225, 13.0, 10f)) // ≈ 25 m vom Bezugspunkt: echte Bewegung
        assertEquals(0.025, track.km, 0.001)
    }

    @Test
    fun `fortgesetzte Aufzeichnung zählt nur die Genauigkeit der neuen Position`() {
        // Die Genauigkeit des gespeicherten Bezugspunkts ist unbekannt
        val track = Track(1.0, 52.0, 13.0)
        assertTrue(track.add(52.000108, 13.0, 10f)) // ≈ 12 m > 10 m
        assertEquals(1.012, track.km, 0.001)
    }

    @Test
    fun `fortgesetzte Aufzeichnung rechnet ab der gespeicherten Position weiter`() {
        val track = Track(5.0, 52.0, 13.0)
        track.add(52.01, 13.0, 5f)
        assertEquals(6.112, track.km, 0.01)
    }

    // ── Automatische Fahrten ─────────────────────────────────────────────────


    // ── Erinnerung ───────────────────────────────────────────────────────────

    private val original = TimeZone.getDefault()
    private val berlin = TimeZone.getTimeZone("Europe/Berlin")

    @Before
    fun setUp() = TimeZone.setDefault(berlin)

    @After
    fun tearDown() = TimeZone.setDefault(original)

    private fun at(text: String): Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply { timeZone = berlin }.parse(text)!!.time

    @Test
    fun `Uhrzeit lesen mit Rückfall auf 18 Uhr`() {
        assertEquals(7 to 30, Reminder.parseTime("07:30"))
        assertEquals(18 to 0, Reminder.parseTime("18:00"))
        assertEquals(9 to 0, Reminder.parseTime("9"))
        assertEquals(18 to 0, Reminder.parseTime("25:61"))
        assertEquals(18 to 0, Reminder.parseTime("abc"))
        assertEquals(18 to 0, Reminder.parseTime(""))
    }

    @Test
    fun `nächster Termin heute oder morgen`() {
        assertEquals(at("2026-10-15 18:00"), Reminder.nextTrigger(at("2026-10-15 17:00"), 18, 0))
        assertEquals(at("2026-10-16 18:00"), Reminder.nextTrigger(at("2026-10-15 18:00"), 18, 0))
        assertEquals(at("2026-10-16 07:30"), Reminder.nextTrigger(at("2026-10-15 19:00"), 7, 30))
    }

    @Test
    fun `nächster Termin bleibt über die Zeitumstellung auf derselben Ortszeit`() {
        // In der Nacht zum 25.10.2026 endet die Sommerzeit
        assertEquals(at("2026-10-25 18:00"), Reminder.nextTrigger(at("2026-10-24 19:00"), 18, 0))
    }
}
