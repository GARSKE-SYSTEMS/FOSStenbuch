package de.fosstenbuch.logic

import de.fosstenbuch.data.Trip
import de.fosstenbuch.logic.export.Export
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.Date

class SealsTest {

    private val trip = Trip(
        id = 7, date = Date(1700000000000L), endTime = Date(1700003600000L), startLocation = "Büro", endLocation = "Kunde Müller",
        businessPartner = "ACME GmbH", purpose = "Beruflich", notes = "Ü", startOdometer = 0, endOdometer = 42, vehicleId = 2,
        distanceKm = 42.0, gpsDistanceKm = 41.7, isExported = true
    )

    private fun newKey(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun signer(key: KeyPair): (ByteArray) -> Seals.Signed = { payload ->
        val sig = Signature.getInstance("SHA256withECDSA").run { initSign(key.private); update(payload); sign() }
        Seals.Signed(Base64.getEncoder().encodeToString(sig), Base64.getEncoder().encodeToString(key.public.encoded))
    }

    // Referenzwerte unabhängig (Python) erzeugt – docs/pruefen.js muss dieselben Bytes bilden.
    // Bricht dieser Test, passen bestehende Siegel nicht mehr!
    @Test
    fun `Nutzdaten-Format ist unverändert`() {
        assertEquals(
            "20:FOSStenbuch-Siegel-1,7:Beendet,13:1700003700000,1:7,1:2,13:1700000000000,13:1700003600000,5:Büro," +
                "13:Kunde Müller,~,9:ACME GmbH,9:Beruflich,2:Ü,1:0,2:42,5:false,~,",
            String(Seals.payload(trip, Seals.ENDED, 1700003700000L))
        )
        val seal = Seals.create(trip, Seals.ENDED, 1700003700000L)
        assertEquals("53992a422392e898164a131cbf86ddff47f39fb143cf1e452337648581344c63", seal.hash)
        assertEquals(7L, seal.tripId)
        assertNull(seal.signature)
        assertNull(seal.tsToken)
    }

    @Test
    fun `nicht gesiegelte Felder (Distanz, GPS, Export-Status, Hash-Kette) ändern das Siegel nicht`() {
        val seal = Seals.create(trip, Seals.ENDED, 1L)
        assertTrue(Seals.matches(trip.copy(distanceKm = 1.5, gpsDistanceKm = null, isExported = false, chainHash = "x"), seal))
        assertFalse(Seals.matches(trip.copy(endOdometer = 43), seal))
        assertFalse(Seals.matches(trip.copy(notes = null), seal))
        assertFalse(Seals.matches(trip, seal.copy(kind = Seals.EDITED)))
        assertFalse(Seals.matches(trip, seal.copy(createdAt = 2L)))
    }

    @Test
    fun `Signatur wird mit dem gespeicherten Schlüssel geprüft`() {
        val key = newKey()
        val seal = Seals.create(trip, Seals.CANCELLED, 5L, signer(key))
        assertTrue(Seals.matches(trip, seal))
        // Fremder Schlüssel, fehlender Schlüssel, kaputte Signatur
        val other = Base64.getEncoder().encodeToString(newKey().public.encoded)
        assertFalse(Seals.matches(trip, seal.copy(publicKey = other)))
        assertFalse(Seals.matches(trip, seal.copy(publicKey = null)))
        assertFalse(Seals.matches(trip, seal.copy(signature = "AAAA")))
        // Neu gebildeter Hash zu veränderten Daten hilft ohne den Schlüssel nicht
        val changed = trip.copy(endOdometer = 50)
        assertFalse(Seals.matches(changed, seal.copy(hash = Seals.create(changed, Seals.CANCELLED, 5L).hash)))
    }

    @Test
    fun `nur das jüngste Siegel einer Fahrt zählt`() {
        val edited = trip.copy(notes = "Kundentermin")
        val other = trip.copy(id = 8)
        val seals = listOf(
            Seals.create(trip, Seals.ENDED, 1L).copy(id = 1),
            Seals.create(edited, Seals.EDITED, 2L).copy(id = 2),
            Seals.create(other, Seals.ENDED, 3L).copy(id = 3),
            Seals.create(trip.copy(id = 99), Seals.ENDED, 4L).copy(id = 4) // Fahrt fehlt: ignoriert
        )
        assertEquals(emptyList<Long>(), Seals.mismatches(listOf(edited, other), seals))
        assertEquals(listOf(7L), Seals.mismatches(listOf(trip, other), seals)) // auf den alten Stand zurückgesetzt
        assertEquals(listOf(8L), Seals.mismatches(listOf(edited, other.copy(isCancelled = true)), seals))
    }

    @Test
    fun `Zeitstempel bestätigt Daten, Signatur und Schlüssel bzw ohne Signatur die Nutzdaten`() {
        val sha = MessageDigest.getInstance("SHA-256")
        val plain = Seals.create(trip, Seals.ENDED, 1L)
        assertArrayEquals(sha.digest(Seals.payload(trip, Seals.ENDED, 1L)), Seals.imprint(plain))
        val signed = Seals.create(trip, Seals.ENDED, 1L, signer(newKey()))
        val b64 = Base64.getDecoder()
        val bound = signed.hash.hexToByteArray() + b64.decode(signed.signature) + b64.decode(signed.publicKey)
        assertArrayEquals(sha.digest(bound), Seals.imprint(signed))
    }

    @Test
    fun `Fingerabdruck sind die ersten 16 Byte von SHA-256 in Vierergruppen`() {
        assertEquals("BA78 16BF 8F01 CFEA 4141 40DE 5DAE 2223", Seals.fingerprint(Base64.getEncoder().encodeToString("abc".toByteArray())))
    }

    // docs/pruefen.js bildet Nutzdaten und PDF-Signatur selbst nach; die Adresse steht in jedem signierten PDF
    @Test
    fun `Prüfseite passt zu Format, PDF-Markierung und Adresse`() {
        val js = File("../docs/pruefen.js").readText()
        assertTrue(js.contains("'${Seals.FORMAT}'"))
        assertTrue(js.contains("'${Keys.PDF_MARKER.replace("\n", "\\n")}'"))
        assertTrue(File("../docs/" + Export.VERIFY_URL.substringAfterLast('/')).isFile)
    }
}
