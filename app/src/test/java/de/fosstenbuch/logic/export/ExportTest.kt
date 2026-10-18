package de.fosstenbuch.logic.export

import de.fosstenbuch.data.Seal
import de.fosstenbuch.data.Trip
import de.fosstenbuch.data.TripAuditLog
import de.fosstenbuch.data.TripPurpose
import de.fosstenbuch.data.Vehicle
import de.fosstenbuch.logic.Keys
import de.fosstenbuch.logic.Seals
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date
import java.util.zip.InflaterInputStream

class ExportTest {

    /** Zeitpunkt in lokaler Zeit (wie auf dem Gerät) */
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0, s: Int = 0, ms: Int = 0): Date =
        Date.from(LocalDateTime.of(y, m, d, h, min, s, ms * 1_000_000).atZone(ZoneId.systemDefault()).toInstant())

    private fun trip(id: Long, date: Date, purposeId: Long? = 1, vehicleId: Long? = 1, km: Double = 10.0, start: Int? = null, end: Int? = null) =
        Trip(id = id, date = date, startLocation = "A", endLocation = "B", distanceKm = km, purposeId = purposeId, vehicleId = vehicleId,
            startOdometer = start, endOdometer = end)

    private val business = TripPurpose(id = 1, name = "Beruflich", isBusinessRelevant = true)
    private val private = TripPurpose(id = 2, name = "Privat", isBusinessRelevant = false)
    private val golf = Vehicle(id = 1, make = "VW", model = "Golf", licensePlate = "B-AB 123", fuelType = "Benzin", auditProtected = true)
    private val bmw = Vehicle(id = 2, make = "BMW", model = "320d", licensePlate = "M-XY 9", fuelType = "Diesel", auditProtected = true)
    private val year2024 = Options(from = LocalDate.of(2024, 1, 1), to = LocalDate.of(2024, 12, 31))

    @Test
    fun `Zeitraum in lokaler Zeit, Bis-Tag vollständig, sortiert, ohne laufende und automatische Fahrten`() {
        val trips = listOf(
            trip(1, at(2024, 12, 31, 23, 59, 59, 999)),
            trip(2, at(2024, 1, 1, 0, 0)),
            trip(3, at(2025, 1, 1, 0, 0)),
            trip(4, at(2023, 12, 31, 23, 59, 59, 999)),
            trip(5, at(2024, 6, 1)).copy(isActive = true),
            trip(6, at(2024, 6, 1)).copy(isGhost = true),
            trip(7, at(2024, 6, 1)).copy(isCancelled = true)
        )
        assertEquals(listOf(2L, 7L, 1L), Export.filter(trips, year2024, listOf(1, 2)).map { it.id })
        // Von nach Bis ergibt nichts
        assertTrue(Export.filter(trips, year2024.copy(from = LocalDate.of(2025, 1, 1)), listOf(1, 2)).isEmpty())
    }

    @Test
    fun `Fahrzeug- und Kategoriefilter, leere Auswahl ergibt keine Fahrten`() {
        val trips = listOf(trip(1, at(2024, 3, 1), purposeId = 1), trip(2, at(2024, 3, 2), purposeId = 2, vehicleId = 2),
            trip(3, at(2024, 3, 3), purposeId = null))
        val ids = listOf(1L, 2L)
        fun result(o: Options) = Export.filter(trips, o, ids).map { it.id }
        // Nichts abgewählt: alles, auch Fahrten ohne Kategorie
        assertEquals(listOf(1L, 2L, 3L), result(year2024))
        assertEquals(listOf(1L), result(year2024.copy(excludedPurposes = setOf(2))))
        assertEquals(emptyList<Long>(), result(year2024.copy(excludedPurposes = setOf(1, 2))))
        // Abgewählte, inzwischen gelöschte Kategorie zählt nicht als Einschränkung
        assertEquals(listOf(1L, 2L, 3L), result(year2024.copy(excludedPurposes = setOf(99))))
        assertEquals(listOf(2L), result(year2024.copy(vehicleId = 2)))
    }

    @Test
    fun `ausführliches PDF ignoriert den Kategoriefilter und braucht genau ein Fahrzeug`() {
        val trips = listOf(trip(1, at(2024, 3, 1), purposeId = 1), trip(2, at(2024, 3, 2), purposeId = 2),
            trip(3, at(2024, 3, 3), purposeId = null), trip(4, at(2024, 3, 4), vehicleId = 2))
        val detailed = year2024.copy(format = Format.DETAILED_PDF, vehicleId = 1, excludedPurposes = setOf(1, 2))
        assertEquals(listOf(1L, 2L, 3L), Export.filter(trips, detailed, listOf(1L, 2L)).map { it.id })
        // Das einfache PDF behält seine Filter
        assertEquals(emptyList<Long>(), Export.filter(trips, detailed.copy(format = Format.PDF), listOf(1L, 2L)).map { it.id })
        assertFalse(detailed.needsVehicle)
        assertTrue(detailed.copy(vehicleId = null).needsVehicle)
        assertFalse(year2024.copy(format = Format.PDF).needsVehicle)
    }

    @Test
    fun `nicht übernommene automatische Fahrten im Zeitraum beim gewählten Fahrzeug`() {
        val ghosts = listOf(
            trip(1, at(2024, 3, 1)).copy(isGhost = true, endTime = at(2024, 3, 1, 13)),
            trip(2, at(2024, 3, 2)).copy(isGhost = true),                                         // läuft noch
            trip(3, at(2025, 1, 1)).copy(isGhost = true, endTime = at(2025, 1, 1, 13)),           // anderer Zeitraum
            trip(4, at(2024, 3, 4), vehicleId = 2).copy(isGhost = true, endTime = at(2024, 3, 4, 13)),
            trip(5, at(2024, 3, 5))                                                               // keine automatische
        )
        assertEquals(1, Export.pendingGhosts(ghosts, year2024.copy(vehicleId = 1)))
        assertEquals(2, Export.pendingGhosts(ghosts, year2024))
    }

    @Test
    fun `Nur neue Fahrten gilt nur für CSV`() {
        val trips = listOf(trip(1, at(2024, 3, 1)).copy(isExported = true), trip(2, at(2024, 3, 2)))
        assertEquals(listOf(2L), Export.filter(trips, year2024, listOf(1)).map { it.id })
        assertEquals(listOf(1L, 2L), Export.filter(trips, year2024.copy(onlyNew = false), listOf(1)).map { it.id })
        assertEquals(listOf(1L, 2L), Export.filter(trips, year2024.copy(format = Format.DETAILED_PDF), listOf(1)).map { it.id })
        assertTrue(year2024.copy(format = Format.DETAILED_PDF, includeAudit = false).withAudit)
    }

    @Test
    fun `CSV-Felder werden nach RFC 4180 gequotet und Formeln entschärft`() {
        assertEquals("Berlin", Export.cell("Berlin"))
        assertEquals("\"a;b\"", Export.cell("a;b"))
        assertEquals("\"sagt \"\"hi\"\"\"", Export.cell("sagt \"hi\""))
        assertEquals("\"a\rb\"", Export.cell("a\rb"))
        assertEquals("\"a\nb\"", Export.cell("a\nb"))
        assertEquals("'=HYPERLINK(\"x\")".let { "\"" + it.replace("\"", "\"\"") + "\"" }, Export.cell("=HYPERLINK(\"x\")"))
        assertEquals("'- Rückfahrt", Export.cell("- Rückfahrt"))
        assertEquals("'+49", Export.cell("+49"))
        assertEquals("'@x", Export.cell("@x"))
        assertEquals("", Export.cell(""))
    }

    @Test
    fun `CSV enthält Kopf, Fahrten, Storno und eindeutig zugeordnetes Änderungsprotokoll`() {
        val t1 = trip(1, at(2024, 2, 5, 8, 15), km = 1234.5, start = 100, end = 1334)
            .copy(startLocation = "Berlin", endLocation = "Hamburg", purpose = "Kunde; Termin", businessPartner = "ACME", endTime = at(2024, 2, 5, 9, 0))
        val t2 = trip(2, at(2024, 2, 6, 10, 0), purposeId = 2, vehicleId = 2, km = 5.0)
            .copy(isCancelled = true, cancellationReason = "Doppelt")
        val log = TripAuditLog(tripId = 1, fieldName = "Zielort", oldValue = "Hamburg-Nord", newValue = "Hamburg", changedAt = at(2024, 2, 7, 10, 30))
        // Der Polo hat eine Bearbeitungssperre, wird aber nicht exportiert → darf nicht im Hinweis stehen
        val polo = Vehicle(id = 3, make = "VW", model = "Polo", licensePlate = "P-1", fuelType = "Benzin", auditProtected = true)
        val data = ExportData(year2024, listOf(t1, t2), listOf(business, private), listOf(golf, bmw.copy(auditProtected = false), polo),
            mapOf(1L to listOf(log)), driver = "Max Muster")

        val csv = Export.csv(data)
        assertTrue(csv.startsWith("\uFEFF"))
        val head = listOf(
            "Datenexport – kein Fahrtenbuch zur Vorlage",
            "",
            "Fahrer;Max Muster",
            "",
            "Fahrzeuge mit Bearbeitungssperre in der App (gilt nicht für diese Datei)",
            "VW Golf;B-AB 123",
            "",
            "Fahrt-ID;Startdatum/-zeit;Enddatum/-zeit;Startort;Zielort;Distanz (km);Zweck;Kategorie;Geschäftlich;Geschäftspartner;Route;" +
                "Fahrzeug;Kennzeichen;Km-Stand Start;Km-Stand Ende;Notizen;Storniert;Stornogrund;Änderungen",
            "1;05.02.2024 08:15;05.02.2024 09:00;Berlin;Hamburg;1234,50;\"Kunde; Termin\";Beruflich;Ja;ACME;;VW Golf;B-AB 123;100;1334;;;;1",
            "2;06.02.2024 10:00;;A;B;5,00;;Privat;Nein;;;BMW 320d;M-XY 9;;;;STORNIERT;Doppelt;0"
        )
        assertEquals(
            head + listOf(
                "",
                "Änderungsprotokoll",
                "Fahrt;Feld;Alter Wert;Neuer Wert;Geändert am",
                "05.02.2024 08:15 Berlin → Hamburg;Zielort;Hamburg-Nord;Hamburg;07.02.2024 10:30",
                ""
            ),
            csv.removePrefix("\uFEFF").split("\r\n")
        )
        // Ohne Änderungsprotokoll: Anzahl der Änderungen bleibt, das Protokoll selbst entfällt
        val withoutLog = Export.csv(ExportData(year2024.copy(includeAudit = false), listOf(t1, t2), listOf(business, private),
            listOf(golf, bmw.copy(auditProtected = false), polo), mapOf(1L to listOf(log)), driver = "Max Muster"))
        assertEquals(head + "", withoutLog.removePrefix("\uFEFF").split("\r\n"))
    }

    @Test
    fun `Summen ohne stornierte Fahrten, Fahrten ohne Kategorie sind privat`() {
        val trips = listOf(
            trip(1, at(2024, 1, 1), purposeId = 1, km = 100.0),
            trip(2, at(2024, 1, 2), purposeId = 2, km = 20.0),
            trip(3, at(2024, 1, 3), purposeId = null, km = 5.0),
            trip(4, at(2024, 1, 4), purposeId = 1, km = 300.0).copy(isCancelled = true)
        )
        val s = ExportData(year2024, trips, listOf(business, private), emptyList()).summary()
        assertEquals(3, s.count)
        assertEquals(125.0, s.km, 0.001)
        assertEquals(1, s.businessCount)
        assertEquals(100.0, s.businessKm, 0.001)
        assertEquals(2, s.privateCount)
        assertEquals(25.0, s.privateKm, 0.001)
        assertEquals(1, s.cancelled)
    }

    @Test
    fun `Monate werden mit Jahr gruppiert und chronologisch sortiert`() {
        val trips = listOf(trip(1, at(2023, 7, 3)), trip(2, at(2024, 1, 2)), trip(3, at(2023, 1, 5)), trip(4, at(2024, 1, 31, 23, 59)))
        val months = Export.months(trips)
        assertEquals(listOf(YearMonth.of(2023, 1), YearMonth.of(2023, 7), YearMonth.of(2024, 1)), months.keys.toList())
        assertEquals(listOf(2L, 4L), months[YearMonth.of(2024, 1)]!!.map { it.id })
        assertEquals("Januar 2024", Export.monthName(YearMonth.of(2024, 1)))
        assertEquals("März 2023", Export.monthName(YearMonth.of(2023, 3)))
    }

    @Test
    fun `Km-Lücken je Fahrzeug, fehlende Stände und stornierte Fahrten`() {
        val trips = listOf(
            trip(1, at(2024, 1, 1), vehicleId = 1, start = 100, end = 150),
            trip(2, at(2024, 1, 2), vehicleId = 2, start = 900, end = 950),  // anderes Fahrzeug: keine Lücke
            trip(3, at(2024, 1, 3), vehicleId = 1, start = 150, end = 200),  // lückenlos
            trip(4, at(2024, 1, 4), vehicleId = 1, start = 210, end = 220),  // Lücke 200 → 210
            trip(5, at(2024, 1, 5), vehicleId = 1, start = 220, end = null), // Stand fehlt
            trip(6, at(2024, 1, 6), vehicleId = 1, start = 1, end = 2).copy(isCancelled = true),
            trip(7, at(2024, 1, 7), vehicleId = 1, start = 230, end = 240)   // nach fehlendem Stand: kein Vergleich
        )
        assertEquals(
            listOf("Nr. 4: beginnt bei 210, die vorherige Fahrt endete bei 200", "Nr. 5: Km-Stand fehlt"),
            Export.odometerGaps(trips)
        )
    }

    @Test
    fun `Km-Stand zu Beginn und am Ende ohne stornierte Fahrten`() {
        val trips = listOf(
            trip(1, at(2024, 1, 1), start = 1, end = 5).copy(isCancelled = true),
            trip(2, at(2024, 1, 2), start = 100, end = 150),
            trip(3, at(2024, 1, 3), start = 160, end = null),
            trip(4, at(2024, 1, 4), start = 170, end = 200),
            trip(5, at(2024, 1, 5), start = 900, end = 999).copy(isCancelled = true)
        )
        assertEquals(100 to 200, Export.odometerRange(trips))
        assertEquals(null to null, Export.odometerRange(emptyList()))
    }

    @Test
    fun `Stand der Bearbeitungssperre`() {
        assertEquals(
            "Keine Bearbeitungssperre – abgeschlossene Fahrten bleiben bearbeitbar; Änderungen werden protokolliert",
            Export.lockStatus(golf.copy(auditProtected = false))
        )
        assertEquals("Bearbeitungssperre aktiv (Zeitpunkt der Aktivierung nicht erfasst)", Export.lockStatus(golf))
        assertEquals("Bearbeitungssperre aktiv seit 05.02.2024 08:15", Export.lockStatus(golf.copy(protectedSince = at(2024, 2, 5, 8, 15).time)))
    }

    @Test
    fun `Siegel-Spalte nach dem jüngsten Siegel`() {
        val t = trip(1, at(2024, 2, 5, 8, 15), start = 100, end = 150)
        val ended = Seals.create(t, Seals.ENDED, 1L).copy(id = 1)
        assertEquals("–", Export.sealMark(t, emptyList()))
        assertEquals("✓", Export.sealMark(t, listOf(ended)))
        assertEquals("✓ 05.02. 09:01", Export.sealMark(t, listOf(ended.copy(tsTime = at(2024, 2, 5, 9, 1).time, tsToken = "x"))))
        // Nach dem Siegeln verändert
        assertEquals("!", Export.sealMark(t.copy(endOdometer = 151), listOf(ended)))
        // Älteres Siegel passt nicht mehr, das jüngste schon
        val edited = t.copy(notes = "Kundentermin")
        assertEquals("✓", Export.sealMark(edited, listOf(ended, Seals.create(edited, Seals.EDITED, 2L).copy(id = 2))))
        // Signiert
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val signed = Seals.create(t, Seals.ENDED, 3L) { payload ->
            val sig = Signature.getInstance("SHA256withECDSA").run { initSign(pair.private); update(payload); sign() }
            Seals.Signed(Base64.getEncoder().encodeToString(sig), Base64.getEncoder().encodeToString(pair.public.encoded))
        }
        assertEquals("✓ sig.", Export.sealMark(t, listOf(signed)))
        assertEquals("!", Export.sealMark(t, listOf(signed.copy(signature = "AAAA"))))
    }

    @Test
    fun `Änderungen je Fahrt und Siegel-Spalte in den Exportdaten`() {
        val t1 = trip(1, at(2024, 3, 1))
        val t2 = trip(2, at(2024, 3, 2))
        val log = TripAuditLog(tripId = 1, fieldName = "Zielort", oldValue = "X", newValue = "B")
        val data = ExportData(year2024, listOf(t1, t2), listOf(business), listOf(golf), mapOf(1L to listOf(log, log)),
            seals = mapOf(1L to listOf(Seals.create(t1, Seals.EDITED, 1L))))
        assertEquals(2, data.changes(t1))
        assertEquals(0, data.changes(t2))
        assertEquals(mapOf(1L to "✓", 2L to "–"), data.sealMarks)
    }

    @Test
    fun `Korrekturen beim Übernehmen zählen nicht als nachträgliche Änderung`() {
        val t = trip(1, at(2024, 3, 1), start = 12380, end = 12383)
        val accepted = at(2024, 3, 1, 18).time
        fun log(field: String, time: Long) = TripAuditLog(tripId = 1, fieldName = field, oldValue = "x", newValue = "y", changedAt = Date(time))
        fun data(logs: List<TripAuditLog>, vararg seals: Seal) =
            ExportData(year2024, listOf(t), listOf(business), listOf(golf), mapOf(1L to logs), seals = mapOf(1L to seals.toList()))
        // Automatische Fahrt übernommen: Startort und Km-Stand korrigiert, in derselben Transaktion wie das Siegel
        val logs = listOf(log("Startort", accepted - 1), log("Km-Stand Ende", accepted))
        val seal = Seals.create(t, Seals.ACCEPTED, accepted)
        assertEquals(0, data(logs, seal).changes(t))
        // Später geändert zählt; bei Fahrten aus der Zeit vor den Siegeln (erstes Siegel "Geändert" oder keins) zählt alles
        val later = logs + log("Notizen", accepted + 60_000)
        assertEquals(1, data(later, seal, Seals.create(t, Seals.EDITED, accepted + 60_000)).changes(t))
        assertEquals(3, data(later, Seals.create(t, Seals.EDITED, accepted + 60_000)).changes(t))
        assertEquals(3, data(later).changes(t))
    }

    @Test
    fun `Dateiname enthält Zeitraum und Zeitstempel, Anzeige des Zeitraums deutsch`() {
        assertEquals("01.01.2024 – 31.12.2024", Export.range(year2024))
        val now = at(2026, 10, 15, 14, 30, 5)
        assertEquals("fahrtenbuch_2024-01-01_2024-12-31_20261015-143005.csv", Export.fileName(year2024, now))
        assertEquals(
            "fahrtenbuch_ausfuehrlich_2024-01-01_2024-12-31_20261015-143005.pdf",
            Export.fileName(year2024.copy(format = Format.DETAILED_PDF), now)
        )
    }

    // ── Prüfdaten und Anhang im PDF (Gegenstück: docs/pruefen.js) ────────────

    private val pdf = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\nxref\n0 1\n0000000000 65535 f \ntrailer\n<</Size 1>>\nstartxref\n30\n%%EOF\n".toByteArray()

    /** Prüfdaten aus dem Anhang lesen wie docs/pruefen.js: Kommentarzeilen nach der Markierung, base64, zlib */
    private fun readProof(file: ByteArray): String {
        val lines = String(file, Charsets.ISO_8859_1).substringAfterLast(Export.PROOF_MARKER).lines()
            .takeWhile { it.matches(Regex("%[A-Za-z0-9+/=]+")) }
        return InflaterInputStream(Base64.getDecoder().decode(lines.joinToString("") { it.drop(1) }).inputStream()).readBytes().decodeToString()
    }

    @Test
    fun `Prüfdaten verraten nichts Ungedrucktes – Privatfahrt mit Notiz ohne Notiz und Siegel`() {
        val work = trip(1, at(2024, 3, 1), purposeId = 1).copy(businessPartner = "ACME", notes = "Termin", startLatitude = 52.5)
        val doctor = trip(2, at(2024, 3, 2), purposeId = 2).copy(notes = "Arzt")
        val home = trip(3, at(2024, 3, 3), purposeId = 2).copy(notes = "", isCancelled = true, cancellationReason = "doppelt")
        val ended = Seals.create(home.copy(isCancelled = false, cancellationReason = null), Seals.ENDED, 6L).copy(id = 3)
        val old = Seals.create(home.copy(notes = "Kita"), Seals.ENDED, 5L).copy(id = 2) // früherer Stand mit privater Notiz
        val unsealed = trip(4, at(2024, 3, 4), purposeId = 2).copy(businessPartner = "Baumarkt") // aus der Zeit vor den Siegeln
        val data = ExportData(
            year2024.copy(format = Format.DETAILED_PDF, vehicleId = 1), listOf(work, doctor, home, unsealed), listOf(business, private), listOf(golf),
            seals = mapOf(
                1L to listOf(Seals.create(work, Seals.ENDED, 5L).copy(id = 1, tsUrl = "https://tsa.example/?key=geheim")),
                2L to listOf(Seals.create(doctor, Seals.ENDED, 5L).copy(id = 4)),
                3L to listOf(old, ended, Seals.create(home, Seals.CANCELLED, 7L).copy(id = 5))
            )
        )
        val o = JSONObject(Export.proofData(data))
        assertEquals("FOSStenbuch-Pruefdaten", o.getString("format"))
        val trips = o.getJSONArray("trips")
        assertEquals("ACME", trips.getJSONObject(0).getString("businessPartner"))
        assertEquals("Termin", trips.getJSONObject(0).getString("notes"))
        assertFalse(trips.getJSONObject(0).has("startLatitude")) // keine GPS-Daten
        assertTrue(trips.getJSONObject(1).getBoolean("withheld"))
        assertFalse(trips.getJSONObject(1).has("notes"))
        assertFalse(trips.getJSONObject(2).has("withheld")) // leere Notiz verrät nichts
        // Ohne Siegel nichts zu prüfen: Geschäftspartner fehlt, aber nicht als "nur mit dem Backup prüfbar"
        assertFalse(trips.getJSONObject(3).has("withheld"))
        assertFalse(trips.getJSONObject(3).has("businessPartner"))
        // Siegel: nicht das der zurückgehaltenen Fahrt (sein Hash verriete die Notiz), nicht den früheren Stand,
        // wohl aber den Stand vor dem Stornieren; ohne Adresse des Zeitstempeldienstes
        val seals = o.getJSONArray("seals")
        assertEquals(listOf(1L, 3L, 5L), List(seals.length()) { seals.getJSONObject(it).getLong("id") })
        assertFalse(seals.getJSONObject(0).has("tsUrl"))
        assertFalse(o.has("vehicles"))
    }

    @Test
    fun `PDF-Anhang aus Prüfdaten, Signatur und wiederholtem startxref`() {
        val key = Keys.generate()
        val js = File("../docs/pruefen.js").readText()
        val trailer = Regex(js.substringAfter("const TRAILER = /").substringBefore("/;\n"))
        assertTrue(js.contains("const PROOF = '${Export.PROOF_MARKER.replace("\n", "\\n")}';"))
        val full = pdf + Export.pdfTrailer(pdf, "{\"x\":1}", key)
        val text = String(full, Charsets.ISO_8859_1)
        // Endet wie jedes PDF: startxref auf die unveränderte xref-Tabelle, %%EOF im letzten KB
        assertTrue(text.endsWith("\nstartxref\n30\n%%EOF\n"))
        assertEquals("{\"x\":1}", readProof(full))
        // Wie docs/pruefen.js: letzte Signatur-Markierung suchen, signiert sind alle Bytes davor – auch die Prüfdaten
        val at = text.lastIndexOf(Keys.PDF_MARKER)
        assertTrue(at > text.indexOf(Export.PROOF_MARKER))
        val tail = text.substring(at + Keys.PDF_MARKER.length)
        val m = trailer.matchEntire(tail)!!
        assertEquals(key.publicKey, m.groupValues[1])
        assertTrue(Seals.verify(full.copyOf(at), m.groupValues[2], key.publicKey))
        assertFalse(Seals.verify(pdf, m.groupValues[2], key.publicKey))
        assertEquals("30", m.groupValues[3])
        // Die Prüfseite akzeptiert nur genau diese Form – nichts Angehängtes oder Eingeschobenes
        assertFalse(trailer.matches(tail + "1 0 obj\n"))
        assertFalse(trailer.matches(tail.replace("{\"v\":1,", "{\"v\":1,\"x\":\"startxref 0\",")))
        // Ohne Schlüssel nur Prüfdaten, einfaches PDF nur die Signatur, ohne beides nichts
        val unsigned = String(pdf + Export.pdfTrailer(pdf, "{}", null), Charsets.ISO_8859_1)
        assertFalse(unsigned.contains(Keys.PDF_MARKER))
        assertTrue(unsigned.endsWith("\nstartxref\n30\n%%EOF\n"))
        assertEquals(pdf.size, String(pdf + Export.pdfTrailer(pdf, null, key), Charsets.ISO_8859_1).lastIndexOf(Keys.PDF_MARKER))
        assertEquals(0, Export.pdfTrailer(pdf, null, null).size)
    }
}
