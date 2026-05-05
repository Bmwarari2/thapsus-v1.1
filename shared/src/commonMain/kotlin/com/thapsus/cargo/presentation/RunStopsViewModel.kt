package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.DispatchParcelRow
import com.thapsus.cargo.data.dto.OutboxFailureDto
import com.thapsus.cargo.data.local.ThapsusLocalCache.Companion.toDto
import com.thapsus.cargo.data.repository.LastMileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the rider's per-run stop list.
 *
 * Reads from `GET /api/last-mile/runs/:id/parcels` — the same endpoint the
 * operator dispatch UI uses — so rider, operator, and admin all see the
 * same source of truth.  Stops are returned in routing order via the
 * `position` column on the `last_mile_run_parcels` join table
 * (migration 012).
 *
 * The previous implementation queried a legacy `run_stops` table that
 * doesn't exist on production; the Supabase call silently failed and the
 * rider saw a blank "Stops" screen.
 */
class RunStopsViewModel(
    private val runId: String,
    private val lastMile: LastMileRepository
) : SharedViewModel() {

    private val _parcels = MutableStateFlow<List<DispatchParcelRow>>(emptyList())
    val parcels: StateFlow<List<DispatchParcelRow>> = _parcels.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Terminal outbox failures (4xx) — surfaced inline on the stop list so
     * the rider can tap to retry / dismiss instead of staring at a
     * "Queued for sync" message that will never resolve (audit M2).
     */
    val outboxFailures: StateFlow<List<OutboxFailureDto>> = lastMile
        .observeOutboxFailures()
        .map { rows -> rows.map { it.toDto() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init { refresh() }

    fun refresh() {
        scope.launch {
            _loading.value = true
            runCatching { lastMile.fetchRunParcels(runId) }
                .onSuccess { resp -> _parcels.value = resp.parcels }
                .onFailure { _error.value = it.message }
            _loading.value = false
        }
    }

    /**
     * Re-enqueue a recorded failure so the worker re-attempts it. UI calls
     * this when the rider taps the inline retry banner.
     */
    fun retryFailure(mutationId: String) {
        scope.launch { lastMile.retryFailedMutation(mutationId) }
    }

    /** Drop a recorded failure without retrying. */
    fun dismissFailure(mutationId: String) {
        lastMile.dismissFailedMutation(mutationId)
    }

    fun dismissError() { _error.value = null }
}
