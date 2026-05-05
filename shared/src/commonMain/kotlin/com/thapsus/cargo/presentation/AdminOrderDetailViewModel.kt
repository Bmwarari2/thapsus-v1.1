package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.AdminOrderDetailDto
import com.thapsus.cargo.data.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Powers `AdminOrderDetailView`. Fetches `GET /api/orders/:id` (admin bypass)
 * and exposes the live `cost_breakdown` block + attached packages list.
 *
 * Edit/cancel actions stay on `AdminOrdersViewModel` — drilling down is
 * read-only here, with `refresh()` available so the detail view picks up the
 * server's recomputed cost after an edit.
 */
class AdminOrderDetailViewModel(
    private val orderId: String,
    private val admin: AdminRepository
) : SharedViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(val order: AdminOrderDetailDto) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load() {
        scope.launch {
            _state.value = UiState.Loading
            admin.orderDetail(orderId)
                .onSuccess { _state.value = UiState.Loaded(it) }
                .onFailure { _state.value = UiState.Error(it.message ?: "Couldn't load order") }
        }
    }

    fun refresh() = load()
}
