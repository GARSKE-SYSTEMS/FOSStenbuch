package de.fosstenbuch.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.fosstenbuch.data.local.PreferencesManager
import de.fosstenbuch.data.local.TripAuditLogDao
import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.model.TripAuditLog
import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.data.repository.TripRepository
import de.fosstenbuch.domain.export.CsvTripExporter
import de.fosstenbuch.domain.export.ExportConfig
import de.fosstenbuch.domain.export.ExportFormat
import de.fosstenbuch.domain.export.FinanzamtPdfTripExporter
import de.fosstenbuch.domain.export.PdfTripExporter
import de.fosstenbuch.domain.usecase.purpose.GetAllPurposesUseCase
import de.fosstenbuch.domain.usecase.trip.GetAllTripsUseCase
import de.fosstenbuch.domain.usecase.vehicle.GetAllVehiclesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val getAllTripsUseCase: GetAllTripsUseCase,
    private val getAllPurposesUseCase: GetAllPurposesUseCase,
    private val getAllVehiclesUseCase: GetAllVehiclesUseCase,
    private val tripRepository: TripRepository,
    private val preferencesManager: PreferencesManager,
    private val tripAuditLogDao: TripAuditLogDao,
    private val csvTripExporter: CsvTripExporter,
    private val pdfTripExporter: PdfTripExporter,
    private val finanzamtPdfTripExporter: FinanzamtPdfTripExporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        loadFilterOptions()
        loadDriverName()
    }

    private fun loadDriverName() {
        viewModelScope.launch {
            preferencesManager.driverName.collect { name ->
                _uiState.update { it.copy(driverName = name) }
            }
        }
    }

    private fun loadFilterOptions() {
        viewModelScope.launch {
            try {
                val purposes = getAllPurposesUseCase().first()
                val vehicles = getAllVehiclesUseCase().first()

                _uiState.update {
                    it.copy(
                        purposes = purposes,
                        selectedPurposeIds = purposes.map { p -> p.id }.toSet(),
                        vehicles = vehicles
                    )
                }

                updateTripCount()
            } catch (e: Exception) {
                Timber.e(e, "Failed to load filter options")
                _uiState.update { it.copy(error = "Filter konnten nicht geladen werden") }
            }
        }
    }

    fun setDateFrom(date: LocalDate) {
        _uiState.update { it.copy(dateFrom = date) }
        updateTripCount()
    }

    fun setDateTo(date: LocalDate) {
        _uiState.update { it.copy(dateTo = date) }
        updateTripCount()
    }

    fun setFormat(format: ExportFormat) {
        _uiState.update { it.copy(format = format) }
    }

    fun setIncludeAuditLog(include: Boolean) {
        _uiState.update { it.copy(includeAuditLog = include) }
    }

    fun togglePurpose(purposeId: Long, checked: Boolean) {
        _uiState.update { state ->
            val newIds = if (checked) {
                state.selectedPurposeIds + purposeId
            } else {
                state.selectedPurposeIds - purposeId
            }
            state.copy(selectedPurposeIds = newIds)
        }
        updateTripCount()
    }

    fun setVehicle(vehicleId: Long?) {
        _uiState.update { it.copy(selectedVehicleId = vehicleId) }
        updateTripCount()
    }

    fun setOnlyNew(onlyNew: Boolean) {
        _uiState.update { it.copy(onlyNew = onlyNew) }
        updateTripCount()
    }

    fun setMarkAsExported(mark: Boolean) {
        _uiState.update { it.copy(markAsExported = mark) }
    }

    fun setCompanyName(name: String) {
        _uiState.update { it.copy(companyName = name) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun consumeExportSuccess() {
        _uiState.update { it.copy(exportSuccess = false, exportedFilePath = null) }
    }

    private fun updateTripCount() {
        viewModelScope.launch {
            try {
                val trips = getFilteredTrips()
                _uiState.update { it.copy(tripCount = trips.size) }
            } catch (e: Exception) {
                Timber.d(e, "Failed to count trips")
            }
        }
    }

    fun performExport() {
        val state = _uiState.value
        if (state.isExporting) return

        _uiState.update { it.copy(isExporting = true, error = null) }

        viewModelScope.launch {
            try {
                val trips = getFilteredTrips()

                if (trips.isEmpty()) {
                    _uiState.update {
                        it.copy(isExporting = false, error = "Keine Fahrten im ausgewählten Zeitraum")
                    }
                    return@launch
                }

                val purposeMap = _uiState.value.purposes.associateBy { it.id }
                val vehicleMap = _uiState.value.vehicles.associateBy { it.id }

                val auditLogs = if (state.includeAuditLog) {
                    collectAuditLogs(trips)
                } else {
                    emptyMap()
                }

                val config = ExportConfig(
                    dateFrom = state.dateFrom,
                    dateTo = state.dateTo,
                    selectedPurposeIds = state.selectedPurposeIds,
                    vehicleId = state.selectedVehicleId,
                    includeAuditLog = state.includeAuditLog,
                    format = state.format,
                    driverName = state.driverName,
                    truthfulnessConfirmed = true,
                    companyName = state.companyName
                )

                val exporter = when (state.format) {
                    ExportFormat.CSV -> csvTripExporter
                    ExportFormat.PDF -> pdfTripExporter
                    ExportFormat.FINANZAMT_PDF -> finanzamtPdfTripExporter
                }

                val file = exporter.export(config, trips, purposeMap, vehicleMap, auditLogs)

                // Mark trips as exported if requested
                if (state.markAsExported) {
                    tripRepository.markTripsAsExported(trips.map { it.id })
                }

                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportSuccess = true,
                        exportedFilePath = file.absolutePath
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Export failed")
                _uiState.update {
                    it.copy(isExporting = false, error = "Export fehlgeschlagen: ${e.localizedMessage}")
                }
            }
        }
    }

    private suspend fun getFilteredTrips(): List<Trip> {
        val state = _uiState.value
        val startMs = state.dateFrom.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMs = state.dateTo.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Use repository methods that respect isExported flag when onlyNew is set
        val baseTrips = if (state.onlyNew) {
            tripRepository.getUnexportedTripsByDateRange(startMs, endMs)
        } else {
            getAllTripsUseCase().first()
                .filter { trip ->
                    val tripMs = trip.date.time
                    tripMs in startMs..endMs
                }
        }

        return baseTrips.filter { trip ->
            (state.selectedPurposeIds.isEmpty() || trip.purposeId in state.selectedPurposeIds) &&
                (state.selectedVehicleId == null || trip.vehicleId == state.selectedVehicleId)
        }.sortedBy { it.date }
    }

    private suspend fun collectAuditLogs(trips: List<Trip>): Map<Long, List<TripAuditLog>> {
        val result = mutableMapOf<Long, List<TripAuditLog>>()
        for (trip in trips) {
            val logs = tripAuditLogDao.getAuditLogForTrip(trip.id).first()
            if (logs.isNotEmpty()) {
                result[trip.id] = logs
            }
        }
        return result
    }
}
