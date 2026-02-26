package de.fosstenbuch.domain.usecase.audit

import de.fosstenbuch.data.local.TripAuditLogDao
import de.fosstenbuch.data.model.TripAuditLog
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Returns the audit log entries for a given trip.
 */
class GetAuditLogForTripUseCase @Inject constructor(
    private val tripAuditLogDao: TripAuditLogDao
) {
    operator fun invoke(tripId: Long): Flow<List<TripAuditLog>> =
        tripAuditLogDao.getAuditLogForTrip(tripId)
}
