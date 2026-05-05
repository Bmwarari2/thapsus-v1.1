package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.ConsolidationDto
import com.thapsus.cargo.data.repository.ConsolidationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CustomerConsolidationViewModel(
    private val consolidationId: String,
    private val consolidations: ConsolidationRepository
) : SharedViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Loaded(val consolidation: ConsolidationDto) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        scope.launch {
            consolidations.customerSummary(consolidationId)
                .onSuccess { _state.value = UiState.Loaded(it) }
                .onFailure { _state.value = UiState.Error(it.message ?: "Failed to load consolidation") }
        }
    }
}
