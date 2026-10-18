package de.fosstenbuch.logic

import android.content.Context
import de.fosstenbuch.data.AppDao
import de.fosstenbuch.data.Seal
import de.fosstenbuch.data.Trip
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Siegel abgeschlossener Fahrten (Format "FOSStenbuch-Siegel-1"): Beim Beenden, Übernehmen, Ändern und
 * Stornieren entsteht aus dem gespeicherten Stand der Fahrt ein SHA-256 über [payload], optional signiert
 * (zeigt, mit welchem Schlüssel gesiegelt wurde) und mit Zeitstempel (belegt den Zeitpunkt).
 * Ohne Zeitstempel erkennt ein Siegel nur versehentliche oder einfache Änderungen – die Formel ist öffentlich, und
 * neu signieren kann der Schlüsselinhaber selbst. Die Prüfdaten im ausführlichen PDF: Export.proofData.
 *
 * Die Nutzdaten müssen Byte für Byte mit docs/pruefen.js übereinstimmen. Format NICHT ändern,
 * sonst passen bestehende Siegel nicht mehr.
 */
object Seals {

    const val FORMAT = "FOSStenbuch-Siegel-1"

    // Anlässe (Seal.kind) – stehen so in den Nutzdaten, nicht umbenennen
    const val ENDED = "Beendet"
    const val ACCEPTED = "Übernommen"
    const val EDITED = "Geändert"
    const val CANCELLED = "Storniert"

    /** Signatur (DER, base64) und öffentlicher Schlüssel (X.509 SPKI, DER, base64) */
    class Signed(val signature: String, val publicKey: String)

    /**
     * Nutzdaten: die Felder der Reihe nach als "<UTF-8-Bytelänge>:<Wert>," bzw. "~," für null.
     * Zahlen dezimal, Wahrheitswerte "true"/"false", Zeiten in epoch ms. Bewusst ohne Kommazahlen
     * (deren Textform unterscheidet sich zwischen Kotlin und JavaScript).
     */
    fun payload(t: Trip, kind: String, createdAt: Long): ByteArray = listOf(
        FORMAT, kind, createdAt, t.id, t.vehicleId, t.date.time, t.endTime?.time, t.startLocation, t.endLocation,
        t.route, t.businessPartner, t.purpose, t.notes, t.startOdometer, t.endOdometer, t.isCancelled, t.cancellationReason
    ).joinToString("") { v -> v?.toString()?.let { "${it.toByteArray().size}:$it," } ?: "~," }.toByteArray()

    /** Siegel zur Fahrt [t]; [sign] signiert die Nutzdaten (null = ohne Signatur) */
    fun create(t: Trip, kind: String, createdAt: Long, sign: (ByteArray) -> Signed? = { null }): Seal {
        val payload = payload(t, kind, createdAt)
        val signed = sign(payload)
        return Seal(
            tripId = t.id, kind = kind, createdAt = createdAt, hash = sha256(payload).toHexString(),
            signature = signed?.signature, publicKey = signed?.publicKey
        )
    }

    /**
     * Siegelt den gespeicherten Stand der Fahrt [id]; innerhalb der Transaktion des Aufrufers aufrufen.
     * Mit [before] (beim Ändern) nur, wenn sich gesiegelte Daten wirklich geändert haben.
     */
    suspend fun write(context: Context, dao: AppDao, id: Long, kind: String, before: Trip? = null) {
        val trip = dao.trip(id) ?: return
        val now = System.currentTimeMillis()
        if (before != null && payload(before, kind, now).contentEquals(payload(trip, kind, now))) return
        dao.insertSeal(create(trip, kind, now) { sign(context, it) })
    }

    /**
     * Signatur mit dem Schlüssel des Nutzers ([Keys]); null = kein (hier nutzbarer) Schlüssel, das Siegel bleibt
     * unsigniert. Ein Signaturfehler darf das Speichern der Fahrt nie verhindern.
     */
    private fun sign(context: Context, payload: ByteArray): Signed? = Keys.load(context)?.let { key ->
        runCatching { Signed(Base64.getEncoder().encodeToString(Keys.sign(key, payload)), key.publicKey) }.getOrNull()
    }

    // ── Prüfung ──────────────────────────────────────────────────────────────

    /** Passen die heutigen Fahrtdaten noch zum Fingerabdruck des Siegels? (ohne Signatur) */
    fun dataMatches(t: Trip, s: Seal): Boolean = sha256(payload(t, s.kind, s.createdAt)).toHexString() == s.hash

    /** Passt das Siegel zur Fahrt? Neu gebildete Nutzdaten (mit Anlass und Zeit des Siegels) und ggf. Signatur */
    fun matches(t: Trip, s: Seal): Boolean {
        if (!dataMatches(t, s)) return false
        return s.signature == null || verify(payload(t, s.kind, s.createdAt), s.signature, s.publicKey ?: return false)
    }

    /** IDs der Fahrten, deren jüngstes Siegel nicht zu ihren Daten passt (nach dem Siegeln verändert) */
    fun mismatches(trips: List<Trip>, seals: List<Seal>): List<Long> {
        val byId = trips.associateBy { it.id }
        return seals.groupBy { it.tripId }.mapNotNull { (id, list) ->
            val trip = byId[id] ?: return@mapNotNull null
            id.takeIf { !matches(trip, list.maxBy { it.id }) }
        }
    }

    /** ECDSA P-256 / SHA-256: [signature] (DER, base64) über [payload] mit [publicKey] (SPKI, base64) */
    fun verify(payload: ByteArray, signature: String, publicKey: String): Boolean = try {
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)))
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(payload)
            verify(Base64.getDecoder().decode(signature))
        }
    } catch (e: Exception) {
        false // kaputter Schlüssel oder kaputte Signatur
    }

    /**
     * Was ein Zeitstempel bestätigt (message imprint): ohne Signatur der Hash der Nutzdaten (= [Seal.hash]),
     * mit Signatur SHA-256(Hash + Signatur + Schlüssel). Nur die Signatur allein reicht nicht: Zu einer alten
     * ECDSA-Signatur lässt sich für geänderte Daten ein passender Schlüssel berechnen.
     */
    fun imprint(s: Seal): ByteArray {
        val signature = s.signature ?: return s.hash.hexToByteArray()
        val b64 = Base64.getDecoder()
        return sha256(s.hash.hexToByteArray() + b64.decode(signature) + b64.decode(s.publicKey.orEmpty()))
    }

    /** Fingerabdruck eines öffentlichen Schlüssels (SPKI, base64): erste 16 Byte von SHA-256, z.B. "AB12 CD34 …" */
    fun fingerprint(publicKey: String): String =
        sha256(Base64.getDecoder().decode(publicKey)).copyOf(16).toHexString().uppercase().chunked(4).joinToString(" ")

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
