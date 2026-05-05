package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.AgentInvoiceDto
import com.thapsus.cargo.data.repository.AgentInvoicesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AgentInvoicesViewModel(
    private val repo: AgentInvoicesRepository
) : SharedViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Loaded(val invoices: List<AgentInvoiceDto>) : UiState
    }

    sealed interface ActionState {
        data object Idle : ActionState
        data object InFlight : ActionState
        data class Done(val message: String) : ActionState
        data class Error(val message: String) : ActionState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _action = MutableStateFlow<ActionState>(ActionState.Idle)
    val action: StateFlow<ActionState> = _action.asStateFlow()

    fun load() {
        scope.launch {
            _state.value = UiState.Loading
            repo.listMine()
                .onSuccess { _state.value = UiState.Loaded(it) }
                .onFailure { _state.value = UiState.Error(it.message ?: "Failed to load invoices") }
        }
    }

    fun submit(consolidationId: String?, invoiceNo: String?, amountKes: Double, docUrl: String?, notes: String?) {
        scope.launch {
            _action.value = ActionState.InFlight
            repo.submit(consolidationId, invoiceNo, amountKes, docUrl, notes)
                .onSuccess { _action.value = ActionState.Done("Invoice submitted"); load() }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Submission failed") }
        }
    }
}
