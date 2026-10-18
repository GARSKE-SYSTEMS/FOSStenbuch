package de.fosstenbuch.logic

import android.content.Context
import de.fosstenbuch.data.Settings
import de.fosstenbuch.data.dao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.UnknownHostException
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * Optionale Zeitstempel nach RFC 3161 (aus, bis der Nutzer sie einschaltet): Ein Zeitstempeldienst bestätigt mit
 * seiner Signatur, dass es den Fingerabdruck eines Siegels ([Seals.imprint]) zu einer bestimmten Zeit gab.
 * Übertragen werden nur dieser SHA-256-Wert und (technisch bedingt) die IP-Adresse. Beweiswert hat das nur bei
 * unabhängigen Diensten – deren privaten Schlüssel hat niemand sonst, auch nicht die App oder ihr Entwickler.
 *
 * Die App prüft Status, Fingerabdruck und Nonce der Antwort; die CMS-Signatur des Dienstes prüfen
 * docs/pruefen.js bzw. `openssl ts -verify`.
 */
object Tsa {

    /** Vorgegebener Dienst: frei nutzbar ohne Konto, per HTTPS, Nutzungsbedingungen geprüft (Stand Oktober 2026) */
    class Provider(val name: String, val country: String, val operator: String, val url: String)

    val PROVIDERS = listOf(
        Provider("FreeTSA", "Deutschland", "FreeTSA.org (privates Projekt)", "https://freetsa.org/tsr"),
        Provider("Sectigo Qualified, eIDAS", "Spanien", "Sectigo (Europe) SL", "https://timestamp.sectigo.com/qualified"),
        Provider("Sectigo", "Vereinigtes Königreich", "Sectigo Limited", "https://timestamp.sectigo.com")
    )
    val DEFAULT_URL = PROVIDERS[0].url

    /** Mindestabstand zwischen zwei Anfragen: Sectigo bittet um 15 s, FreeTSA erlaubt eine pro Sekunde */
    private const val PAUSE_MS = 15_000L

    /** Zeitstempel: [token] = TimeStampToken (CMS ContentInfo, DER), [time] = Zeit laut Dienst (epoch ms) */
    class Stamp(val token: ByteArray, val time: Long)

    /** Fehler des letzten Versuchs (null = zuletzt erfolgreich oder noch keiner) – erscheint nur in den Einstellungen */
    val lastError = MutableStateFlow<String?>(null)

    /** Läuft gerade das Nachholen? */
    val running = MutableStateFlow(false)

    // ── Nachholen ────────────────────────────────────────────────────────────

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var lastRequestMs = -PAUSE_MS

    /**
     * Holt im Hintergrund Zeitstempel für alle Siegel ohne nach, falls eingeschaltet; kehrt sofort zurück.
     * Aufgerufen nach jedem neuen Siegel, beim App-Start und per "Jetzt nachholen". Läuft schon ein Durchgang,
     * wartet dieser Aufruf und schaut danach selbst nach (so geht kein neues Siegel verloren).
     * Beim ersten Fehler (z.B. offline) ist Schluss bis zum nächsten Aufruf.
     */
    fun stampPending(context: Context) {
        val app = context.applicationContext
        scope.launch { lock.withLock { stampAll(app) } }
    }

    private suspend fun stampAll(context: Context) {
        val dao = context.dao
        running.value = true
        try {
            while (true) {
                val seal = dao.nextUnstampedSeal() ?: break
                delay(lastRequestMs + PAUSE_MS - now())
                // Nach der Pause neu lesen: Inzwischen ausgeschaltet oder anderer Dienst gewählt?
                if (!Settings.get(context, Settings.TSA_ENABLED, false)) break
                val url = Settings.get(context, Settings.TSA_URL, DEFAULT_URL)
                lastRequestMs = now()
                val stamp = request(url, Seals.imprint(seal))
                dao.setTimestamp(seal.id, seal.hash, seal.signature, Base64.getEncoder().encodeToString(stamp.token), stamp.time, url)
                lastError.value = null
            }
        } catch (e: Exception) {
            // Kein Absturz und keine Meldung: Das Siegel bleibt ohne Zeitstempel und wird später nachgeholt
            lastError.value = when (e) {
                is UnknownHostException -> "keine Verbindung zum Dienst"
                is SocketTimeoutException -> "Zeitüberschreitung"
                else -> e.message ?: e.javaClass.simpleName
            }
        } finally {
            running.value = false
        }
    }

    private fun now() = System.nanoTime() / 1_000_000

    // ── Anfrage ──────────────────────────────────────────────────────────────

