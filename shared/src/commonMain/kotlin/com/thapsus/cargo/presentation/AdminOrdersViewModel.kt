package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.AdminOrderRow
import com.thapsus.cargo.data.dto.AdminUserDto
import com.thapsus.cargo.data.dto.EditOrderRequest
import com.thapsus.cargo.data.repository.AdminRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminOrdersViewModel(
    private val admin: AdminRepository
) : SharedViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Loaded(
            val orders: List<AdminOrderRow>,
            val total: Int,
            val page: Int,
            val hasMore: Boolean,
            val loadingMore: Boolean = false
        ) : UiState
    }

    sealed interface ActionState {
        data object Idle : ActionState
        data object InFlight : ActionState
        data class Done(val message: String) : ActionState
        data class Error(val message: String) : ActionState
    }

    /** Persisted filter selection — survives bulk update + edit refreshes. */
    data class Filters(
        val status: String? = null,
        val market: String? = null,
        val startDate: String? = null,
        val endDate: String? = null
    )

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _action = MutableStateFlow<ActionState>(ActionState.Idle)
    val action: StateFlow<ActionState> = _action.asStateFlow()

    private val _filters = MutableStateFlow(Filters())
    val filters: StateFlow<Filters> = _filters.asStateFlow()

    /** Live customer-search hits for the "Create order for client" sheet. */
    private val _customerHits = MutableStateFlow<List<AdminUserDto>>(emptyList())
    val customerHits: StateFlow<List<AdminUserDto>> = _customerHits.asStateFlow()

    private var customerSearchJob: Job? = null
    private val pageSize = 20

    fun load() {
        scope.launch {
            _state.value = UiState.Loading
            val f = _filters.value
            admin.listOrders(
                page = 1, limit = pageSize,
                status = f.status, market = f.market,
                startDate = f.startDate, endDate = f.endDate
            )
                .onSuccess { resp ->
                    val total = resp.pagination?.total ?: resp.orders.size
                    val hasMore = resp.orders.size < total
                    _state.value = UiState.Loaded(
                        orders = resp.orders,
                        total = total,
                        page = 1,
                        hasMore = hasMore
                    )
                }
                .onFailure { _state.value = UiState.Error(it.message ?: "Failed to load orders") }
        }
    }

    fun setStatus(status: String?) {
        _filters.value = _filters.value.copy(status = status?.takeIf { it.isNotBlank() })
        load()
    }

    fun setMarket(market: String?) {
        _filters.value = _filters.value.copy(market = market?.takeIf { it.isNotBlank() })
        load()
    }

    fun setDateRange(startDate: String?, endDate: String?) {
        _filters.value = _filters.value.copy(
            startDate = startDate?.takeIf { it.isNotBlank() },
            endDate = endDate?.takeIf { it.isNotBlank() }
        )
        load()
    }

    fun clearFilters() {
        _filters.value = Filters()
        load()
    }

    fun loadMore() {
        val current = _state.value as? UiState.Loaded ?: return
        if (!current.hasMore || current.loadingMore) return
        _state.value = current.copy(loadingMore = true)
        scope.launch {
            val f = _filters.value
            val nextPage = current.page + 1
            admin.listOrders(
                page = nextPage, limit = pageSize,
                status = f.status, market = f.market,
                startDate = f.startDate, endDate = f.endDate
            )
                .onSuccess { resp ->
                    val combined = current.orders + resp.orders
                    val total = resp.pagination?.total ?: combined.size
                    _state.value = UiState.Loaded(
                        orders = combined,
                        total = total,
                        page = nextPage,
                        hasMore = combined.size < total,
                        loadingMore = false
                    )
                }
                .onFailure {
                    _state.value = current.copy(loadingMore = false)
                    _action.value = ActionState.Error(it.message ?: "Couldn't load more orders")
                }
        }
    }

    fun sendReminder(id: String, amount: Double, notes: String?) {
        scope.launch {
            _action.value = ActionState.InFlight
            admin.sendReminder(id, amount, notes)
                .onSuccess { _action.value = ActionState.Done("Reminder sent") }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Reminder failed") }
        }
    }

    fun bulkUpdate(ids: List<String>, status: String) {
        scope.launch {
            _action.value = ActionState.InFlight
            admin.bulkUpdateOrders(ids, status)
                .onSuccess { _action.value = ActionState.Done("Updated ${ids.size} order(s)"); load() }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Bulk update failed") }
        }
    }

    fun editOrder(
        id: String,
        weightKg: Double? = null,
        actualCost: Double? = null,
        customsDuty: Double? = null,
        status: String? = null,
        description: String? = null,
        electronicsItem: String? = null,
        notes: String? = null
    ) {
        scope.launch {
            _action.value = ActionState.InFlight
            admin.editOrder(id, EditOrderRequest(
                weightKg = weightKg,
                actualCost = actualCost,
                customsDuty = customsDuty,
                status = status,
                description = description,
                electronicsItem = electronicsItem,
                orderNotes = notes
            ))
                .onSuccess { _action.value = ActionState.Done("Order updated"); load() }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Edit failed") }
        }
    }

    fun cancel(id: String, reason: String?) {
        scope.launch {
            admin.cancelOrder(id, reason).onSuccess { load() }
        }
    }

    fun requestPayment(id: String, amount: Double, notes: String?) {
        scope.launch {
            _action.value = ActionState.InFlight
            admin.requestPayment(id, amount, notes)
                .onSuccess { _action.value = ActionState.Done("Payment requested") }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Failed") }
        }
    }

    fun createForClient(
        customerEmail: String?,
        customerName: String?,
        retailer: String,
        market: String,
        description: String,
        weightKg: Double?,
        shippingSpeed: String,
        insurance: Boolean,
        declaredValue: Double,
        electronicsItem: String?,
        hsTier: String?
    ) {
        scope.launch {
            _action.value = ActionState.InFlight
            admin.createOrderForClient(
                com.thapsus.cargo.data.dto.CreateOrderForClientRequest(
                    customerEmail = customerEmail,
                    customerName = customerName,
                    hsTier = hsTier,
                    retailer = retailer,
                    market = market,
                    description = description,
                    weightKg = weightKg,
                    shippingSpeed = shippingSpeed,
                    insurance = insurance,
                    declaredValue = declaredValue,
                    electronicsItem = electronicsItem
                )
            )
                .onSuccess {
                    _action.value = ActionState.Done("Order created for client")
                    load()
                }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Create failed") }
        }
    }

    fun resetAction() { _action.value = ActionState.Idle }

    /**
     * Debounced live customer search. Each keystroke cancels the prior job;
     * after 250 ms of quiet, fires `/admin/users/search?q=`. Only customers
     * are surfaced by the server query.
     */
    fun searchCustomers(query: String) {
        customerSearchJob?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            _customerHits.value = emptyList()
            return
        }
        customerSearchJob = scope.launch {
            delay(250)
            admin.searchUsers(q)
                .onSuccess { results ->
                    _customerHits.value = results.filter { it.role == "customer" }.take(8)
                }
                .onFailure { _customerHits.value = emptyList() }
        }
    }

    fun clearCustomerSearch() {
        customerSearchJob?.cancel()
        _customerHits.value = emptyList()
    }
}
