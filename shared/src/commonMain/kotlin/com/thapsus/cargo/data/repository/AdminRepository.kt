package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.AdminFeeDto
import com.thapsus.cargo.data.dto.AdminFeesResponse
import com.thapsus.cargo.data.dto.AdminOrderDetailDto
import com.thapsus.cargo.data.dto.AdminOrderDetailResponse
import com.thapsus.cargo.data.dto.AdminOrderListResponse
import com.thapsus.cargo.data.dto.AdminOrderRow
import com.thapsus.cargo.data.dto.AdminPromotionDto
import com.thapsus.cargo.data.dto.AdminRevenueSummaryResponse
import com.thapsus.cargo.data.dto.AdminPromotionsResponse
import com.thapsus.cargo.data.dto.AdminStatsFullResponse
import com.thapsus.cargo.data.dto.AdminStatsResponse
import com.thapsus.cargo.data.dto.AdminUserDetailResponse
import com.thapsus.cargo.data.dto.AdminUserDto
import com.thapsus.cargo.data.dto.AdminUserListResponse
import com.thapsus.cargo.data.dto.AdminUserSearchResponse
import com.thapsus.cargo.data.dto.AmlFlagDto
import com.thapsus.cargo.data.dto.AmlFlagListResponse
import com.thapsus.cargo.data.dto.BulkUpdateOrdersRequest
import com.thapsus.cargo.data.dto.CancelOrderRequest
import com.thapsus.cargo.data.dto.CreatePromotionRequest
import com.thapsus.cargo.data.dto.EditOrderRequest
import com.thapsus.cargo.data.dto.EmailConfigResponse
import com.thapsus.cargo.data.dto.ProvisionUserResponse
import com.thapsus.cargo.data.dto.ResendWelcomeResponse
import com.thapsus.cargo.data.dto.ErrorLogRow
import com.thapsus.cargo.data.dto.ErrorLogStatsResponse
import com.thapsus.cargo.data.dto.ErrorLogsResponse
import com.thapsus.cargo.data.dto.ExchangeRateDto
import com.thapsus.cargo.data.dto.ExchangeRatesResponse
import com.thapsus.cargo.data.dto.GenericAckResponse
import com.thapsus.cargo.data.dto.ProvisionUserRequest
import com.thapsus.cargo.data.dto.RequestPaymentRequest
import com.thapsus.cargo.data.dto.TestEmailRequest
import com.thapsus.cargo.data.dto.UpdateAmlFlagRequest
import com.thapsus.cargo.data.dto.UpdateExchangeRatesRequest
import com.thapsus.cargo.data.dto.UpdateFeeRequest
import com.thapsus.cargo.data.dto.UpdateUserRequest
import com.thapsus.cargo.data.dto.UserEmailsResponse
import com.thapsus.cargo.data.remote.Tables
import com.thapsus.cargo.data.remote.ThapsusApiClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import com.thapsus.cargo.util.loggingExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

