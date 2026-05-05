package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.CreditLedgerEntryDto
import com.thapsus.cargo.data.dto.PaymentDto
import com.thapsus.cargo.data.repository.PaymentsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the customer Transactions screen — paginated payments + credit ledger.
 *
 * Two independent paginated lists in one VM (mirrors the webapp's tabbed
 * layout). Each list tracks { items, loading, done, error } and exposes
 * `loadPayments(reset:)` / `loadCredit(reset:)` for the View to call on
 * appear / load-more / pull-to-refresh.
 */
class TransactionsViewModel(
    private val payments: PaymentsRepository
) : SharedViewModel() {

    private val _paymentsState = MutableStateFlow(PagedPaymentsState())
    val paymentsState: StateFlow<PagedPaymentsState> = _paymentsState.asStateFlow()

    private val _creditState = MutableStateFlow(PagedCreditState())
    val creditState: StateFlow<PagedCreditState> = _creditState.asStateFlow()

    fun loadPayments(reset: Boolean = false, status: String? = null) {
        scope.launch {
            val cur = _paymentsState.value
            if (cur.loading) return@launch
            val nextOffset = if (reset) 0 else cur.items.size
            _paymentsState.value = cur.copy(loading = true, error = null)
            // Default to group=target so the customer sees one row per
            // BFM/order/consolidation, not one row per attempt (PR 2.1).
            payments.listPaged(status = status, limit = PAGE_SIZE, offset = nextOffset, group = "target")
                .onSuccess { batch ->
                    _paymentsState.value = PagedPaymentsState(
                        items   = if (reset) batch else cur.items + batch,
                        loading = false,
                        done    = batch.size < PAGE_SIZE,
                        error   = null
                    )
                }
                .onFailure { e ->
                    _paymentsState.value = cur.copy(loading = false, error = e.message ?: "Failed to load payments")
                }
        }
    }

    fun loadCredit(reset: Boolean = false) {
        scope.launch {
            val cur = _creditState.value
            if (cur.loading) return@launch
            val nextOffset = if (reset) 0 else cur.items.size
            _creditState.value = cur.copy(loading = true, error = null)
            payments.creditLedger(limit = PAGE_SIZE, offset = nextOffset)
                .onSuccess { batch ->
                    _creditState.value = PagedCreditState(
                        items   = if (reset) batch else cur.items + batch,
                        loading = false,
                        done    = batch.size < PAGE_SIZE,
                        error   = null
                    )
                }
                .onFailure { e ->
                    _creditState.value = cur.copy(loading = false, error = e.message ?: "Failed to load credit")
                }
        }
    }

    companion object {
        const val PAGE_SIZE = 20
    }
}

// Top-level (not nested in TransactionsViewModel) so SKIE exposes them as
// regular Swift classes accessible by name. Non-sealed nested Kotlin data
// classes don't get a Swift import path under SKIE.
data class PagedPaymentsState(
    val items: List<PaymentDto> = emptyList(),
    val loading: Boolean = false,
    val done: Boolean = false,
    val error: String? = null
)

data class PagedCreditState(
    val items: List<CreditLedgerEntryDto> = emptyList(),
    val loading: Boolean = false,
    val done: Boolean = false,
    val error: String? = null
)
