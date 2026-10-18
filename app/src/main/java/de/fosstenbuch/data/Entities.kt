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
    /** Glied der Hash-Kette bei Fahrzeugen mit Bearbeitungssperre (siehe Trips.chainHashes) */
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
    /** Bearbeitungssperre: abgeschlossene Fahrten und Fahrzeugdaten gesperrt, Fahrten per Hash-Kette gesichert. Nicht umkehrbar. */
    val auditProtected: Boolean = false,
    /** MAC-Adresse des Auto-Bluetooth für die automatische Fahrterkennung */
    val bluetoothDeviceAddress: String? = null,
    val bluetoothDeviceName: String? = null,
    /** Einschaltzeitpunkt der Bearbeitungssperre (epoch ms); null = aus oder vor Version 12 eingeschaltet (unbekannt) */
    val protectedSince: Long? = null
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
    val color: String = "#0E8DBF",
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

/**
 * Siegel einer abgeschlossenen Fahrt: SHA-256 über ihre gespeicherten Daten (logic/Seals), optional signiert
 * und mit Zeitstempel. Bei jedem Beenden, Übernehmen, Ändern und Stornieren kommt ein neues hinzu.
 */
@Entity(
    tableName = "seals",
    foreignKeys = [
        ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("tripId")]
)
data class Seal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    /** Anlass: Seals.ENDED, ACCEPTED, EDITED oder CANCELLED */
    val kind: String,
    /** Gerätezeit (epoch ms) – Teil der gesiegelten Daten */
    val createdAt: Long,
    /** SHA-256 der Nutzdaten (Seals.payload), hex klein */
    val hash: String,
    /** ECDSA-Signatur der Nutzdaten (DER, base64) */
    val signature: String? = null,
    /** Öffentlicher Schlüssel zur Signatur (X.509 SubjectPublicKeyInfo, DER, base64) */
    val publicKey: String? = null,
    /** RFC-3161-Zeitstempel (TimeStampToken, DER, base64) */
    val tsToken: String? = null,
    /** Zeit laut Zeitstempel (epoch ms) */
    val tsTime: Long? = null,
    /** Adresse des Zeitstempeldienstes */
    val tsUrl: String? = null
)

/** Room speichert Datumswerte als Epoch-Millisekunden */
class DateConverters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time
}
