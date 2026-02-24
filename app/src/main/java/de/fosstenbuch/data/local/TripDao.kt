package de.fosstenbuch.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import de.fosstenbuch.data.model.Trip
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY date DESC")
    fun getAllTrips(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE id = :id")
    fun getTripById(id: Long): Flow<Trip?>

    @Query("SELECT * FROM trips WHERE businessTrip = 1 ORDER BY date DESC")
    fun getBusinessTrips(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE businessTrip = 0 ORDER BY date DESC")
    fun getPrivateTrips(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getTripsByDateRange(startDate: Long, endDate: Long): Flow<List<Trip>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: Trip): Long

    @Update
    suspend fun updateTrip(trip: Trip)

    @Delete
    suspend fun deleteTrip(trip: Trip)

    @Query("DELETE FROM trips")
    suspend fun deleteAllTrips()

    @Query("SELECT SUM(distanceKm) FROM trips WHERE businessTrip = 1")
    fun getTotalBusinessDistance(): Flow<Double?>

    @Query("SELECT SUM(distanceKm) FROM trips WHERE businessTrip = 0")
    fun getTotalPrivateDistance(): Flow<Double?>
}