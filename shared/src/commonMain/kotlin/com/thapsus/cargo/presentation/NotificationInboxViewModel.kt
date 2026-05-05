package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.NotificationDto
import com.thapsus.cargo.data.repository.NotificationsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Live customer inbox. Subscribes to `NotificationsRepository.observeForUser`
 * which fans the cache + Realtime stream into one Flow. New rows arriving over
 * Realtime hit the SwiftUI inbox without a manual refresh.
 */
class NotificationInboxViewModel(
    private val userId: String,
    private val notifications: NotificationsRepository
) : SharedViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Loaded(val items: List<NotificationDto>, val unread: Int) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        scope.launch {
            notifications.observeForUser(userId).collect { items ->
                _state.value = UiState.Loaded(
                    items = items,
                    unread = items.count { !it.isRead }
                )
            }
        }
    }

    fun refresh() {
        scope.launch {
            notifications.refresh(userId)
                .onFailure {
                    if (_state.value !is UiState.Loaded) {
                        _state.value = UiState.Error(it.message ?: "Failed to load notifications")
                    }
                }
        }
    }

    fun markRead(id: String) {
        scope.launch { notifications.markRead(id) }
    }

    fun markAllRead() {
        scope.launch { notifications.markAllRead(userId) }
    }
}
