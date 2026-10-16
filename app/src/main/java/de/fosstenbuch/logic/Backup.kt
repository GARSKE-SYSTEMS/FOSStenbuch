package de.fosstenbuch.logic

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import de.fosstenbuch.data.AppDatabase
import de.fosstenbuch.data.AppDao
import de.fosstenbuch.data.SavedLocation
import de.fosstenbuch.data.Settings
import de.fosstenbuch.data.Trip
import de.fosstenbuch.data.TripAuditLog
import de.fosstenbuch.data.TripPurpose
import de.fosstenbuch.data.TripTemplate
import de.fosstenbuch.data.Vehicle
import de.fosstenbuch.data.db
import de.fosstenbuch.tracking.TrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ein JSON-Objekt als Map (Werte: String, Number, Boolean, null, Row, List) */
private typealias Row = Map<String, Any?>

/**
 * Datensicherung als JSON-Datei, Wiederherstellung und "Alle Daten löschen".
 *
 * Format "version": 1 – bleibt kompatibel zu allen bisherigen Backups: ein Objekt mit
 * "exportedAt" und je einem Array pro Tabelle. Später hinzugekommene Daten (Vorlagen,
 * Bluetooth-Kopplung, Export-Status, Koordinaten, automatische Fahrten) sind zusätzliche Keys:
 * Alte Backups ohne sie bleiben lesbar, alte App-Versionen ignorieren sie.
 *
 * Entities <-> JSON läuft über einfache Maps und Listen ([encode]/[decode]), damit es ohne
 * Android testbar ist (org.json gibt es in JVM-Unit-Tests nur als Attrappe).
 * Nur [toText] und [fromText] benutzen org.json.
 */
object Backup {

    /** Der komplette Datenbestand der App */
    data class Content(
        val vehicles: List<Vehicle> = emptyList(),
        val purposes: List<TripPurpose> = emptyList(),
        val locations: List<SavedLocation> = emptyList(),
        /** Inklusive automatisch erkannter Fahrten (isGhost) */
        val trips: List<Trip> = emptyList(),
        val templates: List<TripTemplate> = emptyList(),
        val auditLogs: List<TripAuditLog> = emptyList()
    )

    /** Die Hash-Kette eines änderungssicheren Fahrzeugs passt nicht zum Backup-Inhalt */
    class BrokenChain(message: String) : Exception(message)

    private const val FILE_PREFIX = "fosstenbuch_backup_"

    // ── Datei & Datenbank ────────────────────────────────────────────────────

