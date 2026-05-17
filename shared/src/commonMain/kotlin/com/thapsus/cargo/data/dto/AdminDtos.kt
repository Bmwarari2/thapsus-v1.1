package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AdminUserDto(
    val id: String,
    val email: String,
    val name: String,
    val phone: String? = null,
    val role: String = "customer",
    @SerialName("warehouse_id") val warehouseId: String? = null,
    @SerialName("language_pref") val languagePref: String? = null,
    @SerialName("referral_code") val referralCode: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("kyc_status") val kycStatus: String? = null,
    /**
     * Customer's Kenya delivery address (free-form). Served by both
     * `/api/admin/users` and `/api/admin/users/:id`; surfaced on the
     * admin user-detail screens so admins / operators can read the
     * recipient's location without leaving the user profile.
     */
    @SerialName("delivery_address") val deliveryAddress: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AdminUserListResponse(
    val success: Boolean = true,
    val users: List<AdminUserDto> = emptyList(),
    val pagination: AdminPagination? = null
)

/**
 * Response for `GET /api/admin/users/search` — the server returns the matches
 * under `customers`, not `users`. Reusing `AdminUserListResponse` silently
 * dropped every hit.
 */
@Serializable
data class AdminUserSearchResponse(
    val success: Boolean = true,
    val customers: List<AdminUserDto> = emptyList()
)

@Serializable
data class AdminPagination(
    @Serializable(with = LooseIntSerializer::class) val page: Int = 1,
    @Serializable(with = LooseIntSerializer::class) val limit: Int = 20,
    @Serializable(with = LooseIntSerializer::class) val total: Int = 0,
    @Serializable(with = LooseIntSerializer::class) val totalPages: Int = 0
)

/**
 * Compact summary the iOS Admin Dashboard renders. The webapp `/admin/stats`
 * endpoint actually returns a nested `{ stats: { users, orders, … } }` shape;
 * `AdminRepository.stats()` flattens it into this for the existing dashboard
 * tiles. KPI dashboard uses the richer [AdminStatsFullResponse] directly.
 */
@Serializable
data class AdminStatsResponse(
    val success: Boolean = true,
    @SerialName("total_users")
    @Serializable(with = LooseIntSerializer::class) val totalUsers: Int = 0,
    @SerialName("total_orders")
    @Serializable(with = LooseIntSerializer::class) val totalOrders: Int = 0,
    @SerialName("active_orders")
    @Serializable(with = LooseIntSerializer::class) val activeOrders: Int = 0,
    @SerialName("delivered_orders")
    @Serializable(with = LooseIntSerializer::class) val deliveredOrders: Int = 0,
    @SerialName("revenue_kes")
    @Serializable(with = LooseDoubleSerializer::class) val revenueKes: Double = 0.0,
    @SerialName("paid_via_card_kes")
    @Serializable(with = LooseDoubleSerializer::class) val paidViaCardKes: Double = 0.0,
    @SerialName("paid_via_mpesa_kes")
    @Serializable(with = LooseDoubleSerializer::class) val paidViaMpesaKes: Double = 0.0,
    @SerialName("pending_payments")
    @Serializable(with = LooseIntSerializer::class) val pendingPayments: Int = 0
)

/** Full response shape returned by GET /api/admin/stats — mirrors routes/admin.js:581. */
@Serializable
data class AdminStatsFullResponse(
    val success: Boolean = true,
    val stats: AdminStatsBlock = AdminStatsBlock()
)

@Serializable
data class AdminStatsBlock(
    val users: AdminUserStats = AdminUserStats(),
    val orders: AdminOrderStats = AdminOrderStats(),
    val markets: List<AdminMarketSlice> = emptyList(),
    @SerialName("order_statuses") val orderStatuses: List<AdminStatusSlice> = emptyList(),
    val revenue: AdminRevenueStats = AdminRevenueStats(),
    val referrals: AdminReferralStats = AdminReferralStats(),
    @SerialName("daily_orders") val dailyOrders: List<AdminDailyOrderPoint> = emptyList()
)

/**
 * Every numeric field below is wrapped in a Loose…Serializer because the
 * webapp serialises Postgres `COUNT(*)` / `SUM(…)` as STRINGS (the pg-node
 * BIGINT/NUMERIC default) and as `null` on empty tables. A strict `Int` /
 * `Double` deserialiser would fail with:
 *
 *   "Unexpected JSON token at offset N: Unexpected symbol 'n' in numeric
 *    literal at path: $.stats.orders.delivered"
 *
 * This was the root cause of the "Couldn't load" banner on the iOS Admin
 * Console (Image 5).
 */
@Serializable
data class AdminUserStats(
    @Serializable(with = LooseIntSerializer::class) val total: Int = 0,
    @Serializable(with = LooseIntSerializer::class) val customers: Int = 0,
    @Serializable(with = LooseIntSerializer::class) val admins: Int = 0,
    @SerialName("active_users") @Serializable(with = LooseIntSerializer::class) val activeUsers: Int = 0,
    @SerialName("new_today") @Serializable(with = LooseIntSerializer::class) val newToday: Int = 0
)

@Serializable
data class AdminOrderStats(
    @SerialName("total_orders") @Serializable(with = LooseIntSerializer::class) val totalOrders: Int = 0,
    @Serializable(with = LooseIntSerializer::class) val delivered: Int = 0,
    @Serializable(with = LooseIntSerializer::class) val pending: Int = 0,
    @SerialName("in_transit") @Serializable(with = LooseIntSerializer::class) val inTransit: Int = 0,
    @SerialName("avg_estimated_cost") @Serializable(with = LooseDoubleSerializer::class) val avgEstimatedCost: Double = 0.0,
    @SerialName("total_estimated_value") @Serializable(with = LooseDoubleSerializer::class) val totalEstimatedValue: Double = 0.0,
    @SerialName("new_today") @Serializable(with = LooseIntSerializer::class) val newToday: Int = 0,
    @SerialName("active_orders") @Serializable(with = LooseIntSerializer::class) val activeOrders: Int = 0
)

@Serializable
data class AdminMarketSlice(
    val market: String? = null,
    @Serializable(with = LooseIntSerializer::class) val count: Int = 0,
    @Serializable(with = LooseDoubleSerializer::class) val value: Double = 0.0
)

@Serializable
data class AdminStatusSlice(
    val status: String,
    @Serializable(with = LooseIntSerializer::class) val count: Int = 0
)

@Serializable
data class AdminRevenueStats(
    @SerialName("total_transactions") @Serializable(with = LooseIntSerializer::class) val totalTransactions: Int = 0,
    @SerialName("total_revenue") @Serializable(with = LooseDoubleSerializer::class) val totalRevenue: Double = 0.0,
    @Serializable(with = LooseDoubleSerializer::class) val deposits: Double = 0.0,
    @Serializable(with = LooseDoubleSerializer::class) val payments: Double = 0.0,
    // Breakdown of `total_revenue` by channel — added server-side in
    // Swiftcargo-main#209 after the /admin/stats query was extended to
    // union the legacy `transactions` table with the modern `payments`
    // table. Pre-#209 these fields are absent on the wire; the default
    // 0.0 keeps deserialisation working against older deployments.
    @SerialName("paid_via_card")  @Serializable(with = LooseDoubleSerializer::class) val paidViaCard: Double = 0.0,
    @SerialName("paid_via_mpesa") @Serializable(with = LooseDoubleSerializer::class) val paidViaMpesa: Double = 0.0,
    @SerialName("referral_credits_issued") @Serializable(with = LooseDoubleSerializer::class) val referralCreditsIssued: Double = 0.0
)

@Serializable
data class AdminReferralStats(
    @SerialName("total_referrals") @Serializable(with = LooseIntSerializer::class) val totalReferrals: Int = 0,
    @SerialName("completed_referrals") @Serializable(with = LooseIntSerializer::class) val completedReferrals: Int = 0,
    @SerialName("total_rewards_paid") @Serializable(with = LooseDoubleSerializer::class) val totalRewardsPaid: Double = 0.0
)

@Serializable
data class AdminDailyOrderPoint(
    val date: String,
    @Serializable(with = LooseIntSerializer::class) val count: Int = 0,
    @Serializable(with = LooseDoubleSerializer::class) val revenue: Double = 0.0
)

/**
 * Money Thapsus actually earned, post the wallet rip in mig 028.
 *
 * - 10 % of every paid Buy-for-me order (`estimate_gbp * 0.10`).
 * - 100 % of every paid customer-facing consolidation invoice
 *   (`customer_consolidations.invoice_amount` where `invoice_status='paid'`).
 *
 * Mirrors `GET /api/admin/revenue-summary` (routes/admin.js).
 */
@Serializable
data class AdminRevenueSummaryResponse(
    val success: Boolean = true,
    val summary: AdminRevenueSummary = AdminRevenueSummary(),
    @SerialName("by_month") val byMonth: List<AdminRevenueMonthPoint> = emptyList()
)

@Serializable
data class AdminRevenueSummary(
    @SerialName("buy_for_me_commission_gbp")
    @Serializable(with = LooseDoubleSerializer::class) val buyForMeCommissionGbp: Double = 0.0,
    @SerialName("buy_for_me_paid_count")
    @Serializable(with = LooseIntSerializer::class) val buyForMePaidCount: Int = 0,
    @SerialName("invoice_revenue_gbp")
    @Serializable(with = LooseDoubleSerializer::class) val invoiceRevenueGbp: Double = 0.0,
    @SerialName("invoice_paid_count")
    @Serializable(with = LooseIntSerializer::class) val invoicePaidCount: Int = 0,
    @SerialName("total_revenue_gbp")
    @Serializable(with = LooseDoubleSerializer::class) val totalRevenueGbp: Double = 0.0
)

@Serializable
data class AdminRevenueMonthPoint(
    val month: String,
    @SerialName("buy_for_me_gbp")
    @Serializable(with = LooseDoubleSerializer::class) val buyForMeGbp: Double = 0.0,
    @SerialName("invoice_gbp")
    @Serializable(with = LooseDoubleSerializer::class) val invoiceGbp: Double = 0.0,
    @SerialName("total_gbp")
    @Serializable(with = LooseDoubleSerializer::class) val totalGbp: Double = 0.0
)

@Serializable
data class ExchangeRateDto(
    val id: Int? = null,
    @SerialName("currency_pair") val currencyPair: String,
    val rate: Double,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class ExchangeRatesResponse(
    val success: Boolean = true,
    val rates: List<ExchangeRateDto> = emptyList()
)

/**
 * Body for `PUT /api/admin/exchange-rates`. The server expects a `rates`
 * object keyed by canonical pair (`USD_KES`, `GBP_KES`, `EUR_KES`, `CNY_KES`)
 * with positive numeric values. Sending one or many entries are both fine —
 * the server iterates `Object.entries(rates)` and upserts each row.
 */
@Serializable
data class UpdateExchangeRatesRequest(
    val rates: Map<String, Double>
)

/** Legacy alias kept so historical call sites compile during the cut-over. */
typealias UpdateExchangeRateRequest = UpdateExchangeRatesRequest

/**
 * API-shaped fee row from /api/pricing-tiers/fees. Note: distinct from the
 * domain FeeDto in PricingTierDto.kt which models an internal pricing engine
 * representation (amount_minor + kind enum).
 */
@Serializable
data class AdminFeeDto(
    val id: String,
    val code: String,
    val label: String,
    val currency: String = "GBP",
    @Serializable(with = LooseDoubleSerializer::class)
    val amount: Double = 0.0,
    @SerialName("is_percentage") val isPercentage: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class AdminFeesResponse(
    val success: Boolean = true,
    val fees: List<AdminFeeDto> = emptyList()
)

@Serializable
data class UpdateFeeRequest(
    val amount: Double? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("is_percentage") val isPercentage: Boolean? = null
)

/**
 * API-shaped promotion row from /api/pricing-tiers/promotions. Distinct from
 * the domain PromotionDto in PricingTierDto.kt.
 */
@Serializable
data class AdminPromotionDto(
    val id: String,
    val code: String,
    val type: String,
    @Serializable(with = LooseDoubleSerializer::class)
    val value: Double = 0.0,
    @SerialName("valid_from") val validFrom: String? = null,
    @SerialName("valid_to") val validTo: String? = null,
    @SerialName("max_uses") val maxUses: Int? = null,
    @Serializable(with = LooseIntSerializer::class)
    val uses: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
    val description: String? = null
)

@Serializable
data class AdminPromotionsResponse(
    val success: Boolean = true,
    val promotions: List<AdminPromotionDto> = emptyList()
)

@Serializable
data class CreatePromotionRequest(
    val code: String,
    val type: String,
    val value: Double,
    @SerialName("valid_from") val validFrom: String? = null,
    @SerialName("valid_to") val validTo: String? = null,
    @SerialName("max_uses") val maxUses: Int? = null,
    val description: String? = null
)
