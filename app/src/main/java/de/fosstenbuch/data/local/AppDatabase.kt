package de.fosstenbuch.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.fosstenbuch.data.model.SavedLocation
import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.model.TripAuditLog
import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.model.TripTemplate
import de.fosstenbuch.data.model.Vehicle

@Database(
    entities = [
        Trip::class,
        Vehicle::class,
        TripAuditLog::class,
        SavedLocation::class,
        TripPurpose::class,
        TripTemplate::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(DateConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun tripAuditLogDao(): TripAuditLogDao
    abstract fun savedLocationDao(): SavedLocationDao
    abstract fun tripPurposeDao(): TripPurposeDao
    abstract fun tripTemplateDao(): TripTemplateDao
}