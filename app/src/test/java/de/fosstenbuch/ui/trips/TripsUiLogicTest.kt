package de.fosstenbuch.ui.trips

import de.fosstenbuch.data.Trip
import de.fosstenbuch.data.TripPurpose
import de.fosstenbuch.ui.trips.TripEditFragment.Mode
import de.fosstenbuch.logic.Trips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/** Reine Logik der Fahrten-Bildschirme (ohne Android) */
class TripsUiLogicTest {

    private val business = TripPurpose(id = 1, name = "Beruflich", isBusinessRelevant = true)
    private val privat = TripPurpose(id = 2, name = "Privat", isBusinessRelevant = false)

    private fun trip(id: Long, day: Long, km: Double, purposeId: Long? = null) =
        Trip(id = id, date = Date(day * 86_400_000), startLocation = "A", endLocation = "B", distanceKm = km, purposeId = purposeId)

    private val trips = listOf(
        trip(1, day = 3, km = 10.0, purposeId = 1),
        trip(2, day = 1, km = 30.0, purposeId = 2),
        trip(3, day = 2, km = 20.0) // ohne Kategorie, z.B. laufende Fahrt
    )

    private fun ids(filter: TripFilter, sort: Int = 0) = tripRows(trips, listOf(business, privat), filter, sort).map { it.trip.id }

    @Test
    fun `Filter richtet sich nach der Kategorie, Fahrten ohne Kategorie nur unter Alle`() {
        assertEquals(listOf(1L, 3L, 2L), ids(TripFilter.ALL))
        assertEquals(listOf(1L), ids(TripFilter.BUSINESS))
        assertEquals(listOf(2L), ids(TripFilter.PRIVATE))
    }

    @Test
    fun `Sortierung wie sort_options`() {
        assertEquals(listOf(1L, 3L, 2L), ids(TripFilter.ALL, 0)) // Datum neueste zuerst
        assertEquals(listOf(2L, 3L, 1L), ids(TripFilter.ALL, 1)) // Datum älteste zuerst
        assertEquals(listOf(2L, 3L, 1L), ids(TripFilter.ALL, 2)) // Distanz größte zuerst
        assertEquals(listOf(1L, 3L, 2L), ids(TripFilter.ALL, 3)) // Distanz kleinste zuerst
    }

    @Test
    fun `Zeilen tragen ihre Kategorie`() {
        val rows = tripRows(trips, listOf(business, privat), TripFilter.ALL, 0)
        assertEquals(business, rows.first { it.trip.id == 1L }.purpose)
        assertNull(rows.first { it.trip.id == 3L }.purpose)
    }

    @Test
    fun `Modus ergibt sich aus der Fahrt`() {
        val done = trip(1, 1, 5.0)
        assertEquals(Mode.NEW, Mode.of(null))
        assertEquals(Mode.ACTIVE, Mode.of(done.copy(isActive = true)))
        assertEquals(Mode.RECORDING, Mode.of(done.copy(isGhost = true)))
        assertEquals(Mode.GHOST, Mode.of(done.copy(isGhost = true, endTime = Date())))
        assertEquals(Mode.EDIT, Mode.of(done))
    }

    @Test
    fun `End-Km-Vorschlag aus Start und gerundeter Strecke`() {
        assertEquals(1013, Trips.estimatedEndOdometer(1000, 12.6))
        assertNull(Trips.estimatedEndOdometer(null, 12.6))
        assertNull(Trips.estimatedEndOdometer(1000, 0.0))
        assertNull(Trips.estimatedEndOdometer(1000, null))
    }

    @Test
    fun `Km-Vorschlag ersetzt nie eine Eingabe des Nutzers`() {
        assertTrue(isReplaceable("", null))
        assertTrue(isReplaceable("50000", "50000")) // eigener Vorschlag, z.B. beim Fahrzeugwechsel
        assertFalse(isReplaceable("50100", "50000"))
        assertFalse(isReplaceable("50000", null))
    }

    @Test
    fun `Kategorie-Text behält alten Freitext nur bei unveränderter Kategorie`() {
        val new = Trip(date = Date(), startLocation = "")
        assertEquals("Beruflich", purposeText(new, 1, "Beruflich"))
        val old = trip(5, 1, 5.0, purposeId = 1).copy(purpose = "Kundenbesuch")
        assertEquals("Kundenbesuch", purposeText(old, 1, "Beruflich"))
        assertEquals("Privat", purposeText(old, 2, "Privat"))
        assertEquals("Beruflich", purposeText(old.copy(purpose = ""), 1, "Beruflich"))
        assertEquals("", purposeText(new, null, null))
    }

    @Test
    fun `Ortsnutzung zählt jede Fahrt einmal, beim Bearbeiten nur Änderungen`() {
        val saved = trip(7, 1, 5.0).copy(startLocation = "Büro", endLocation = "Kunde")
        assertEquals(listOf("Büro", "Kunde"), locationsToCount(Trip(date = Date(), startLocation = ""), saved))
        assertEquals(listOf("Büro", "Kunde"), locationsToCount(saved.copy(isActive = true, endLocation = ""), saved))
        assertEquals(listOf("Büro", "Kunde"), locationsToCount(saved.copy(isGhost = true), saved))
        assertEquals(emptyList<String>(), locationsToCount(saved, saved))
        assertEquals(listOf("Kunde"), locationsToCount(saved.copy(endLocation = "Lager"), saved))
    }
}