class AdminRepository(
    private val api: ThapsusApiClient,
    private val supabase: SupabaseClient
) {
    private val realtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + loggingExceptionHandler)
    private val realtimeJson = Json { ignoreUnknownKeys = true }

    // ----- Users -----
    suspend fun listUsers(page: Int = 1, limit: Int = 20, role: String? = null): Result<List<AdminUserDto>> = runCatching {
        val roleQ = role?.let { "&role=$it" } ?: ""
        api.get<AdminUserListResponse>("/admin/users?page=$page&limit=$limit$roleQ").users
    }

    /**
     * SKIE-friendly variant — returns the list directly so Swift gets
     * `[AdminUserDto]` instead of an `Any?` Result wrapper. Used by the iOS
     * "Assign clearing agent" picker on ConsolidationDetailView.
     */
    suspend fun listUsersByRole(role: String, limit: Int = 100): List<AdminUserDto> = try {
        api.get<AdminUserListResponse>("/admin/users?page=1&limit=$limit&role=$role").users
    } catch (_: Throwable) {
        emptyList()
    }

    suspend fun searchUsers(query: String): Result<List<AdminUserDto>> = runCatching {
        api.get<AdminUserSearchResponse>("/admin/users/search?q=$query").customers
    }

    suspend fun userDetail(id: String): Result<AdminUserDto> = runCatching {
        api.get<AdminUserDetailResponse>("/admin/users/$id").user
            ?: error("User detail: missing user")
    }

    suspend fun provisionUser(name: String, email: String, phone: String?, role: String): Result<ProvisionUserResponse> = runCatching {
        api.post<ProvisionUserResponse, ProvisionUserRequest>(
            "/admin/users/create",
            ProvisionUserRequest(name = name, email = email, phone = phone, role = role)
        )
    }

    suspend fun resendWelcome(userId: String): Result<ResendWelcomeResponse> = runCatching {
        api.post<ResendWelcomeResponse, String>("/admin/users/$userId/resend-welcome", null)
    }

    suspend fun emailConfig(): Result<EmailConfigResponse> = runCatching {
        api.get<EmailConfigResponse>("/admin/email-config")
    }

    /** Hard-deletes the user and all dependent rows. Server enforces "cannot delete yourself". */
    suspend fun deleteUser(id: String): Result<Unit> = runCatching {
        api.delete<GenericAckResponse>("/admin/users/$id")
    }

    /** Soft-toggle account activation via PUT. Server logs reactivate_user / deactivate_user. */
    suspend fun setUserActive(id: String, isActive: Boolean): Result<Unit> = runCatching {
        api.put<GenericAckResponse, UpdateUserRequest>(
            "/admin/users/$id",
            UpdateUserRequest(isActive = isActive)
        )
    }

    /** Update editable user fields. role must be one of customer/admin/operator/clearing_agent/rider. */
    suspend fun updateUser(
        id: String,
        role: String? = null,
        isActive: Boolean? = null,
        deliveryAddress: String? = null,
        adminNotes: String? = null
    ): Result<Unit> = runCatching {
        api.put<GenericAckResponse, UpdateUserRequest>(
            "/admin/users/$id",
            UpdateUserRequest(role = role, isActive = isActive, deliveryAddress = deliveryAddress, adminNotes = adminNotes)
        )
    }

    /**
     * Triggers `POST /api/admin/users/:id/reset-password`. The server picks
     * the user's email, mints a one-time token, and sends a reset email —
     * the admin does NOT supply a new password directly. The endpoint
     * silently ignores any body, so we send an empty request.
     *
     * Audit §3.5.4: previously the iOS UI prompted the admin for a new
     * password and shipped it as `new_password`, which the server discarded.
     */
    suspend fun sendUserPasswordResetEmail(id: String): Result<Unit> = runCatching {
        api.post<GenericAckResponse, Unit>(
            "/admin/users/$id/reset-password",
            null
        )
        Unit
    }

    suspend fun userEmails(id: String): Result<UserEmailsResponse> = runCatching {
        api.get<UserEmailsResponse>("/admin/users/$id/emails")
    }

    // ----- Stats -----
    /**
     * Returns the rich nested response from /admin/stats. Use this directly
     * for KPI screens that render market/status breakdowns and the revenue
     * trend.
     */
    suspend fun statsFull(): Result<AdminStatsFullResponse> = runCatching {
        api.get<AdminStatsFullResponse>("/admin/stats")
    }

    /**
     * Backwards-compatible flat shape the existing AdminDashboardView consumes.
     * Internally calls [statsFull] and projects the relevant fields so the
     * dashboard tiles populate with real numbers (the previous flat-only
     * deserialiser silently returned zeros because the wire shape is nested).
     */
    suspend fun stats(): Result<AdminStatsResponse> = runCatching {
        val full = api.get<AdminStatsFullResponse>("/admin/stats")
        AdminStatsResponse(
            success = full.success,
            totalUsers = full.stats.users.total,
            totalOrders = full.stats.orders.totalOrders,
            activeOrders = full.stats.orders.activeOrders,
            deliveredOrders = full.stats.orders.delivered,
            revenueKes = full.stats.revenue.totalRevenue,
            pendingPayments = 0
        )
    }

    /**
     * GET /api/admin/revenue-summary — Thapsus margin only (10% BFM + paid
     * customer invoices). Independent of the legacy /admin/stats revenue
     * block, which read off the now-empty `transactions` table.
     */
    suspend fun revenueSummary(): Result<AdminRevenueSummaryResponse> = runCatching {
        api.get<AdminRevenueSummaryResponse>("/admin/revenue-summary")
    }

    // ----- Orders -----
    /**
     * `GET /api/admin/orders` with the same filter set the React admin tab
     * uses (status, market, startDate, endDate). Returns the full response so
     * the caller has access to `pagination.total` for "load more" UX.
     */
    suspend fun listOrders(
        page: Int = 1,
        limit: Int = 20,
        status: String? = null,
        market: String? = null,
        startDate: String? = null,
        endDate: String? = null
    ): Result<AdminOrderListResponse> = runCatching {
        val q = buildList {
            add("page=$page")
            add("limit=$limit")
            if (!status.isNullOrBlank())     add("status=$status")
            if (!market.isNullOrBlank())     add("market=$market")
            if (!startDate.isNullOrBlank())  add("startDate=$startDate")
            if (!endDate.isNullOrBlank())    add("endDate=$endDate")
        }.joinToString("&")
        api.get<AdminOrderListResponse>("/admin/orders?$q")
    }

    /** `GET /api/orders/:id` — admin bypass allows access to any order. */
    suspend fun orderDetail(id: String): Result<AdminOrderDetailDto> = runCatching {
        api.get<AdminOrderDetailResponse>("/orders/$id").order
            ?: error("Order detail: missing payload")
    }

    suspend fun createOrderForClient(req: com.thapsus.cargo.data.dto.CreateOrderForClientRequest):
        Result<Unit> = runCatching {
        api.post<com.thapsus.cargo.data.dto.CreateOrderForClientResponse, com.thapsus.cargo.data.dto.CreateOrderForClientRequest>(
            "/admin/orders/create-for-client", req
        )
        Unit
    }

    suspend fun bulkUpdateOrders(ids: List<String>, status: String): Result<Unit> = runCatching {
        api.put<GenericAckResponse, BulkUpdateOrdersRequest>(
            "/admin/orders/bulk-update",
            BulkUpdateOrdersRequest(orderIds = ids, status = status)
        )
    }

    suspend fun editOrder(id: String, edits: EditOrderRequest): Result<Unit> = runCatching {
        api.put<GenericAckResponse, EditOrderRequest>("/admin/orders/$id/edit", edits)
    }

    suspend fun cancelOrder(id: String, reason: String?): Result<Unit> = runCatching {
        api.post<GenericAckResponse, CancelOrderRequest>(
            "/admin/orders/$id/cancel",
            CancelOrderRequest(reason = reason)
        )
    }

    suspend fun requestPayment(id: String, amount: Double, notes: String?): Result<Unit> = runCatching {
        api.post<GenericAckResponse, RequestPaymentRequest>(
            "/admin/orders/$id/request-payment",
            RequestPaymentRequest(amount = amount, notes = notes)
        )
    }

    suspend fun sendReminder(id: String, amount: Double, notes: String?): Result<Unit> = runCatching {
        api.post<GenericAckResponse, RequestPaymentRequest>(
            "/admin/orders/$id/send-reminder",
            RequestPaymentRequest(amount = amount, notes = notes)
        )
    }

    // ----- Pending payments — REMOVED in PR B (server-side flow moved to
    // PaymentsRepository / /api/admin/payments/* per migration 028).
    // The admin pending-payment queue now reads from the new `payments`
    // table via PaymentsRepository.pendingMpesaQueue() / approve / reject. -----

    // ----- Test email -----
    suspend fun testEmail(email: String): Result<Unit> = runCatching {
        api.post<GenericAckResponse, TestEmailRequest>(
            "/admin/test-email",
            TestEmailRequest(email = email)
        )
    }

    // ----- Error logs -----
    suspend fun errorLogs(page: Int = 1, level: String? = null, source: String? = null, search: String? = null): Result<List<ErrorLogRow>> = runCatching {
        val params = buildList {
            add("page=$page")
            if (level != null) add("level=$level")
            if (source != null) add("source=$source")
            if (search != null) add("search=$search")
        }.joinToString("&")
        api.get<ErrorLogsResponse>("/admin/error-logs?$params").logs
    }

    suspend fun errorLogStats(): Result<ErrorLogStatsResponse> = runCatching {
        api.get<ErrorLogStatsResponse>("/admin/error-logs/stats")
    }

    suspend fun clearErrorLogs(): Result<Unit> = runCatching {
        api.delete<GenericAckResponse>("/admin/error-logs")
    }

    /**
     * Admin audit-log feed (`GET /api/admin/logs`). Records who did what
     * (provision a user, force a password reset, edit pricing). Paginated
     * server-side; `page` is 1-indexed.
     */
    suspend fun adminLogs(page: Int = 1, limit: Int = 25): com.thapsus.cargo.data.dto.AdminLogsResponse =
        api.get("/admin/logs?page=$page&limit=$limit")

    /**
     * Admin revenue snapshot (`GET /api/admin/revenue`). Returns line-item
     * rows aggregated by `(date, payment_method, type)` plus a per-method
     * summary. Filter both with optional `startDate` / `endDate` (yyyy-MM-dd).
     */
    suspend fun revenue(
        startDate: String? = null,
        endDate: String? = null
    ): com.thapsus.cargo.data.dto.AdminRevenueResponse {
        val q = buildList {
            if (startDate != null) add("startDate=$startDate")
            if (endDate != null) add("endDate=$endDate")
        }.joinToString("&")
        val path = if (q.isEmpty()) "/admin/revenue" else "/admin/revenue?$q"
        return api.get(path)
    }

    // ----- Exchange rates -----
    suspend fun rates(): Result<List<ExchangeRateDto>> = runCatching {
        api.get<ExchangeRatesResponse>("/admin/exchange-rates").rates
    }

    /**
     * Update one or more exchange rates. The server endpoint takes a `rates`
     * object keyed by canonical pair, so a single-pair update sends a 1-key map.
     */
    suspend fun updateRate(currencyPair: String, rate: Double): Result<Unit> = runCatching {
        api.put<GenericAckResponse, UpdateExchangeRatesRequest>(
            "/admin/exchange-rates",
            UpdateExchangeRatesRequest(rates = mapOf(currencyPair to rate))
        )
    }

    /** Bulk-update multiple pairs in one round trip — Phase 4 Ops Settings parity. */
    suspend fun updateRates(rates: Map<String, Double>): Result<Unit> = runCatching {
        api.put<GenericAckResponse, UpdateExchangeRatesRequest>(
            "/admin/exchange-rates",
            UpdateExchangeRatesRequest(rates = rates)
        )
    }

    // ----- AML flags -----
    suspend fun amlFlags(status: String = "open"): Result<List<AmlFlagDto>> = runCatching {
        api.get<AmlFlagListResponse>("/admin/aml-flags?status=$status").flags
    }

    suspend fun resolveAmlFlag(id: String, status: String, notes: String? = null): Result<Unit> = runCatching {
        api.put<GenericAckResponse, UpdateAmlFlagRequest>(
            "/admin/aml-flags/$id",
            UpdateAmlFlagRequest(status = status, notes = notes)
        )
    }

    /**
     * Live-updating AML queue. Bootstraps from the Express endpoint, then
     * applies INSERT/UPDATE events from `aml_flags` over Supabase Realtime
     * (admin-only via the `is_thapsus_admin` RLS policy added by 009).
     * Emits the in-memory list — there's no SQLDelight cache for this; it's
     * an admin queue, online-only.
     */
    fun observeAmlFlags(status: String = "open"): Flow<List<AmlFlagDto>> = callbackFlow {
        var snapshot: List<AmlFlagDto> = emptyList()
        suspend fun emitSnapshot() { trySend(snapshot.sortedByDescending { it.createdAt ?: "" }) }

        realtimeScope.launch {
            amlFlags(status).onSuccess { initial ->
                snapshot = initial
                emitSnapshot()
            }
        }

        val channel = supabase.channel("rt-aml-${Clock.System.now().toEpochMilliseconds()}")
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = Tables.AML_FLAGS
        }
        val rtJob = realtimeScope.launch {
            flow.collect { action ->
                val record = when (action) {
                    is PostgresAction.Insert -> action.record
                    is PostgresAction.Update -> action.record
                    else -> null
                } ?: return@collect
                runCatching {
                    val flag = realtimeJson.decodeFromJsonElement(AmlFlagDto.serializer(), record)
                    snapshot = (snapshot.filter { it.id != flag.id } + flag)
                    emitSnapshot()
                }
            }
        }
        channel.subscribe()

        awaitClose {
            rtJob.cancel()
            realtimeScope.launch {
                runCatching { channel.unsubscribe() }
                runCatching { supabase.realtime.removeChannel(channel) }
            }
        }
    }
}

