package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.BuyForMeOrderDto
import com.thapsus.cargo.data.repository.BuyForMeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Operator-side queue for Buy-for-me concierge requests.
 * Drives:
 *   • Live list of pending_quote / quoted / paid / rejected orders, sourced from
 *     `BuyForMeRepository.observeOperatorQueue()` (snapshot + Realtime merges).
 *   • Quote action — calls `POST /buy-for-me/:id/quote` which also fires the
 *     "quote ready" email to the customer server-side.
 */
class OpsBuyForMeViewModel(
    private val repo: BuyForMeRepository
) : SharedViewModel() {

    sealed interface ActionState {
        data object Idle : ActionState
        data object InFlight : ActionState
        data class Done(val message: String) : ActionState
        data class Error(val message: String) : ActionState
    }

    private val _orders = MutableStateFlow<List<BuyForMeOrderDto>>(emptyList())
    val orders: StateFlow<List<BuyForMeOrderDto>> = _orders.asStateFlow()

    private val _action = MutableStateFlow<ActionState>(ActionState.Idle)
    val action: StateFlow<ActionState> = _action.asStateFlow()

    init {
        scope.launch {
            repo.observeOperatorQueue().collect { _orders.value = it }
        }
    }

    /** Operator submits a quote; server sends the customer a quote-ready email. */
    fun submitQuote(id: String, estimateGbp: Double, markupPct: Double, notes: String?) {
        scope.launch {
            _action.value = ActionState.InFlight
            repo.quote(id, estimateGbp, markupPct, notes)
                .onSuccess { _action.value = ActionState.Done("Quote sent. Customer will be emailed.") }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Quote failed") }
        }
    }

    fun resetAction() { _action.value = ActionState.Idle }
}
