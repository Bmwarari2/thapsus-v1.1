package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape for the four numeric pricing knobs that live in the
 * `pricing_settings` table (migration 029). The Express backend
 * surfaces this via `GET /api/pricing/settings` as either an array
 * of rows or a key/value map; both are accepted by the repository.
 *
 * Defaults match swiftcargo-main's migration 051 so the two repos
 * produce identical totals for the same parcel + settings.
 */
@Serializable
data class PricingSettingsDto(
    @SerialName("base_shipping_per_kg")
    @Serializable(with = LooseDoubleSerializer::class)
    val baseShippingPerKg: Double = 8.0,
    @SerialName("base_handling_fee")
    @Serializable(with = LooseDoubleSerializer::class)
    val baseHandlingFee: Double = 3.0,
    @SerialName("card_processing_pct")
    @Serializable(with = LooseDoubleSerializer::class)
    val cardProcessingPct: Double = 0.0,
    @SerialName("dim_divisor")
    @Serializable(with = LooseDoubleSerializer::class)
    val dimDivisor: Double = 5000.0
) {
    companion object {
        /** In-code default used when the table is missing or the API call fails. */
        val DEFAULT = PricingSettingsDto()
    }
}

/**
 * Row of `customs_tiers`. The Express endpoint exposes percentages as
 * fractions (0.25 = 25%) so the engine can multiply directly.
 */
@Serializable
data class CustomsTierDto(
    @SerialName("tier_key") val tierKey: String,
    @SerialName("label") val label: String,
    @SerialName("duty_pct")
    @Serializable(with = LooseDoubleSerializer::class)
    val dutyPct: Double,
    @SerialName("vat_pct")
    @Serializable(with = LooseDoubleSerializer::class)
    val vatPct: Double = 0.16,
    @SerialName("idf_pct")
    @Serializable(with = LooseDoubleSerializer::class)
    val idfPct: Double = 0.035,
    @SerialName("rdl_pct")
    @Serializable(with = LooseDoubleSerializer::class)
    val rdlPct: Double = 0.02,
    @SerialName("notes") val notes: String? = null,
    @SerialName("is_active") val isActive: Boolean = true
) {
    /** True when VAT is zero — convenience flag used by the engine. */
    val vatZeroRated: Boolean get() = vatPct == 0.0

    companion object {
        /** Hard fallback for items whose hs_code does not match any prefix. */
        val GENERAL = CustomsTierDto(
            tierKey = "general",
            label = "General goods (default)",
            dutyPct = 0.25,
            vatPct = 0.16,
            notes = "Default 25% duty band — fallback when HS code is unmapped."
        )

        /** In-code seed mirroring the migration 029 / 051 seed. Used when the
         *  customs_tiers table is unreachable or empty. */
        fun defaults(): Map<String, CustomsTierDto> = mapOf(
            "general"            to GENERAL,
            "electronics"        to CustomsTierDto("electronics",        "Consumer electronics",        0.00, 0.16),
            "clothing_textiles"  to CustomsTierDto("clothing_textiles",  "Clothing & textiles",          0.25, 0.16),
            "food_processed"     to CustomsTierDto("food_processed",     "Processed food / supplements", 0.35, 0.16),
            "raw_materials"      to CustomsTierDto("raw_materials",      "Raw materials / inputs",       0.00, 0.16),
            "books_media"        to CustomsTierDto("books_media",        "Books / printed media",        0.00, 0.00),
            "zero_rated"         to CustomsTierDto("zero_rated",         "Zero-rated (medical / exempt)",0.00, 0.00),
        )
    }
}

/** Row of `hs_code_tiers`. */
@Serializable
data class HsCodeTierDto(
    @SerialName("hs_prefix") val hsPrefix: String,
    @SerialName("tier_key") val tierKey: String,
    @SerialName("notes") val notes: String? = null
)

/** Row of `electronics_fees`. */
@Serializable
data class ElectronicsFeeDto(
    @SerialName("item_key") val itemKey: String,
    @SerialName("label") val label: String,
    @SerialName("fee_gbp")
    @Serializable(with = LooseDoubleSerializer::class)
    val feeGbp: Double,
    @SerialName("min_weight_kg")
    @Serializable(with = LooseDoubleSerializer::class)
    val minWeightKg: Double = 0.0,
    @SerialName("is_active") val isActive: Boolean = true
)

/**
 * One line in an order's per-item HS-code-driven customs calculation.
 * `declaredValueGbp` is in major units (£), not pence — the pricing
 * engine bridges to Money internally.
 */
@Serializable
data class QuoteItemDto(
    @SerialName("hs_code") val hsCode: String? = null,
    @SerialName("qty") val qty: Int = 1,
    @SerialName("declared_value_gbp")
    @Serializable(with = LooseDoubleSerializer::class)
    val declaredValueGbp: Double = 0.0
)
