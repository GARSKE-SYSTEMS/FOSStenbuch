package de.fosstenbuch.ui.locations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.fosstenbuch.data.model.SavedLocation
import de.fosstenbuch.data.repository.SavedLocationRepository
import de.fosstenbuch.domain.usecase.location.InsertSavedLocationUseCase
import de.fosstenbuch.domain.usecase.location.UpdateSavedLocationUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LocationDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val savedLocationRepository: SavedLocationRepository,
    private val insertSavedLocationUseCase: InsertSavedLocationUseCase,
    private val updateSavedLocationUseCase: UpdateSavedLocationUseCase
) : ViewModel() {

    private val locationId: Long? = savedStateHandle.get<Long>("locationId")?.takeIf { it > 0 }

    private val _uiState = MutableStateFlow(LocationDetailUiState())
    val uiState: StateFlow<LocationDetailUiState> = _uiState.asStateFlow()

    init {
        locationId?.let { loadLocation(it) }
    }

    private fun loadLocation(id: Long) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            savedLocationRepository.getSavedLocationById(id)
                .catch { e ->
                    Timber.e(e, "Failed to load location $id")
                    _uiState.update {
                        it.copy(isLoading = false, error = "Ort konnte nicht geladen werden")
                    }
                }
                .collect { location ->
                    _uiState.update { it.copy(isLoading = false, location = location) }
                }
        }
    }

    fun saveLocation(location: SavedLocation) {
        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                if (locationId != null) {
                    updateSavedLocationUseCase(location)
                    _uiState.update {
                        it.copy(isSaving = false, savedSuccessfully = true, location = location)
                    }
                } else {
                    when (val result = insertSavedLocationUseCase(location)) {
                        is InsertSavedLocationUseCase.Result.Success -> {
                            _uiState.update {
                                it.copy(
                                    isSaving = false,
                                    savedSuccessfully = true,
                                    location = location.copy(id = result.locationId)
                                )
                            }
                        }
                        is InsertSavedLocationUseCase.Result.Error -> {
                            _uiState.update {
                                it.copy(isSaving = false, error = result.exception.message)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save location")
                _uiState.update {
                    it.copy(isSaving = false, error = "Ort konnte nicht gespeichert werden")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun onSaveConsumed() {
        _uiState.update { it.copy(savedSuccessfully = false) }
    }
}
