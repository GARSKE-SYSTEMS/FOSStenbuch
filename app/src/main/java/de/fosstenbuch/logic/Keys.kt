package de.fosstenbuch.logic

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Optionaler Signaturschlüssel des Nutzers (ECDSA P-256, in Software erzeugt) für Siegel und PDFs.
 * Eine Signatur zeigt, mit welchem Schlüssel signiert wurde und dass die Daten seitdem unverändert sind –
 * nicht, wer den Schlüssel benutzt hat, und nicht den Zeitpunkt (dafür [Tsa]).
 *
 * Auf dem Gerät liegt der private Schlüssel (PKCS#8) verschlüsselt mit einem nicht exportierbaren AES-Schlüssel
 * aus dem AndroidKeyStore. Kommt die Datei per Android-Systembackup auf ein anderes Gerät, lässt sie sich dort
 * nicht entschlüsseln: Der Schlüssel gilt dann als nicht nutzbar und muss per Schlüsseldatei
 * ([exportFile]/[importFile], mit Passwort) übertragen werden.
 */
object Keys {

    /** Schlüsselpaar; [publicKey] = X.509 SubjectPublicKeyInfo (DER), base64 */
    class Key(val privateKey: PrivateKey, val publicKey: String)

    /** Datei in filesDir: drei Zeilen base64 – IV, verschlüsselter privater Schlüssel, öffentlicher Schlüssel */
    private const val FILE = "signing_key"
    private const val ALIAS = "fosstenbuch_signing_wrap"
    private const val AES = "AES/GCM/NoPadding"
    private const val KDF = "PBKDF2-HMAC-SHA256"
    private const val ITERATIONS = 210_000

    /** Anfang jedes P-256-Schlüssels als SPKI (Named Curve) – nur diese Form prüft docs/pruefen.js (WebCrypto) */
    private const val P256_SPKI = "3059301306072a8648ce3d020106082a8648ce3d03010703420004"

    /** Anfang der Signaturzeile am Ende eines PDFs (signiert sind alle Bytes davor) */
    const val PDF_MARKER = "\n%FOSStenbuch-Signatur "

    /** Entschlüsselter Schlüssel (spart den KeyStore-Zugriff bei jedem Siegel) */
    @Volatile private var cached: Key? = null

    // ── Gerät ────────────────────────────────────────────────────────────────

    /** Nutzbarer Schlüssel; null = keiner oder auf diesem Gerät nicht entschlüsselbar. Blockiert (Datei, KeyStore). */
    fun load(context: Context): Key? = cached ?: try {
        val (iv, data, publicKey) = File(context.filesDir, FILE).readLines()
        val wrap = keyStore().getKey(ALIAS, null) as SecretKey // fehlt nach Gerätewechsel -> Exception
        val cipher = Cipher.getInstance(AES).apply { init(Cipher.DECRYPT_MODE, wrap, GCMParameterSpec(128, b64(iv))) }
        key(cipher.doFinal(b64(data)), publicKey).also { cached = it }
    } catch (e: Exception) {
        null
    }

    /** Gespeicherter öffentlicher Schlüssel, auch wenn der private hier nicht nutzbar ist; null = kein Schlüssel */
    fun storedPublicKey(context: Context): String? = runCatching { File(context.filesDir, FILE).readLines()[2] }.getOrNull()

    /** Speichert [key] verschlüsselt und ersetzt einen bisherigen Schlüssel */
    fun save(context: Context, key: Key) {
        // Der KeyStore erzeugt den IV selbst (eigene IVs lehnt er ab)
        val cipher = Cipher.getInstance(AES).apply { init(Cipher.ENCRYPT_MODE, wrapKey()) }
        val data = cipher.doFinal(key.privateKey.encoded)
        // Erst komplett schreiben, dann umbenennen: Ein Abbruch mittendrin lässt den bisherigen Schlüssel heil
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeText(listOf(b64(cipher.iv), b64(data), key.publicKey).joinToString("\n"))
        check(tmp.renameTo(File(context.filesDir, FILE))) { "Schlüssel konnte nicht gespeichert werden" }
        cached = key
    }

    fun remove(context: Context) {
        File(context.filesDir, FILE).delete()
        cached = null
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    /** AES-Schlüssel im KeyStore (ohne Nutzer-Authentifizierung), beim ersten Mal erzeugt */
    private fun wrapKey(): SecretKey = keyStore().getKey(ALIAS, null) as SecretKey?
        ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()

    // ── Schlüssel, Signatur, Schlüsseldatei (ohne Android, getestet in KeysTest) ─

    fun generate(): Key {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        return Key(pair.private, b64(pair.public.encoded))
    }

    /** SHA256withECDSA, Ergebnis DER */
    fun sign(key: Key, data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run { initSign(key.privateKey); update(data); sign() }

    /**
     * Schlüssel aus PKCS#8 und SPKI (base64); nur P-256 (eine Schlüsseldatei kann auch von außerhalb stammen,
     * z.B. openssl), per Probesignatur geprüft, dass beide zusammengehören
     */
    private fun key(pkcs8: ByteArray, publicKey: String): Key {
        val spki = b64(publicKey)
        require(spki.size == 91 && spki.toHexString().startsWith(P256_SPKI)) { "Nur ECDSA-P-256-Schlüssel werden unterstützt" }
        val key = Key(KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8)), publicKey)
        val probe = "FOSStenbuch".toByteArray()
        require(Seals.verify(probe, b64(sign(key, probe)), publicKey)) { "Privater und öffentlicher Schlüssel passen nicht zusammen" }
        return key
    }

    /** Schlüsseldatei zum Übertragen: privater Schlüssel mit AES-256-GCM, Schlüssel per PBKDF2 aus [password] */
    fun exportFile(key: Key, password: CharArray): String {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance(AES).apply { init(Cipher.ENCRYPT_MODE, passwordKey(password, salt, ITERATIONS), GCMParameterSpec(128, iv)) }
        return JSONObject()
            .put("format", "fosstenbuch-key").put("version", 1)
            .put("kdf", KDF).put("iterations", ITERATIONS)
            .put("salt", b64(salt)).put("iv", b64(iv)).put("data", b64(cipher.doFinal(key.privateKey.encoded)))
            .put("publicKey", key.publicKey)
            .toString(2)
    }

    /** Liest eine Schlüsseldatei ([exportFile]); wirft IllegalArgumentException mit lesbarer Meldung */
    fun importFile(text: String, password: CharArray): Key {
        val o = runCatching { JSONObject(text) }.getOrNull()
        require(o != null && o.optString("format") == "fosstenbuch-key" && o.optInt("version") == 1 && o.optString("kdf") == KDF) {
            "Keine FOSStenbuch-Schlüsseldatei"
        }
        val iterations = o.getInt("iterations")
        require(iterations in 1..10_000_000) { "Ungültige Schlüsseldatei" } // sonst rechnete PBKDF2 ewig
        val pkcs8 = try {
            Cipher.getInstance(AES).apply {
                init(Cipher.DECRYPT_MODE, passwordKey(password, b64(o.getString("salt")), iterations), GCMParameterSpec(128, b64(o.getString("iv"))))
            }.doFinal(b64(o.getString("data")))
        } catch (e: GeneralSecurityException) {
            throw IllegalArgumentException("Falsches Passwort oder beschädigte Datei")
        }
        return key(pkcs8, o.getString("publicKey"))
    }

    private fun passwordKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey = SecretKeySpec(
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password, salt, iterations, 256)).encoded, "AES"
    )

    // ── PDF ──────────────────────────────────────────────────────────────────

    /**
     * Signaturzeile für ein PDF: Kommentar mit kompaktem JSON, signiert sind alle Bytes [signed] davor.
     * Export.pdfTrailer setzt sie zusammen; docs/pruefen.js sucht die LETZTE Markierung.
     */
    fun pdfSignature(signed: ByteArray, key: Key): String =
        "$PDF_MARKER{\"v\":1,\"alg\":\"ECDSA-P256-SHA256\",\"pub\":\"${key.publicKey}\",\"sig\":\"${b64(sign(key, signed))}\"}"

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun b64(text: String): ByteArray = Base64.getDecoder().decode(text)
}
