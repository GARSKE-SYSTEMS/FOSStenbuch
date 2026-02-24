package de.fosstenbuch.data.repository

import de.fosstenbuch.data.local.VehicleDao
import de.fosstenbuch.data.model.Vehicle
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class VehicleRepositoryImpl @Inject constructor(
    private val vehicleDao: VehicleDao
) : VehicleRepository {
    override fun getAllVehicles(): Flow<List<Vehicle>> = vehicleDao.getAllVehicles()
    override fun getVehicleById(id: Long): Flow<Vehicle?> = vehicleDao.getVehicleById(id)
    override fun getPrimaryVehicle(): Flow<Vehicle?> = vehicleDao.getPrimaryVehicle()
    override suspend fun insertVehicle(vehicle: Vehicle): Long = vehicleDao.insertVehicle(vehicle)
    override suspend fun updateVehicle(vehicle: Vehicle) = vehicleDao.updateVehicle(vehicle)
    override suspend fun deleteVehicle(vehicle: Vehicle) = vehicleDao.deleteVehicle(vehicle)
    override suspend fun deleteAllVehicles() = vehicleDao.deleteAllVehicles()
    override suspend fun clearPrimaryVehicle() = vehicleDao.clearPrimaryVehicle()
}