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

    @Query("""
        SELECT trips.* FROM trips 
        INNER JOIN trip_purposes ON trips.purposeId = trip_purposes.id 
        WHERE trip_purposes.isBusinessRelevant = 1 
        ORDER BY trips.date DESC
    """)
    fun getBusinessTrips(): Flow<List<Trip>>

    @Query("""
        SELECT trips.* FROM trips 
        INNER JOIN trip_purposes ON trips.purposeId = trip_purposes.id 
        WHERE trip_purposes.isBusinessRelevant = 0 
        ORDER BY trips.date DESC
    """)
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

    @Query("SELECT COUNT(*) FROM trips WHERE vehicleId = :vehicleId")
    suspend fun getTripCountForVehicle(vehicleId: Long): Int

    @Query("""
        SELECT SUM(trips.distanceKm) FROM trips 
        INNER JOIN trip_purposes ON trips.purposeId = trip_purposes.id 
        WHERE trip_purposes.isBusinessRelevant = 1
    """)
    fun getTotalBusinessDistance(): Flow<Double?>

    @Query("""
        SELECT SUM(trips.distanceKm) FROM trips 
        INNER JOIN trip_purposes ON trips.purposeId = trip_purposes.id 
        WHERE trip_purposes.isBusinessRelevant = 0
    """)
    fun getTotalPrivateDistance(): Flow<Double?>

    @Query("SELECT SUM(distanceKm) FROM trips")
    fun getTotalDistance(): Flow<Double?>

    @Query("SELECT COUNT(*) FROM trips WHERE date BETWEEN :startDate AND :endDate")
    fun getTripCountByDateRange(startDate: Long, endDate: Long): Flow<Int>

    @Query("""
        SELECT CAST(strftime('%m', date / 1000, 'unixepoch') AS INTEGER) as month,
               SUM(distanceKm) as totalDistance
        FROM trips
        WHERE CAST(strftime('%Y', date / 1000, 'unixepoch') AS INTEGER) = :year
        GROUP BY month
        ORDER BY month
    """)
    fun getMonthlyDistanceSummary(year: Int): Flow<List<MonthlyDistance>>
}

data class MonthlyDistance(
    val month: Int,
    val totalDistance: Double
)