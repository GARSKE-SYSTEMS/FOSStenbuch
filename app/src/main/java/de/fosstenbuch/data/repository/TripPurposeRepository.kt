package de.fosstenbuch.data.repository

import de.fosstenbuch.data.model.TripPurpose
import kotlinx.coroutines.flow.Flow

interface TripPurposeRepository {
    fun getAllPurposes(): Flow<List<TripPurpose>>
    fun getPurposeById(id: Long): Flow<TripPurpose?>
    suspend fun getPurposeByName(name: String): TripPurpose?
    suspend fun getTripCountForPurpose(purposeId: Long): Int
    suspend fun insertPurpose(purpose: TripPurpose): Long
    suspend fun updatePurpose(purpose: TripPurpose)
    suspend fun deletePurpose(purpose: TripPurpose)
    suspend fun getPurposeCount(): Int
}
