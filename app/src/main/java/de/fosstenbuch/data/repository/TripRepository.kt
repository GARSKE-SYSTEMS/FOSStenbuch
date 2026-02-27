package de.fosstenbuch.data.repository

import de.fosstenbuch.data.local.MonthlyDistance
import de.fosstenbuch.data.model.Trip
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    fun getAllTrips(): Flow<List<Trip>>
    fun getTripById(id: Long): Flow<Trip?>
    fun getBusinessTrips(): Flow<List<Trip>>
    fun getPrivateTrips(): Flow<List<Trip>>
    fun getTripsByDateRange(startDate: Long, endDate: Long): Flow<List<Trip>>
    suspend fun insertTrip(trip: Trip): Long
    suspend fun updateTrip(trip: Trip)
    suspend fun deleteTrip(trip: Trip)
    suspend fun deleteAllTrips()
    suspend fun getTripCountForVehicle(vehicleId: Long): Int
    fun getTotalBusinessDistance(): Flow<Double?>
    fun getTotalPrivateDistance(): Flow<Double?>
    fun getTotalDistance(): Flow<Double?>
    fun getTripCountByDateRange(startDate: Long, endDate: Long): Flow<Int>
    fun getMonthlyDistanceSummary(year: Int): Flow<List<MonthlyDistance>>
    fun getBusinessDistanceForYear(year: Int): Flow<Double?>
    fun getBusinessTripCountForYear(year: Int): Flow<Int>
    fun getActiveTrip(): Flow<Trip?>
    suspend fun getLastEndOdometerForVehicle(vehicleId: Long): Int?
    suspend fun getLastEndOdometer(): Int?
    suspend fun getLastCompletedTrip(): Trip?
    suspend fun markTripsAsExported(tripIds: List<Long>)
    suspend fun getUnexportedTripsByDateRange(startDate: Long, endDate: Long): List<Trip>
    suspend fun getCompletedTripsByDateRange(startDate: Long, endDate: Long): List<Trip>
    fun getTotalDistanceByDateRange(startDate: Long, endDate: Long): Flow<Double?>
    fun getBusinessDistanceByDateRange(startDate: Long, endDate: Long): Flow<Double?>
    fun getPrivateDistanceByDateRange(startDate: Long, endDate: Long): Flow<Double?>
    suspend fun getTripsForVehicleOrdered(vehicleId: Long): List<Trip>
    suspend fun updateTripChainHash(tripId: Long, chainHash: String?)
}