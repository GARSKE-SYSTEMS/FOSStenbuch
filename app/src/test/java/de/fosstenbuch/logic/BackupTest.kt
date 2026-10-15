package de.fosstenbuch.logic

import de.fosstenbuch.data.SavedLocation
import de.fosstenbuch.data.Trip
import de.fosstenbuch.data.TripAuditLog
import de.fosstenbuch.data.TripPurpose
import de.fosstenbuch.data.TripTemplate
import de.fosstenbuch.data.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * Backup-Format ohne org.json (in JVM-Tests nicht verfügbar): geprüft wird die Umwandlung
 * Entities <-> Maps. [asParsed] ahmt nach, wie org.json Zahlen schreibt und wieder liest.
 */
class BackupTest {

    private val protectedCar = Vehicle(
        id = 2, make = "VW", model = "Golf", licensePlate = "B-AB 123", fuelType = "Diesel", isPrimary = true,
        auditProtected = true, bluetoothDeviceAddress = "AA:BB:CC:DD:EE:FF", bluetoothDeviceName = "Golf BT"
    )
    private val otherCar = Vehicle(id = 3, make = "Opel", model = "Corsa", licensePlate = "M-X 1", fuelType = "Benzin", notes = "Zweitwagen")

    private val trip1 = Trip(
        id = 1, date = Date(1700000000000L), startLocation = "Berlin", endLocation = "Hamburg", distanceKm = 280.0,
        purpose = "Beruflich", purposeId = 1, startOdometer = 0, endOdometer = 280, vehicleId = 2,
        endTime = Date(1700010000000L), gpsDistanceKm = 1.0E7, isExported = true,
        startLatitude = 52.52, startLongitude = 13.405, endLatitude = 53.55, endLongitude = 10.0
    )
    private val trip2 = Trip(
        id = 4, date = Date(1700100000000L), startLocation = "Hamburg", endLocation = "Kiel", distanceKm = 96.5,
        purpose = "Privat", startOdometer = 280, endOdometer = 376, vehicleId = 2, isCancelled = true,
        cancellationReason = "Doppelt", businessPartner = "ACME", route = "A7", notes = "Ü|x"
    )
    /** Automatisch erkannt, zwischen den Fahrten der Kette und ohne Hash */
    private val ghost = Trip(id = 3, date = Date(1700050000000L), startLocation = "Unbekannt", vehicleId = 2, isGhost = true, gpsDistanceKm = 12.4)

    /** Fahrten mit korrekter Hash-Kette (wie sie Trips.recomputeChain speichert) */
    private fun chained(vararg trips: Trip): List<Trip> = trips.toList().zip(Trips.chainHashes(trips.toList())) { t, h -> t.copy(chainHash = h) }

    private val full = Backup.Content(
        vehicles = listOf(protectedCar, otherCar),
        purposes = listOf(TripPurpose(id = 1, name = "Beruflich", isBusinessRelevant = true, isDefault = true)),
        locations = listOf(SavedLocation(id = 7, name = "Büro", latitude = 52.0, longitude = 13.4, address = "Hauptstr. 1", usageCount = 0)),
        trips = chained(trip1, trip2) + Trip(id = 9, date = Date(1700200000000L), startLocation = "A", vehicleId = 3, isActive = true) + ghost,
        templates = listOf(TripTemplate(id = 5, name = "Kunde", startLocation = "Berlin", endLocation = "Potsdam", distanceKm = 35.0, purpose = "Beruflich", purposeId = 1, vehicleId = 2, route = "B1")),
        auditLogs = listOf(TripAuditLog(id = 1, tripId = 1, fieldName = "Zielort", oldValue = null, newValue = "Hamburg", changedAt = Date(1700020000000L)))
    )

