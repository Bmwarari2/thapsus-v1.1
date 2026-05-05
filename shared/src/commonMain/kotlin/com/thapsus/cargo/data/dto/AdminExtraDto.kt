package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Single user detail response from `GET /api/admin/users/:id`. */
@Serializable
data class AdminUserDetailResponse(
    val success: Boolean = true,
    val user: AdminUserDto? = null
)

@Serializable
data class ProvisionUserRequest(
    val name: String,
    val email: String,
    val phone: String? = null,
    val role: String = "customer"
)

/** PUT /api/admin/users/:id — every field is optional; the server only writes what is supplied. */
@Serializable
data class UpdateUserRequest(
    val role: String? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("delivery_address") val deliveryAddress: String? = null,
    @SerialName("admin_notes") val adminNotes: String? = null
)

@Serializable
data class AdminOrderRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("tracking_number") val trackingNumber: String? = null,
    val retailer: String? = null,
    val market: String? = null,
    val status: String,
    val description: String? = null,
    @SerialName("weight_kg")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val weightKg: Double? = null,
    @SerialName("declared_value")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val declaredValue: Double? = null,
    @SerialName("estimated_cost")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val estimatedCost: Double? = null,
    @SerialName("actual_cost")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val actualCost: Double? = null,
    @SerialName("customs_duty")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val customsDuty: Double? = null,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("customer_email") val customerEmail: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AdminOrderListResponse(
    val success: Boolean = true,
    val orders: List<AdminOrderRow> = emptyList(),
    val pagination: AdminPagination? = null
)

/**
 * `GET /api/orders/:id` — admin bypass returns the row plus attached packages
 * and the live `cost_breakdown` the server recomputes from current pricing.
 * Numeric fields use `NullableLooseDouble` because the legacy webapp passes
 * them through pg-node (string-encoded) and emits null for unset values.
 */
@Serializable
data class AdminOrderDetailResponse(
    val success: Boolean = true,
    val order: AdminOrderDetailDto? = null,
    val message: String? = null
)

@Serializable
data class AdminOrderDetailDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("tracking_number") val trackingNumber: String? = null,
    val retailer: String? = null,
    val market: String? = null,
    val status: String,
    val description: String? = null,
    @SerialName("weight_kg")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val weightKg: Double? = null,
    @SerialName("dimensions_json")
    @Serializable(with = DimensionsObjectOrStringSerializer::class)
    val dimensionsJson: OrderDimensionsDto? = null,
    @SerialName("shipping_speed") val shippingSpeed: String? = null,
    val insurance: Boolean? = null,
    @SerialName("declared_value")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val declaredValue: Double? = null,
    @SerialName("estimated_cost")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val estimatedCost: Double? = null,
    @SerialName("actual_cost")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val actualCost: Double? = null,
    @SerialName("customs_duty")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val customsDuty: Double? = null,
    @SerialName("electronics_item") val electronicsItem: String? = null,
    @SerialName("order_notes") val orderNotes: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("packages") val packages: List<AdminOrderPackageDto> = emptyList(),
    @SerialName("cost_breakdown") val costBreakdown: OrderCostBreakdownDto? = null
)

@Serializable
data class AdminOrderPackageDto(
    val id: String,
    val description: String? = null,
    @SerialName("weight_kg")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val weightKg: Double? = null,
    val status: String? = null,
    @SerialName("warehouse_location") val warehouseLocation: String? = null,
    @SerialName("received_at") val receivedAt: String? = null
)

/**
 * Server cost breakdown is `{ total, breakdown: { base_shipping: {amount,...},
 * insurance: {...}, electronics_handling: {...}, handling_fee: {...},
 * customs_estimate: {...} } }`. Each entry has a `label` + `amount` (and
 * sometimes notes); we only need amount on iOS.
 */
@Serializable
data class OrderCostBreakdownDto(
    @Serializable(with = LooseDoubleSerializer::class) val total: Double = 0.0,
    val breakdown: OrderCostBreakdownBlock = OrderCostBreakdownBlock()
)

