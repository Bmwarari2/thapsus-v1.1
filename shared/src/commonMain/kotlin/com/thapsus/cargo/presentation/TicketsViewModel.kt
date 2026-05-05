package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.TicketDto
import com.thapsus.cargo.data.dto.TicketMessageDto
import com.thapsus.cargo.data.repository.TicketsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Live tickets list. Customer mode (`asAdmin = false`) requires the
 * `userId` so the cache + Realtime channel filter on the signed-in user.
 * Admin mode subscribes to the full table.
 */
class TicketsListViewModel(
    private val tickets: TicketsRepository,
    private val userId: String? = null,
    private val asAdmin: Boolean = false
) : SharedViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Loaded(val items: List<TicketDto>) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        scope.launch {
            val source = if (asAdmin) {
                tickets.observeAdminAll()
            } else {
                val uid = userId ?: error("TicketsListViewModel: userId required for customer mode")
                tickets.observeMine(uid)
            }
            source.collect { items ->
                _state.value = UiState.Loaded(items)
            }
        }
    }

    fun create(subject: String, description: String) {
        scope.launch {
            tickets.create(subject, description)
        }
    }
}

class TicketDetailViewModel(
    private val ticketId: String,
    private val tickets: TicketsRepository
) : SharedViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Loaded(val ticket: TicketDto?, val messages: List<TicketMessageDto>) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // Live message thread — bootstrap fetch fills the cache, then
        // Realtime keeps it in sync. The thread payload includes the parent
        // ticket too via the bootstrap call inside observeThread.
        scope.launch {
            tickets.observeThread(ticketId).collect { messages ->
                val current = _state.value as? UiState.Loaded
                _state.value = UiState.Loaded(
                    ticket = current?.ticket,
                    messages = messages
                )
            }
        }
        // Bootstrap the parent ticket once so the header renders.
        refreshTicket()
    }

    private fun refreshTicket() {
        scope.launch {
            tickets.detail(ticketId)
                .onSuccess { resp ->
                    val t = resp.ticket
                    val current = _state.value as? UiState.Loaded
                    if (t == null && current == null) {
                        _state.value = UiState.Error("Ticket not found")
                    } else {
                        _state.value = UiState.Loaded(
                            ticket = t,
                            messages = current?.messages.orEmpty()
                        )
                    }
                }
                .onFailure {
                    if (_state.value is UiState.Loading) {
                        _state.value = UiState.Error(it.message ?: "Failed to load ticket")
                    }
                }
        }
    }

    fun reply(message: String, attachmentUrl: String? = null) {
        scope.launch {
            tickets.postMessage(ticketId, message, attachmentUrl?.takeIf { it.isNotBlank() })
        }
    }
}
