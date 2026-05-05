package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.PaymentDto
import com.thapsus.cargo.data.repository.PaymentsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Admin queue for M-Pesa payments awaiting review (server PR #61 / migration
 * 028). Stripe payments don't appear here — they auto-flip via webhook.
 *
 * Replaced the legacy wallet-deposit queue that read from
 * AdminRepository.pendingPayments (dead since the wallet was dropped).
 */
class AdminPaymentsViewModel(
    private val payments: PaymentsRepository
) : SharedViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Loaded(val pending: List<PaymentDto>) : UiState
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
            payments.pendingMpesaQueue()
                .onSuccess { _state.value = UiState.Loaded(it) }
                .onFailure { _state.value = UiState.Error(it.message ?: "Failed to load payments") }
        }
    }

    /**
     * Approve an M-Pesa payment. Pass [overrideReason] (>=10 chars) when
     * the customer-claimed amount is short of the invoice — the iOS
     * AdminPaymentsView opens a mismatch sheet to capture the reason
     * before calling this. Clean rows pass `overrideReason = null`
     * (audit P1.2).
     */
    fun approve(id: String, overrideReason: String? = null) {
        scope.launch {
            _action.value = ActionState.InFlight
            payments.approve(id, overrideReason)
                .onSuccess { resp ->
                    val msg = if (resp.overrideApplied)
                        "Approved with override — note recorded on the payment."
                    else
                        "Approved — payment recorded and target updated."
                    _action.value = ActionState.Done(msg)
                    load()
                }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Approve failed") }
        }
    }

    fun reject(id: String, reason: String) {
        scope.launch {
            _action.value = ActionState.InFlight
            payments.reject(id, reason)
                .onSuccess {
                    _action.value = ActionState.Done("Rejected — customer can resubmit.")
                    load()
                }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Reject failed") }
        }
    }

    fun resetAction() { _action.value = ActionState.Idle }
}
