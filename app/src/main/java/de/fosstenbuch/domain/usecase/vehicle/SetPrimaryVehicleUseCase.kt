package de.fosstenbuch.domain.usecase.vehicle

import de.fosstenbuch.data.repository.VehicleRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Sets a vehicle as the primary vehicle.
 * Clears any existing primary vehicle, then updates the target vehicle.
 */
class SetPrimaryVehicleUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository
) {
    suspend operator fun invoke(vehicleId: Long) {
        vehicleRepository.clearPrimaryVehicle()
        val vehicle = vehicleRepository.getVehicleById(vehicleId).first()
            ?: throw IllegalArgumentException("Vehicle with ID $vehicleId not found")
        vehicleRepository.updateVehicle(vehicle.copy(isPrimary = true))
    }
}
