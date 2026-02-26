package de.fosstenbuch.domain.usecase.vehicle

import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.data.repository.VehicleRepository
import de.fosstenbuch.domain.validation.ValidationResult
import de.fosstenbuch.domain.validation.VehicleValidator
import javax.inject.Inject

/**
 * Updates an existing vehicle after validation.
 * If the vehicle is marked as primary, clears any existing primary vehicle first.
 */
class UpdateVehicleUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val vehicleValidator: VehicleValidator
) {
    sealed class Result {
        object Success : Result()
        data class ValidationError(val validation: ValidationResult) : Result()
        data class Error(val exception: Throwable) : Result()
    }

    suspend operator fun invoke(vehicle: Vehicle): Result {
        val validation = vehicleValidator.validate(vehicle)
        if (!validation.isValid) {
            return Result.ValidationError(validation)
        }

        return try {
            if (vehicle.isPrimary) {
                vehicleRepository.clearPrimaryVehicle()
            }
            vehicleRepository.updateVehicle(vehicle)
            Result.Success
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