class PricingTiersRepository(private val api: ThapsusApiClient) {

    suspend fun fees(): Result<List<AdminFeeDto>> = runCatching {
        api.get<AdminFeesResponse>("/pricing-tiers/fees").fees
    }

    suspend fun updateFee(id: String, amount: Double?, isActive: Boolean?, isPercentage: Boolean? = null): Result<Unit> = runCatching {
        api.put<GenericAckResponse, UpdateFeeRequest>(
            "/pricing-tiers/fees/$id",
            UpdateFeeRequest(amount = amount, isActive = isActive, isPercentage = isPercentage)
        )
    }

    suspend fun promotions(): Result<List<AdminPromotionDto>> = runCatching {
        api.get<AdminPromotionsResponse>("/pricing-tiers/promotions").promotions
    }

    suspend fun createPromotion(req: CreatePromotionRequest): Result<Unit> = runCatching {
        api.post<GenericAckResponse, CreatePromotionRequest>("/pricing-tiers/promotions", req)
    }

    /** Create a new weight band. `POST /api/pricing-tiers/tiers`. */
    suspend fun createTier(req: com.thapsus.cargo.data.dto.CreatePricingTierRequest): Result<String?> =
        runCatching {
            api.post<com.thapsus.cargo.data.dto.CreatePricingTierResponse, com.thapsus.cargo.data.dto.CreatePricingTierRequest>(
                "/pricing-tiers/tiers", req
            ).id
        }

    /** Edit a weight band. `PATCH /api/pricing-tiers/tiers/:id`. */
    suspend fun updateTier(id: String, req: com.thapsus.cargo.data.dto.UpdatePricingTierRequest): Result<Unit> =
        runCatching {
            api.patch<GenericAckResponse, com.thapsus.cargo.data.dto.UpdatePricingTierRequest>(
                "/pricing-tiers/tiers/$id", req
            )
        }
}
