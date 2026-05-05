package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.LastMileRunDto
import com.thapsus.cargo.data.dto.PodEventDto
import com.thapsus.cargo.data.repository.LastMileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Rider PWA-equivalent (spec §4.6) for the native iOS app.
 *
 * Rider taps a parcel → scans recipient OTP → captures POD photo + signature.
 * Writes always go through the outbox so a dropped 4G signal in Karen or Eastlands
 * cannot lose a delivery confirmation.
 */
class RiderRunViewModel(
    private val riderId: String,
    private val repo: LastMileRepository
) : SharedViewModel() {

    private val today: String = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    val runs: StateFlow<List<LastMileRunDto>> = repo.observeRunsForRider(riderId)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _capturing = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val capturing: StateFlow<CaptureState> = _capturing.asStateFlow()

    init { refresh() }

    fun refresh() {
        scope.launch { repo.refreshTodayForRider(riderId, today) }
    }

    fun capturePod(event: PodEventDto) {
        scope.launch {
            _capturing.value = CaptureState.Saving
            repo.capturePod(event)
                .onSuccess { _capturing.value = CaptureState.Saved(event.id) }
                .onFailure { _capturing.value = CaptureState.Failed(it.message ?: "queue failed") }
        }
    }

    fun resetCapture() { _capturing.value = CaptureState.Idle }

    fun flushOutbox() {
        scope.launch { repo.flushOutbox() }
    }

    sealed interface CaptureState {
        data object Idle : CaptureState
        data object Saving : CaptureState
        data class Saved(val eventId: String) : CaptureState
        data class Failed(val message: String) : CaptureState
    }

    @Suppress("unused")
    private fun localToday(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}
