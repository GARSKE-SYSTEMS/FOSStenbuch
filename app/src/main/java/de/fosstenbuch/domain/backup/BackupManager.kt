package de.fosstenbuch.domain.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import de.fosstenbuch.data.local.AppDatabase
import de.fosstenbuch.data.local.SavedLocationDao
import de.fosstenbuch.data.local.TripAuditLogDao
import de.fosstenbuch.data.local.TripDao
import de.fosstenbuch.data.local.TripPurposeDao
import de.fosstenbuch.data.local.VehicleDao
import de.fosstenbuch.data.model.SavedLocation
import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.model.TripAuditLog
import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.model.Vehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val tripDao: TripDao,
    private val vehicleDao: VehicleDao,
    private val tripAuditLogDao: TripAuditLogDao,
    private val savedLocationDao: SavedLocationDao,
    private val tripPurposeDao: TripPurposeDao
) {

    companion object {
        private const val BACKUP_VERSION = 1
        private const val KEY_VERSION = "version"
        private const val KEY_EXPORTED_AT = "exportedAt"
        private const val KEY_VEHICLES = "vehicles"
        private const val KEY_PURPOSES = "purposes"
        private const val KEY_LOCATIONS = "locations"
        private const val KEY_TRIPS = "trips"
        private const val KEY_AUDIT_LOGS = "auditLogs"
    }

    /**
     * Creates a full JSON backup of all data and returns the file.
     */
    suspend fun exportBackup(): File = withContext(Dispatchers.IO) {
        val vehicles = vehicleDao.getAllVehicles().first()
        val purposes = tripPurposeDao.getAllPurposes().first()
        val locations = savedLocationDao.getAllSavedLocations().first()
        val trips = tripDao.getAllTrips().first()

        // Collect all audit logs
        val allAuditLogs = mutableListOf<TripAuditLog>()
        for (trip in trips) {
            val logs = tripAuditLogDao.getAuditLogForTrip(trip.id).first()
            allAuditLogs.addAll(logs)
        }

        val json = JSONObject().apply {
            put(KEY_VERSION, BACKUP_VERSION)
            put(KEY_EXPORTED_AT, System.currentTimeMillis())
            put(KEY_VEHICLES, vehiclesToJson(vehicles))
            put(KEY_PURPOSES, purposesToJson(purposes))
            put(KEY_LOCATIONS, locationsToJson(locations))
            put(KEY_TRIPS, tripsToJson(trips))
            put(KEY_AUDIT_LOGS, auditLogsToJson(allAuditLogs))
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.ROOT)
        val fileName = "fosstenbuch_backup_${dateFormat.format(Date())}.json"
        val file = File(context.cacheDir, fileName)
        file.writeText(json.toString(2))

        Timber.i("Backup created: ${file.name}, ${vehicles.size} vehicles, ${trips.size} trips")
        file
    }

    /**
     * Restores data from a JSON backup at the given URI.
     * WARNING: This will clear all existing data first!
     */
    suspend fun importBackup(uri: Uri) = withContext(Dispatchers.IO) {
        val jsonString = context.contentResolver.openInputStream(uri)?.use {
            it.bufferedReader().readText()
        } ?: throw IllegalArgumentException("Backup-Datei konnte nicht gelesen werden")

        val json = JSONObject(jsonString)
        val version = json.optInt(KEY_VERSION, 0)
        if (version < 1) {
            throw IllegalArgumentException("Ungültiges Backup-Format")
        }

        // Clear all tables
        database.clearAllTables()

        // Restore in correct order: purposes & vehicles first (referenced by trips)
        val purposes = jsonToPurposes(json.getJSONArray(KEY_PURPOSES))
        for (purpose in purposes) {
            tripPurposeDao.insertPurpose(purpose)
        }

        val vehicles = jsonToVehicles(json.getJSONArray(KEY_VEHICLES))
        for (vehicle in vehicles) {
            vehicleDao.insertVehicle(vehicle)
        }

        val locations = jsonToLocations(json.getJSONArray(KEY_LOCATIONS))
        for (location in locations) {
            savedLocationDao.insertSavedLocation(location)
        }

        val trips = jsonToTrips(json.getJSONArray(KEY_TRIPS))
        for (trip in trips) {
            tripDao.insertTrip(trip)
        }

        if (json.has(KEY_AUDIT_LOGS)) {
            val auditLogs = jsonToAuditLogs(json.getJSONArray(KEY_AUDIT_LOGS))
            for (log in auditLogs) {
                tripAuditLogDao.insertAuditLog(log)
            }
        }

        Timber.i("Backup restored: ${vehicles.size} vehicles, ${purposes.size} purposes, ${trips.size} trips")
    }

    /**
     * Deletes ALL data and re-seeds default purposes.
     */
    suspend fun deleteAllData() = withContext(Dispatchers.IO) {
        database.clearAllTables()

        // Re-seed default purposes
        tripPurposeDao.insertPurpose(
            TripPurpose(name = "Beruflich", isBusinessRelevant = true, color = "#6200EE", isDefault = true)
        )
        tripPurposeDao.insertPurpose(
            TripPurpose(name = "Privat", isBusinessRelevant = false, color = "#018786", isDefault = true)
        )

        Timber.i("All data deleted, default purposes re-seeded")
    }

    // --- JSON Serialization ---

    private fun vehiclesToJson(vehicles: List<Vehicle>): JSONArray {
        return JSONArray().apply {
            vehicles.forEach { v ->
                put(JSONObject().apply {
                    put("id", v.id)
                    put("make", v.make)
                    put("model", v.model)
                    put("licensePlate", v.licensePlate)
                    put("fuelType", v.fuelType)
                    put("isPrimary", v.isPrimary)
                    put("notes", v.notes ?: JSONObject.NULL)
                    put("auditProtected", v.auditProtected)
                })
            }
        }
    }

    private fun purposesToJson(purposes: List<TripPurpose>): JSONArray {
        return JSONArray().apply {
            purposes.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("isBusinessRelevant", p.isBusinessRelevant)
                    put("color", p.color)
                    put("isDefault", p.isDefault)
                })
            }
        }
    }

    private fun locationsToJson(locations: List<SavedLocation>): JSONArray {
        return JSONArray().apply {
            locations.forEach { l ->
                put(JSONObject().apply {
                    put("id", l.id)
                    put("name", l.name)
                    put("latitude", l.latitude)
                    put("longitude", l.longitude)
                    put("address", l.address ?: JSONObject.NULL)
                    put("usageCount", l.usageCount)
                })
            }
        }
    }

    private fun tripsToJson(trips: List<Trip>): JSONArray {
        return JSONArray().apply {
            trips.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("date", t.date.time)
                    put("startLocation", t.startLocation)
                    put("endLocation", t.endLocation)
                    put("distanceKm", t.distanceKm)
                    put("purpose", t.purpose)
                    put("purposeId", t.purposeId ?: JSONObject.NULL)
                    put("notes", t.notes ?: JSONObject.NULL)
                    put("startOdometer", t.startOdometer ?: JSONObject.NULL)
                    put("endOdometer", t.endOdometer ?: JSONObject.NULL)
                    put("vehicleId", t.vehicleId ?: JSONObject.NULL)
                    put("isCancelled", t.isCancelled)
                    put("cancellationReason", t.cancellationReason ?: JSONObject.NULL)
                })
            }
        }
    }

    private fun auditLogsToJson(logs: List<TripAuditLog>): JSONArray {
        return JSONArray().apply {
            logs.forEach { l ->
                put(JSONObject().apply {
                    put("id", l.id)
                    put("tripId", l.tripId)
                    put("fieldName", l.fieldName)
                    put("oldValue", l.oldValue ?: JSONObject.NULL)
                    put("newValue", l.newValue ?: JSONObject.NULL)
                    put("changedAt", l.changedAt.time)
                })
            }
        }
    }

    // --- JSON Deserialization ---

    private fun jsonToVehicles(array: JSONArray): List<Vehicle> {
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Vehicle(
                id = o.getLong("id"),
                make = o.getString("make"),
                model = o.getString("model"),
                licensePlate = o.getString("licensePlate"),
                fuelType = o.getString("fuelType"),
                isPrimary = o.optBoolean("isPrimary", false),
                notes = o.optStringOrNull("notes"),
                auditProtected = o.optBoolean("auditProtected", false)
            )
        }
    }

    private fun jsonToPurposes(array: JSONArray): List<TripPurpose> {
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            TripPurpose(
                id = o.getLong("id"),
                name = o.getString("name"),
                isBusinessRelevant = o.getBoolean("isBusinessRelevant"),
                color = o.optString("color", "#6200EE"),
                isDefault = o.optBoolean("isDefault", false)
            )
        }
    }

    private fun jsonToLocations(array: JSONArray): List<SavedLocation> {
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            SavedLocation(
                id = o.getLong("id"),
                name = o.getString("name"),
                latitude = o.getDouble("latitude"),
                longitude = o.getDouble("longitude"),
                address = o.optStringOrNull("address"),
                usageCount = o.optInt("usageCount", 0)
            )
        }
    }

    private fun jsonToTrips(array: JSONArray): List<Trip> {
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Trip(
                id = o.getLong("id"),
                date = Date(o.getLong("date")),
                startLocation = o.getString("startLocation"),
                endLocation = o.getString("endLocation"),
                distanceKm = o.getDouble("distanceKm"),
                purpose = o.getString("purpose"),
                purposeId = o.optLongOrNull("purposeId"),
                notes = o.optStringOrNull("notes"),
                startOdometer = o.optIntOrNull("startOdometer"),
                endOdometer = o.optIntOrNull("endOdometer"),
                vehicleId = o.optLongOrNull("vehicleId"),
                isCancelled = o.optBoolean("isCancelled", false),
                cancellationReason = o.optStringOrNull("cancellationReason")
            )
        }
    }

    private fun jsonToAuditLogs(array: JSONArray): List<TripAuditLog> {
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            TripAuditLog(
                id = o.getLong("id"),
                tripId = o.getLong("tripId"),
                fieldName = o.getString("fieldName"),
                oldValue = o.optStringOrNull("oldValue"),
                newValue = o.optStringOrNull("newValue"),
                changedAt = Date(o.getLong("changedAt"))
            )
        }
    }

    // --- Nullable JSON helpers ---

    private fun JSONObject.optStringOrNull(key: String): String? {
        return if (has(key) && !isNull(key)) optString(key) else null
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        return if (isNull(key)) null else optLong(key, 0).takeIf { it != 0L }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        return if (isNull(key)) null else optInt(key, 0).takeIf { it != 0 }
    }
}
