package de.fosstenbuch.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.fosstenbuch.data.local.AppDatabase
import de.fosstenbuch.data.local.SavedLocationDao
import de.fosstenbuch.data.local.TripAuditLogDao
import de.fosstenbuch.data.local.TripPurposeDao
import de.fosstenbuch.data.local.TripTemplateDao
import de.fosstenbuch.data.repository.SavedLocationRepository
import de.fosstenbuch.data.repository.SavedLocationRepositoryImpl
import de.fosstenbuch.data.repository.TripPurposeRepository
import de.fosstenbuch.data.repository.TripPurposeRepositoryImpl
import de.fosstenbuch.data.repository.TripRepository
import de.fosstenbuch.data.repository.TripRepositoryImpl
import de.fosstenbuch.data.repository.TripTemplateRepository
import de.fosstenbuch.data.repository.TripTemplateRepositoryImpl
import de.fosstenbuch.data.repository.VehicleRepository
import de.fosstenbuch.data.repository.VehicleRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE trips ADD COLUMN businessPartner TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE trips ADD COLUMN route TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE trip_templates ADD COLUMN businessPartner TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE trip_templates ADD COLUMN route TEXT DEFAULT NULL")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE saved_locations ADD COLUMN businessPartner TEXT DEFAULT NULL")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE trips ADD COLUMN chainHash TEXT DEFAULT NULL")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "fosstenbuch-database"
        )
            .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .fallbackToDestructiveMigration()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Seed default trip purposes
                    db.execSQL(
                        "INSERT INTO trip_purposes (name, isBusinessRelevant, color, isDefault) " +
                            "VALUES ('Beruflich', 1, '#6200EE', 1)"
                    )
                    db.execSQL(
                        "INSERT INTO trip_purposes (name, isBusinessRelevant, color, isDefault) " +
                            "VALUES ('Privat', 0, '#018786', 1)"
                    )
                }
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideTripRepository(database: AppDatabase): TripRepository {
        return TripRepositoryImpl(database.tripDao())
    }

    @Provides
    @Singleton
    fun provideVehicleRepository(database: AppDatabase): VehicleRepository {
        return VehicleRepositoryImpl(database.vehicleDao())
    }

    @Provides
    @Singleton
    fun provideTripAuditLogDao(database: AppDatabase): TripAuditLogDao {
        return database.tripAuditLogDao()
    }

    @Provides
    @Singleton
    fun provideSavedLocationDao(database: AppDatabase): SavedLocationDao {
        return database.savedLocationDao()
    }

    @Provides
    @Singleton
    fun provideTripPurposeDao(database: AppDatabase): TripPurposeDao {
        return database.tripPurposeDao()
    }

    @Provides
    @Singleton
    fun provideTripDao(database: AppDatabase): de.fosstenbuch.data.local.TripDao {
        return database.tripDao()
    }

    @Provides
    @Singleton
    fun provideVehicleDao(database: AppDatabase): de.fosstenbuch.data.local.VehicleDao {
        return database.vehicleDao()
    }

    @Provides
    @Singleton
    fun provideSavedLocationRepository(database: AppDatabase): SavedLocationRepository {
        return SavedLocationRepositoryImpl(database.savedLocationDao())
    }

    @Provides
    @Singleton
    fun provideTripPurposeRepository(database: AppDatabase): TripPurposeRepository {
        return TripPurposeRepositoryImpl(database.tripPurposeDao())
    }

    @Provides
    @Singleton
    fun provideTripTemplateDao(database: AppDatabase): TripTemplateDao {
        return database.tripTemplateDao()
    }

    @Provides
    @Singleton
    fun provideTripTemplateRepository(database: AppDatabase): TripTemplateRepository {
        return TripTemplateRepositoryImpl(database.tripTemplateDao())
    }
}