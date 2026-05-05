package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape for `GET /api/tracking/:trackingNumber` (public — no auth).
 * The server emits a slim subset of the `orders` row plus a list of attached
 * `packages`. Field names mirror routes/tracking.js exactly.
 */
@Serializable
data class TrackingResponse(
    val success: Boolean = true,
    val tracking: TrackingDto? = null,
    val message: String? = null
)

@Serializable
data class TrackingDto(
    @SerialName("id") val id: String,
    @SerialName("tracking_number") val trackingNumber: String,
    @SerialName("retailer") val retailer: String? = null,
    @SerialName("market") val market: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("weight_kg")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val weightKg: Double? = null,
    @SerialName("dimensions_json")
    @Serializable(with = DimensionsObjectOrStringSerializer::class)
    val dimensionsJson: OrderDimensionsDto? = null,
    @SerialName("shipping_speed") val shippingSpeed: String? = null,
    @SerialName("hold_reason") val holdReason: String? = null,
    @SerialName("hold_resolved_at") val holdResolvedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("packages") val packages: List<TrackingPackageDto> = emptyList()
)

@Serializable
data class TrackingPackageDto(
    @SerialName("id") val id: String,
    @SerialName("description") val description: String? = null,
    @SerialName("weight_kg")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val weightKg: Double? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("warehouse_location") val warehouseLocation: String? = null,
    @SerialName("received_at") val receivedAt: String? = null
)