    /**
     * Schreibt das Backup nach cacheDir/backup und liefert die Datei (zum Teilen per shareFile).
     * Frühere Backup-Dateien im Cache werden dabei gelöscht – sie enthalten sämtliche Fahrtdaten.
     */
    suspend fun export(context: Context): File {
        val db = context.db
        val dao = db.dao()
        // Eine Transaktion = ein stimmiger Stand (z.B. kein Protokolleintrag ohne seine Fahrt)
        val content = db.withTransaction {
            val vehicles = dao.allVehicles()
            // Ältere App-Versionen haben automatische Fahrten in die Kette eingerechnet, die im
            // Backup fehlen. Neu berechnen heilt solche Ketten, sonst scheitert die Wiederherstellung.
            vehicles.filter { it.auditProtected }.forEach { Trips.recomputeChain(context, it.id) }
            // Eine laufende Aufzeichnung ist noch keine Fahrt und fehlt deshalb (backupTrips):
            // Wiederhergestellt würde sie als Dauer-Aufzeichnung die Bluetooth-Erkennung blockieren.
            Content(vehicles, dao.allPurposes(), dao.allLocations(), dao.backupTrips(), dao.allTemplates(), dao.allAuditLogs())
        }
        return withContext(Dispatchers.IO) {
            val text = toText(encode(content, System.currentTimeMillis()))
            // Die Vorversion legte Backups direkt in cacheDir ab und räumte sie nie auf
            context.cacheDir.listFiles { f -> f.isFile && f.name.startsWith(FILE_PREFIX) && f.extension == "json" }?.forEach { it.delete() }
            // Eigener Unterordner: Nur exports/ und backup/ sind über den FileProvider teilbar
            val dir = File(context.cacheDir, "backup").apply { deleteRecursively(); mkdirs() }
            val name = FILE_PREFIX + SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.ROOT).format(Date()) + ".json"
            File(dir, name).apply { writeText(text) }
        }
    }

    /**
     * Ersetzt ALLE Daten durch das Backup in [uri]. Die Datei wird zuerst komplett gelesen und
     * geprüft, erst danach wird in einer Transaktion geleert und eingefügt. Jeder Fehler – auch
     * ein Abbruch mittendrin – lässt die bisherigen Daten unverändert. Die Standard-Kategorie
     * wird schon vor dem Einspielen abgewählt und bleibt es auch, falls das Einspielen scheitert.
     * @param force trotz [BrokenChain] wiederherstellen (nur nach Rückfrage beim Nutzer)
     * @throws IllegalStateException während einer GPS-Aufzeichnung
     */
    suspend fun restore(context: Context, uri: Uri, force: Boolean) {
        // Nicht während einer Aufzeichnung: Der Dienst schreibt bis zu seinem (asynchronen) Stopp
        // GPS-Stände per Fahrt-ID – nach dem Einspielen träfe das eine fremde, wiederhergestellte
        // Fahrt (verfälschte Daten, gebrochene Hash-Kette). Vorher stoppen hilft nicht, weil das
        // Beenden der Aufzeichnung die alte Fahrt danach über die neue Zeile schreiben könnte.
        check(TrackingService.live.value == null) { "Bitte zuerst die laufende Fahrt-Aufzeichnung beenden" }
        val content = withContext(Dispatchers.IO) {
            val text = context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
                ?: throw IllegalArgumentException("Backup-Datei konnte nicht gelesen werden")
            // Auch die Kettenprüfung (SHA-256 je Fahrt) dauert bei Tausenden Fahrten spürbar –
            // daher hier im Hintergrund statt im Main-Thread des Aufrufers (viewModelScope)
            decode(fromText(text)).also { if (!force) checkChains(it) }
        }
        // Vorher zurücksetzen: Die Kategorie-IDs stammen aus dem Backup und können dort eine ganz
        // andere Kategorie bezeichnen. Danach gesetzt, entfiele das, wenn der Aufrufer abbricht,
        // während Room die Transaktion noch committet – die Daten wären dann trotzdem ersetzt.
        Settings.set(context, Settings.DEFAULT_PURPOSE_ID, null)
        val db = context.db
        db.withTransaction {
            val dao = db.dao()
            clear(dao)
            // Reihenfolge wegen Fremdschlüsseln: Fahrten nach Kategorien/Fahrzeugen, Protokoll nach Fahrten
            dao.insertPurposes(content.purposes)
            dao.insertVehicles(content.vehicles)
            dao.insertLocations(content.locations)
            dao.insertTrips(content.trips)
            dao.insertTemplates(content.templates)
            dao.insertAuditLogs(content.auditLogs)
        }
    }

    /**
     * Löscht alle Daten: die Datenbank in einer Transaktion (danach wieder mit den zwei Standard-Kategorien),
     * anschließend Fahrername und Cache-Dateien
     */
    suspend fun deleteAll(context: Context) {
        val db = context.db
        db.withTransaction {
            val dao = db.dao()
            clear(dao)
            dao.insertPurposes(AppDatabase.DEFAULT_PURPOSES)
        }
        // Die gewählte Standard-Kategorie ist weg (SQLite vergibt ihre ID nicht erneut)
        Settings.set(context, Settings.DEFAULT_PURPOSE_ID, null)
        // Auch die übrigen persönlichen Daten: Fahrername und der ganze Cache (exports/, backup/ und Altdateien
        // der Vorversion, die Exporte und Backups direkt in den Cache-Ordner legte)
        Settings.set(context, Settings.DRIVER_NAME, null)
        withContext(Dispatchers.IO) { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
        // Die aufgezeichnete Fahrt ist gelöscht. Nachzügler-Schreibzugriffe des Dienstes laufen ins
        // Leere, denn gelöschte IDs vergibt SQLite (AUTOINCREMENT) nie erneut.
        if (TrackingService.live.value != null) TrackingService.stop(context)
    }

    /** Leert alle Tabellen; Reihenfolge so, dass keine Fremdschlüssel-Aktionen nötig sind */
    private suspend fun clear(dao: AppDao) {
        dao.deleteAllAuditLogs()
        dao.deleteAllTrips()
        dao.deleteAllTemplates()
        dao.deleteAllLocations()
        dao.deleteAllVehicles()
        dao.deleteAllPurposes()
    }

    // ── Prüfung ──────────────────────────────────────────────────────────────

    /**
     * Prüft je änderungssicherem Fahrzeug die Hash-Kette seiner Fahrten (ohne automatische,
     * nach ID sortiert, siehe Trips.chainHashes) und wirft [BrokenChain] beim ersten Bruch.
     * Die Kette erkennt versehentliche oder naive Änderungen an der Datei; ein echter
     * Manipulationsschutz ist sie nicht (die Formel ist öffentlich).
     */
    fun checkChains(c: Content) {
        for (v in c.vehicles.filter { it.auditProtected }) {
            val chain = c.trips.filter { it.vehicleId == v.id && !it.isGhost }.sortedBy { it.id }
            val broken = Trips.firstBrokenLink(chain) ?: continue
            throw BrokenChain(
                if (broken.chainHash == null) {
                    "Fehlende Hash-Kette für änderungssicheres Fahrzeug ${v.label} bei Fahrt #${broken.id}. " +
                        "Alle Fahrten müssen verifizierbare Chain-Hashes besitzen."
                } else {
                    "Hash-Kette für Fahrzeug ${v.label} ist beschädigt bei Fahrt #${broken.id}. " +
                        "Die Backup-Daten wurden möglicherweise manipuliert."
                }
            )
        }
    }

    // ── JSON-Text (einzige Stelle mit org.json) ──────────────────────────────

    /** Eingerücktes JSON; null-Werte werden als JSON null geschrieben */
    fun toText(root: Row): String = JSONObject(root).toString(2)

    /** JSON-Text als Maps und Listen; JSON null wird zu null. Zahlen kommen als Integer, Long oder Double. */
    @Suppress("UNCHECKED_CAST")
    fun fromText(text: String): Row = plain(JSONObject(text)) as Row

    private fun plain(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associateWith { plain(value.get(it)) }
        is JSONArray -> List(value.length()) { plain(value.get(it)) }
        JSONObject.NULL -> null
        else -> value
    }

    // ── Entities <-> Maps (reine Funktionen, getestet in BackupTest) ─────────

    /**
     * Das Backup als JSON-Objekt. Automatisch erkannte Fahrten stehen getrennt in "ghostTrips":
     * Ältere App-Versionen kennen isGhost nicht und würden sie sonst als echte Fahrten einspielen.
     */
    fun encode(c: Content, exportedAt: Long): Row = mapOf(
        "version" to 1,
        "exportedAt" to exportedAt,
        "vehicles" to c.vehicles.map {
            mapOf(
                "id" to it.id, "make" to it.make, "model" to it.model, "licensePlate" to it.licensePlate,
                "fuelType" to it.fuelType, "isPrimary" to it.isPrimary, "notes" to it.notes,
                "auditProtected" to it.auditProtected,
                "bluetoothDeviceAddress" to it.bluetoothDeviceAddress, "bluetoothDeviceName" to it.bluetoothDeviceName
            )
        },
        "purposes" to c.purposes.map {
            mapOf("id" to it.id, "name" to it.name, "isBusinessRelevant" to it.isBusinessRelevant, "color" to it.color, "isDefault" to it.isDefault)
        },
        "locations" to c.locations.map {
            mapOf(
                "id" to it.id, "name" to it.name, "latitude" to it.latitude, "longitude" to it.longitude,
                "address" to it.address, "usageCount" to it.usageCount, "businessPartner" to it.businessPartner
            )
        },
        "trips" to c.trips.filter { !it.isGhost }.map(::encodeTrip),
        "ghostTrips" to c.trips.filter { it.isGhost }.map(::encodeTrip),
        "templates" to c.templates.map {
            mapOf(
                "id" to it.id, "name" to it.name, "startLocation" to it.startLocation, "endLocation" to it.endLocation,
                "distanceKm" to it.distanceKm, "purpose" to it.purpose, "purposeId" to it.purposeId, "notes" to it.notes,
                "vehicleId" to it.vehicleId, "businessPartner" to it.businessPartner, "route" to it.route
            )
        },
        "auditLogs" to c.auditLogs.map {
            mapOf(
                "id" to it.id, "tripId" to it.tripId, "fieldName" to it.fieldName, "oldValue" to it.oldValue,
                "newValue" to it.newValue, "changedAt" to it.changedAt.time
            )
        }
    )

    private fun encodeTrip(t: Trip): Row = mapOf(
        "id" to t.id, "date" to t.date.time, "startLocation" to t.startLocation, "endLocation" to t.endLocation,
        "distanceKm" to t.distanceKm, "purpose" to t.purpose, "purposeId" to t.purposeId, "notes" to t.notes,
        "startOdometer" to t.startOdometer, "endOdometer" to t.endOdometer, "vehicleId" to t.vehicleId,
        "isCancelled" to t.isCancelled, "cancellationReason" to t.cancellationReason, "isActive" to t.isActive,
        "endTime" to t.endTime?.time, "gpsDistanceKm" to t.gpsDistanceKm, "businessPartner" to t.businessPartner,
        "route" to t.route, "chainHash" to t.chainHash,
        // Fehlen in älteren Backups (beim Lesen dann Standardwerte)
        "isExported" to t.isExported, "isGhost" to t.isGhost,
        "startLatitude" to t.startLatitude, "startLongitude" to t.startLongitude,
        "endLatitude" to t.endLatitude, "endLongitude" to t.endLongitude
    )

    /**
     * Liest ein Backup, auch ältere ohne die neueren Keys (fehlend = Standardwert).
     * Wirft bei ungültigem Inhalt – bevor irgendetwas an der Datenbank geändert wird.
     * Die vier ursprünglichen Arrays sind Pflicht: So ersetzt eine fremde JSON-Datei nie
     * versehentlich alle Daten durch nichts.
     */
    fun decode(root: Row): Content {
        require(((root["version"] as? Number)?.toInt() ?: 0) >= 1) { "Ungültiges Backup-Format" }
        return Content(
            vehicles = root.rows("vehicles").map {
                Vehicle(
                    id = it.long("id"), make = it.string("make"), model = it.string("model"),
                    licensePlate = it.string("licensePlate"), fuelType = it.string("fuelType"),
                    isPrimary = it.flag("isPrimary"), notes = it.stringOrNull("notes"),
                    auditProtected = it.flag("auditProtected"),
                    bluetoothDeviceAddress = it.stringOrNull("bluetoothDeviceAddress"),
                    bluetoothDeviceName = it.stringOrNull("bluetoothDeviceName")
                )
            },
            purposes = root.rows("purposes").map {
                TripPurpose(
                    id = it.long("id"), name = it.string("name"), isBusinessRelevant = it.need("isBusinessRelevant") as Boolean,
                    color = it.stringOrNull("color") ?: "#6200EE", isDefault = it.flag("isDefault")
                )
            },
            locations = root.rows("locations").map {
                SavedLocation(
                    id = it.long("id"), name = it.string("name"), latitude = it.double("latitude"),
                    longitude = it.double("longitude"), address = it.stringOrNull("address"),
                    usageCount = it.numberOrNull("usageCount")?.toInt() ?: 0, businessPartner = it.stringOrNull("businessPartner")
                )
            },
            trips = (root.rows("trips") + root.rows("ghostTrips", required = false)).map(::decodeTrip),
            templates = root.rows("templates", required = false).map {
                TripTemplate(
                    id = it.long("id"), name = it.string("name"), startLocation = it.string("startLocation"),
                    endLocation = it.string("endLocation"), distanceKm = it.double("distanceKm"),
                    purpose = it.string("purpose"), purposeId = it.numberOrNull("purposeId")?.toLong(),
                    notes = it.stringOrNull("notes"), vehicleId = it.numberOrNull("vehicleId")?.toLong(),
                    businessPartner = it.stringOrNull("businessPartner"), route = it.stringOrNull("route")
                )
            },
            auditLogs = root.rows("auditLogs", required = false).map {
                TripAuditLog(
                    id = it.long("id"), tripId = it.long("tripId"), fieldName = it.string("fieldName"),
                    oldValue = it.stringOrNull("oldValue"), newValue = it.stringOrNull("newValue"),
                    changedAt = Date(it.long("changedAt"))
                )
            }
        )
    }

    private fun decodeTrip(o: Row) = Trip(
        id = o.long("id"), date = Date(o.long("date")), startLocation = o.string("startLocation"),
        endLocation = o.string("endLocation"), distanceKm = o.double("distanceKm"), purpose = o.string("purpose"),
        purposeId = o.numberOrNull("purposeId")?.toLong(), notes = o.stringOrNull("notes"),
        startOdometer = o.numberOrNull("startOdometer")?.toInt(), endOdometer = o.numberOrNull("endOdometer")?.toInt(),
        vehicleId = o.numberOrNull("vehicleId")?.toLong(), isCancelled = o.flag("isCancelled"),
        cancellationReason = o.stringOrNull("cancellationReason"), isActive = o.flag("isActive"),
        endTime = o.numberOrNull("endTime")?.let { Date(it.toLong()) }, gpsDistanceKm = o.numberOrNull("gpsDistanceKm")?.toDouble(),
        businessPartner = o.stringOrNull("businessPartner"), route = o.stringOrNull("route"), chainHash = o.stringOrNull("chainHash"),
        isExported = o.flag("isExported"), isGhost = o.flag("isGhost"),
        startLatitude = o.numberOrNull("startLatitude")?.toDouble(), startLongitude = o.numberOrNull("startLongitude")?.toDouble(),
        endLatitude = o.numberOrNull("endLatitude")?.toDouble(), endLongitude = o.numberOrNull("endLongitude")?.toDouble()
    )

    // Lese-Helfer. Wichtig: Fehlender Key und JSON null ergeben null, eine 0 bleibt 0
    // (frühere Versionen machten aus Kilometerstand 0 null und brachen damit die Hash-Kette).
    // JSON-Zahlen kommen je nach Größe als Integer, Long oder Double (280.0 steht als 280 in der Datei),
    // deshalb immer über Number umwandeln.

    private fun Row.need(key: String): Any = get(key) ?: throw IllegalArgumentException("Ungültiges Backup: \"$key\" fehlt")
    private fun Row.long(key: String) = (need(key) as Number).toLong()
    private fun Row.double(key: String) = (need(key) as Number).toDouble()
    private fun Row.string(key: String) = need(key) as String
    private fun Row.numberOrNull(key: String) = get(key) as Number?
    private fun Row.stringOrNull(key: String) = get(key) as String?
    private fun Row.flag(key: String) = get(key) as Boolean? ?: false

    @Suppress("UNCHECKED_CAST")
    private fun Row.rows(key: String, required: Boolean = true): List<Row> =
        (if (required) need(key) else get(key) ?: emptyList<Row>()) as List<Row>
}
