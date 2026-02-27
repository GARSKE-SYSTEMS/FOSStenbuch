package de.fosstenbuch.domain.export

import java.time.LocalDate

data class ExportConfig(
    val dateFrom: LocalDate,
    val dateTo: LocalDate,
    val selectedPurposeIds: Set<Long>,
    val vehicleId: Long?,
    val includeAuditLog: Boolean = false,
    val format: ExportFormat = ExportFormat.CSV,
    val driverName: String = "",
    val truthfulnessConfirmed: Boolean = false,
    val companyName: String = ""
)
