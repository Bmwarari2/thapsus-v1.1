package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.ConsolidationDto
import com.thapsus.cargo.data.dto.GenericAckResponse
import com.thapsus.cargo.data.local.ThapsusLocalCache
import com.thapsus.cargo.data.local.ThapsusLocalCache.Companion.toDto
import com.thapsus.cargo.data.remote.Tables
import com.thapsus.cargo.data.remote.ThapsusApiClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@Serializable
internal data class CustomerConsolidationResponse(
    val success: Boolean = true,
    val consolidation: ConsolidationDto? = null
)

@Serializable
internal data class CurrentConsolidationResponse(
    val success: Boolean = true,
    val consolidation: ConsolidationDto? = null
)

@Serializable
internal data class CreateConsolidationRequest(
    @kotlinx.serialization.SerialName("week_start") val weekStart: String,
    @kotlinx.serialization.SerialName("cutoff_at") val cutoffAt: String,
    @kotlinx.serialization.SerialName("departure_at") val departureAt: String? = null,
    val notes: String? = null
)

@Serializable
internal data class CreateConsolidationResponse(
    val success: Boolean = true,
    @kotlinx.serialization.SerialName("consolidation_id") val consolidationId: String? = null
)

@Serializable
internal data class AssignParcelRequest(
    @kotlinx.serialization.SerialName("parcel_id") val parcelId: String
)

@Serializable
internal data class AssignParcelsBatchRequest(
    @kotlinx.serialization.SerialName("parcel_ids") val parcelIds: List<String>
)

/**
 * Public so the ConsolidationRepository.assignParcels return shape can
 * cross the SKIE bridge — Kotlin's "internal exposes public" rule fires
 * otherwise. The fields are read-only and the class is data-class so
 * Swift sees it as a plain value object.
 */
@Serializable
data class AssignParcelsBatchResponse(
    val success: Boolean = true,
    @kotlinx.serialization.SerialName("assigned") val assigned: Int = 0,
    @kotlinx.serialization.SerialName("missing") val missing: List<String> = emptyList(),
    val message: String? = null
)

@Serializable
internal data class AssignAgentRequest(
    @kotlinx.serialization.SerialName("assigned_agent_id") val assignedAgentId: String?
)

/**
 * Body for `PATCH /api/consolidations/:id`. Server accepts any subset of
 * these fields (see `routes/consolidationsV2.js` allow-list); we omit
 * unspecified ones so a partial update doesn't blank an unrelated column.
 *
 * Public so iOS / Android consumers can pass it through SKIE; the other
 * `internal` request DTOs in this file are private to the repo.
 */
@Serializable
data class UpdateConsolidationRequest(
    val status: String? = null,
    @kotlinx.serialization.SerialName("master_awb_no") val masterAwbNo: String? = null,
    @kotlinx.serialization.SerialName("master_awb_pdf") val masterAwbPdf: String? = null,
    @kotlinx.serialization.SerialName("tudor_invoice_no") val tudorInvoiceNo: String? = null,
    @kotlinx.serialization.SerialName("tudor_invoice_pdf") val tudorInvoicePdf: String? = null,
    @kotlinx.serialization.SerialName("manifest_pdf") val manifestPdf: String? = null,
    @kotlinx.serialization.SerialName("departure_at") val departureAt: String? = null,
    @kotlinx.serialization.SerialName("arrival_at") val arrivalAt: String? = null,
    val notes: String? = null,
    @kotlinx.serialization.SerialName("cutoff_at") val cutoffAt: String? = null
)

@Serializable
internal data class ConsolidationListResponse(
    val success: Boolean = true,
    val consolidations: List<ConsolidationDto> = emptyList()
)

