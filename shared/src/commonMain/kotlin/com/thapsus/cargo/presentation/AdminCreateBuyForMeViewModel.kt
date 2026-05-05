package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.AdminUserDto
import com.thapsus.cargo.data.dto.RetailerDto
import com.thapsus.cargo.data.repository.AdminRepository
import com.thapsus.cargo.data.repository.BuyForMeRepository
import com.thapsus.cargo.data.repository.RetailersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the admin "Create Buy-for-me on behalf" form. Loads the customer
 * list + retailer catalog up front; submission either:
 *   - leaves the row at status='pending_quote' for the operator queue, OR
 *   - pre-quotes (estimateGbp non-null) → status='quoted' + customer
 *     receives the quote email immediately + can pay straight away.
 */
class AdminCreateBuyForMeViewModel(
    private val admin: AdminRepository,
    private val buyForMe: BuyForMeRepository,
    private val retailers: RetailersRepository
) : SharedViewModel() {

    sealed interface ActionState {
        data object Idle : ActionState
        data object Submitting : ActionState
        data class Done(
            val orderId: String,
            val itemName: String,
            val customerEmail: String?,
            val preQuoted: Boolean
        ) : ActionState
        data class Error(val message: String) : ActionState
    }

    private val _users = MutableStateFlow<List<AdminUserDto>>(emptyList())
    val users: StateFlow<List<AdminUserDto>> = _users.asStateFlow()

    private val _retailers = MutableStateFlow<List<RetailerDto>>(emptyList())
    val retailerCatalog: StateFlow<List<RetailerDto>> = _retailers.asStateFlow()

    private val _action = MutableStateFlow<ActionState>(ActionState.Idle)
    val action: StateFlow<ActionState> = _action.asStateFlow()

    fun bootstrap() {
        scope.launch {
            admin.listUsers(role = null, limit = 500, page = 1)
                .onSuccess { _users.value = it }
                .onFailure { _action.value = ActionState.Error("Couldn't load customers: ${it.message}") }
        }
        scope.launch {
            retailers.list()
                .onSuccess { _retailers.value = it }
                // Picker is enhancement — fall back to URL field if catalog 500s.
                .onFailure { /* ignore */ }
        }
    }

    fun create(
        userId: String,
        itemName: String,
        retailerId: String?,
        retailerUrl: String?,
        size: String?,
        qty: Int,
        notes: String?,
        estimateGbp: Double?,
        markupPct: Double?
    ) {
        scope.launch {
            _action.value = ActionState.Submitting
            buyForMe.adminCreate(
                userId = userId,
                itemName = itemName,
                retailerId = retailerId,
                retailerUrl = retailerUrl,
                size = size,
                qty = qty,
                notes = notes,
                estimateGbp = estimateGbp,
                markupPct = markupPct,
            ).onSuccess { resp ->
                val customer = _users.value.firstOrNull { it.id == userId }
                _action.value = ActionState.Done(
                    orderId = resp.orderId ?: "",
                    itemName = itemName,
                    customerEmail = customer?.email,
                    preQuoted = resp.preQuoted,
                )
            }.onFailure {
                _action.value = ActionState.Error(it.message ?: "Failed to create order")
            }
        }
    }

    fun resetAction() { _action.value = ActionState.Idle }
}
