package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Live response shapes from `routes/insurance.js`. The Express layer
 * passes the row through (`SELECT ip.*, o.tracking_number`) so column
 * names map directly: `declared_value_gbp`, `premium_gbp`, `payout_gbp`,
 * `cert_pdf_url`, `created_at`, `claimed_at`. Numerics are loose-decoded
 * because the underlying columns are nullable real.
 */

/**
 * The four-tier ladder offered to customers when they buy declared-value
 * insurance on a parcel. Wire form is lower-snake-case (`standard`, `plus`,
 * `premier`, `custom`) — matches the DB enum on `insurance_policies.tier`.
 */
@Serializable
enum class InsuranceTier {
    @SerialName("standard") STANDARD,
    @SerialName("plus") PLUS,
    @SerialName("premier") PREMIER,
    @SerialName("custom") CUSTOM
}

@Serializable
data class InsuranceQuoteRequest(
    val tier: String,
    @SerialName("declared_value_gbp") val declaredValueGbp: Double
)

@Serializable
data class InsuranceQuoteDto(
    @SerialName("premium_gbp") val premiumGbp: Double = 0.0,
    @SerialName("max_cover_gbp") val maxCoverGbp: Double = 0.0,
    @SerialName("requires_manual_review") val requiresManualReview: Boolean = false,
    val error: String? = null
)

@Serializable
data class InsuranceQuoteResponse(
    val success: Boolean = true,
    val quote: InsuranceQuoteDto = InsuranceQuoteDto()
)

@Serializable
data class IssuePolicyRequest(
    @SerialName("parcel_id") val parcelId: String,
    val tier: String,
    @SerialName("declared_value_gbp") val declaredValueGbp: Double
)

@Serializable
data class InsurancePolicyApiDto(
    val id: String,
    val tier: String,
    @SerialName("premium_gbp") val premiumGbp: Double = 0.0,
    @SerialName("max_cover_gbp") val maxCoverGbp: Double = 0.0,
    @SerialName("declared_value_gbp") val declaredValueGbp: Double = 0.0,
    val status: String = "active"
)

@Serializable
data class IssuePolicyResponse(
    val success: Boolean = true,
    val policy: InsurancePolicyApiDto? = null
)

@Serializable
data class PolicyRowDto(
    val id: String,
    @SerialName("parcel_id") val parcelId: String,
    @SerialName("tracking_number") val trackingNumber: String? = null,
    val tier: String,
    @SerialName("declared_value_gbp")
    @Serializable(with = LooseDoubleSerializer::class)
    val declaredValueGbp: Double = 0.0,
    @SerialName("premium_gbp")
    @Serializable(with = LooseDoubleSerializer::class)
    val premiumGbp: Double = 0.0,
    @SerialName("cert_pdf_url") val certPdfUrl: String? = null,
    val status: String = "active",
    @SerialName("claimed_at") val claimedAt: String? = null,
    @SerialName("payout_gbp")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val payoutGbp: Double? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class PolicyListResponse(
    val success: Boolean = true,
    val policies: List<PolicyRowDto> = emptyList()
)

@Serializable
data class ClaimRequest(
    @SerialName("claim_amount_gbp") val claimAmountGbp: Double,
    val notes: String? = null
)

@Serializable
data class ClaimResponse(
    val success: Boolean = true,
    val message: String? = null
)
