package com.thapsus.cargo.domain.pricing

import com.thapsus.cargo.data.dto.FeeDto
import com.thapsus.cargo.data.dto.InsuranceTier
import com.thapsus.cargo.data.dto.PricingChannel
import com.thapsus.cargo.data.dto.PricingTierDto
import com.thapsus.cargo.domain.model.Currency
import com.thapsus.cargo.domain.model.ParcelDimensions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pricing-engine spec tests. Inputs match the new DB shape: `gbp_per_kg`
 * is full GBP (Double, not pence) and fees use `amount` (full GBP / percent
 * 0..100) plus `is_percentage` (Boolean), per migration 001's seed values.
 */
class QuoteEngineTest {

    private val effectiveFrom = "2026-01-01T00:00:00Z"

    private val airTiers = listOf(
        PricingTierDto(
            id = "t-1",
            channel = PricingChannel.UK_AIR,
            minKg = 0.0,
            maxKg = 5.0,
            gbpPerKg = 15.0, // £15/kg
            effectiveFrom = effectiveFrom
        ),
        PricingTierDto(
            id = "t-2",
            channel = PricingChannel.UK_AIR,
            minKg = 5.0,
            maxKg = 20.0,
            gbpPerKg = 12.0, // £12/kg
            effectiveFrom = effectiveFrom
        ),
        PricingTierDto(
            id = "t-3",
            channel = PricingChannel.UK_AIR,
            minKg = 20.0,
            maxKg = 100.0,
            gbpPerKg = 10.0, // £10/kg
            effectiveFrom = effectiveFrom
        )
    )

    private val gbpFees = listOf(
        FeeDto(
            id = "f-1",
            code = "uk_handling",
            label = "UK handling",
            currency = "GBP",
            amount = 5.0,           // £5 flat
            isPercentage = false
        ),
        FeeDto(
            id = "f-2",
            code = "card_processing",
            label = "Card processing",
            currency = "GBP",
            amount = 1.99,          // 1.99 %
            isPercentage = true
        )
    )

    @Test
    fun quote_lands_in_correct_tier_and_currency() {
        // 30x20x20 box, 2kg actual → vol = 2.0 → chargeable 2.0 → tier-1 £15/kg
        val dims = ParcelDimensions(30.0, 20.0, 20.0, 2.0)
        val q = QuoteEngine().quote(
            dims = dims,
            channel = PricingChannel.UK_AIR,
            tiers = airTiers,
            fees = gbpFees,
            insuranceTier = InsuranceTier.STANDARD,
            declaredValuePence = 5_000
        )
        assertEquals("t-1", q.tier.id)
        assertEquals(Currency.GBP, q.total.currency)
        // freight = 2kg * £15 = £30 = 3000p
        assertEquals(3000L, q.freight.minor)
        // handling £5 = 500p
        assertEquals(500L, q.handling.minor)
        // standard insurance is free
        assertEquals(0L, q.insurancePremium.minor)
        // subtotal = 3500p; processing = round(3500 * 0.0199) = 70p
        assertEquals(70L, q.processingFee.minor)
        assertEquals(3570L, q.total.minor)
    }

    @Test
    fun bulky_light_box_gets_volumetric_charged() {
        // 80x50x60, 6kg actual → vol = 40kg → chargeable 40kg → tier-3 £10/kg
        val dims = ParcelDimensions(80.0, 50.0, 60.0, 6.0)
        val q = QuoteEngine().quote(
            dims = dims,
            channel = PricingChannel.UK_AIR,
            tiers = airTiers,
            fees = gbpFees,
            insuranceTier = InsuranceTier.STANDARD,
            declaredValuePence = 0
        )
        assertEquals("t-3", q.tier.id)
        assertTrue(q.volumetric.volumetricRules)
        // freight = 40 * £10 = £400 = 40_000p
        assertEquals(40_000L, q.freight.minor)
    }

    @Test
    fun premier_insurance_is_three_point_five_percent() {
        val dims = ParcelDimensions(30.0, 20.0, 20.0, 2.0)
        val q = QuoteEngine().quote(
            dims = dims,
            channel = PricingChannel.UK_AIR,
            tiers = airTiers,
            fees = gbpFees,
            insuranceTier = InsuranceTier.PREMIER,
            declaredValuePence = 100_000 // £1,000 declared
        )
        // 3.5% of £1,000 = £35 = 3500p
        assertEquals(3500L, q.insurancePremium.minor)
    }

    @Test
    fun plus_insurance_is_eight_pounds_flat() {
        val dims = ParcelDimensions(30.0, 20.0, 20.0, 2.0)
        val q = QuoteEngine().quote(
            dims = dims,
            channel = PricingChannel.UK_AIR,
            tiers = airTiers,
            fees = gbpFees,
            insuranceTier = InsuranceTier.PLUS,
            declaredValuePence = 30_000
        )
        assertEquals(800L, q.insurancePremium.minor)
    }

    @Test
    fun overflow_weight_falls_back_to_top_tier() {
        // 200kg exceeds every tier; engine now falls back to the highest
        // band rather than throwing, so the quote still renders.
        val dims = ParcelDimensions(120.0, 100.0, 100.0, 200.0)
        val q = QuoteEngine().quote(
            dims = dims,
            channel = PricingChannel.UK_AIR,
            tiers = airTiers,
            fees = gbpFees,
            insuranceTier = InsuranceTier.STANDARD,
            declaredValuePence = 0
        )
        assertEquals("t-3", q.tier.id)
    }
}
