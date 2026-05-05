package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.ConsolidationDto
import com.thapsus.cargo.data.dto.CustomsEntryDto
import com.thapsus.cargo.data.dto.CustomsStatus
import com.thapsus.cargo.data.remote.ThapsusApiClient
import com.thapsus.cargo.data.repository.AgentParcelRow
import com.thapsus.cargo.data.repository.CustomsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AssignedConsolidationsResponse(
    val success: Boolean = true,
    val consolidations: List<ConsolidationDto> = emptyList()
)

/**
 * Spec §4.5 + §3.4 /partner/agent. Clearing agent posts IDF, KRA entry,
 * duty/VAT/RDL/IDF charges and marks each parcel cleared. The agent only
 * sees consolidations whose `assigned_agent_id` matches their JWT subject —
 * we fetch that scoped list from `/api/customs/agent/consolidations` rather
 * than the cached global one.
 */
class CustomsAgentViewModel(
    private val agentId: String,
    private val customs: CustomsRepository,
    private val api: ThapsusApiClient
) : SharedViewModel() {

    private val _assignedConsolidations = MutableStateFlow<List<ConsolidationDto>>(emptyList())
    val assignedConsolidations: StateFlow<List<ConsolidationDto>> = _assignedConsolidations.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val _entries = MutableStateFlow<List<CustomsEntryDto>>(emptyList())
    val entries: StateFlow<List<CustomsEntryDto>> = _entries.asStateFlow()

    /** Pre-alert pack for the selected consolidation (for the parcel picker). */
    private val _parcels = MutableStateFlow<List<AgentParcelRow>>(emptyList())
    val parcels: StateFlow<List<AgentParcelRow>> = _parcels.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        scope.launch { refreshAssigned() }
    }

    suspend fun refreshAssigned() {
        runCatching {
            api.get<AssignedConsolidationsResponse>("/customs/agent/consolidations").consolidations
        }.onSuccess {
            _assignedConsolidations.value = it
        }.onFailure {
            _error.value = it.message
        }
    }

    fun select(consolidationId: String) {
        _selectedId.value = consolidationId
        scope.launch {
            customs.refreshForConsolidation(consolidationId)
                .onSuccess { _entries.value = it; _error.value = null }
                .onFailure { _error.value = it.message }
            _parcels.value = customs.parcelsForConsolidation(consolidationId)
        }
    }

    fun submitEntry(
        parcelId: String,
        idfNo: String,
        entryNo: String,
        cifKes: Double,
        dutyKes: Double,
        vatKes: Double,
        idfKes: Double,
        rdlKes: Double,
        docUrl: String? = null
    ) {
        scope.launch {
            _busy.value = true
            customs.submitEntry(
                parcelId = parcelId,
                idfNo = idfNo.ifBlank { null },
                entryNo = entryNo.ifBlank { null },
                cifKes = cifKes,
                dutyKes = dutyKes,
                vatKes = vatKes,
                idfKes = idfKes,
                rdlKes = rdlKes,
                docUrl = docUrl?.takeIf { it.isNotBlank() }
            ).onSuccess {
                _selectedId.value?.let { cid ->
                    customs.refreshForConsolidation(cid)
                        .onSuccess { rows -> _entries.value = rows }
                }
            }.onFailure { _error.value = it.message }
            _busy.value = false
        }
    }

    /**
     * Submit ONE set of customs paperwork that the server fans out across
     * every un-entered parcel for the given customer in the selected
     * consolidation. Saves the agent from filing N forms when one customer
     * has multiple parcels on the same flight (audit follow-up).
     */
    fun submitBulkEntries(
        userId: String,
        idfNo: String,
        entryNo: String,
        cifKes: Double,
        dutyKes: Double,
        vatKes: Double,
        idfKes: Double,
        rdlKes: Double,
        docUrl: String? = null
    ) {
        val cid = _selectedId.value ?: run {
            _error.value = "Pick a consolidation first."
            return
        }
        scope.launch {
            _busy.value = true
            customs.submitBulkEntries(
                consolidationId = cid,
                userId = userId,
                idfNo = idfNo.ifBlank { null },
                entryNo = entryNo.ifBlank { null },
                cifKes = cifKes,
                dutyKes = dutyKes,
                vatKes = vatKes,
                idfKes = idfKes,
                rdlKes = rdlKes,
                docUrl = docUrl?.takeIf { it.isNotBlank() }
            ).onSuccess {
                customs.refreshForConsolidation(cid)
                    .onSuccess { rows -> _entries.value = rows }
                _parcels.value = customs.parcelsForConsolidation(cid)
                _error.value = null
            }.onFailure { _error.value = it.message }
            _busy.value = false
        }
    }

    /**
     * Advance an entry's status. Server cascades to `out_for_delivery` when
     * status='released'. After the call returns, the consolidation's entries
     * are re-fetched so the UI shows the new status without a manual reload.
     * Audit §3.3.3 / S1-11.
     */
    fun advanceEntryStatus(entryId: String, newStatus: CustomsStatus) {
        scope.launch {
            _busy.value = true
            customs.updateEntryStatus(entryId, newStatus.name.lowercase())
                .onSuccess {
                    _selectedId.value?.let { cid ->
                        customs.refreshForConsolidation(cid)
                            .onSuccess { rows -> _entries.value = rows }
                        _parcels.value = customs.parcelsForConsolidation(cid)
                    }
                }
                .onFailure { _error.value = it.message }
            _busy.value = false
        }
    }

    fun dismissError() { _error.value = null }
}
