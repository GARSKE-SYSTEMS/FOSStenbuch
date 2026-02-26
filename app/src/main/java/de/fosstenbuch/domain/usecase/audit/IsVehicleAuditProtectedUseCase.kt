package de.fosstenbuch.domain.usecase.audit

import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.data.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Checks whether a vehicle has audit protection enabled.
 * If a vehicle is audit-protected, its trips cannot be deleted and
 * all edits are logged to the audit trail.
 */
class IsVehicleAuditProtectedUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository
) {
    operator fun invoke(vehicleId: Long): Flow<Boolean> =
        vehicleRepository.getVehicleById(vehicleId).map { vehicle ->
            vehicle?.auditProtected ?: false
        }
}
