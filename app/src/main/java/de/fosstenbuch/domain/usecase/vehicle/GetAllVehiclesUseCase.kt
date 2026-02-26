package de.fosstenbuch.domain.usecase.vehicle

import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.data.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllVehiclesUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository
) {
    operator fun invoke(): Flow<List<Vehicle>> = vehicleRepository.getAllVehicles()
}
