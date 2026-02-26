package de.fosstenbuch.data.repository

import de.fosstenbuch.data.model.SavedLocation
import kotlinx.coroutines.flow.Flow

interface SavedLocationRepository {
    fun getAllSavedLocations(): Flow<List<SavedLocation>>
    fun getSavedLocationById(id: Long): Flow<SavedLocation?>
    suspend fun getAllSavedLocationsSync(): List<SavedLocation>
    suspend fun insertSavedLocation(location: SavedLocation): Long
    suspend fun updateSavedLocation(location: SavedLocation)
    suspend fun deleteSavedLocation(location: SavedLocation)
    suspend fun incrementUsageCount(locationId: Long)
}
