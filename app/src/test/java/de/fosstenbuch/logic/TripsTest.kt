package de.fosstenbuch.logic

import de.fosstenbuch.data.Trip
import de.fosstenbuch.data.Vehicle
import de.fosstenbuch.logic.Trips.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class TripsTest {

    // Referenzwerte, erzeugt mit der alten TripChainHashCalculator-Implementierung.
    // Bricht dieser Test, sind bestehende Hash-Ketten und Backups der Nutzer ungültig!
    private val t1 = Trip(
        id = 1, date = Date(1700000000000L), startLocation = "Berlin", endLocation = "München, Hbf", distanceKm = 584.5,
        purpose = "Kunde", purposeId = 1, startOdometer = 10000, endOdometer = 10585, vehicleId = 2,
        endTime = Date(1700020000000L), gpsDistanceKm = 1.0E7, businessPartner = "ACME", route = "A9"
    )
    private val t2 = Trip(
        id = 5, date = Date(1700100000000L), startLocation = "Ü|x", endLocation = "", distanceKm = 280.0, purpose = "",
        vehicleId = 2, isCancelled = true, cancellationReason = "Fehler"
    )

    @Test
    fun `kanonisches Format ist unverändert`() {
        assertEquals(
            "1|1700000000000|Berlin|München, Hbf|584.5|Kunde|1|null|10000|10585|2|false|null|false|1700020000000|1.0E7|ACME|A9",
            Trips.canonical(t1)
        )
        assertEquals("5|1700100000000|Ü|x||280.0||null|null|null|null|2|true|Fehler|false|null|null|null|null", Trips.canonical(t2))
    }

    @Test
    fun `Hash-Kette entspricht der alten Implementierung`() {
        assertEquals(
            listOf(
                "bb6f55804fcefee061abe06e5385d2b260966d0d23d0fad69cad2aa241d35006",
                "6e46f7e2d5a7861b19527194d2e202667f27eac11f071ac750093142583d153f"
            ),
            Trips.chainHashes(listOf(t1, t2))
        )
    }

    @Test
    fun `Kettenprüfung findet die erste manipulierte Fahrt`() {
        val hashes = Trips.chainHashes(listOf(t1, t2))
        val chain = listOf(t1.copy(chainHash = hashes[0]), t2.copy(chainHash = hashes[1]))
        assertNull(Trips.firstBrokenLink(chain))
        val tampered = listOf(chain[0].copy(endOdometer = 10600), chain[1])
        assertEquals(1L, Trips.firstBrokenLink(tampered)?.id)
    }

    @Test
    fun `Start braucht Startort, Kilometerstand und Fahrzeug`() {
        val errors = Trips.validateStart(Trip(date = Date(), startLocation = " "))
        assertEquals(setOf(Field.START_LOCATION, Field.START_ODOMETER, Field.VEHICLE), errors.keys)
        assertTrue(Trips.validateStart(Trip(date = Date(), startLocation = "A", startOdometer = 0, vehicleId = 1)).isEmpty())
    }

    @Test
    fun `abgeschlossene Fahrt wird vollständig geprüft`() {
        val now = Date(2_000_000L)
        val ok = Trip(
            date = Date(1_000_000L), endTime = Date(1_500_000L), startLocation = "A", endLocation = "B",
            purposeId = 1, startOdometer = 100, endOdometer = 150, vehicleId = 1
        )
        assertTrue(Trips.validate(ok, now).isEmpty())
        assertEquals(setOf(Field.ODOMETER), Trips.validate(ok.copy(endOdometer = 99), now).keys)
        // 0 km: gestartet, aber nicht gefahren – beenden und stornieren muss möglich sein
        assertTrue(Trips.validate(ok.copy(endOdometer = 100), now).isEmpty())
        assertEquals(setOf(Field.END_TIME), Trips.validate(ok.copy(endTime = Date(999_000L)), now).keys)
        assertEquals(setOf(Field.DATE), Trips.validate(ok.copy(date = Date(3_000_000L), endTime = null), now).keys)
        assertEquals(setOf(Field.PURPOSE, Field.END_LOCATION), Trips.validate(ok.copy(purposeId = null, endLocation = ""), now).keys)
        assertEquals(setOf(Field.DISTANCE), Trips.validate(ok.copy(endOdometer = 200_000), now).keys)
        // Ankunft höchstens eine Minute nach jetzt (Uhr- und Bedienungsspielraum)
        assertTrue(Trips.validate(ok.copy(endTime = Date(2_060_000L)), now).isEmpty())
        assertEquals("Ankunft darf nicht in der Zukunft liegen", Trips.validate(ok.copy(endTime = Date(2_061_000L)), now)[Field.END_TIME])
    }

    @Test
    fun `berufliche Fahrt braucht Geschäftspartner oder Notiz`() {
        val now = Date(2_000_000L)
        val ok = Trip(
            date = Date(1_000_000L), startLocation = "A", endLocation = "B", purposeId = 1, startOdometer = 100, endOdometer = 150, vehicleId = 1
        )
        assertTrue(Trips.validate(ok, now).isEmpty()) // privat
        assertEquals(setOf(Field.PARTNER), Trips.validate(ok.copy(businessPartner = " ", notes = ""), now, business = true).keys)
        assertTrue(Trips.validate(ok.copy(businessPartner = "ACME"), now, business = true).isEmpty())
        assertTrue(Trips.validate(ok.copy(notes = "Messe"), now, business = true).isEmpty())
    }

    @Test
    fun `nur automatische und gerade erst gestartete Fahrten lassen sich löschen`() {
        val now = Date(10 * 60_000L)
        val completed = t1.copy()
        assertEquals("Abgeschlossene Fahrten lassen sich nur stornieren", assertThrows(Trips.Rejected::class.java) { Trips.checkDelete(completed, now) }.message)
        assertThrows(Trips.Rejected::class.java) { Trips.checkDelete(t2, now) } // auch storniert nicht
        Trips.checkDelete(t1.copy(isGhost = true, endTime = null), now)
        val running = Trip(date = Date(now.time - Trips.DISCARD_MS), startLocation = "A", isActive = true)
        assertTrue(Trips.canDiscard(running, now))
        Trips.checkDelete(running, now)
        val old = running.copy(date = Date(now.time - Trips.DISCARD_MS - 1))
        assertFalse(Trips.canDiscard(old, now))
        assertEquals("Laufende Fahrt bitte beenden und dann stornieren", assertThrows(Trips.Rejected::class.java) { Trips.checkDelete(old, now) }.message)
        assertFalse(Trips.canDiscard(completed, now))
    }

    @Test
    fun `Fahrzeugwechsel erscheint im Protokoll mit Kennzeichen`() {
        val names = mapOf(2L to "B-AB 123", 3L to "M-X 1")
        val log = Trips.auditDiff(t1, t1.copy(vehicleId = 3)) { names[it] }
        assertEquals(listOf("Fahrzeug"), log.map { it.fieldName })
        assertEquals("B-AB 123", log[0].oldValue)
        assertEquals("M-X 1", log[0].newValue)
        assertEquals("3", Trips.auditDiff(t1, t1.copy(vehicleId = 3))[0].newValue) // ohne Namen: die ID
        assertEquals("VW Golf", Trips.vehicleName(Vehicle(id = 4, make = "VW", model = "Golf", licensePlate = " ", fuelType = "")))
        assertEquals("4", Trips.vehicleName(Vehicle(id = 4, make = "", model = "", licensePlate = "", fuelType = "")))
    }

    @Test
    fun `Distanz ergibt sich aus den Kilometerständen`() {
        assertEquals(50.0, Trips.odometerDistance(Trip(date = Date(), startLocation = "", startOdometer = 100, endOdometer = 150)))
        assertNull(Trips.odometerDistance(Trip(date = Date(), startLocation = "", startOdometer = 100)))
    }

    @Test
    fun `Änderungsprotokoll enthält nur geänderte Felder`() {
        val log = Trips.auditDiff(t1, t1.copy(endLocation = "Hamburg", isCancelled = true))
        assertEquals(listOf("Zielort", "Storniert"), log.map { it.fieldName })
        assertEquals("München, Hbf", log[0].oldValue)
        assertEquals("Hamburg", log[0].newValue)
        assertTrue(log.all { it.tripId == 1L })
    }

    @Test
    fun `beim Beenden zählt nur das Überschreiben erfasster Werte`() {
        val running = Trip(id = 7, date = Date(1_000L), startLocation = "Büro", startOdometer = 100, vehicleId = 2, isActive = true)
        val ended = running.copy(isActive = false, endLocation = "Kunde", endOdometer = 150, endTime = Date(2_000L), distanceKm = 50.0, purpose = "Beruflich")
        assertTrue(Trips.auditDiff(running, ended).isEmpty())
        // Korrigierter Start-Km-Stand beim Beenden wird protokolliert
        assertEquals(listOf("Km-Stand Start"), Trips.auditDiff(running, ended.copy(startOdometer = 110)).map { it.fieldName })
        // Automatische Fahrt: verschobene Abfahrt wird protokolliert (minutengenau), die erste Kategorie nicht
        val ghost = running.copy(isActive = false, isGhost = true, endTime = Date(2_000L))
        assertEquals(listOf("Abfahrt"), Trips.auditDiff(ghost, ended.copy(date = Date(3_601_000L))).map { it.fieldName })
        // Bei abgeschlossenen Fahrten zählt auch das erste Ausfüllen (z.B. nachträgliche Notiz)
        assertEquals(listOf("Notizen"), Trips.auditDiff(ended, ended.copy(notes = "neu")).map { it.fieldName })
    }
}