@Serializable
data class OrderCostBreakdownBlock(
    @SerialName("base_shipping") val baseShipping: OrderCostLine? = null,
    @SerialName("electronics_handling") val electronicsHandling: OrderCostLine? = null,
    @SerialName("handling_fee") val handlingFee: OrderCostLine? = null,
    @SerialName("insurance") val insurance: OrderCostLine? = null,
    @SerialName("customs_estimate") val customsEstimate: OrderCostLine? = null
)

@Serializable
data class OrderCostLine(
    @Serializable(with = LooseDoubleSerializer::class) val amount: Double = 0.0,
    val label: String? = null
)

@Serializable
data class BulkUpdateOrdersRequest(
    @SerialName("order_ids") val orderIds: List<String>,
    val status: String
)

@Serializable
data class EditOrderRequest(
    @SerialName("weight_kg") val weightKg: Double? = null,
    val length: Double? = null,
    val width: Double? = null,
    val height: Double? = null,
    @SerialName("actual_cost") val actualCost: Double? = null,
    @SerialName("customs_duty") val customsDuty: Double? = null,
    val status: String? = null,
    val description: String? = null,
    @SerialName("electronics_item") val electronicsItem: String? = null,
    @SerialName("order_notes") val orderNotes: String? = null
)

@Serializable
data class CancelOrderRequest(val reason: String? = null)

/**
 * POST /api/admin/orders/create-for-client. The server accepts either
 * customer_email OR customer_name; both are nullable here so iOS can pick.
 */
@Serializable
data class CreateOrderForClientRequest(
    @SerialName("customer_email") val customerEmail: String? = null,
    @SerialName("customer_name") val customerName: String? = null,
    val retailer: String,
    val market: String,
    val description: String,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("shipping_speed") val shippingSpeed: String = "economy",
    val insurance: Boolean = false,
    @SerialName("declared_value") val declaredValue: Double = 0.0,
    @SerialName("electronics_item") val electronicsItem: String? = null,
    @SerialName("hs_tier") val hsTier: String? = null
)

/**
 * Server returns `{ success, message, order: { id, tracking_number, customer: { id, name, email }, … } }`.
 * The order payload uses a nested `customer` object instead of a top-level `user_id`,
 * so it does not match `AdminOrderRow`. The view model only refreshes the list on
 * success, so the order detail is intentionally not parsed here.
 */
@Serializable
data class CreateOrderForClientResponse(
    val success: Boolean = true,
    val message: String? = null
)

@Serializable
data class RequestPaymentRequest(
    val amount: Double,
    val notes: String? = null
)

// PendingTransactionRow / PendingTransactionsResponse / Approve|RejectTransactionRequest /
// TransactionProofResponse REMOVED in PR D.
// The legacy /admin/transactions/* endpoints they targeted were removed in PR A
// when the wallet was dropped (migration 028). The new admin payments review
// queue lives in PaymentDto + PaymentsRepository.pendingMpesaQueue / approve / reject.