class ConsolidationRepository(
    private val supabase: SupabaseClient,
    private val cache: ThapsusLocalCache,
    private val api: ThapsusApiClient
) {

    /** Customer-readable consolidation summary for a parcel the user owns. */
    suspend fun customerSummary(consolidationId: String): Result<ConsolidationDto> = runCatching {
        api.get<CustomerConsolidationResponse>("/consolidations/customer/$consolidationId")
            .consolidation
            ?: error("customer consolidation: missing payload")
    }

    /**
     * Current open consolidation (the upcoming flight cut-off). Public — no
     * auth — so the home screen can show a cut-off countdown even before the
     * customer signs in. Returns null when no consolidation is open.
     */
    suspend fun current(): Result<ConsolidationDto?> = runCatching {
        api.get<CurrentConsolidationResponse>("/consolidations/current").consolidation
    }


    fun observeOpen(): Flow<List<ConsolidationDto>> =
        cache.observeOpenConsolidations().map { rows -> rows.map { it.toDto() } }

    fun observeAll(): Flow<List<ConsolidationDto>> =
        cache.observeAllConsolidations().map { rows -> rows.map { it.toDto() } }

    fun observeOne(id: String): Flow<ConsolidationDto?> =
        cache.observeConsolidation(id).map { it?.toDto() }

    /**
     * Operator/admin list of every consolidation. Goes through the Express
     * endpoint (`GET /api/consolidations`) instead of direct PostgREST so RLS
     * policies on `consolidations` can't silently zero the list — the server
     * uses its service-role pool. Newly created consolidations show up here
     * the moment the create call returns.
     */
    suspend fun refreshAll(): Result<List<ConsolidationDto>> = runCatching {
        val now = Clock.System.now().toEpochMilliseconds()
        val rows = api.get<ConsolidationListResponse>("/consolidations").consolidations
        rows.forEach { cache.upsertConsolidation(it, now) }
        rows
    }

    /**
     * Generic partial update. Routes through Express
     * (`PATCH /api/consolidations/:id`); the previous direct PostgREST path
     * was 403'd by the 2026-04-30 RLS lockdown (no UPDATE policy on
     * `consolidations` for the authenticated role). Server-side handler
     * vets the field list before issuing the SQL.
     */
    suspend fun update(id: String, body: UpdateConsolidationRequest): Result<Unit> = runCatching {
        api.patch<GenericAckResponse, UpdateConsolidationRequest>(
            "/consolidations/$id",
            body
        )
        Unit
    }

    /** Convenience: locks the manifest. Server flips status to 'locked'. */
    suspend fun lock(id: String): Result<Unit> = update(id, UpdateConsolidationRequest(status = "locked"))

    /**
     * Operator/admin opens a new consolidation. Cadence is on-demand — there is
     * no Monday/cut-off-day constraint server-side. `weekStart` and `cutoffAt`
     * are ISO-8601 dates (yyyy-MM-dd) / timestamps the caller picks.
     */
    suspend fun create(
        weekStart: String,
        cutoffAt: String,
        departureAt: String? = null,
        notes: String? = null
    ): Result<String> = runCatching {
        val resp = api.post<CreateConsolidationResponse, CreateConsolidationRequest>(
            "/consolidations",
            CreateConsolidationRequest(
                weekStart = weekStart,
                cutoffAt = cutoffAt,
                departureAt = departureAt,
                notes = notes
            )
        )
        resp.consolidationId ?: error("create consolidation: missing id")
    }

    /** Attach one parcel (an order/package id) to a consolidation. */
    suspend fun assignParcel(consolidationId: String, parcelId: String): Result<Unit> = runCatching {
        api.post<GenericAckResponse, AssignParcelRequest>(
            "/consolidations/$consolidationId/assign-parcel",
            AssignParcelRequest(parcelId = parcelId)
        )
        Unit
    }

    /**
     * Attach many parcels in a single round-trip. Server route added in
     * audit S2-6 (Swiftcargo PR #39). Caps at 200 ids per call server-side;
     * the iOS view-model already chunks if a tagging session somehow
     * exceeds that. The response carries the list of any ids the server
     * couldn't resolve so the caller can surface them to the operator.
     */
    suspend fun assignParcels(
        consolidationId: String,
        parcelIds: List<String>
    ): AssignParcelsBatchResponse {
        if (parcelIds.isEmpty()) return AssignParcelsBatchResponse(success = true)
        return api.post<AssignParcelsBatchResponse, AssignParcelsBatchRequest>(
            "/consolidations/$consolidationId/assign-parcels",
            AssignParcelsBatchRequest(parcelIds = parcelIds)
        )
    }

    /**
     * Admin/operator assigns a clearing agent to this consolidation.
     * Sets `consolidations.assigned_agent_id` so the agent can see it under
     * `GET /api/customs/agent/consolidations`. Pass `null` to unassign.
     */
    suspend fun assignAgent(consolidationId: String, agentId: String?): Result<Unit> = runCatching {
        api.patch<GenericAckResponse, AssignAgentRequest>(
            "/consolidations/$consolidationId",
            AssignAgentRequest(assignedAgentId = agentId)
        )
        Unit
    }
}
