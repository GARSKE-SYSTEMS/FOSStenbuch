package de.fosstenbuch.data.repository

import de.fosstenbuch.data.local.SavedLocationDao
import de.fosstenbuch.data.model.SavedLocation
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SavedLocationRepositoryImpl @Inject constructor(
    private val savedLocationDao: SavedLocationDao
) : SavedLocationRepository {
    override fun getAllSavedLocations(): Flow<List<SavedLocation>> =
        savedLocationDao.getAllSavedLocations()

    override fun getSavedLocationById(id: Long): Flow<SavedLocation?> =
        savedLocationDao.getSavedLocationById(id)

    override suspend fun getAllSavedLocationsSync(): List<SavedLocation> =
        savedLocationDao.getAllSavedLocationsSync()

    override suspend fun insertSavedLocation(location: SavedLocation): Long =
        savedLocationDao.insertSavedLocation(location)

    override suspend fun updateSavedLocation(location: SavedLocation) =
        savedLocationDao.updateSavedLocation(location)

    override suspend fun deleteSavedLocation(location: SavedLocation) =
        savedLocationDao.deleteSavedLocation(location)

    override suspend fun incrementUsageCount(locationId: Long) =
        savedLocationDao.incrementUsageCount(locationId)
}