@Serializable
data class EmailLogRow(
    val id: String,
    @SerialName("email_to") val emailTo: String,
    @SerialName("email_type") val emailType: String,
    val subject: String,
    val status: String,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class UserEmailsResponse(
    val success: Boolean = true,
    @SerialName("email_logs") val emails: List<EmailLogRow> = emptyList()
)

@Serializable
data class TestEmailRequest(val email: String)

/** Returned by GET /api/admin/email-config so the admin UI can show whether
 *  Gmail OAuth is wired up without exposing the actual secrets. */
@Serializable
data class EmailConfigResponse(
    val success: Boolean = true,
    val configured: Boolean = false,
    @SerialName("has_client_id") val hasClientId: Boolean = false,
    @SerialName("has_client_secret") val hasClientSecret: Boolean = false,
    @SerialName("has_refresh_token") val hasRefreshToken: Boolean = false,
    @SerialName("client_id_length") val clientIdLength: Int = 0,
    @SerialName("client_secret_length") val clientSecretLength: Int = 0,
    @SerialName("refresh_token_length") val refreshTokenLength: Int = 0,
    @SerialName("client_id_preview") val clientIdPreview: String? = null,
    @SerialName("sender_email") val senderEmail: String? = null,
    @SerialName("support_email") val supportEmail: String? = null,
    @SerialName("process_uptime_s") val processUptimeSeconds: Long = 0
)

@Serializable
data class ProvisionUserResponse(
    val success: Boolean = true,
    val message: String? = null,
    @SerialName("email_status") val emailStatus: String? = null,
    @SerialName("email_error") val emailError: String? = null,
    val user: AdminUserDto? = null
)

@Serializable
data class ResendWelcomeResponse(
    val success: Boolean = true,
    @SerialName("email_status") val emailStatus: String? = null,
    val message: String? = null
)

@Serializable
data class ErrorLogRow(
    val id: String,
    val level: String,
    val source: String? = null,
    val message: String,
    val stack: String? = null,
    val method: String? = null,
    val path: String? = null,
    @SerialName("status_code") val statusCode: Int? = null,
    @SerialName("user_id") val userId: String? = null,
    val meta: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ErrorLogsResponse(
    val success: Boolean = true,
    @SerialName("error_logs") val logs: List<ErrorLogRow> = emptyList(),
    val pagination: AdminPagination? = null
)

@Serializable
data class ErrorLogStatsBody(
    @SerialName("last_24h") @Serializable(with = LooseIntSerializer::class) val last24h: Int = 0,
    @SerialName("last_7d")  @Serializable(with = LooseIntSerializer::class) val last7d: Int = 0,
    @SerialName("fatal_24h") @Serializable(with = LooseIntSerializer::class) val fatal24h: Int = 0,
    @Serializable(with = LooseIntSerializer::class) val total: Int = 0
)

@Serializable
data class ErrorLogStatsResponse(
    val success: Boolean = true,
    val stats: ErrorLogStatsBody = ErrorLogStatsBody()
)

/**
 * Admin audit-log row from `GET /api/admin/logs`. Each row records a
 * privileged action (`admin_id` joined to `users` for `admin_email` /
 * `admin_name`). `details` is JSONB on the wire, but iOS treats it as
 * an opaque string for now since the shape varies per action type.
 */
@Serializable
data class AdminLogRow(
    val id: String,
    val action: String,
    val details: String? = null,
    @SerialName("admin_email") val adminEmail: String? = null,
    @SerialName("admin_name") val adminName: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AdminLogsResponse(
    val success: Boolean = true,
    val logs: List<AdminLogRow> = emptyList(),
    val pagination: AdminPagination? = null
)

/**
 * Admin revenue row from `GET /api/admin/revenue`. Aggregated server-side
 * by `(date, payment_method, type)`, with `count` (BIGINT) and `total`
 * (NUMERIC) loose-decoded so empty groups don't crash the parser.
 */
@Serializable
data class AdminRevenueRow(
    val date: String,
    @SerialName("payment_method") val paymentMethod: String? = null,
    val type: String? = null,
    @Serializable(with = LooseIntSerializer::class) val count: Int = 0,
    @Serializable(with = LooseDoubleSerializer::class) val total: Double = 0.0
)

@Serializable
data class AdminRevenueSummaryRow(
    @SerialName("payment_method") val paymentMethod: String? = null,
    @Serializable(with = LooseDoubleSerializer::class) val deposits: Double = 0.0,
    @Serializable(with = LooseDoubleSerializer::class) val payments: Double = 0.0,
    @Serializable(with = LooseDoubleSerializer::class) val total: Double = 0.0
)

@Serializable
data class AdminRevenueResponse(
    val success: Boolean = true,
    val revenue: List<AdminRevenueRow> = emptyList(),
    val summary: List<AdminRevenueSummaryRow> = emptyList()
)
