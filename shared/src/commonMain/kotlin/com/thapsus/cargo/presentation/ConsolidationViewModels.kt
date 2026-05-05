package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.ConsolidationDto
import com.thapsus.cargo.data.dto.ConsolidationStatus
import com.thapsus.cargo.data.dto.PackageDto
import com.thapsus.cargo.data.local.ThapsusLocalCache
import com.thapsus.cargo.data.local.ThapsusLocalCache.Companion.toDto
import com.thapsus.cargo.data.repository.ConsolidationRepository
import com.thapsus.cargo.data.repository.PackageRepository
import com.thapsus.cargo.data.repository.UpdateConsolidationRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Spec §3.3 /ops/consolidations. Lists every weekly flight unit; tap one to land
 * on the manifest builder.
 */
class ConsolidationListViewModel(
    private val repo: ConsolidationRepository
) : SharedViewModel() {

    val list: StateFlow<List<ConsolidationDto>> = repo.observeAll()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _createState = MutableStateFlow<CreateState>(CreateState.Idle)
    val createState: StateFlow<CreateState> = _createState.asStateFlow()

    init { refresh() }

    fun refresh() {
        scope.launch {
            _refreshing.value = true
            repo.refreshAll()
            _refreshing.value = false
        }
    }

    fun lock(id: String) {
        scope.launch { repo.lock(id) }
    }

    /**
     * Operator/admin opens a new consolidation. `weekStart` is `yyyy-MM-dd`,
     * `cutoffAt` is an ISO-8601 timestamp. `departureAt` and `notes` are optional.
     */
    fun create(weekStart: String, cutoffAt: String, departureAt: String? = null, notes: String? = null) {
        scope.launch {
            _createState.value = CreateState.InFlight
            repo.create(weekStart, cutoffAt, departureAt, notes)
                .onSuccess {
                    _createState.value = CreateState.Done(it)
                    repo.refreshAll()
                }
                .onFailure { _createState.value = CreateState.Error(it.message ?: "Create failed") }
        }
    }

    fun resetCreateState() { _createState.value = CreateState.Idle }

    sealed interface CreateState {
        data object Idle : CreateState
        data object InFlight : CreateState
        data class Done(val consolidationId: String) : CreateState
        data class Error(val message: String) : CreateState
    }
}

/**
 * Spec §4.4 manifest builder, AWB capture, Tudor handover. Surfaces the parcels
 * currently assigned to the consolidation plus a derived summary.
 */
