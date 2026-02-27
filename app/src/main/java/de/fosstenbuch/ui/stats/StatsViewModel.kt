package de.fosstenbuch.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.fosstenbuch.data.local.TripAuditLogDao
import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.model.TripAuditLog
import de.fosstenbuch.data.repository.TripRepository
import de.fosstenbuch.domain.export.CsvTripExporter
import de.fosstenbuch.domain.export.ExportConfig
import de.fosstenbuch.domain.export.ExportFormat
import de.fosstenbuch.domain.export.PdfTripExporter
import de.fosstenbuch.domain.usecase.purpose.GetAllPurposesUseCase
import de.fosstenbuch.domain.usecase.vehicle.GetAllVehiclesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val getAllPurposesUseCase: GetAllPurposesUseCase,
    private val getAllVehiclesUseCase: GetAllVehiclesUseCase,
    private val tripAuditLogDao: TripAuditLogDao,
    private val csvTripExporter: CsvTripExporter,
    private val pdfTripExporter: PdfTripExporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState(
        customDateFromMs = getYearStartMs(Calendar.getInstance().get(Calendar.YEAR)),
        customDateToMs = System.currentTimeMillis()
    ))
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun setFilterMode(mode: StatsFilterMode) {
        _uiState.update { it.copy(filterMode = mode) }
        loadStats()
    }

    fun setYear(year: Int) {
        _uiState.update { it.copy(selectedYear = year) }
        loadStats()
    }

    fun setMonth(month: Int) {
        _uiState.update { it.copy(selectedMonth = month) }
        loadStats()
    }

    fun previousMonth() {
        val state = _uiState.value
        if (state.selectedMonth == 0) {
            _uiState.update { it.copy(selectedYear = it.selectedYear - 1, selectedMonth = 11) }
        } else {
            _uiState.update { it.copy(selectedMonth = it.selectedMonth - 1) }
        }
        loadStats()
    }

    fun nextMonth() {
        val state = _uiState.value
        if (state.selectedMonth == 11) {
            _uiState.update { it.copy(selectedYear = it.selectedYear + 1, selectedMonth = 0) }
        } else {
            _uiState.update { it.copy(selectedMonth = it.selectedMonth + 1) }
        }
        loadStats()
    }

    fun setCustomDateFrom(ms: Long) {
        _uiState.update { it.copy(customDateFromMs = ms) }
        loadStats()
    }

    fun setCustomDateTo(ms: Long) {
        _uiState.update { it.copy(customDateToMs = ms) }
        loadStats()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun consumeExportSuccess() {
        _uiState.update { it.copy(exportSuccess = false, exportedFilePath = null) }
    }

    private fun loadStats() {
        _uiState.update { it.copy(isLoading = true, error = null) }

        val (startMs, endMs) = _uiState.value.dateRangeMs

        viewModelScope.launch {
            combine(
                tripRepository.getTotalDistanceByDateRange(startMs, endMs),
                tripRepository.getBusinessDistanceByDateRange(startMs, endMs),
                tripRepository.getPrivateDistanceByDateRange(startMs, endMs),
                tripRepository.getTripCountByDateRange(startMs, endMs)
            ) { totalDist, businessDist, privateDist, tripCount ->
                _uiState.value.copy(
                    isLoading = false,
                    totalDistanceKm = totalDist ?: 0.0,
                    businessDistanceKm = businessDist ?: 0.0,
                    privateDistanceKm = privateDist ?: 0.0,
                    tripCount = tripCount
                )
            }
                .catch { e ->
                    Timber.e(e, "Failed to load statistics")
                    _uiState.update {
                        it.copy(isLoading = false, error = "Statistiken konnten nicht geladen werden")
                    }
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    fun performExport(format: ExportFormat, onlyNew: Boolean, markAsExported: Boolean) {
        if (_uiState.value.isExporting) return

        _uiState.update { it.copy(isExporting = true, error = null) }

        viewModelScope.launch {
            try {
                val (startMs, endMs) = _uiState.value.dateRangeMs

                val trips = if (onlyNew) {
                    tripRepository.getUnexportedTripsByDateRange(startMs, endMs)
                } else {
                    tripRepository.getCompletedTripsByDateRange(startMs, endMs)
                }

                if (trips.isEmpty()) {
                    _uiState.update {
                        it.copy(isExporting = false, error = "Keine Fahrten zum Exportieren")
                    }
                    return@launch
                }

                val purposes = getAllPurposesUseCase().first().associateBy { it.id }
                val vehicles = getAllVehiclesUseCase().first().associateBy { it.id }

                val dateFrom = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()).toLocalDate()
                val dateTo = Instant.ofEpochMilli(endMs).atZone(ZoneId.systemDefault()).toLocalDate()

                val config = ExportConfig(
                    dateFrom = dateFrom,
                    dateTo = dateTo,
                    selectedPurposeIds = purposes.keys,
                    vehicleId = null,
                    includeAuditLog = false,
                    format = format
                )

                val exporter = when (format) {
                    ExportFormat.CSV -> csvTripExporter
                    ExportFormat.PDF -> pdfTripExporter
                }

                val file = exporter.export(config, trips, purposes, vehicles, emptyMap())

                if (markAsExported) {
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

    fun getExportTripCount(onlyNew: Boolean, callback: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val (startMs, endMs) = _uiState.value.dateRangeMs
                val trips = if (onlyNew) {
                    tripRepository.getUnexportedTripsByDateRange(startMs, endMs)
                } else {
                    tripRepository.getCompletedTripsByDateRange(startMs, endMs)
                }
                callback(trips.size)
            } catch (e: Exception) {
                callback(0)
            }
        }
    }

    private fun getYearStartMs(year: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(year, Calendar.JANUARY, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
