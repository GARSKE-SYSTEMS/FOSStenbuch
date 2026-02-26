package de.fosstenbuch.domain.usecase.vehicle

import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.data.repository.VehicleRepository
import javax.inject.Inject

class DeleteVehicleUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository
) {
    suspend operator fun invoke(vehicle: Vehicle) {
        vehicleRepository.deleteVehicle(vehicle)
    }
}
