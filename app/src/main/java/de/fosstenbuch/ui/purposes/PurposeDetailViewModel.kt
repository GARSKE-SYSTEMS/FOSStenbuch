package de.fosstenbuch.ui.purposes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.repository.TripPurposeRepository
import de.fosstenbuch.domain.usecase.purpose.InsertPurposeUseCase
import de.fosstenbuch.domain.usecase.purpose.UpdatePurposeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PurposeDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val tripPurposeRepository: TripPurposeRepository,
    private val insertPurposeUseCase: InsertPurposeUseCase,
    private val updatePurposeUseCase: UpdatePurposeUseCase
) : ViewModel() {

    private val purposeId: Long? = savedStateHandle.get<Long>("purposeId")?.takeIf { it > 0 }

    private val _uiState = MutableStateFlow(PurposeDetailUiState())
    val uiState: StateFlow<PurposeDetailUiState> = _uiState.asStateFlow()

    init {
        purposeId?.let { loadPurpose(it) }
    }

    private fun loadPurpose(id: Long) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            tripPurposeRepository.getPurposeById(id)
                .catch { e ->
                    Timber.e(e, "Failed to load purpose $id")
                    _uiState.update {
                        it.copy(isLoading = false, error = "Zweck konnte nicht geladen werden")
                    }
                }
                .collect { purpose ->
                    _uiState.update { it.copy(isLoading = false, purpose = purpose) }
                }
        }
    }

    fun savePurpose(purpose: TripPurpose) {
        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            if (purposeId != null) {
                when (val result = updatePurposeUseCase(purpose)) {
                    is UpdatePurposeUseCase.Result.Success -> {
                        _uiState.update {
                            it.copy(isSaving = false, savedSuccessfully = true, purpose = purpose)
                        }
                    }
                    is UpdatePurposeUseCase.Result.Error -> {
                        _uiState.update {
                            it.copy(isSaving = false, error = result.message)
                        }
                    }
                }
            } else {
                when (val result = insertPurposeUseCase(purpose)) {
                    is InsertPurposeUseCase.Result.Success -> {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                savedSuccessfully = true,
                                purpose = purpose.copy(id = result.purposeId)
                            )
                        }
                    }
                    is InsertPurposeUseCase.Result.Error -> {
                        _uiState.update {
                            it.copy(isSaving = false, error = result.message)
                        }
                    }
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