    /** Wie org.json: Ganzzahlige Werte stehen ohne Nachkommastelle in der Datei (280.0 -> 280) und kommen als Integer bzw. Long zurück */
    private fun asParsed(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.mapValues { asParsed(it.value) }
        is List<*> -> value.map(::asParsed)
        is Number -> {
            val d = value.toDouble()
            if (d % 1.0 != 0.0) d else d.toLong().let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it }
        }
        else -> value
    }

    @Suppress("UNCHECKED_CAST")
    private fun roundTrip(c: Backup.Content) = Backup.decode(asParsed(Backup.encode(c, 1L)) as Map<String, Any?>)

    @Test
    fun `Rundreise erhält alle Felder inklusive Vorlagen, Bluetooth, Export-Status und automatischer Fahrten`() {
        assertEquals(full, roundTrip(full))
    }

    @Test
    fun `automatische Fahrten stehen getrennt, damit alte App-Versionen sie nicht als echte Fahrten einspielen`() {
        val root = Backup.encode(full, 42L)
        assertEquals(1, root["version"])
        assertEquals(42L, root["exportedAt"])
        assertEquals(listOf(1L, 4L, 9L), (root["trips"] as List<*>).map { (it as Map<*, *>)["id"] })
        assertEquals(listOf(3L), (root["ghostTrips"] as List<*>).map { (it as Map<*, *>)["id"] })
    }

    @Test
    fun `altes Backup ohne neuere Keys wird gelesen, 0 bleibt 0 und null bleibt null`() {
        val expected = chained(trip1.copy(isExported = false, startLatitude = null, startLongitude = null, endLatitude = null, endLongitude = null))[0]
        // So sah eine Datei der bisherigen App-Version nach dem Einlesen durch org.json aus
        val old: Map<String, Any?> = mapOf(
            "version" to 1, "exportedAt" to 1700000000000L,
            "vehicles" to listOf(
                mapOf(
                    "id" to 2, "make" to "VW", "model" to "Golf", "licensePlate" to "B-AB 123", "fuelType" to "Diesel",
                    "isPrimary" to true, "notes" to null, "auditProtected" to true
                )
            ),
            "purposes" to listOf(mapOf("id" to 1, "name" to "Beruflich", "isBusinessRelevant" to true, "color" to "#6200EE", "isDefault" to true)),
            "locations" to listOf(
                mapOf("id" to 7, "name" to "Büro", "latitude" to 52.5, "longitude" to 13, "address" to null, "usageCount" to 3, "businessPartner" to null)
            ),
            "trips" to listOf(
                mapOf(
                    "id" to 1, "date" to 1700000000000L, "startLocation" to "Berlin", "endLocation" to "Hamburg",
                    "distanceKm" to 280, "purpose" to "Beruflich", "purposeId" to 1, "notes" to null,
                    "startOdometer" to 0, "endOdometer" to 280, "vehicleId" to 2, "isCancelled" to false,
                    "cancellationReason" to null, "isActive" to false, "endTime" to 1700010000000L,
                    "gpsDistanceKm" to 10000000, "businessPartner" to null, "route" to null, "chainHash" to expected.chainHash
                )
            )
            // kein "auditLogs", "templates", "ghostTrips" (gab es früher nicht bzw. optional)
        )
        val c = Backup.decode(old)
        assertEquals(listOf(expected), c.trips)
        assertEquals(0, c.trips[0].startOdometer)
        assertNull(c.trips[0].notes)
        assertFalse(c.trips[0].isGhost)
        assertNull(c.vehicles[0].bluetoothDeviceAddress)
        assertEquals(13.0, c.locations[0].longitude, 0.0)
        assertTrue(c.templates.isEmpty() && c.auditLogs.isEmpty())
        Backup.checkChains(c) // Kette passt weiterhin
    }

    @Test
    fun `fremde oder unvollständige Dateien werden abgelehnt`() {
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(mapOf("trips" to emptyList<Any>())) }
        // Ohne Pflicht-Arrays würde sonst alles durch "nichts" ersetzt
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(mapOf("version" to 1, "vehicles" to emptyList<Any>())) }
        val noName = Backup.encode(full, 1L) + ("purposes" to listOf(mapOf("id" to 1, "isBusinessRelevant" to true)))
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(noName) }
    }

    @Test
    fun `Kettenprüfung ignoriert automatische Fahrten und ungeschützte Fahrzeuge`() {
        Backup.checkChains(full) // Ghost #3 ohne Hash liegt zwischen #1 und #4, Fahrt #9 (ungeschützt) ohne Hash
    }

    @Test
    fun `Kettenprüfung findet veränderte und fehlende Hashes`() {
        val tampered = full.copy(trips = full.trips.map { if (it.id == 4L) it.copy(endOdometer = 400) else it })
        val e = assertThrows(Backup.BrokenChain::class.java) { Backup.checkChains(tampered) }
        assertTrue(e.message!!, e.message!!.contains("beschädigt bei Fahrt #4"))

        val missing = full.copy(trips = full.trips.map { if (it.id == 1L) it.copy(chainHash = null) else it })
        val e2 = assertThrows(Backup.BrokenChain::class.java) { Backup.checkChains(missing) }
        assertTrue(e2.message!!, e2.message!!.startsWith("Fehlende Hash-Kette für änderungssicheres Fahrzeug VW Golf (B-AB 123) bei Fahrt #1"))
    }
}
