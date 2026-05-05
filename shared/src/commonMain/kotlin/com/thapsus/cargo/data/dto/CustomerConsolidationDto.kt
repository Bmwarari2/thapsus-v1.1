package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Per-user grouping of received parcels with a single shared invoice.
 * Mirrors the `customer_consolidations` table (migration 025). The
 * shipping-side relationship is one hop away via
 * `shippingConsolidationId` once the row reaches status='shipped'.
 *
 * Status lifecycle (server enforces forward-only on PATCH):
 *   pending  → no invoice issued yet
 *   invoiced → admin has set invoiceAmount; customer email fired
 *   paid     → invoice settled; ready for shipping batch
 *   shipped  → attached to a shipping consolidation that has departed
 */
@Serializable
data class CustomerConsolidationDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("shipping_consolidation_id") val shippingConsolidationId: String? = null,
    val status: String,
    @SerialName("invoice_amount") val invoiceAmount: Double? = null,
    @SerialName("invoice_currency") val invoiceCurrency: String = "KES",
    @SerialName("invoice_status") val invoiceStatus: String? = null,
    @SerialName("invoice_paid_at") val invoicePaidAt: String? = null,
    val notes: String? = null,
    /** PR 5: free-text line item visible to the customer (standalone invoices). */
    val description: String? = null,
    /** PR 5: true for admin-issued one-off invoices with no parcels attached. */
    @SerialName("is_standalone") val isStandalone: Boolean = false,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    /** Server convenience join — only populated by GET /admin list endpoint. */
    @SerialName("user_email") val userEmail: String? = null,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("parcel_count") val parcelCount: Int? = null
)

/** Body for POST /api/customer-consolidations (admin). */
@Serializable
data class CreateCustomerConsolidationRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("parcel_ids") val parcelIds: List<String>,
    val notes: String? = null
)

@Serializable
data class CreateCustomerConsolidationResponse(
    val success: Boolean = true,
    @SerialName("customer_consolidation_id") val id: String? = null,
    val message: String? = null
)

/** Body for PATCH /api/customer-consolidations/:id/invoice (admin). */
@Serializable
data class SetInvoiceRequest(
    val amount: Double,
    val currency: String? = null
)

/**
 * PR 5: body for POST /api/customer-consolidations/standalone-invoice (admin).
 * Creates a one-off invoice with no parcels attached; customer pays via
 * the regular target_kind='consolidation' payment flow.
 */
@Serializable
data class IssueStandaloneInvoiceRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("amount_kes") val amountKes: Double,
    val description: String,
    val currency: String? = null,
    val notes: String? = null
)

@Serializable
data class CustomerConsolidationResponse(
    val success: Boolean = true,
    @SerialName("customer_consolidation") val customerConsolidation: CustomerConsolidationDto? = null,
    val message: String? = null
)

@Serializable
data class CustomerConsolidationListResponse(
    val success: Boolean = true,
    @SerialName("customer_consolidations") val customerConsolidations: List<CustomerConsolidationDto> = emptyList()
)

/** Body for POST /api/customer-consolidations/attach-to-shipping/:id (admin). */
@Serializable
data class AttachCustomerConsolidationsRequest(
    @SerialName("customer_consolidation_ids") val customerConsolidationIds: List<String>
)

/**
 * Response shape for GET /api/customer-consolidations/:id/suggested-invoice.
 * Server sums per-parcel shipping (recomputed via calculateShippingCost
 * from the operator-stamped weight + dims) plus the operator-set
 * customs_duty across every parcel in the customer-consolidation, then
 * converts to KES using the live GBP_KES rate. The admin "Set invoice"
 * sheet uses `total` to prefill the amount input.
 */
@Serializable
data class SuggestedInvoiceResponse(
    val success: Boolean = true,
    val currency: String = "KES",
    @SerialName("gbp_to_kes_rate") val gbpToKesRate: Double? = null,
    val total: Double = 0.0,
    val breakdown: List<SuggestedInvoiceLine> = emptyList()
)

@Serializable
data class SuggestedInvoiceLine(
    @SerialName("parcel_id") val parcelId: String,
    @SerialName("tracking_number") val trackingNumber: String? = null,
    @SerialName("chargeable_kg") val chargeableKg: Double = 0.0,
    @SerialName("shipping_kes") val shippingKes: Double = 0.0,
    @SerialName("customs_duty_kes") val customsDutyKes: Double = 0.0,
    @SerialName("line_total_kes") val lineTotalKes: Double = 0.0
)
