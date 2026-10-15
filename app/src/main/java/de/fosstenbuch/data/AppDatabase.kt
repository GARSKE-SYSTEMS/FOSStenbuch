package de.fosstenbuch.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Trip::class, Vehicle::class, TripAuditLog::class, SavedLocation::class, TripPurpose::class, TripTemplate::class],
    version = 11,
    exportSchema = false
)
@TypeConverters(DateConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** Die eine Datenbank-Instanz der App (threadsicher, wird beim ersten Zugriff geöffnet) */
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "fosstenbuch-database")
                .addMigrations(*MIGRATIONS)
                // Für Versionen vor 6 gibt es keine Migrationen mehr. Alle anderen Versionen
                // werden migriert – niemals stillschweigend Fahrten löschen.
                .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4, 5)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) = seedPurposes(db)
                })
                .build()
                .also { instance = it }
        }

        /** Legt die beiden Standard-Kategorien an (neue Datenbank bzw. nach "Alle Daten löschen") */
        fun seedPurposes(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT INTO trip_purposes (name, isBusinessRelevant, color, isDefault) VALUES ('Beruflich', 1, '#6200EE', 1)")
            db.execSQL("INSERT INTO trip_purposes (name, isBusinessRelevant, color, isDefault) VALUES ('Privat', 0, '#018786', 1)")
        }

        private fun migration(from: Int, vararg sql: String) = object : Migration(from, from + 1) {
            override fun migrate(db: SupportSQLiteDatabase) = sql.forEach(db::execSQL)
        }

        private val MIGRATIONS = arrayOf(
            migration(
                6,
                "ALTER TABLE trips ADD COLUMN businessPartner TEXT DEFAULT NULL",
                "ALTER TABLE trips ADD COLUMN route TEXT DEFAULT NULL",
                "ALTER TABLE trip_templates ADD COLUMN businessPartner TEXT DEFAULT NULL",
                "ALTER TABLE trip_templates ADD COLUMN route TEXT DEFAULT NULL"
            ),
            migration(7, "ALTER TABLE saved_locations ADD COLUMN businessPartner TEXT DEFAULT NULL"),
            migration(8, "ALTER TABLE trips ADD COLUMN chainHash TEXT DEFAULT NULL"),
            migration(
                9,
                "ALTER TABLE trips ADD COLUMN isGhost INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE trips ADD COLUMN startLatitude REAL DEFAULT NULL",
                "ALTER TABLE trips ADD COLUMN startLongitude REAL DEFAULT NULL",
                "ALTER TABLE trips ADD COLUMN endLatitude REAL DEFAULT NULL",
                "ALTER TABLE trips ADD COLUMN endLongitude REAL DEFAULT NULL"
            ),
            migration(
                10,
                "ALTER TABLE vehicles ADD COLUMN bluetoothDeviceAddress TEXT DEFAULT NULL",
                "ALTER TABLE vehicles ADD COLUMN bluetoothDeviceName TEXT DEFAULT NULL"
            )
        )
    }
}

/** Kurzform für den Datenbankzugriff aus jedem Context: `context.dao.trips()` */
val Context.db: AppDatabase get() = AppDatabase.get(this)
val Context.dao: AppDao get() = db.dao()
