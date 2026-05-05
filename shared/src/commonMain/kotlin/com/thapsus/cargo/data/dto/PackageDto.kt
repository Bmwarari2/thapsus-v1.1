package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Maps to `packages` table (existing) extended with the columns from spec §5.3.
 */
@Serializable
data class PackageDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("tracking_number") val trackingNumber: String? = null,
    @SerialName("barcode") val barcode: String? = null,
    @SerialName("retailer") val retailer: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("declared_value_gbp_pence")
    @Serializable(with = LooseLongSerializer::class)
    val declaredValueGbpPence: Long = 0,
    @SerialName("actual_kg")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val actualKg: Double? = null,
    @SerialName("volumetric_kg")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val volumetricKg: Double? = null,
    @SerialName("chargeable_kg")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val chargeableKg: Double? = null,
    @SerialName("length_cm")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val lengthCm: Double? = null,
    @SerialName("width_cm")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val widthCm: Double? = null,
    @SerialName("height_cm")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val heightCm: Double? = null,
    @SerialName("status") val status: PackageStatus = PackageStatus.PRE_REGISTERED,
    @SerialName("hold_reason") val holdReason: String? = null,
    @SerialName("hold_resolved_at") val holdResolvedAt: String? = null,
    @SerialName("photographed_at") val photographedAt: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("screening_result") val screeningResult: ScreeningResult = ScreeningResult.PENDING,
    @SerialName("consolidation_id") val consolidationId: String? = null,
    @SerialName("insurance_policy_id") val insurancePolicyId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/** `GET /api/tracking/user/packages` paginated response. */
@Serializable
data class PackageListResponse(
    val success: Boolean = true,
    val packages: List<PackageDto> = emptyList(),
    @SerialName("pagination") val pagination: PaginationDto = PaginationDto()
)

@Serializable
enum class PackageStatus {
    @SerialName("pre_registered") PRE_REGISTERED,
    @SerialName("received_at_warehouse") RECEIVED_AT_WAREHOUSE,
    @SerialName("photographed") PHOTOGRAPHED,
    @SerialName("weighed") WEIGHED,
    @SerialName("screened") SCREENED,
    @SerialName("manifested") MANIFESTED,
    @SerialName("in_transit") IN_TRANSIT,
    @SerialName("jkia_arrived") JKIA_ARRIVED,
    @SerialName("awaiting_duty_payment") AWAITING_DUTY_PAYMENT,
    @SerialName("released") RELEASED,
    @SerialName("out_for_delivery") OUT_FOR_DELIVERY,
    @SerialName("delivered") DELIVERED,
    @SerialName("held") HELD,
    @SerialName("held_at_nairobi_hub") HELD_AT_NAIROBI_HUB,
    @SerialName("abandoned") ABANDONED
}

@Serializable
enum class ScreeningResult {
    @SerialName("pending") PENDING,
    @SerialName("clean") CLEAN,
    @SerialName("held") HELD,
    @SerialName("dg_suspect") DG_SUSPECT
}
