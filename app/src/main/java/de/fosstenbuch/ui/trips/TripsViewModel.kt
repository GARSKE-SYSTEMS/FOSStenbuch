package de.fosstenbuch.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.domain.usecase.purpose.GetAllPurposesUseCase
import de.fosstenbuch.domain.usecase.trip.DeleteTripUseCase
import de.fosstenbuch.domain.usecase.trip.GetActiveTripUseCase
import de.fosstenbuch.domain.usecase.trip.GetAllTripsUseCase
import de.fosstenbuch.domain.usecase.trip.GetBusinessTripsUseCase
import de.fosstenbuch.domain.usecase.trip.GetPrivateTripsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val getAllTripsUseCase: GetAllTripsUseCase,
    private val getBusinessTripsUseCase: GetBusinessTripsUseCase,
    private val getPrivateTripsUseCase: GetPrivateTripsUseCase,
    private val deleteTripUseCase: DeleteTripUseCase,
    private val getAllPurposesUseCase: GetAllPurposesUseCase,
    private val getActiveTripUseCase: GetActiveTripUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripsUiState())
    val uiState: StateFlow<TripsUiState> = _uiState.asStateFlow()

    init {
        loadPurposes()
        loadTrips()
        loadActiveTrip()
    }

    fun setFilter(filter: TripFilter) {
        _uiState.update { it.copy(filter = filter) }
        loadTrips()
    }

    fun setSort(sort: TripSort) {
        _uiState.update { it.copy(sort = sort) }
        loadTrips()
    }

    fun deleteTrip(trip: Trip) {
        viewModelScope.launch {
            try {
                deleteTripUseCase(trip)
                // Flow will automatically update the list
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete trip")
                _uiState.update { it.copy(error = "Fahrt konnte nicht gelöscht werden") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun loadPurposes() {
        viewModelScope.launch {
            getAllPurposesUseCase()
                .catch { e -> Timber.e(e, "Failed to load purposes") }
                .collect { purposes ->
                    _uiState.update { it.copy(purposes = purposes) }
                }
        }
    }

    private fun loadTrips() {
        _uiState.update { it.copy(isLoading = true, error = null) }

        val flow = when (_uiState.value.filter) {
            TripFilter.ALL -> getAllTripsUseCase()
            TripFilter.BUSINESS -> getBusinessTripsUseCase()
            TripFilter.PRIVATE -> getPrivateTripsUseCase()
        }

        viewModelScope.launch {
            flow
                .catch { e ->
                    Timber.e(e, "Failed to load trips")
                    _uiState.update {
                        it.copy(isLoading = false, error = "Fahrten konnten nicht geladen werden")
                    }
                }
                .collect { trips ->
                    val sorted = sortTrips(trips, _uiState.value.sort)
                    _uiState.update {
                        it.copy(isLoading = false, trips = sorted, error = null)
                    }
                }
        }
    }

    private fun loadActiveTrip() {
        viewModelScope.launch {
            getActiveTripUseCase()
                .catch { e -> Timber.e(e, "Failed to load active trip") }
                .collect { activeTrip ->
                    _uiState.update { it.copy(activeTrip = activeTrip) }
                }
        }
    }

    private fun sortTrips(trips: List<Trip>, sort: TripSort): List<Trip> {
        return when (sort) {
            TripSort.DATE_DESC -> trips.sortedByDescending { it.date }
            TripSort.DATE_ASC -> trips.sortedBy { it.date }
            TripSort.DISTANCE_DESC -> trips.sortedByDescending { it.distanceKm }
            TripSort.DISTANCE_ASC -> trips.sortedBy { it.distanceKm }
        }
    }
}
