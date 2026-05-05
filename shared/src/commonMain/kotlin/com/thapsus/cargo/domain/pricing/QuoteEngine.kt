package com.thapsus.cargo.domain.pricing

import com.thapsus.cargo.data.dto.FeeDto
import com.thapsus.cargo.data.dto.InsuranceTier
import com.thapsus.cargo.data.dto.PricingChannel
import com.thapsus.cargo.data.dto.PricingTierDto
import com.thapsus.cargo.domain.model.Currency
import com.thapsus.cargo.domain.model.Money
import com.thapsus.cargo.domain.model.ParcelDimensions
import kotlin.math.round

/**
 * Server-side authoritative price function (per spec §4.7) ported to Kotlin so
 * the mobile app can quote on-device with the same numbers the web API will see.
 *
 * Inputs are loaded from the `pricing_tiers` and `fees` tables (or their
 * `/api/pricing-tiers/...` endpoints). The DB stores money as full GBP
 * doubles, not minor units; this engine bridges to the pence-based Money
 * domain by multiplying when needed.
 */
class QuoteEngine {

    fun quote(
        dims: ParcelDimensions,
        channel: PricingChannel,
        tiers: List<PricingTierDto>,
        fees: List<FeeDto>,
        insuranceTier: InsuranceTier,
        declaredValuePence: Long,
        // Trunking is the operator-side hub-to-airport leg cost — it should
        // not appear in the customer-facing quote calculator (audit C1).
        // Off by default; the operator intake path can flip it on if/when
        // we surface an operator quote.
        includeTrunking: Boolean = false
    ): Quote {
        val breakdown = VolumetricWeightCalculator.breakdown(dims)
        val chargeKg = breakdown.chargeableKg

        val activeOnChannel = tiers.filter { it.channel == channel && it.isActive }
        val tier = activeOnChannel.firstOrNull { chargeKg >= it.minKg && chargeKg < it.maxKg }
            ?: activeOnChannel.maxByOrNull { it.maxKg }
            // Last-ditch: any active tier, irrespective of channel — better to over-quote
            // than to refuse to price the parcel.
            ?: tiers.filter { it.isActive }.maxByOrNull { it.maxKg }
            ?: throw IllegalStateException(
                "No pricing tiers loaded — Supabase pricing_tiers table is empty " +
                    "or is_active = false. Run database/migrations/005_pricing_tiers_repair.sql."
            )

        // gbp_per_kg is stored as full GBP doubles → convert to pence for Money math.
        val freightPence = round(chargeKg * tier.gbpPerKg * 100).toLong()
        val freight = Money.gbp(freightPence)

        val gbpFees = fees.filter { it.currency == "GBP" && it.isActive }

        val handling = gbpFees.firstOrNull { it.code == "uk_handling" && !it.isPercentage }
            ?.let { Money.gbp(round(it.amount * 100).toLong()) } ?: Money.gbp(0)

        val trunking = if (includeTrunking) {
            gbpFees.firstOrNull { it.code == "trunking" && !it.isPercentage }
                ?.let { Money.gbp(round(it.amount * 100).toLong()) } ?: Money.gbp(0)
        } else Money.gbp(0)

        val insurancePremium = insurancePremium(insuranceTier, declaredValuePence)

        val subtotal = freight + handling + trunking + insurancePremium

        // Percentage-typed fees (e.g. card_processing, admin_pct) — `amount` is
        // an integer percentage 0..100 in the DB, so divide by 100 to get a rate.
        val processingPercent = gbpFees.firstOrNull {
            (it.code == "card_processing" || it.code == "admin_pct") && it.isPercentage
        }
        val processing = processingPercent?.let { fee ->
            subtotal.applyRate(fee.amount / 100.0)
        } ?: Money.gbp(0)

        val total = subtotal + processing

        return Quote(
            volumetric = breakdown,
            channel = channel,
            tier = tier,
            freight = freight,
            handling = handling,
            perKgFee = trunking,
            insurancePremium = insurancePremium,
            processingFee = processing,
            total = total
        )
    }

    /** Spec §4.9 insurance pricing. */
    fun insurancePremium(tier: InsuranceTier, declaredValuePence: Long): Money = when (tier) {
        InsuranceTier.STANDARD -> Money.gbp(0)
        InsuranceTier.PLUS -> Money.gbp(800) // £8 flat
        InsuranceTier.PREMIER -> Money.gbp(round(declaredValuePence * 0.035).toLong())
        InsuranceTier.CUSTOM -> Money.gbp(0) // manual quote — placeholder
    }

    data class Quote(
        val volumetric: VolumetricWeightCalculator.Breakdown,
        val channel: PricingChannel,
        val tier: PricingTierDto,
        val freight: Money,
        val handling: Money,
        val perKgFee: Money,
        val insurancePremium: Money,
        val processingFee: Money,
        val total: Money
    ) {
        init {
            val all = listOf(freight, handling, perKgFee, insurancePremium, processingFee, total)
            require(all.all { it.currency == Currency.GBP }) {
                "Quote pricing must be GBP-only at v1"
            }
        }
    }
}
