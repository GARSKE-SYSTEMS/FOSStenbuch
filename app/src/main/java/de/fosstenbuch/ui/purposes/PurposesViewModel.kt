package de.fosstenbuch.ui.purposes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.domain.usecase.purpose.DeletePurposeUseCase
import de.fosstenbuch.domain.usecase.purpose.GetAllPurposesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PurposesViewModel @Inject constructor(
    private val getAllPurposesUseCase: GetAllPurposesUseCase,
    private val deletePurposeUseCase: DeletePurposeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurposesUiState())
    val uiState: StateFlow<PurposesUiState> = _uiState.asStateFlow()

    init {
        loadPurposes()
    }

    fun deletePurpose(purpose: TripPurpose) {
        viewModelScope.launch {
            when (val result = deletePurposeUseCase(purpose)) {
                is DeletePurposeUseCase.Result.Success -> {
                    // Flow will automatically update the list
                }
                is DeletePurposeUseCase.Result.Error -> {
                    _uiState.update { it.copy(error = result.message) }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun loadPurposes() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            getAllPurposesUseCase()
                .catch { e ->
                    Timber.e(e, "Failed to load purposes")
                    _uiState.update {
                        it.copy(isLoading = false, error = "Zwecke konnten nicht geladen werden")
                    }
                }
                .collect { purposes ->
                    _uiState.update {
                        it.copy(isLoading = false, purposes = purposes)
                    }
                }
        }
    }
}
