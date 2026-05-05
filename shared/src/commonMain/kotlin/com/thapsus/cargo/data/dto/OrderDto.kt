package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the Supabase `orders` table — the customer-facing order entity used by
 * the React webapp. (The iOS `packages` table is the operations-side hub-intake
 * record; the two are linked by tracking number in v1.)
 */
@Serializable
data class OrderDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("tracking_number") val trackingNumber: String? = null,
    @SerialName("retailer") val retailer: String? = null,
    @SerialName("market") val market: String? = null,
    @SerialName("status") val status: OrderStatus = OrderStatus.PENDING,
    @SerialName("description") val description: String? = null,
    @SerialName("weight_kg")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val weightKg: Double? = null,
    @SerialName("dimensions_json")
    @Serializable(with = DimensionsObjectOrStringSerializer::class)
    val dimensionsJson: OrderDimensionsDto? = null,
    @SerialName("shipping_speed") val shippingSpeed: String? = null,
    @SerialName("insurance") val insurance: Boolean = false,
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
    @Serializable(with = LooseDoubleSerializer::class)
    val customsDuty: Double = 0.0,
    @SerialName("electronics_item") val electronicsItem: String? = null,
    @SerialName("hs_tier") val hsTier: String = "general",
    @SerialName("consolidation_id") val consolidationId: String? = null,
    @SerialName("volumetric_kg")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val volumetricKg: Double? = null,
    @SerialName("chargeable_kg")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val chargeableKg: Double? = null,
    @SerialName("hold_reason") val holdReason: String? = null,
    @SerialName("photographed_at") val photographedAt: String? = null,
    @SerialName("insurance_tier") val insuranceTier: String? = null,
    @SerialName("insurance_premium_gbp")
    @Serializable(with = LooseDoubleSerializer::class)
    val insurancePremiumGbp: Double = 0.0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
enum class OrderStatus {
    @SerialName("pending") PENDING,
    @SerialName("received_at_warehouse") RECEIVED_AT_WAREHOUSE,
    @SerialName("consolidating") CONSOLIDATING,
    @SerialName("in_transit") IN_TRANSIT,
    @SerialName("customs") CUSTOMS,
    @SerialName("out_for_delivery") OUT_FOR_DELIVERY,
    @SerialName("delivered") DELIVERED,
    @SerialName("cancelled") CANCELLED
}

/**
 * Body for `POST /api/orders` (customer-side, `routes/orders.js:55`).
 * Server destructures `{retailer, market, description, weight_kg, dimensions,
 * shipping_speed, insurance, declared_value}` and ignores anything else.
 *
 * Weight + dimensions are deliberately optional — they're filled in by the
 * warehouse team at intake. Electronics handling is captured later by an
 * admin order-edit, not here.
 */
@Serializable
data class CreateOrderRequest(
    val retailer: String,
    val description: String,
    @SerialName("market") val market: String = "UK",
    @SerialName("shipping_speed") val shippingSpeed: String = "economy",
    @SerialName("insurance") val insurance: Boolean = false,
    @SerialName("declared_value") val declaredValue: Double = 0.0,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("dimensions") val dimensions: OrderDimensionsDto? = null,
    @SerialName("electronics_item") val electronicsItem: String? = null,
    @SerialName("hs_tier") val hsTier: String? = null
)

/** Public list returned by `GET /api/pricing/hs-tiers`. */
@Serializable
data class HsTierDto(
    val key: String,
    val label: String,
    @SerialName("duty_rate") val dutyRate: Double = 0.0,
    @SerialName("vat_zero_rated") val vatZeroRated: Boolean = false,
    val note: String? = null
)

@Serializable
data class HsTierListResponse(
    val success: Boolean = true,
    val tiers: List<HsTierDto> = emptyList()
)

@Serializable
data class OrderDimensionsDto(
    @SerialName("length_cm") val lengthCm: Double,
    @SerialName("width_cm") val widthCm: Double,
    @SerialName("height_cm") val heightCm: Double
)

@Serializable
data class OrderResponse(
    val success: Boolean = true,
    val message: String? = null,
    val order: OrderDto? = null
)

@Serializable
data class OrderListResponse(
    val success: Boolean = true,
    val orders: List<OrderDto> = emptyList(),
    @SerialName("pagination") val pagination: PaginationDto = PaginationDto()
)
