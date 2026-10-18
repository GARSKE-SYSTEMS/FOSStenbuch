package de.fosstenbuch.logic

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest

/**
 * Anfrage und Antwort gegen eine echte Antwort von FreeTSA (test/resources/tsa, siehe fixture.json):
 * request.der ist Byte für Byte die Anfrage, wie sie auch `openssl ts -query` bildet.
 */
class TsaTest {

    private fun resource(name: String) = javaClass.getResourceAsStream("/tsa/$name")!!.use { it.readBytes() }
    private val fixture = JSONObject(String(resource("fixture.json")))
    private val digest = fixture.getString("digest").hexToByteArray()
    private val nonce = BigInteger(fixture.getString("nonce"))
    private val response = resource("response.der")

    @Test
    fun `Anfrage ist DER wie openssl ts -query`() {
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(fixture.getString("text").toByteArray()), digest)
        assertArrayEquals(resource("request.der"), Tsa.query(digest, nonce))
    }

    @Test
    fun `Antwort liefert Token und Zeit`() {
        val stamp = Tsa.parse(response, digest, nonce)
        val start = fixture.getInt("tokenOffset")
        assertArrayEquals(response.copyOfRange(start, start + fixture.getInt("tokenLength")), stamp.token)
        assertEquals(fixture.getString("tokenSha256"), MessageDigest.getInstance("SHA-256").digest(stamp.token).toHexString())
        assertEquals(fixture.getLong("genTimeMillis"), stamp.time)
    }

    @Test
    fun `fremder Fingerabdruck, fremde Nonce, Ablehnung und Müll werden erkannt`() {
        assertThrows(IOException::class.java) { Tsa.parse(response, ByteArray(32), nonce) }
        assertThrows(IOException::class.java) { Tsa.parse(response, digest, nonce + BigInteger.ONE) }
        // TimeStampResp { PKIStatusInfo { status 2 = rejection } }
        val rejected = byteArrayOf(0x30, 0x05, 0x30, 0x03, 0x02, 0x01, 0x02)
        assertEquals("Dienst lehnt ab (Status 2)", assertThrows(IOException::class.java) { Tsa.parse(rejected, digest, nonce) }.message)
        assertThrows(IOException::class.java) { Tsa.parse(response.copyOf(100), digest, nonce) }
        assertThrows(IOException::class.java) { Tsa.parse(ByteArray(0), digest, nonce) }
    }

    @Test
    fun `genTime mit und ohne Sekundenbruchteile`() {
        assertEquals(1792315099000L, Tsa.genTime("20261018091819Z"))
        assertEquals(1792315099871L, Tsa.genTime("20261018091819.871Z"))
        assertEquals(1792315099800L, Tsa.genTime("20261018091819.8Z"))
        assertThrows(IOException::class.java) { Tsa.genTime("20261018091819+0200") }
    }

    @Test
    fun `eigene Adresse - URL oder IP, ohne Schema gilt http`() {
        assertEquals("https://freetsa.org/tsr", Tsa.normalizeUrl(" https://freetsa.org/tsr "))
        assertEquals("http://192.0.2.5:3180/tsa", Tsa.normalizeUrl("192.0.2.5:3180/tsa"))
        assertEquals("http://[::1]:318", Tsa.normalizeUrl("[::1]:318"))
        assertNull(Tsa.normalizeUrl(""))
        assertNull(Tsa.normalizeUrl("http://"))
        assertNull(Tsa.normalizeUrl("ftp://tsa.example"))
        assertNull(Tsa.normalizeUrl("tsa example"))
        assertTrue(Tsa.isCleartext("HTTP://192.0.2.1"))
        assertTrue(Tsa.PROVIDERS.none { Tsa.isCleartext(it.url) })
    }
}
