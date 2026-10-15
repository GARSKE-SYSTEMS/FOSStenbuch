package de.fosstenbuch.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.util.Date

// ACHTUNG: Reihenfolge, Typen und Nullbarkeit der Felder bestimmen das DB-Schema.
// Jede Änderung braucht eine Migration (siehe AppDatabase), sonst gehen Nutzerdaten verloren.

/** Eine Fahrt. Auch laufende (isActive) und automatisch erkannte (isGhost) Fahrten liegen hier. */
@Entity(
    tableName = "trips",
    foreignKeys = [
        ForeignKey(entity = TripPurpose::class, parentColumns = ["id"], childColumns = ["purposeId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = Vehicle::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("purposeId"), Index("vehicleId"), Index("date")]
)
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Startzeitpunkt */
    val date: Date,
    val startLocation: String,
    val endLocation: String = "",
    /** Gefahrene Strecke laut Kilometerstand (Ende − Start) */
    val distanceKm: Double = 0.0,
    /** Name der Kategorie zum Zeitpunkt der Fahrt (bleibt bei Umbenennung der Kategorie erhalten) */
    val purpose: String = "",
    val purposeId: Long? = null,
    val notes: String? = null,
    val startOdometer: Int? = null,
    val endOdometer: Int? = null,
    val vehicleId: Long? = null,
    /** Stornierte Fahrten bleiben sichtbar, zählen aber nirgends mit */
    val isCancelled: Boolean = false,
    val cancellationReason: String? = null,
    /** Fahrt läuft gerade (gestartet, noch nicht beendet) */
    val isActive: Boolean = false,
    val endTime: Date? = null,
    /** Per GPS gemessene Strecke (nur Info, maßgeblich ist der Kilometerstand) */
    val gpsDistanceKm: Double? = null,
    val isExported: Boolean = false,
    val businessPartner: String? = null,
    val route: String? = null,
    /** Glied der Hash-Kette bei änderungssicheren Fahrzeugen (siehe Trips.chainHashes) */
    val chainHash: String? = null,
    /** Per Bluetooth automatisch erkannt und noch nicht vom Nutzer übernommen */
    val isGhost: Boolean = false,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val endLatitude: Double? = null,
    val endLongitude: Double? = null
)

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val make: String,
    val model: String,
    val licensePlate: String,
    val fuelType: String,
    /** Wird bei neuen Fahrten vorausgewählt */
    val isPrimary: Boolean = false,
    val notes: String? = null,
    /** Änderungssicher: Fahrten sind gesperrt und per Hash-Kette gesichert. Nicht umkehrbar. */
    val auditProtected: Boolean = false,
    /** MAC-Adresse des Auto-Bluetooth für die automatische Fahrterkennung */
    val bluetoothDeviceAddress: String? = null,
    val bluetoothDeviceName: String? = null
) {
    /** Anzeigename, z.B. "VW Golf (B-AB 123)" */
    val label: String get() = "$make $model ($licensePlate)"
}

/** Fahrt-Kategorie (z.B. "Beruflich", "Privat") */
@Entity(tableName = "trip_purposes")
data class TripPurpose(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isBusinessRelevant: Boolean,
    val color: String = "#6200EE",
    /** Standard-Kategorien können nicht gelöscht werden */
    val isDefault: Boolean = false
)

@Entity(tableName = "saved_locations")
data class SavedLocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val usageCount: Int = 0,
    val businessPartner: String? = null
)

/** Vorlage für wiederkehrende Fahrten (bewusst ohne Fremdschlüssel) */
@Entity(tableName = "trip_templates")
data class TripTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startLocation: String,
    val endLocation: String,
    val distanceKm: Double,
    val purpose: String,
    val purposeId: Long? = null,
    val notes: String? = null,
    val vehicleId: Long? = null,
    val businessPartner: String? = null,
    val route: String? = null
)

/** Eine protokollierte Änderung an einer abgeschlossenen Fahrt */
@Entity(
    tableName = "trip_audit_log",
    foreignKeys = [
        ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("tripId")]
)
data class TripAuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val fieldName: String,
    val oldValue: String?,
    val newValue: String?,
    val changedAt: Date = Date()
)

/** Room speichert Datumswerte als Epoch-Millisekunden */
class DateConverters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time
}
