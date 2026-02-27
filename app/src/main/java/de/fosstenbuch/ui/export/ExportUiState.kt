package de.fosstenbuch.ui.export

import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.domain.export.ExportFormat
import java.time.LocalDate

data class ExportUiState(
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val error: String? = null,
    val exportSuccess: Boolean = false,
    val exportedFilePath: String? = null,

    // Filter options
    val dateFrom: LocalDate = LocalDate.now().withDayOfYear(1),
    val dateTo: LocalDate = LocalDate.now(),
    val format: ExportFormat = ExportFormat.CSV,
    val includeAuditLog: Boolean = true,

    // Export tracking
    val onlyNew: Boolean = true,
    val markAsExported: Boolean = true,

    // Driver & confirmation
    val driverName: String = "",
    val companyName: String = "",

    // Available data
    val purposes: List<TripPurpose> = emptyList(),
    val selectedPurposeIds: Set<Long> = emptySet(),
    val vehicles: List<Vehicle> = emptyList(),
    val selectedVehicleId: Long? = null,

    // Preview
    val tripCount: Int = 0
)