class ConsolidationDetailViewModel(
    private val consolidationId: String,
    private val cache: ThapsusLocalCache,
    private val packages: PackageRepository,
    private val consolidations: ConsolidationRepository
) : SharedViewModel() {

    val parcels: StateFlow<List<PackageDto>> = cache.observePackagesInConsolidation(consolidationId)
        .map { rows -> rows.map { it.toDto() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * Parcels eligible for assignment to this consolidation: any package that
     * is at the warehouse (received → screened) and not yet attached to a
     * consolidation. Sourced from the local cache so it stays in sync with
     * Realtime updates without an extra fetch.
     */
    val availableParcels: StateFlow<List<PackageDto>> = cache.observeAllPackages()
        .map { rows ->
            rows.map { it.toDto() }.filter { p ->
                p.consolidationId.isNullOrBlank() && p.status in setOf(
                    com.thapsus.cargo.data.dto.PackageStatus.RECEIVED_AT_WAREHOUSE,
                    com.thapsus.cargo.data.dto.PackageStatus.PHOTOGRAPHED,
                    com.thapsus.cargo.data.dto.PackageStatus.WEIGHED,
                    com.thapsus.cargo.data.dto.PackageStatus.SCREENED
                )
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val summary: StateFlow<ConsolidationSummary> = parcels
        .map { ConsolidationSummary.from(it) }
        .stateIn(scope, SharingStarted.Eagerly, ConsolidationSummary.empty())

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // Eagerly populate the local cache with every package so
        // `availableParcels` has something to filter the moment the screen
        // opens. Without this, an operator who navigates straight to a
        // consolidation (instead of via Today) sees an empty "Ready to
        // consolidate" list even when the warehouse has parcels.
        scope.launch { runCatching { packages.refreshAll() } }
    }

    /**
     * Master AWB + PDF + status transition. Routes through Express
     * (`PATCH /api/consolidations/:id`); the previous PostgREST update path
     * was 403'd by the 2026-04-30 RLS lockdown.
     */
    fun submitMasterAwb(awb: String, pdfUrl: String?) {
        scope.launch {
            _saving.value = true
            consolidations.update(
                consolidationId,
                UpdateConsolidationRequest(
                    masterAwbNo = awb,
                    masterAwbPdf = pdfUrl,
                    status = ConsolidationStatus.IN_TRANSIT.name.lowercase()
                )
            ).onFailure { _error.value = it.message }
            _saving.value = false
        }
    }

    fun submitTudorInvoice(invoiceNo: String) {
        scope.launch {
            _saving.value = true
            consolidations.update(
                consolidationId,
                UpdateConsolidationRequest(tudorInvoiceNo = invoiceNo)
            ).onFailure { _error.value = it.message }
            _saving.value = false
        }
    }

    /**
     * Freeze the manifest at status='locked' so no more parcels can be
     * attached and the customer-facing cut-off banner stops counting down.
     * Pairs with [unlock]; routes through Express (S0-6).
     */
    fun lock() {
        scope.launch {
            _saving.value = true
            consolidations.update(
                consolidationId,
                UpdateConsolidationRequest(status = ConsolidationStatus.CUTOFF_LOCKED.name.lowercase())
            ).onFailure { _error.value = it.message }
            _saving.value = false
        }
    }

    /** Re-open a manifest that was locked too early. */
    fun unlock() {
        scope.launch {
            _saving.value = true
            consolidations.update(
                consolidationId,
                UpdateConsolidationRequest(status = ConsolidationStatus.OPEN.name.lowercase())
            ).onFailure { _error.value = it.message }
            _saving.value = false
        }
    }

    /**
     * Mark the consolidation as arrived at JKIA. Stamps `arrival_at` to now
     * (UTC ISO-8601) so the public tracking timeline shows the touchdown
     * time. Used by ops once the master AWB clears Kenyan customs intake;
     * pairs with [markCleared] for the customs-released transition.
     */
    fun markArrivedJkia() {
        scope.launch {
            _saving.value = true
            consolidations.update(
                consolidationId,
                UpdateConsolidationRequest(
                    status = ConsolidationStatus.JKIA_ARRIVED.name.lowercase(),
                    arrivalAt = Clock.System.now().toString()
                )
            ).onFailure { _error.value = it.message }
            _saving.value = false
        }
    }

    /**
     * Customs cleared. Operators flip this once the clearing agent confirms
     * release; downstream the parcels become eligible for `released` and the
     * dispatch board picks them up.
     */
    fun markCleared() {
        scope.launch {
            _saving.value = true
            consolidations.update(
                consolidationId,
                UpdateConsolidationRequest(status = ConsolidationStatus.CLEARED.name.lowercase())
            ).onFailure { _error.value = it.message }
            _saving.value = false
        }
    }

    /** Final state: every parcel delivered. Closes the consolidation. */
    fun markClosed() {
        scope.launch {
            _saving.value = true
            consolidations.update(
                consolidationId,
                UpdateConsolidationRequest(status = ConsolidationStatus.CLOSED.name.lowercase())
            ).onFailure { _error.value = it.message }
            _saving.value = false
        }
    }

    /**
     * Attach the selected parcels to this consolidation. Single round-trip
     * via POST /consolidations/:id/assign-parcels (audit S2-6). Server caps
     * each batch at 200 ids; we chunk on the client as a defensive measure
     * for the rare large-tagging session. Refreshes the package cache once
     * at the end so the UI sees the new manifest in a single re-render.
     */
    fun assignParcels(parcelIds: List<String>) {
        if (parcelIds.isEmpty()) return
        scope.launch {
            _saving.value = true
            var lastError: Throwable? = null
            val missing = mutableListOf<String>()
            parcelIds.chunked(BATCH_SIZE).forEach { chunk ->
                runCatching { consolidations.assignParcels(consolidationId, chunk) }
                    .onSuccess { resp -> missing += resp.missing }
                    .onFailure { lastError = it }
            }
            // Re-pull packages so cache reflects the new consolidation_id.
            runCatching { packages.refreshAll() }
            _error.value = when {
                lastError != null -> lastError?.message ?: "Some parcels couldn't be added"
                missing.isNotEmpty() -> "${missing.size} parcels couldn't be found"
                else -> null
            }
            _saving.value = false
        }
    }

    private companion object {
        const val BATCH_SIZE = 200
    }

    /**
     * Admin assigns this consolidation to a clearing agent. Pass `null` to
     * unassign. After saving, refreshes the local cache so the consolidation
     * detail reflects the new `assigned_agent_id`.
     */
    fun assignAgent(agentId: String?) {
        scope.launch {
            _saving.value = true
            consolidations.assignAgent(consolidationId, agentId)
                .onFailure { _error.value = it.message }
            runCatching { consolidations.refreshAll() }
            _saving.value = false
        }
    }

    fun dismissError() { _error.value = null }
}

data class ConsolidationSummary(
    val totalParcels: Int,
    val totalChargeableKg: Double,
    val totalDeclaredValueGbpPence: Long
) {
    companion object {
        fun empty() = ConsolidationSummary(0, 0.0, 0)
        // Mirrors the server's `SUM(COALESCE(chargeable_kg, weight_kg, 0))`
        // fallback so the manifest summary doesn't read 0 kg when packages
        // have actual weight but the chargeable_kg column hasn't been
        // computed yet (intake captured weight only).
        fun from(parcels: List<PackageDto>) = ConsolidationSummary(
            totalParcels = parcels.size,
            totalChargeableKg = parcels.sumOf { it.chargeableKg ?: it.actualKg ?: 0.0 },
            totalDeclaredValueGbpPence = parcels.sumOf { it.declaredValueGbpPence }
        )
    }
}
