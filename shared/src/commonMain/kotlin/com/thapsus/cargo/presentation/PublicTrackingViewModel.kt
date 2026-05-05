package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.TrackingDto
import com.thapsus.cargo.data.repository.TrackingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Backs `TrackingView.swift` for the public tracking flow. */
class PublicTrackingViewModel(
    private val repo: TrackingRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun search(trackingNumber: String) {
        val trimmed = trackingNumber.trim()
        if (trimmed.isEmpty()) {
            _state.value = State.Error("Enter a tracking number")
            return
        }
        _state.value = State.Loading(trimmed)
        scope.launch {
            repo.publicTrack(trimmed)
                .onSuccess { _state.value = State.Found(it) }
                .onFailure {
                    _state.value = State.Error(it.message ?: "Couldn't find that tracking number")
                }
        }
    }

    fun reset() { _state.value = State.Idle }

    fun clear() { scope.cancel() }

    sealed interface State {
        data object Idle : State
        data class Loading(val trackingNumber: String) : State
        data class Found(val tracking: TrackingDto) : State
        data class Error(val message: String) : State
    }
}
