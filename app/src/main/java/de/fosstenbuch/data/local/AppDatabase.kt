package de.fosstenbuch.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.model.Vehicle

@Database(
    entities = [Trip::class, Vehicle::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun vehicleDao(): VehicleDao
}