    /** Fragt bei [url] einen Zeitstempel für [digest] (SHA-256) an und prüft die Antwort ([parse]) */
    suspend fun request(url: String, digest: ByteArray): Stamp = withContext(Dispatchers.IO) {
        if (normalizeUrl(url) == null) throw IOException("keine gültige Adresse eingestellt")
        val nonce = BigInteger(64, SecureRandom())
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Content-Type", "application/timestamp-query")
            conn.setRequestProperty("User-Agent", Geo.USER_AGENT) // sonst gingen Gerätemodell und Android-Version mit
            conn.outputStream.use { it.write(query(digest, nonce)) }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            parse(conn.inputStream.use { it.readBytes() }, digest, nonce)
        } finally {
            conn.disconnect()
        }
    }

    /** OID 2.16.840.1.101.3.4.2.1 (SHA-256), DER */
    private val SHA256_OID = der(0x06, byteArrayOf(0x60, 0x86.toByte(), 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01))

    /** TimeStampReq { version 1, messageImprint { sha256 mit NULL, digest }, nonce, certReq TRUE } (DER) */
    fun query(digest: ByteArray, nonce: BigInteger): ByteArray = der(
        0x30,
        der(0x02, byteArrayOf(1)),
        der(0x30, der(0x30, SHA256_OID, der(0x05)), der(0x04, digest)),
        der(0x02, nonce.toByteArray()),
        der(0x01, byteArrayOf(-1))
    )

    /** DER-Element; die Anfrage ist kurz (< 128 Byte je Element), daher immer die kurze Längenangabe */
    private fun der(tag: Int, vararg parts: ByteArray): ByteArray {
        val content = parts.fold(ByteArray(0), ByteArray::plus)
        check(content.size < 0x80)
        return byteArrayOf(tag.toByte(), content.size.toByte()) + content
    }

    // ── Antwort ──────────────────────────────────────────────────────────────

    /** Ein DER-Element in [b] ab [from]: Inhalt von [start] bis [end] (exklusiv) */
    private class Der(val b: ByteArray, val from: Int) {
        val tag = b[from].toInt() and 0xFF
        val start: Int
        val end: Int

        init {
            var i = from + 1
            var length = b[i++].toInt() and 0xFF
            if (length >= 0x80) {
                val count = length and 0x7F
                if (count !in 1..3) throw IOException("ungültige Antwort (Länge)")
                length = 0
                repeat(count) { length = (length shl 8) or (b[i++].toInt() and 0xFF) }
            }
            start = i
            end = i + length
            if (end > b.size) throw IOException("unvollständige Antwort")
        }

        val content: ByteArray get() = b.copyOfRange(start, end)
        val encoded: ByteArray get() = b.copyOfRange(from, end)

        /** Enthaltene Elemente (SEQUENCE, SET, [n] EXPLICIT) */
        val children: List<Der> by lazy {
            val list = mutableListOf<Der>()
            var i = start
            while (i < end) list += Der(b, i).also { i = it.end }
            list
        }
    }

    /**
     * Prüft eine TimeStampResp: Status 0 oder 1, Fingerabdruck = [digest], Nonce = [nonce].
     * Liefert das Token und genTime; wirft IOException.
     */
    fun parse(response: ByteArray, digest: ByteArray, nonce: BigInteger): Stamp = try {
        // TimeStampResp { PKIStatusInfo { status, … }, TimeStampToken }
        val resp = Der(response, 0).children
        val status = BigInteger(resp[0].children[0].content).toInt()
        if (status != 0 && status != 1) throw IOException("Dienst lehnt ab (Status $status)")
        val token = resp.getOrNull(1) ?: throw IOException("Antwort ohne Zeitstempel")
        // ContentInfo { signedData, [0] SignedData { version, digestAlgorithms, encapContentInfo { TSTInfo-OID, [0] OCTET STRING } … } }
        val eContent = token.children[1].children[0].children[2].children[1].children[0]
        // TSTInfo { version, policy, messageImprint, serialNumber, genTime, accuracy?, ordering?, nonce?, … }
        val tst = Der(eContent.content, 0).children
        val imprint = tst[2].children
        if (!imprint[0].children[0].encoded.contentEquals(SHA256_OID) || !imprint[1].content.contentEquals(digest)) {
            throw IOException("Zeitstempel passt nicht zum Siegel")
        }
        val tstNonce = tst.drop(5).firstOrNull { it.tag == 0x02 } // das erste INTEGER nach genTime
        if (tstNonce == null || BigInteger(tstNonce.content) != nonce) throw IOException("Nonce passt nicht")
        Stamp(token.encoded, genTime(String(tst[4].content, Charsets.US_ASCII)))
    } catch (e: RuntimeException) {
        throw IOException("ungültige Antwort", e) // falsch verschachtelt, zu kurz, kaputte Zahl oder Zeit
    }

    /** GeneralizedTime "JJJJMMTThhmmss[.f…]Z" (UTC) in epoch ms */
    fun genTime(text: String): Long {
        val m = Regex("""(\d{14})(?:\.(\d+))?Z""").matchEntire(text) ?: throw IOException("ungültige Zeitangabe")
        val seconds = LocalDateTime.parse(m.groupValues[1], DateTimeFormatter.ofPattern("uuuuMMddHHmmss")).toInstant(ZoneOffset.UTC).toEpochMilli()
        return seconds + (m.groupValues[2] + "00").take(3).toLong()
    }

    // ── Adresse ──────────────────────────────────────────────────────────────

    /** Eingegebene Adresse als URL; ohne Schema gilt http:// (z.B. "192.0.2.5:3180/tsa"). null = ungültig. */
    fun normalizeUrl(text: String): String? {
        val t = text.trim()
        val url = if ("://" in t) t else "http://$t"
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        return url.takeIf { uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrEmpty() }
    }

    /** Unverschlüsselt (http)? Der Zeitstempel bleibt trotzdem fälschungssicher – er ist vom Dienst signiert. */
    fun isCleartext(url: String) = url.startsWith("http://", ignoreCase = true)
}
