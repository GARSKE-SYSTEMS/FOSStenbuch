package de.fosstenbuch.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import de.fosstenbuch.data.model.TripPurpose
import kotlinx.coroutines.flow.Flow

@Dao
interface TripPurposeDao {
    @Query("SELECT * FROM trip_purposes ORDER BY isDefault DESC, name ASC")
    fun getAllPurposes(): Flow<List<TripPurpose>>

    @Query("SELECT * FROM trip_purposes WHERE id = :id")
    fun getPurposeById(id: Long): Flow<TripPurpose?>

    @Query("SELECT * FROM trip_purposes WHERE name = :name LIMIT 1")
    suspend fun getPurposeByName(name: String): TripPurpose?

    @Query("SELECT COUNT(*) FROM trips WHERE purposeId = :purposeId")
    suspend fun getTripCountForPurpose(purposeId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurpose(purpose: TripPurpose): Long

    @Update
    suspend fun updatePurpose(purpose: TripPurpose)

    @Delete
    suspend fun deletePurpose(purpose: TripPurpose)

    @Query("SELECT COUNT(*) FROM trip_purposes")
    suspend fun getPurposeCount(): Int
}
