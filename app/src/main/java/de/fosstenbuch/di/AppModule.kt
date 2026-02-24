package de.fosstenbuch.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.fosstenbuch.data.local.AppDatabase
import de.fosstenbuch.data.repository.TripRepository
import de.fosstenbuch.data.repository.TripRepositoryImpl
import de.fosstenbuch.data.repository.VehicleRepository
import de.fosstenbuch.data.repository.VehicleRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "fosstenbuch-database"
        ).build()
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
}