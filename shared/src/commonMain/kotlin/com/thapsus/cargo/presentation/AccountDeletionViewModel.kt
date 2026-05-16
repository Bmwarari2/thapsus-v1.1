package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.AccountDeletionRequestDto
import com.thapsus.cargo.data.repository.AccountDeletionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Customer-facing VM for the 14-day account-deletion cooldown flow.
 * Mirrors the server-side endpoints under /api/account/deletion-request.
 *
 * UI states:
 *   - Loading    — initial fetch in flight
 *   - Idle       — no active or recent request; customer can start one
 *   - Active     — pending cooldown row; show countdown + download link + cancel CTA
 *   - Cancelled  — most-recent row was cancelled; customer can start a fresh one
 *   - Completed  — account already deleted (should never actually render
 *                  because the session is invalidated, but kept for safety)
 *   - Error      — load failure (sanitized server message)
 */
class AccountDeletionViewModel(
    private val repo: AccountDeletionRepository
) : SharedViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data object Idle : UiState
        data class Active(val request: AccountDeletionRequestDto) : UiState
        data class Cancelled(val request: AccountDeletionRequestDto) : UiState
        data class Completed(val request: AccountDeletionRequestDto) : UiState
        data class Error(val message: String) : UiState
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
            repo.current()
                .onSuccess { req ->
                    _state.value = when {
                        req == null -> UiState.Idle
                        req.status == "cancelled" -> UiState.Cancelled(req)
                        req.status == "completed" -> UiState.Completed(req)
                        else -> UiState.Active(req)
                    }
                }
                .onFailure { _state.value = UiState.Error(it.message ?: "Couldn't load deletion status.") }
        }
    }

    /** Caller must show a confirmation dialog first. */
    fun startDeletion() {
        scope.launch {
            _action.value = ActionState.InFlight
            repo.start()
                .onSuccess { req ->
                    _state.value = UiState.Active(req)
                    _action.value = ActionState.Done(
                        "Deletion scheduled. We've emailed your data export to you."
                    )
                }
                .onFailure {
                    _action.value = ActionState.Error(it.message ?: "Couldn't schedule deletion.")
                }
        }
    }

    fun cancelDeletion(reason: String? = null) {
        scope.launch {
            _action.value = ActionState.InFlight
            repo.cancel(reason)
                .onSuccess { req ->
                    _state.value = UiState.Cancelled(req)
                    _action.value = ActionState.Done("Deletion cancelled. Your account stays as-is.")
                }
                .onFailure {
                    _action.value = ActionState.Error(it.message ?: "Couldn't cancel deletion.")
                }
        }
    }

    /**
     * Re-mint the signed download URL — used when the customer comes
     * back days later and the original email link has expired. On
     * success, mutates the Active state's request in place so the UI
     * can rebind.
     */
    fun refreshExportUrl() {
        val current = (_state.value as? UiState.Active) ?: return
        scope.launch {
            _action.value = ActionState.InFlight
            repo.refreshExportUrl()
                .onSuccess { url ->
                    _state.value = UiState.Active(current.request.copy(exportSignedUrl = url))
                    _action.value = ActionState.Done("Download link refreshed.")
                }
                .onFailure {
                    _action.value = ActionState.Error(it.message ?: "Couldn't refresh download link.")
                }
        }
    }

    fun resetActionState() { _action.value = ActionState.Idle }
}
