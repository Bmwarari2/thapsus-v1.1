package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.ConsolidationDto
import com.thapsus.cargo.data.repository.ConsolidationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Powers the cut-off countdown banner on `CustomerHomeView` and
 * `CustomerDashboardView`. Calls `GET /api/consolidations/current` (public
 * endpoint — works pre-login). Returns `null` when no consolidation is open
 * so the banner can hide cleanly.
 */
class CutoffBannerViewModel(
    private val repo: ConsolidationRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    fun load() {
        scope.launch {
            _state.value = State.Loading
            repo.current()
                .onSuccess { _state.value = if (it == null) State.Empty else State.Open(it) }
                .onFailure { _state.value = State.Error(it.message ?: "Couldn't load cut-off") }
        }
    }

    fun clear() { scope.cancel() }

    sealed interface State {
        data object Loading : State
        data object Empty : State
        data class Open(val consolidation: ConsolidationDto) : State
        data class Error(val message: String) : State
    }
}
