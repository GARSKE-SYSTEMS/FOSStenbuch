package de.fosstenbuch.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Alle Datenbankzugriffe der App. SQL nur für Lesen/Schreiben – Auswertungen (Summen,
 * Filter nach Zeitraum usw.) passieren in Kotlin, das ist einfacher und bei einigen
 * tausend Fahrten schnell genug.
 *
 * Flow-Abfragen liefern bei jeder Datenänderung automatisch neue Werte (für die UI),
 * suspend-Abfragen lesen genau einmal (für Logik und Hintergrundarbeit).
 *
 * Fahrten-Schreibzugriffe mit Geschäftsregeln (Validierung, Hash-Kette, Änderungsprotokoll)
 * laufen über logic/Trips.kt, nicht direkt über diese Methoden.
 */
@Dao
interface AppDao {

    // ── Fahrten ──────────────────────────────────────────────────────────────

    /** Alle echten Fahrten (ohne automatisch erkannte), neueste zuerst; inkl. laufender und stornierter */
    @Query("SELECT * FROM trips WHERE isGhost = 0 ORDER BY date DESC")
    fun trips(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE isGhost = 0 ORDER BY date DESC")
    suspend fun allTrips(): List<Trip>

    /** Alles fürs Backup: echte Fahrten und abgeschlossene automatische (laufende Aufzeichnung nicht) */
    @Query("SELECT * FROM trips WHERE isGhost = 0 OR endTime IS NOT NULL ORDER BY id")
    suspend fun backupTrips(): List<Trip>

    /** Automatisch erkannte, abgeschlossene und noch nicht übernommene Fahrten (ohne laufende Aufzeichnung) */
    @Query("SELECT * FROM trips WHERE isGhost = 1 AND endTime IS NOT NULL ORDER BY date DESC")
    fun ghostTrips(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE id = :id")
    fun tripFlow(id: Long): Flow<Trip?>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun trip(id: Long): Trip?

    /** Die laufende manuelle Fahrt (es gibt höchstens eine) */
    @Query("SELECT * FROM trips WHERE isActive = 1 AND isGhost = 0 LIMIT 1")
    fun activeTripFlow(): Flow<Trip?>

    @Query("SELECT * FROM trips WHERE isActive = 1 AND isGhost = 0 LIMIT 1")
    suspend fun activeTrip(): Trip?

    /** Die automatische Fahrt, die gerade aufgezeichnet wird (Bluetooth verbunden, noch kein Ende) */
    @Query("SELECT * FROM trips WHERE isGhost = 1 AND endTime IS NULL ORDER BY date DESC LIMIT 1")
    fun recordingGhostFlow(): Flow<Trip?>

    @Query("SELECT * FROM trips WHERE isGhost = 1 AND endTime IS NULL ORDER BY date DESC LIMIT 1")
    suspend fun recordingGhost(): Trip?

    /**
     * Höchster End-Kilometerstand eines Fahrzeugs = Vorschlag für den Start der nächsten Fahrt.
     * Abgeschlossene automatische Fahrten zählen mit (GPS-Schätzung) – sonst begännen mehrere
     * noch nicht geprüfte automatische Fahrten alle beim selben Stand und überlappten sich.
     */
    @Query("SELECT MAX(endOdometer) FROM trips WHERE vehicleId = :vehicleId AND isCancelled = 0")
    suspend fun lastOdometer(vehicleId: Long): Int?

    /** Wie [lastOdometer], aber nur Fahrten vor [date] – Vorschlag beim Prüfen einer automatischen Fahrt */
    @Query("SELECT MAX(endOdometer) FROM trips WHERE vehicleId = :vehicleId AND isCancelled = 0 AND date < :date")
    suspend fun odometerBefore(vehicleId: Long, date: Date): Int?

    /** Fahrten eines Fahrzeugs in der Reihenfolge der Hash-Kette (Einfügereihenfolge) */
    @Query("SELECT * FROM trips WHERE vehicleId = :vehicleId AND isGhost = 0 ORDER BY id")
    suspend fun chainTrips(vehicleId: Long): List<Trip>

    @Query("UPDATE trips SET chainHash = :hash WHERE id = :id")
    suspend fun setChainHash(id: Long, hash: String?)

    /** Max. 999 IDs pro Aufruf (SQLite-Grenze) – Aufrufer teilt größere Listen auf */
    @Query("UPDATE trips SET isExported = 1 WHERE id IN (:ids)")
    suspend fun markExported(ids: List<Long>)

    /**
     * Zwischenstand einer automatischen Aufzeichnung; die erste Position wird zum Startpunkt.
     * Wirkt nur, solange die Aufzeichnung läuft – eine verspätete Position kann eine
     * abgeschlossene oder übernommene (evtl. gesperrte) Fahrt nicht mehr verändern.
     */
    @Query(
        """UPDATE trips SET gpsDistanceKm = :km,
           startLatitude = COALESCE(startLatitude, :lat), startLongitude = COALESCE(startLongitude, :lng),
           endLatitude = :lat, endLongitude = :lng WHERE id = :id AND isGhost = 1 AND endTime IS NULL"""
    )
    suspend fun updateGhostGps(id: Long, km: Double, lat: Double, lng: Double)

    /** Anzahl Fahrten (inkl. automatischer) eines Fahrzeugs – solche Fahrzeuge sind nicht löschbar */
    @Query("SELECT COUNT(*) FROM trips WHERE vehicleId = :vehicleId")
    suspend fun tripCountForVehicle(vehicleId: Long): Int

    /** Anzahl Fahrten einer Kategorie – solche Kategorien sind nicht löschbar */
    @Query("SELECT COUNT(*) FROM trips WHERE purposeId = :purposeId")
    suspend fun tripCountForPurpose(purposeId: Long): Int

    @Insert
    suspend fun insertTrip(trip: Trip): Long

    @Update
    suspend fun updateTrip(trip: Trip)

    @Delete
    suspend fun deleteTrip(trip: Trip)

    // ── Änderungsprotokoll ───────────────────────────────────────────────────

    @Query("SELECT * FROM trip_audit_log WHERE tripId = :tripId ORDER BY changedAt DESC")
    fun auditLog(tripId: Long): Flow<List<TripAuditLog>>

    @Query("SELECT * FROM trip_audit_log ORDER BY changedAt")
    suspend fun allAuditLogs(): List<TripAuditLog>

    @Insert
    suspend fun insertAuditLogs(logs: List<TripAuditLog>)

    // ── Siegel ───────────────────────────────────────────────────────────────

    @Query("SELECT * FROM seals ORDER BY id")
    suspend fun allSeals(): List<Seal>

    @Insert
    suspend fun insertSeal(seal: Seal): Long

    /** Ältestes Siegel ohne Zeitstempel (zum Nachholen, siehe Tsa) */
    @Query("SELECT * FROM seals WHERE tsToken IS NULL ORDER BY id LIMIT 1")
    suspend fun nextUnstampedSeal(): Seal?

    @Query("SELECT COUNT(*) FROM seals WHERE tsToken IS NULL")
    fun unstampedSealCount(): Flow<Int>

    /** Trägt den Zeitstempel ein – nur wenn das Siegel noch dasselbe ist (inzwischen ein Backup eingespielt?) */
    @Query(
        """UPDATE seals SET tsToken = :token, tsTime = :time, tsUrl = :url
           WHERE id = :id AND hash = :hash AND signature IS :signature AND tsToken IS NULL"""
    )
    suspend fun setTimestamp(id: Long, hash: String, signature: String?, token: String, time: Long, url: String)

    // ── Fahrzeuge ────────────────────────────────────────────────────────────

    @Query("SELECT * FROM vehicles ORDER BY isPrimary DESC, make, model")
    fun vehicles(): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicles ORDER BY isPrimary DESC, make, model")
    suspend fun allVehicles(): List<Vehicle>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun vehicle(id: Long): Vehicle?

    @Query("SELECT * FROM vehicles WHERE bluetoothDeviceAddress = :address COLLATE NOCASE LIMIT 1")
    suspend fun vehicleByBluetooth(address: String): Vehicle?

    /** Macht genau dieses Fahrzeug zum Hauptfahrzeug (alle anderen verlieren den Status) */
    @Query("UPDATE vehicles SET isPrimary = (id = :id)")
    suspend fun setPrimaryVehicle(id: Long)

    /** Neu anlegen (id = 0, liefert neue ID) oder aktualisieren (liefert -1) */
    @Upsert
    suspend fun saveVehicle(vehicle: Vehicle): Long

    @Delete
    suspend fun deleteVehicle(vehicle: Vehicle)

    // ── Kategorien ───────────────────────────────────────────────────────────

    @Query("SELECT * FROM trip_purposes ORDER BY isDefault DESC, name")
    fun purposes(): Flow<List<TripPurpose>>

    @Query("SELECT * FROM trip_purposes ORDER BY isDefault DESC, name")
    suspend fun allPurposes(): List<TripPurpose>

    @Query("SELECT * FROM trip_purposes WHERE id = :id")
    suspend fun purpose(id: Long): TripPurpose?

    /** Für die Duplikatprüfung (Groß-/Kleinschreibung egal) */
    @Query("SELECT * FROM trip_purposes WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun purposeByName(name: String): TripPurpose?

    @Upsert
    suspend fun savePurpose(purpose: TripPurpose): Long

    @Delete
    suspend fun deletePurpose(purpose: TripPurpose)

    // ── Gespeicherte Orte ────────────────────────────────────────────────────

    @Query("SELECT * FROM saved_locations ORDER BY usageCount DESC, name")
    fun locations(): Flow<List<SavedLocation>>

    @Query("SELECT * FROM saved_locations ORDER BY usageCount DESC, name")
    suspend fun allLocations(): List<SavedLocation>

    @Query("SELECT * FROM saved_locations WHERE id = :id")
    suspend fun location(id: Long): SavedLocation?

    /** Zählt, wie oft ein Ort in Fahrten verwendet wurde (für die Sortierung) */
    @Query("UPDATE saved_locations SET usageCount = usageCount + 1 WHERE name = :name")
    suspend fun countLocationUsage(name: String)

    @Upsert
    suspend fun saveLocation(location: SavedLocation): Long

    @Delete
    suspend fun deleteLocation(location: SavedLocation)

    // ── Vorlagen ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM trip_templates ORDER BY name")
    suspend fun allTemplates(): List<TripTemplate>

    @Upsert
    suspend fun saveTemplate(template: TripTemplate): Long

    // Vorlagen haben keinen Fremdschlüssel: Verweise vor dem Löschen von Fahrzeug/Kategorie lösen,
    // sonst scheitert später das Speichern einer Fahrt aus der Vorlage
    @Query("UPDATE trip_templates SET vehicleId = NULL WHERE vehicleId = :vehicleId")
    suspend fun detachTemplatesFromVehicle(vehicleId: Long)

    @Query("UPDATE trip_templates SET purposeId = NULL WHERE purposeId = :purposeId")
    suspend fun detachTemplatesFromPurpose(purposeId: Long)

    @Delete
    suspend fun deleteTemplate(template: TripTemplate)

    // ── Backup-Wiederherstellung (immer innerhalb einer Transaktion aufrufen) ─

    @Query("DELETE FROM seals") suspend fun deleteAllSeals()
    @Query("DELETE FROM trip_audit_log") suspend fun deleteAllAuditLogs()
    @Query("DELETE FROM trips") suspend fun deleteAllTrips()
    @Query("DELETE FROM trip_templates") suspend fun deleteAllTemplates()
    @Query("DELETE FROM saved_locations") suspend fun deleteAllLocations()
    @Query("DELETE FROM vehicles") suspend fun deleteAllVehicles()
    @Query("DELETE FROM trip_purposes") suspend fun deleteAllPurposes()

    @Insert suspend fun insertVehicles(items: List<Vehicle>)
    @Insert suspend fun insertPurposes(items: List<TripPurpose>)
    @Insert suspend fun insertLocations(items: List<SavedLocation>)
    @Insert suspend fun insertTrips(items: List<Trip>)
    @Insert suspend fun insertTemplates(items: List<TripTemplate>)
    @Insert suspend fun insertSeals(items: List<Seal>)
}
