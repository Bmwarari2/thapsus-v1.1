package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.BuyForMeOrderDto
import com.thapsus.cargo.data.dto.RetailerDto
import com.thapsus.cargo.data.repository.BuyForMeRepository
import com.thapsus.cargo.data.repository.RetailersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BuyForMeViewModel(
    private val buyForMe: BuyForMeRepository,
    private val retailers: RetailersRepository? = null
) : SharedViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Loaded(val orders: List<BuyForMeOrderDto>) : UiState
    }

    sealed interface ActionState {
        data object Idle : ActionState
        data object InFlight : ActionState
        data class Done(val message: String) : ActionState
        data class Error(val message: String) : ActionState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _action = MutableStateFlow<ActionState>(ActionState.Idle)
    val action: StateFlow<ActionState> = _action.asStateFlow()

    /** PR 4: curated retailer catalog for the create form's picker. Empty
     *  until loadRetailers() resolves; iOS shows the "Other" path while
     *  empty so a fetch failure doesn't block the customer. */
    private val _retailers = MutableStateFlow<List<RetailerDto>>(emptyList())
    val retailerCatalog: StateFlow<List<RetailerDto>> = _retailers.asStateFlow()

    fun loadRetailers() {
        val repo = retailers ?: return
        scope.launch {
            repo.list()
                .onSuccess { _retailers.value = it }
                .onFailure { /* picker is enhancement — keep list empty */ }
        }
    }

    fun load() {
        scope.launch {
            _state.value = UiState.Loading
            buyForMe.list()
                .onSuccess { _state.value = UiState.Loaded(it) }
                .onFailure { _state.value = UiState.Error(it.message ?: "Failed to load") }
        }
    }

    fun create(retailerUrl: String, itemName: String, size: String?, qty: Int, notes: String?) {
        // Backwards-compatible: accepts a free-text URL only (legacy callers).
        create(itemName = itemName, size = size, qty = qty, notes = notes,
               retailerId = null, retailerUrl = retailerUrl)
    }

    /** PR 4: create with optional `retailerId` from the picker. When set,
     *  the server resolves base_url from the catalog; the URL field stays
     *  optional (item-specific link). */
    fun create(
        itemName: String,
        size: String?,
        qty: Int,
        notes: String?,
        retailerId: String?,
        retailerUrl: String?
    ) {
        scope.launch {
            _action.value = ActionState.InFlight
            buyForMe.create(
                retailerUrl = retailerUrl,
                itemName    = itemName,
                size        = size,
                qty         = qty,
                notes       = notes,
                retailerId  = retailerId,
            )
                .onSuccess {
                    _action.value = ActionState.Done("Concierge request submitted. We'll quote shortly.")
                    load()
                }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Submission failed") }
        }
    }

    fun pay(id: String) {
        scope.launch {
            _action.value = ActionState.InFlight
            buyForMe.pay(id)
                .onSuccess {
                    _action.value = ActionState.Done("Payment captured. We'll buy the item now.")
                    load()
                }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Payment failed") }
        }
    }

    /** Customer accepts a quote with an optional note. Same wallet rules as pay. */
    fun accept(id: String, reason: String? = null) {
        scope.launch {
            _action.value = ActionState.InFlight
            buyForMe.accept(id, reason)
                .onSuccess {
                    _action.value = ActionState.Done("Quote accepted. We'll buy the item now.")
                    load()
                }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Accept failed") }
        }
    }

    /** Customer rejects a quote — reason is required so operator can re-quote. */
    fun reject(id: String, reason: String) {
        scope.launch {
            _action.value = ActionState.InFlight
            buyForMe.reject(id, reason)
                .onSuccess {
                    _action.value = ActionState.Done("Quote rejected. Thanks for the feedback.")
                    load()
                }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Reject failed") }
        }
    }

    fun cancel(id: String) {
        scope.launch {
            buyForMe.cancel(id).onSuccess { load() }
        }
    }

    fun resetAction() { _action.value = ActionState.Idle }
}
