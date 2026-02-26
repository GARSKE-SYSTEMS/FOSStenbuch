package de.fosstenbuch.data.repository

import de.fosstenbuch.data.local.TripPurposeDao
import de.fosstenbuch.data.model.TripPurpose
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TripPurposeRepositoryImpl @Inject constructor(
    private val tripPurposeDao: TripPurposeDao
) : TripPurposeRepository {
    override fun getAllPurposes(): Flow<List<TripPurpose>> =
        tripPurposeDao.getAllPurposes()

    override fun getPurposeById(id: Long): Flow<TripPurpose?> =
        tripPurposeDao.getPurposeById(id)

    override suspend fun getPurposeByName(name: String): TripPurpose? =
        tripPurposeDao.getPurposeByName(name)

    override suspend fun getTripCountForPurpose(purposeId: Long): Int =
        tripPurposeDao.getTripCountForPurpose(purposeId)

    override suspend fun insertPurpose(purpose: TripPurpose): Long =
        tripPurposeDao.insertPurpose(purpose)

    override suspend fun updatePurpose(purpose: TripPurpose) =
        tripPurposeDao.updatePurpose(purpose)

    override suspend fun deletePurpose(purpose: TripPurpose) =
        tripPurposeDao.deletePurpose(purpose)

    override suspend fun getPurposeCount(): Int =
        tripPurposeDao.getPurposeCount()
}
