package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body for `POST /api/ops/parcels/:id/receive`. The server destructures
 * `{weight_kg, dimensions, photo_url, barcode}` and returns the recomputed
 * volumetric and chargeable weights — see `routes/ops.js:86`.
 */
@Serializable
data class OpsReceiveRequest(
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("dimensions") val dimensions: OrderDimensionsDto? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("barcode") val barcode: String? = null,
    /**
     * Customs duty (KES) operator-stamped at receive. The customer
     * intake flow no longer asks for weight/dims, and the receive
     * step is the canonical place to capture both the chargeable
     * weight and the duty — feeds the Phase 2 invoice prefill.
     * Nullable so an operator can update measurements without
     * blanking a duty an admin already set.
     */
    @SerialName("customs_duty") val customsDuty: Double? = null,
    /**
     * Audit P2.3: BFM auto-create stamps every parcel as
     * hs_tier='general' (markPaymentPaid.maybeCreatePreRegisteredParcelForBfm).
     * The operator picks the real tier at receive time so duty/VAT
     * on the eventual invoice prefill matches the goods. Server
     * validates against utils/pricing.js HS_TIERS; null leaves the
     * existing tier alone (COALESCE).
     */
    @SerialName("hs_tier") val hsTier: String? = null
)

@Serializable
data class OpsReceiveResponse(
    val success: Boolean = true,
    @SerialName("weight_kg")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val weightKg: Double? = null,
    @SerialName("volumetric_kg")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val volumetricKg: Double? = null,
    @SerialName("chargeable_kg")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val chargeableKg: Double? = null,
    @SerialName("customs_duty")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val customsDuty: Double? = null,
    @SerialName("hs_tier") val hsTier: String? = null,
    val message: String? = null
)

/**
 * `GET /api/ops/parcels/:id/customer` — projection used to populate the
 * Phase C operator label with the actual customer name + their personal
 * warehouse code (`TC-XXXX`). Operator-scoped.
 */
@Serializable
data class OpsCustomerLookupResponse(
    val success: Boolean = true,
    val customer: OpsCustomerDto? = null,
    val message: String? = null
)

@Serializable
data class OpsCustomerDto(
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("warehouse_id") val warehouseId: String? = null,
    val email: String? = null,
    val phone: String? = null
)

/**
 * `GET /api/ops/parcels/by-barcode/:barcode` — full operator-scope
 * projection for a scanned SKU. Used by the operator camera scanner to
 * route to either the Receive sheet (if `package_status == "pre_registered"`)
 * or the parcel detail panel (everything else).
 */
@Serializable
data class OpsBarcodeLookupResponse(
    val success: Boolean = true,
    val parcel: OpsScannedParcelDto? = null,
    val message: String? = null
)

@Serializable
data class OpsScannedParcelDto(
    @SerialName("package_id") val packageId: String,
    @SerialName("order_id") val orderId: String? = null,
    val barcode: String? = null,
    @SerialName("tracking_number") val trackingNumber: String? = null,
    @SerialName("package_status") val packageStatus: String? = null,
    @SerialName("order_status") val orderStatus: String? = null,
    val retailer: String? = null,
    val description: String? = null,
    val market: String? = null,
    @SerialName("declared_value_gbp_pence") val declaredValueGbpPence: Long? = null,
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
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("received_at") val receivedAt: String? = null,
    @SerialName("photographed_at") val photographedAt: String? = null,
    @SerialName("is_consolidated") val isConsolidated: Boolean? = null,
    @SerialName("consolidation_id") val consolidationId: String? = null,
    @SerialName("consolidation_master_awb") val consolidationMasterAwb: String? = null,
    @SerialName("consolidation_flight_date") val consolidationFlightDate: String? = null,
    @SerialName("consolidation_status") val consolidationStatus: String? = null,
    @SerialName("hold_reason") val holdReason: String? = null,
    @SerialName("hold_resolved_at") val holdResolvedAt: String? = null,
    val customer: OpsCustomerDto? = null
) {
    /**
     * Bridge to PackageDto so the Receive sheet (which is shaped around
     * PackageDto) can receive a parcel that came in through the scanner.
     * The receive flow keys on `id` as the order id, so we substitute
     * orderId here (or fall back to packageId if the row predates the
     * orphan backfill).
     */
    fun toPackageDto(): PackageDto = PackageDto(
        id = packageId,
        userId = "",
        orderId = orderId,
        trackingNumber = trackingNumber,
        barcode = barcode,
        retailer = retailer,
        description = description,
        declaredValueGbpPence = declaredValueGbpPence ?: 0L,
        actualKg = actualKg,
        volumetricKg = volumetricKg,
        chargeableKg = chargeableKg,
        lengthCm = lengthCm,
        widthCm = widthCm,
        heightCm = heightCm,
        status = runCatching {
            PackageStatus.valueOf((packageStatus ?: "pre_registered").uppercase())
        }.getOrDefault(PackageStatus.PRE_REGISTERED),
        holdReason = holdReason,
        holdResolvedAt = holdResolvedAt,
        photographedAt = photographedAt,
        photoUrl = photoUrl,
        screeningResult = ScreeningResult.PENDING,
        consolidationId = consolidationId,
        insurancePolicyId = null,
        createdAt = null,
        updatedAt = null
    )
}
