package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.ErrorLogRow
import com.thapsus.cargo.data.dto.ErrorLogStatsResponse
import com.thapsus.cargo.data.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminErrorLogsViewModel(
    private val admin: AdminRepository
) : SharedViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Loaded(
            val stats: ErrorLogStatsResponse,
            val logs: List<ErrorLogRow>,
            val filterLevel: String? = null,
            val search: String? = null
        ) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(level: String? = null, search: String? = null) {
        scope.launch {
            _state.value = UiState.Loading
            val stats = admin.errorLogStats().getOrNull() ?: ErrorLogStatsResponse()
            val logs = admin.errorLogs(level = level, search = search).getOrNull().orEmpty()
            _state.value = UiState.Loaded(stats, logs, level, search)
        }
    }

    fun clearLogs() {
        scope.launch {
            admin.clearErrorLogs().onSuccess { load() }
        }
    }
}
