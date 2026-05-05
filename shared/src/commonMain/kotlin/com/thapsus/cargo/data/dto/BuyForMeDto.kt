package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BuyForMeOrderDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("retailer_url") val retailerUrl: String,
    @SerialName("item_name") val itemName: String,
    val size: String? = null,
    val qty: Int = 1,
    val notes: String? = null,
    @SerialName("estimate_gbp")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val estimateGbp: Double? = null,
    @SerialName("markup_pct")
    @Serializable(with = LooseDoubleSerializer::class)
    val markupPct: Double = 10.0,
    val status: String = "pending_quote",
    @SerialName("parcel_id") val parcelId: String? = null,
    @SerialName("customer_decision_reason") val customerDecisionReason: String? = null,
    @SerialName("quoted_at") val quotedAt: String? = null,
    @SerialName("decided_at") val decidedAt: String? = null,
    /** Operator queue join only — null on the customer-facing list. */
    val email: String? = null,
    val name: String? = null,
    /** PR 3 (operator queue): tracking number of the auto-pre-registered parcel
     *  spawned when the customer paid. Null until payment lands. */
    @SerialName("parcel_tracking_number") val parcelTrackingNumber: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class CreateBuyForMeRequest(
    /** PR 4: optional id from the retailers picker. When set, the server
     *  resolves base_url; URL field below stays optional (item-specific). */
    @SerialName("retailer_id") val retailerId: String? = null,
    @SerialName("retailer_url") val retailerUrl: String? = null,
    @SerialName("item_name") val itemName: String,
    val size: String? = null,
    val qty: Int = 1,
    val notes: String? = null
)

/**
 * Body for POST /api/buy-for-me/admin-create — admin/operator creates a
 * BFM on behalf of a customer who placed the order off-platform.
 *
 * If `estimateGbp` is non-null + positive, the row starts at status='quoted'
 * and the customer's quote-ready email fires immediately. Otherwise it
 * enters the regular operator queue at status='pending_quote'.
 */
@Serializable
data class AdminCreateBuyForMeRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("retailer_id") val retailerId: String? = null,
    @SerialName("retailer_url") val retailerUrl: String? = null,
    @SerialName("item_name") val itemName: String,
    val size: String? = null,
    val qty: Int = 1,
    val notes: String? = null,
    @SerialName("estimate_gbp") val estimateGbp: Double? = null,
    @SerialName("markup_pct") val markupPct: Double? = null
)

@Serializable
data class AdminCreateBuyForMeResponse(
    val success: Boolean = true,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("pre_quoted") val preQuoted: Boolean = false,
    val message: String? = null
)

@Serializable
data class CreateBuyForMeResponse(
    val success: Boolean = true,
    @SerialName("order_id") val orderId: String? = null,
    val message: String? = null
)

@Serializable
data class BuyForMeListResponse(
    val success: Boolean = true,
    val orders: List<BuyForMeOrderDto> = emptyList()
)

@Serializable
data class BuyForMeDetailResponse(
    val success: Boolean = true,
    val order: BuyForMeOrderDto? = null
)

/** Operator → server: set the quote that the customer reviews in the app. */
@Serializable
data class QuoteBuyForMeRequest(
    @SerialName("estimate_gbp") val estimateGbp: Double,
    @SerialName("markup_pct") val markupPct: Double = 10.0,
    val notes: String? = null
)

/** Customer → server: accept a quote (optionally with a free-text note). */
@Serializable
data class AcceptBuyForMeRequest(
    val reason: String? = null
)

/** Customer → server: reject a quote with a (required) reason. */
@Serializable
data class RejectBuyForMeRequest(
    val reason: String
)

@Serializable
data class BuyForMeAckResponse(
    val success: Boolean = true,
    val message: String? = null
)

@Serializable
data class BuyForMePayResponse(
    val success: Boolean = true,
    @SerialName("paid_kes")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val paidKes: Double? = null,
    @SerialName("paid_gbp")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val paidGbp: Double? = null,
    @SerialName("new_balance_kes")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val newBalanceKes: Double? = null,
    val message: String? = null
)
