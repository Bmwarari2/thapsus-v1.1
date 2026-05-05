package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.AdminUserDto
import com.thapsus.cargo.data.dto.EmailLogRow
import com.thapsus.cargo.data.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminUsersViewModel(
    private val admin: AdminRepository
) : SharedViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Loaded(val users: List<AdminUserDto>, val query: String = "") : UiState
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
            admin.listUsers()
                .onSuccess { _state.value = UiState.Loaded(it) }
                .onFailure { _state.value = UiState.Error(it.message ?: "Failed to load users") }
        }
    }

    fun search(query: String) {
        scope.launch {
            if (query.isBlank()) { load(); return@launch }
            admin.searchUsers(query.trim())
                .onSuccess { _state.value = UiState.Loaded(it, query) }
                .onFailure { _state.value = UiState.Error(it.message ?: "Search failed") }
        }
    }

    fun provision(name: String, email: String, phone: String?, role: String) {
        scope.launch {
            _action.value = ActionState.InFlight
            admin.provisionUser(name, email, phone, role)
                .onSuccess { resp ->
                    val statusLine = when (resp.emailStatus) {
                        "sent" -> "Welcome email sent to $email."
                        "failed" -> "Account created. Welcome email failed: ${resp.emailError ?: "unknown"} — use Resend on the user detail."
                        else -> resp.message ?: "User provisioned."
                    }
                    _action.value = ActionState.Done(statusLine)
                    load()
                }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Failed to provision") }
        }
    }

    fun resendWelcome(id: String) {
        scope.launch {
            _action.value = ActionState.InFlight
            admin.resendWelcome(id)
                .onSuccess { resp ->
                    if (resp.emailStatus == "sent") {
                        _action.value = ActionState.Done(resp.message ?: "Welcome email re-sent.")
                    } else {
                        _action.value = ActionState.Error(resp.message ?: "Resend failed")
                    }
                }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Resend failed") }
        }
    }

    fun delete(id: String) {
        scope.launch {
            _action.value = ActionState.InFlight
            admin.deleteUser(id)
                .onSuccess {
                    _action.value = ActionState.Done("User deleted.")
                    load()
                }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Delete failed") }
        }
    }

    fun setActive(id: String, isActive: Boolean) {
        scope.launch {
            _action.value = ActionState.InFlight
            admin.setUserActive(id, isActive)
                .onSuccess {
                    _action.value = ActionState.Done(if (isActive) "User reactivated." else "User deactivated.")
                    load()
                }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Update failed") }
        }
    }

    /**
     * Sends a password reset email to the user. The admin does not pick a
     * new password — the server emails a one-time reset link. See audit §3.5.4.
     */
    fun sendPasswordResetEmail(id: String) {
        scope.launch {
            _action.value = ActionState.InFlight
            admin.sendUserPasswordResetEmail(id)
                .onSuccess { _action.value = ActionState.Done("Password reset email sent.") }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Reset failed") }
        }
    }

    fun resetActionState() { _action.value = ActionState.Idle }
}

class AdminUserDetailViewModel(
    private val userId: String,
    private val admin: AdminRepository
) : SharedViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Loaded(
            val user: AdminUserDto,
            val emails: List<EmailLogRow>
        ) : UiState
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

    init { load() }

    fun load() {
        scope.launch {
            val user = admin.userDetail(userId).getOrElse {
                _state.value = UiState.Error(it.message ?: "Failed to load user")
                return@launch
            }
            val emails = admin.userEmails(userId).getOrNull()?.emails.orEmpty()
            _state.value = UiState.Loaded(user, emails)
        }
    }

    fun resendWelcome() {
        scope.launch {
            _action.value = ActionState.InFlight
            admin.resendWelcome(userId)
                .onSuccess { resp ->
                    if (resp.emailStatus == "sent") {
                        _action.value = ActionState.Done(resp.message ?: "Welcome email re-sent.")
                    } else {
                        _action.value = ActionState.Error(resp.message ?: "Resend failed")
                    }
                    load()
                }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Resend failed") }
        }
    }

    fun setActive(isActive: Boolean) {
        scope.launch {
            _action.value = ActionState.InFlight
            admin.setUserActive(userId, isActive)
                .onSuccess {
                    _action.value = ActionState.Done(if (isActive) "User reactivated." else "User deactivated.")
                    load()
                }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Update failed") }
        }
    }

    fun setRole(role: String) {
        scope.launch {
            _action.value = ActionState.InFlight
            admin.updateUser(userId, role = role)
                .onSuccess {
                    _action.value = ActionState.Done("Role changed to $role.")
                    load()
                }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Update failed") }
        }
    }

    fun sendPasswordResetEmail() {
        scope.launch {
            _action.value = ActionState.InFlight
            admin.sendUserPasswordResetEmail(userId)
                .onSuccess { _action.value = ActionState.Done("Password reset email sent.") }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Reset failed") }
        }
    }

    /** Hard-delete via DELETE /admin/users/:id. Caller must show a confirmation first. */
    fun delete(onDeleted: () -> Unit) {
        scope.launch {
            _action.value = ActionState.InFlight
            admin.deleteUser(userId)
                .onSuccess {
                    _action.value = ActionState.Done("User deleted.")
                    onDeleted()
                }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Delete failed") }
        }
    }

    fun resetActionState() { _action.value = ActionState.Idle }
}
