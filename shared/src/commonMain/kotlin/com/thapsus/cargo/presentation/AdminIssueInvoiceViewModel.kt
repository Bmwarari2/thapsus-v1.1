package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.AdminUserDto
import com.thapsus.cargo.data.repository.AdminRepository
import com.thapsus.cargo.data.repository.CustomerConsolidationsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * PR 5: drives the admin "Issue invoice" form.
 *
 *  - loadUsers()                 → fetches the customer list once.
 *  - issue(userId, amount, …)    → POSTs to standalone-invoice endpoint;
 *                                  on success exposes the new row id so the
 *                                  view can render a confirmation banner.
 *
 * Returns sealed ActionState so the view can swap between Idle / Submitting
 * / Done / Error without juggling multiple flags.
 */
class AdminIssueInvoiceViewModel(
    private val admin: AdminRepository,
    private val consolidations: CustomerConsolidationsRepository
) : SharedViewModel() {

    sealed interface ActionState {
        data object Idle : ActionState
        data object Submitting : ActionState
        data class Done(
            val customerConsolidationId: String,
            val amountKes: Double,
            val description: String,
            val customerEmail: String?
        ) : ActionState
        data class Error(val message: String) : ActionState
    }

    private val _users = MutableStateFlow<List<AdminUserDto>>(emptyList())
    val users: StateFlow<List<AdminUserDto>> = _users.asStateFlow()

    private val _action = MutableStateFlow<ActionState>(ActionState.Idle)
    val action: StateFlow<ActionState> = _action.asStateFlow()

    fun loadUsers() {
        scope.launch {
            admin.listUsers(role = null, limit = 500, page = 1)
                .onSuccess { _users.value = it }
                .onFailure { _action.value = ActionState.Error("Couldn't load customers: ${it.message}") }
        }
    }

    fun issue(
        userId: String,
        amountKes: Double,
        description: String,
        notes: String? = null
    ) {
        scope.launch {
            _action.value = ActionState.Submitting
            consolidations.issueStandaloneInvoice(
                userId = userId,
                amountKes = amountKes,
                description = description,
                currency = "KES",
                notes = notes,
            ).onSuccess { row ->
                val customer = _users.value.firstOrNull { it.id == userId }
                _action.value = ActionState.Done(
                    customerConsolidationId = row.id,
                    amountKes = amountKes,
                    description = description,
                    customerEmail = customer?.email,
                )
            }.onFailure {
                _action.value = ActionState.Error(it.message ?: "Failed to issue invoice")
            }
        }
    }

    fun resetAction() { _action.value = ActionState.Idle }
}
