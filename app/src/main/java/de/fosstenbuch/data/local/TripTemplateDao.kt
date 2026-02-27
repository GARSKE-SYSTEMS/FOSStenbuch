package de.fosstenbuch.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.fosstenbuch.data.model.TripTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface TripTemplateDao {
    @Query("SELECT * FROM trip_templates ORDER BY name ASC")
    fun getAllTemplates(): Flow<List<TripTemplate>>

    @Query("SELECT * FROM trip_templates WHERE id = :id")
    fun getTemplateById(id: Long): Flow<TripTemplate?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: TripTemplate): Long

    @Delete
    suspend fun deleteTemplate(template: TripTemplate)

    @Query("DELETE FROM trip_templates")
    suspend fun deleteAllTemplates()
}
