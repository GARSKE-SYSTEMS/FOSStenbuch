package de.fosstenbuch.domain.export

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.model.TripAuditLog
import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.model.Vehicle
import java.io.File

interface TripExporter {

    /**
     * Export trips to a file in the given format.
     *
     * @param config Export configuration (date range, purpose filter, vehicle filter, etc.)
     * @param trips The filtered trips to export
     * @param purposes Map of purpose ID to TripPurpose (for displaying category names)
     * @param vehicles Map of vehicle ID to Vehicle (for displaying vehicle info)
     * @param auditLogs Map of trip ID to audit log entries (only used if config.includeAuditLog)
     * @return The generated file
     */
    suspend fun export(
        config: ExportConfig,
        trips: List<Trip>,
        purposes: Map<Long, TripPurpose>,
        vehicles: Map<Long, Vehicle>,
        auditLogs: Map<Long, List<TripAuditLog>>
    ): File
}
