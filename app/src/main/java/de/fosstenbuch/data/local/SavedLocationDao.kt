package de.fosstenbuch.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import de.fosstenbuch.data.model.SavedLocation
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLocationDao {
    @Query("SELECT * FROM saved_locations ORDER BY usageCount DESC, name ASC")
    fun getAllSavedLocations(): Flow<List<SavedLocation>>

    @Query("SELECT * FROM saved_locations WHERE id = :id")
    fun getSavedLocationById(id: Long): Flow<SavedLocation?>

    @Query("SELECT * FROM saved_locations ORDER BY usageCount DESC")
    suspend fun getAllSavedLocationsSync(): List<SavedLocation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedLocation(location: SavedLocation): Long

    @Update
    suspend fun updateSavedLocation(location: SavedLocation)

    @Delete
    suspend fun deleteSavedLocation(location: SavedLocation)

    @Query("UPDATE saved_locations SET usageCount = usageCount + 1 WHERE id = :locationId")
    suspend fun incrementUsageCount(locationId: Long)
}
