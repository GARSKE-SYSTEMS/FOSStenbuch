package de.fosstenbuch.logic

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class KeysTest {

    private val key = Keys.generate()
    private val password = "geheim123".toCharArray()

    @Test
    fun `Schlüssel ist P-256 als SPKI und signiert prüfbar`() {
        val spki = Base64.getDecoder().decode(key.publicKey)
        assertEquals(91, spki.size)
        // Named-Curve-Form (secp256r1), wie sie WebCrypto in docs/pruefen.js braucht
        assertTrue(spki.toHexString().startsWith("3059301306072a8648ce3d020106082a8648ce3d03010703420004"))
        val data = "Fahrt".toByteArray()
        assertTrue(Seals.verify(data, Base64.getEncoder().encodeToString(Keys.sign(key, data)), key.publicKey))
    }

    @Test
    fun `Schlüsseldatei lässt sich mit dem Passwort wieder einlesen`() {
        val text = Keys.exportFile(key, password)
        val o = JSONObject(text)
        assertEquals("fosstenbuch-key", o.getString("format"))
        assertEquals(1, o.getInt("version"))
        assertEquals("PBKDF2-HMAC-SHA256", o.getString("kdf"))
        assertEquals(210_000, o.getInt("iterations"))
        assertEquals(16, Base64.getDecoder().decode(o.getString("salt")).size)
        assertEquals(12, Base64.getDecoder().decode(o.getString("iv")).size)
        assertEquals(key.publicKey, o.getString("publicKey"))

        val imported = Keys.importFile(text, password)
        assertEquals(key.publicKey, imported.publicKey)
        assertTrue(imported.privateKey.encoded.contentEquals(key.privateKey.encoded))
    }

    @Test
    fun `falsches Passwort, fremde Datei und vertauschter Schlüssel werden abgelehnt`() {
        val text = Keys.exportFile(key, password)
        assertEquals(
            "Falsches Passwort oder beschädigte Datei",
            assertThrows(IllegalArgumentException::class.java) { Keys.importFile(text, "falsch123".toCharArray()) }.message
        )
        assertThrows(IllegalArgumentException::class.java) { Keys.importFile("{\"version\":1}", password) }
        assertThrows(IllegalArgumentException::class.java) { Keys.importFile("kein JSON", password) }
        // Öffentlicher Schlüssel eines anderen Schlüsselpaars
        val swapped = JSONObject(text).put("publicKey", Keys.generate().publicKey).toString()
        assertThrows(IllegalArgumentException::class.java) { Keys.importFile(swapped, password) }
    }

    @Test
    fun `Schlüsseldatei mit anderer Kurve als P-256 wird abgelehnt`() {
        // z.B. mit openssl erzeugt: Signieren ginge, die Prüfseite (WebCrypto, P-256) könnte aber nichts davon prüfen
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp384r1")) }.generateKeyPair()
        val text = Keys.exportFile(Keys.Key(pair.private, Base64.getEncoder().encodeToString(pair.public.encoded)), password)
        assertEquals(
            "Nur ECDSA-P-256-Schlüssel werden unterstützt",
            assertThrows(IllegalArgumentException::class.java) { Keys.importFile(text, password) }.message
        )
    }
}
