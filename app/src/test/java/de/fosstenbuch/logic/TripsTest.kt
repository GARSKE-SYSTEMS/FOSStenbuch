package de.fosstenbuch.logic

import de.fosstenbuch.data.Trip
import de.fosstenbuch.logic.Trips.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(setOf(Field.ODOMETER), Trips.validate(ok.copy(endOdometer = 100), now).keys)
        assertEquals(setOf(Field.END_TIME), Trips.validate(ok.copy(endTime = Date(999_000L)), now).keys)
        assertEquals(setOf(Field.DATE), Trips.validate(ok.copy(date = Date(3_000_000L), endTime = null), now).keys)
        assertEquals(setOf(Field.PURPOSE, Field.END_LOCATION), Trips.validate(ok.copy(purposeId = null, endLocation = ""), now).keys)
        assertEquals(setOf(Field.DISTANCE), Trips.validate(ok.copy(endOdometer = 200_000), now).keys)
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
}
