package de.fosstenbuch.data.repository

import de.fosstenbuch.data.local.TripDao
import de.fosstenbuch.data.model.Trip
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TripRepositoryImpl @Inject constructor(
    private val tripDao: TripDao
) : TripRepository {
    override fun getAllTrips(): Flow<List<Trip>> = tripDao.getAllTrips()
    override fun getTripById(id: Long): Flow<Trip?> = tripDao.getTripById(id)
    override fun getBusinessTrips(): Flow<List<Trip>> = tripDao.getBusinessTrips()
    override fun getPrivateTrips(): Flow<List<Trip>> = tripDao.getPrivateTrips()
    override fun getTripsByDateRange(startDate: Long, endDate: Long): Flow<List<Trip>> = 
        tripDao.getTripsByDateRange(startDate, endDate)
    override suspend fun insertTrip(trip: Trip): Long = tripDao.insertTrip(trip)
    override suspend fun updateTrip(trip: Trip) = tripDao.updateTrip(trip)
    override suspend fun deleteTrip(trip: Trip) = tripDao.deleteTrip(trip)
    override suspend fun deleteAllTrips() = tripDao.deleteAllTrips()
    override fun getTotalBusinessDistance(): Flow<Double?> = tripDao.getTotalBusinessDistance()
    override fun getTotalPrivateDistance(): Flow<Double?> = tripDao.getTotalPrivateDistance()
}