package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.repository.NpsRepository
import com.thapsus.cargo.util.loggingExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One-shot post-delivery NPS survey.
 *
 * The SwiftUI modal opens once a parcel transitions to `delivered`; the user
 * picks a score (0–10), optionally adds a comment, and submits. The view
 * model dismisses itself on success — there is no list to refresh.
 */
class NpsSurveyViewModel(
    private val repo: NpsRepository,
    private val parcelId: String? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + loggingExceptionHandler)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun submit(score: Int, comment: String?) {
        if (score !in 0..10) {
            _state.value = State.Error("Score must be between 0 and 10")
            return
        }
        _state.value = State.Submitting
        scope.launch {
            repo.submit(score = score, comment = comment, parcelId = parcelId)
                .onSuccess { _state.value = State.Sent }
                .onFailure { _state.value = State.Error(it.message ?: "Couldn't submit survey") }
        }
    }

    fun reset() { _state.value = State.Idle }

    fun clear() { scope.cancel() }

    sealed interface State {
        data object Idle : State
        data object Submitting : State
        data object Sent : State
        data class Error(val message: String) : State
    }
}
