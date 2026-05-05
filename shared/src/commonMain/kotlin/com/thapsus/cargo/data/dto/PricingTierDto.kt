package com.thapsus.cargo.data.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Editable weight-band ladder; spec §5.2.
 *
 * NOTE: pricing in the DB (`pricing_tiers.gbp_per_kg`) is stored as full GBP
 * doubles, not pence. The QuoteEngine multiplies by 100 internally to bridge
 * to the Money(pence) domain.
 */
@Serializable
data class PricingTierDto(
    @SerialName("id") val id: String,
    @SerialName("channel") val channel: PricingChannel = PricingChannel.UK_AIR,
    @SerialName("min_kg")
    @Serializable(with = LooseDoubleSerializer::class)
    val minKg: Double,
    @SerialName("max_kg")
    @Serializable(with = LooseDoubleSerializer::class)
    val maxKg: Double,
    @SerialName("gbp_per_kg")
    @Serializable(with = LooseDoubleSerializer::class)
    val gbpPerKg: Double,
    @SerialName("effective_from") val effectiveFrom: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("notes") val notes: String? = null
)

/**
 * DB strings for the `pricing_tiers.channel` column. The canonical seed values
 * are `UK_air`, `UK_sea`, `China_air`, but a custom serializer keeps lookup
 * case-insensitive so a row written by hand as `uk_air` / `UK_AIR` still
 * deserialises into the right enum constant — without that, kotlinx.serialization
 * would throw `IllegalArgumentException: <name> is not a valid value` and the
 * whole tiers response would fail, leaving the QuoteEngine with an empty list
 * and the iOS calculator stuck on "No pricing tiers available".
 */
@Serializable(with = PricingChannelSerializer::class)
enum class PricingChannel(val wireName: String) {
    UK_AIR("UK_air"),
    UK_SEA("UK_sea"),
    CHINA_AIR("China_air");
}

object PricingChannelSerializer : KSerializer<PricingChannel> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("PricingChannel", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PricingChannel) {
        encoder.encodeString(value.wireName)
    }

    override fun deserialize(decoder: Decoder): PricingChannel {
        val raw = decoder.decodeString().trim().lowercase().replace('-', '_')
        return when (raw) {
            "uk_air", "ukair", "air_uk" -> PricingChannel.UK_AIR
            "uk_sea", "uksea", "sea_uk" -> PricingChannel.UK_SEA
            "china_air", "chinaair", "cn_air", "air_china" -> PricingChannel.CHINA_AIR
            else -> PricingChannel.UK_AIR // tolerant default keeps the calculator alive
        }
    }
}

/**
 * Authoritative fee row from `fees`. `amount` holds GBP (when isPercentage=false)
 * or a percentage 0..100 (when isPercentage=true). The QuoteEngine handles both.
 */
@Serializable
data class FeeDto(
    @SerialName("id") val id: String,
    @SerialName("code") val code: String,
    @SerialName("label") val label: String,
    @SerialName("currency") val currency: String = "GBP",
    @SerialName("amount")
    @Serializable(with = LooseDoubleSerializer::class)
    val amount: Double,
    @SerialName("is_percentage") val isPercentage: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("notes") val notes: String? = null
)

@Serializable
data class FxRateDto(
    @SerialName("id") val id: String,
    @SerialName("currency_pair") val currencyPair: String,
    @SerialName("rate")
    @Serializable(with = LooseDoubleSerializer::class)
    val rate: Double,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class PromotionDto(
    @SerialName("id") val id: String,
    @SerialName("code") val code: String,
    @SerialName("type") val type: String,
    @SerialName("value")
    @Serializable(with = LooseDoubleSerializer::class)
    val value: Double,
    @SerialName("valid_from") val validFrom: String? = null,
    @SerialName("valid_to") val validTo: String? = null
)

/** Body for `POST /api/pricing-tiers/tiers`. */
@Serializable
data class CreatePricingTierRequest(
    @SerialName("channel") val channel: PricingChannel,
    @SerialName("min_kg") val minKg: Double,
    @SerialName("max_kg") val maxKg: Double,
    @SerialName("gbp_per_kg") val gbpPerKg: Double,
    @SerialName("notes") val notes: String? = null
)

@Serializable
data class CreatePricingTierResponse(
    val success: Boolean,
    val id: String? = null,
    val message: String? = null
)

/** Body for `PATCH /api/pricing-tiers/tiers/:id` — partial update. */
@Serializable
data class UpdatePricingTierRequest(
    @SerialName("gbp_per_kg") val gbpPerKg: Double? = null,
    @SerialName("min_kg") val minKg: Double? = null,
    @SerialName("max_kg") val maxKg: Double? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("effective_to") val effectiveTo: String? = null
)
