package de.fosstenbuch.data.repository

import de.fosstenbuch.data.model.Vehicle
import kotlinx.coroutines.flow.Flow

interface VehicleRepository {
    fun getAllVehicles(): Flow<List<Vehicle>>
    fun getVehicleById(id: Long): Flow<Vehicle?>
    fun getPrimaryVehicle(): Flow<Vehicle?>
    suspend fun insertVehicle(vehicle: Vehicle): Long
    suspend fun updateVehicle(vehicle: Vehicle)
    suspend fun deleteVehicle(vehicle: Vehicle)
    suspend fun deleteAllVehicles()
    suspend fun clearPrimaryVehicle()
}