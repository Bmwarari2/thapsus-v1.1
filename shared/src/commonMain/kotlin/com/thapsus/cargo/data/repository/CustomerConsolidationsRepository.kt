package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.AttachCustomerConsolidationsRequest
import com.thapsus.cargo.data.dto.CreateCustomerConsolidationRequest
import com.thapsus.cargo.data.dto.CreateCustomerConsolidationResponse
import com.thapsus.cargo.data.dto.CustomerConsolidationDto
import com.thapsus.cargo.data.dto.CustomerConsolidationListResponse
import com.thapsus.cargo.data.dto.CustomerConsolidationResponse
import com.thapsus.cargo.data.dto.GenericAckResponse
import com.thapsus.cargo.data.dto.IssueStandaloneInvoiceRequest
import com.thapsus.cargo.data.dto.SetInvoiceRequest
import com.thapsus.cargo.data.dto.SuggestedInvoiceResponse
import com.thapsus.cargo.data.remote.Tables
import com.thapsus.cargo.data.remote.ThapsusApiClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Customer-consolidations (per-user grouping + invoice). Phase 2.
 *
 * Reads:
 *   • Customer  → Supabase PostgREST + Realtime, RLS scopes by user_id.
 *   • Admin     → Express GET /api/customer-consolidations (with filters).
 *
 * Writes are admin-only and route through Express so server-side
 * notification fan-out (in-app + email + SSE) commits with the
 * status flip.
 */
class CustomerConsolidationsRepository(
    private val supabase: SupabaseClient,
    private val api: ThapsusApiClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    // ── Customer reads (RLS-scoped via Supabase JWT) ──────────────────────

    /**
     * One-shot fetch for the customer's own consolidations. RLS policy
     * (migration 025) restricts SELECT to `auth.uid()::text = user_id`,
     * so the synthetic Supabase JWT minted at login is sufficient.
     */
    suspend fun fetchForUser(userId: String): List<CustomerConsolidationDto> =
        supabase.from(Tables.CUSTOMER_CONSOLIDATIONS)
            .select {
                filter { eq("user_id", userId) }
                order("created_at", order = Order.DESCENDING)
                limit(50)
            }
            .decodeList()

    /**
     * Live channel for the customer's Orders tab — emits each
     * Insert/Update on `customer_consolidations` rows owned by them.
     * Migration 025 added the table to `supabase_realtime` so the
     * subscription actually fires.
     */
    fun observeForUser(userId: String): Flow<CustomerConsolidationDto> = callbackFlow {
        val channel = supabase.channel("rt-customer-consolidations-$userId")
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = Tables.CUSTOMER_CONSOLIDATIONS
            filter("user_id", FilterOperator.EQ, userId)
        }
        val job = scope.launch {
            // Same belt-and-braces as PackageRepository.observeRealtimeForUser:
            // any throw inside `collect` (decoder mismatch, websocket
            // closed mid-frame) was bubbling to the supervisor scope's
            // CoroutineExceptionHandler and crashing the app on the
            // Orders tab.
            runCatching {
                changeFlow.collect { action ->
                    when (action) {
                        is PostgresAction.Insert -> runCatching {
                            trySend(json.decodeFromJsonElement(CustomerConsolidationDto.serializer(), action.record))
                        }
                        is PostgresAction.Update -> runCatching {
                            trySend(json.decodeFromJsonElement(CustomerConsolidationDto.serializer(), action.record))
                        }
                        is PostgresAction.Delete -> Unit
                        is PostgresAction.Select -> Unit
                    }
                }
            }.onFailure { t ->
                println("[CustomerConsolidationsRepository.observeForUser] collect failed: ${t::class.simpleName}: ${t.message}")
            }
        }
        runCatching { channel.subscribe() }
            .onFailure { t ->
                println("[CustomerConsolidationsRepository.observeForUser] subscribe failed: ${t.message}")
            }
        awaitClose {
            job.cancel()
            scope.launch { runCatching { channel.unsubscribe() } }
        }
    }

    // ── Admin endpoints (Express) ─────────────────────────────────────────

    /**
     * Filterable list for the admin tab. Pass null filters to get
     * everything (capped at 200 server-side).
     */
    suspend fun listForAdmin(
        userId: String? = null,
        status: String? = null,
        shippingConsolidationId: String? = null
    ): List<CustomerConsolidationDto> {
        val params = mutableListOf<String>()
        userId?.let { params += "user_id=$it" }
        status?.let { params += "status=$it" }
        shippingConsolidationId?.let { params += "shipping_consolidation_id=$it" }
        val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
        return api.get<CustomerConsolidationListResponse>("/customer-consolidations$query")
            .customerConsolidations
    }

    suspend fun create(req: CreateCustomerConsolidationRequest): String {
        val resp = api.post<CreateCustomerConsolidationResponse, CreateCustomerConsolidationRequest>(
            "/customer-consolidations",
            req
        )
        return resp.id ?: error(resp.message ?: "Customer consolidation creation failed")
    }

    /**
     * PR 5: admin issues a one-off invoice (no parcels). Returns the new
     * customer_consolidation row, which the customer can then pay via
     * the existing target_kind='consolidation' payment flow.
     */
    suspend fun issueStandaloneInvoice(
        userId: String,
        amountKes: Double,
        description: String,
        currency: String? = "KES",
        notes: String? = null
    ): Result<CustomerConsolidationDto> = runCatching {
        val resp = api.post<CustomerConsolidationResponse, IssueStandaloneInvoiceRequest>(
            "/customer-consolidations/standalone-invoice",
            IssueStandaloneInvoiceRequest(
                userId = userId,
                amountKes = amountKes,
                description = description,
                currency = currency,
                notes = notes,
            )
        )
        resp.customerConsolidation ?: error(resp.message ?: "Failed to issue invoice")
    }

    /**
     * Server computes the suggested invoice amount from each child
     * parcel's shipping cost + operator-stamped customs duty.  The
     * admin's "Set invoice" sheet calls this on appear so the amount
     * input is prefilled with a sensible total instead of starting
     * blank.
     */
    suspend fun suggestedInvoice(id: String): SuggestedInvoiceResponse =
        api.get<SuggestedInvoiceResponse>("/customer-consolidations/$id/suggested-invoice")

    suspend fun setInvoice(id: String, amount: Double, currency: String? = null): CustomerConsolidationDto {
        val resp = api.patch<CustomerConsolidationResponse, SetInvoiceRequest>(
            "/customer-consolidations/$id/invoice",
            SetInvoiceRequest(amount = amount, currency = currency)
        )
        return resp.customerConsolidation ?: error(resp.message ?: "Failed to set invoice")
    }

    suspend fun markPaid(id: String): CustomerConsolidationDto {
        val resp = api.post<CustomerConsolidationResponse, Map<String, String>>(
            "/customer-consolidations/$id/mark-paid",
            null
        )
        return resp.customerConsolidation ?: error(resp.message ?: "Failed to mark paid")
    }

    suspend fun attachToShipping(
        shippingConsolidationId: String,
        customerConsolidationIds: List<String>
    ): Result<Int> = runCatching {
        val resp = api.post<GenericAckResponse, AttachCustomerConsolidationsRequest>(
            "/customer-consolidations/attach-to-shipping/$shippingConsolidationId",
            AttachCustomerConsolidationsRequest(customerConsolidationIds = customerConsolidationIds)
        )
        // Server returns { success, attached } via GenericAckResponse fallback —
        // we don't need the count UI-side, just confirmation it didn't throw.
        resp.success.let { customerConsolidationIds.size }
    }
}
