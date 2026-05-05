package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.DispatchParcelRow
import com.thapsus.cargo.data.dto.LastMileRunDto
import com.thapsus.cargo.data.dto.RiderDto
import com.thapsus.cargo.data.local.ThapsusLocalCache
import com.thapsus.cargo.data.repository.LastMileRepository
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Spec §3.3 /ops/dispatch + §4.6. Operator dispatch board — clusters
 * dispatch-ready parcels and creates rider runs.
 *
 * Driven entirely by `GET /api/last-mile/dispatch` so the iOS view shows
 * the same set the webapp does.  The previous local-cache-filter path
 * looked for `package.status == "released"` which the customs/duty-payment
 * flow on prod never produced (orders.status stays 'customs' with
 * hold_reason cleared).
 */
class DispatchViewModel(
    private val supabase: SupabaseClient,
    private val cache: ThapsusLocalCache,
    private val lastMile: LastMileRepository
) : SharedViewModel() {

    private val _pendingParcels = MutableStateFlow<List<DispatchParcelRow>>(emptyList())
    val pendingParcels: StateFlow<List<DispatchParcelRow>> = _pendingParcels.asStateFlow()

    private val _zones = MutableStateFlow<List<String>>(emptyList())
    val zones: StateFlow<List<String>> = _zones.asStateFlow()

    private val _runsList = MutableStateFlow<List<LastMileRunDto>>(emptyList())
    val runsList: StateFlow<List<LastMileRunDto>> = _runsList.asStateFlow()

    private val _riders = MutableStateFlow<List<RiderDto>>(emptyList())
    val riders: StateFlow<List<RiderDto>> = _riders.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            _busy.value = true
            runCatching { lastMile.fetchDispatchBoard() }
                .onSuccess { board ->
                    _pendingParcels.value = board.pending
                    _runsList.value = board.runs
                    if (board.zones.isNotEmpty()) _zones.value = board.zones
                }
                .onFailure { _error.value = it.message }
            // Riders rarely change; refresh in the background but don't block
            // the busy spinner on it.
            runCatching { _riders.value = lastMile.fetchRiders() }
            _busy.value = false
        }
    }

    /**
     * (Re)assign the rider on an existing run. After the server confirms,
     * refreshes the runs list so the row's rider id flips locally without a
     * pull-to-refresh.
     */
    fun assignRider(runId: String, riderId: String?) {
        scope.launch {
            _busy.value = true
            lastMile.assignRider(runId, riderId)
                .onSuccess { refresh() }
                .onFailure { _error.value = it.message }
            _busy.value = false
        }
    }

    /**
     * Flip a planned run to in_progress.  Server activates the run —
     * mints OTPs, dispatches recipient notifications, flips orders +
     * packages to out_for_delivery.  Refreshes the board so the run
     * row shows its new status.
     */
    fun startRun(runId: String) {
        scope.launch {
            _busy.value = true
            lastMile.startRun(runId)
                .onSuccess { refresh() }
                .onFailure { _error.value = it.message }
            _busy.value = false
        }
    }

    /**
     * Operator creates a new rider run via Express (`POST /api/last-mile/runs`).
     * The previous `supabase.from(LAST_MILE_RUNS).insert(...)` path was
     * silently 403'd by the 2026-04-30 RLS lockdown — there is no INSERT
     * policy on `last_mile_runs` for the authenticated role and won't be.
     * Server now owns the insert + the parcel→`out_for_delivery` cascade.
     */
    fun createRun(riderId: String, zone: String, runDate: String, parcelIds: List<String>) {
        scope.launch {
            _busy.value = true
            lastMile.createRun(
                riderId = riderId,
                zone = zone,
                runDate = runDate,
                parcelIds = parcelIds
            )
                .onSuccess { refresh() }
                .onFailure { _error.value = it.message }
            _busy.value = false
        }
    }

    fun dismissError() { _error.value = null }
}
