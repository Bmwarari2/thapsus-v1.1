package com.thapsus.cargo.domain.pricing

import com.thapsus.cargo.data.dto.CustomsTierDto
import com.thapsus.cargo.data.dto.HsCodeTierDto
import com.thapsus.cargo.data.dto.InsuranceTier
import com.thapsus.cargo.data.dto.PricingChannel
import com.thapsus.cargo.data.dto.PricingSettingsDto
import com.thapsus.cargo.data.dto.QuoteItemDto
import com.thapsus.cargo.domain.model.Currency
import com.thapsus.cargo.domain.model.ParcelDimensions
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests cover the post-029 six-knob pricing model. The shared core is pure —
 * chargeable-weight floor, customs-on-CIF, per-item HS resolution, card fee
 * on full subtotal — and all of it can be exercised by passing the four
 * inputs (settings, customsTiers, hsMap, electronicsFees) explicitly.
 *
 * The "cross-repo parity oracle" test at the bottom of this file mirrors the
 * same test in swiftcargo-main/tests/unit/pricing.test.js — both must produce
 * the same £420.38 total for the same input, which is what the divisor
 * reconciliation is supposed to deliver.
 */
class QuoteEngineTest {

    private val tiers = CustomsTierDto.defaults()

    // Starter HS map matching the migration 029 seeds.
    private val hsMap = listOf(
        HsCodeTierDto("8517", "electronics"),
        HsCodeTierDto("8471", "electronics"),
        HsCodeTierDto("8528", "electronics"),
        HsCodeTierDto("6109", "clothing_textiles"),
        HsCodeTierDto("4901", "books_media"),
    )

    private val defaults = PricingSettingsDto(
        baseShippingPerKg = 8.0,
        baseHandlingFee = 3.0,
        cardProcessingPct = 0.0,
        dimDivisor = 5000.0,
    )

    private fun assertClose(expected: Double, actual: Double, tol: Double = 0.02) {
        assertTrue(abs(expected - actual) <= tol, "expected $expected ±$tol, got $actual")
    }

    // ── Volumetric weight ───────────────────────────────────────────────────

    @Test
    fun volumetric_60x40x50_actual2kg_divisor5000_chargeable24() {
        val q = QuoteEngine().quote(
            dims = ParcelDimensions(60.0, 40.0, 50.0, 2.0),
            settings = defaults,
            customsTiers = tiers,
        )
        assertEquals(2.0, q.volumetric.actualKg)
        assertClose(24.0, q.volumetric.volumetricKg)
        assertClose(24.0, q.volumetric.chargeableKg)
        assertEquals(5000.0, q.volumetric.dimDivisor)
    }

    @Test
    fun volumetric_60x40x50_actual30kg_chargeable30() {
        val q = QuoteEngine().quote(
            dims = ParcelDimensions(60.0, 40.0, 50.0, 30.0),
            settings = defaults,
            customsTiers = tiers,
        )
        assertClose(30.0, q.volumetric.chargeableKg)
    }

    @Test
    fun divisor_5000_to_6000_shrinks_chargeable() {
        val dims = ParcelDimensions(60.0, 40.0, 50.0, 2.0)
        val a = QuoteEngine().quote(dims, settings = defaults.copy(dimDivisor = 5000.0), customsTiers = tiers)
        val b = QuoteEngine().quote(dims, settings = defaults.copy(dimDivisor = 6000.0), customsTiers = tiers)
        assertClose(24.0, a.volumetric.chargeableKg)
        assertClose(20.0, b.volumetric.chargeableKg)
        assertTrue(b.volumetric.chargeableKg < a.volumetric.chargeableKg)
    }

    // ── Base shipping (single rate, not tiered) ─────────────────────────────

    @Test
    fun base_shipping_per_kg_times_chargeable() {
        // 10 kg actual, no dims expansion (since dims are required by ParcelDimensions,
        // pick a tiny box where vol < actual). 1×1×1/5000 → vol = 0.0002 → chargeable = 10.
        val q = QuoteEngine().quote(
            dims = ParcelDimensions(1.0, 1.0, 1.0, 10.0),
            settings = defaults.copy(baseShippingPerKg = 7.5),
            customsTiers = tiers,
        )
        assertEquals(7500L, q.freight.minor) // 10 × £7.5 = £75 = 7500p
    }

    @Test
    fun handling_fee_is_flat() {
        val small = QuoteEngine().quote(
            ParcelDimensions(1.0, 1.0, 1.0, 1.0),
            settings = defaults.copy(baseHandlingFee = 5.0),
            customsTiers = tiers,
        )
        val big = QuoteEngine().quote(
            ParcelDimensions(1.0, 1.0, 1.0, 20.0),
            settings = defaults.copy(baseHandlingFee = 5.0),
            customsTiers = tiers,
        )
        assertEquals(500L, small.handling.minor)
        assertEquals(500L, big.handling.minor)
    }

    // ── HS-code resolution (via per-item path) ──────────────────────────────

    @Test
    fun hs_8517_resolves_to_electronics_tier() {
        val q = QuoteEngine().quote(
            dims = ParcelDimensions(20.0, 20.0, 20.0, 1.0),
            settings = defaults,
            customsTiers = tiers,
            hsMap = hsMap,
            items = listOf(QuoteItemDto(hsCode = "8517.12", qty = 1, declaredValueGbp = 500.0)),
        )
        assertEquals(1, q.customsItems.size)
        assertEquals("electronics", q.customsItems[0].tierKey)
        assertEquals("8517", q.customsItems[0].matchedPrefix)
        // Electronics: 0% duty.
        assertEquals(0L, q.customsItems[0].duty.minor)
    }

    @Test
    fun unmapped_hs_falls_back_to_general() {
        val q = QuoteEngine().quote(
            dims = ParcelDimensions(20.0, 20.0, 20.0, 1.0),
            settings = defaults,
            customsTiers = tiers,
            hsMap = hsMap,
            items = listOf(QuoteItemDto(hsCode = "9999.99", qty = 1, declaredValueGbp = 100.0)),
        )
        assertEquals("general", q.customsItems[0].tierKey)
        assertNull(q.customsItems[0].matchedPrefix)
    }

    // ── Card processing fee ─────────────────────────────────────────────────

    @Test
    fun card_fee_applies_to_full_subtotal_incl_customs() {
        val q = QuoteEngine().quote(
            dims = ParcelDimensions(60.0, 40.0, 50.0, 2.0),
            settings = defaults.copy(cardProcessingPct = 0.03),
            customsTiers = tiers,
            hsMap = hsMap,
            items = listOf(QuoteItemDto(hsCode = "general", qty = 1, declaredValueGbp = 100.0)),
        )
        // Card fee should be ~3% of the subtotal that INCLUDES customs.
        val expectedCardFee = q.subtotal.major * 0.03
        assertClose(expectedCardFee, q.cardFee.major, tol = 0.05)
        assertTrue(q.customsEstimate.minor > 0, "customs should be > 0 with declared value")
    }

    @Test
    fun card_fee_zero_when_pct_is_zero() {
        val q = QuoteEngine().quote(
            dims = ParcelDimensions(60.0, 40.0, 50.0, 2.0),
            settings = defaults.copy(cardProcessingPct = 0.0),
            customsTiers = tiers,
            items = listOf(QuoteItemDto(hsCode = "general", qty = 1, declaredValueGbp = 100.0)),
        )
        assertEquals(0L, q.cardFee.minor)
    }

    // ── Insurance (kept hardcoded — out of scope but verify it still works) ─

    @Test
    fun premier_insurance_three_point_five_percent_of_declared() {
        val q = QuoteEngine().quote(
            dims = ParcelDimensions(20.0, 20.0, 20.0, 1.0),
            settings = defaults,
            customsTiers = tiers,
            declaredValuePence = 100_000L, // £1,000
            insuranceTier = InsuranceTier.PREMIER,
        )
        assertEquals(3500L, q.insurancePremium.minor) // 3.5% of £1,000 = £35
    }

    @Test
    fun plus_insurance_eight_pounds_flat() {
        val q = QuoteEngine().quote(
            dims = ParcelDimensions(20.0, 20.0, 20.0, 1.0),
            settings = defaults,
            customsTiers = tiers,
            insuranceTier = InsuranceTier.PLUS,
        )
        assertEquals(800L, q.insurancePremium.minor)
    }

    // ── Currency invariant ──────────────────────────────────────────────────

    @Test
    fun all_quote_money_is_gbp() {
        val q = QuoteEngine().quote(
            dims = ParcelDimensions(20.0, 20.0, 20.0, 1.0),
            settings = defaults,
            customsTiers = tiers,
        )
        listOf(q.freight, q.handling, q.specialHandling, q.insurancePremium,
               q.customsEstimate, q.cardFee, q.subtotal, q.total).forEach {
            assertEquals(Currency.GBP, it.currency)
        }
    }

    // ── Back-compat aliases (Swift QuoteCalculatorView still uses these) ───

    @Test
    fun back_compat_aliases_still_work() {
        val q = QuoteEngine().quote(
            dims = ParcelDimensions(20.0, 20.0, 20.0, 1.0),
            settings = defaults.copy(cardProcessingPct = 0.05),
            customsTiers = tiers,
        )
        // processingFee aliases cardFee
        assertEquals(q.cardFee.minor, q.processingFee.minor)
        // perKgFee (legacy trunking line) is always zero in the new model
        assertEquals(0L, q.perKgFee.minor)
    }

    // ── Cross-repo parity oracle ────────────────────────────────────────────
    // The exact same input + settings as the swiftcargo-main test of the same
    // name. Both must land at £420.38 — that proves the divisor reconciliation
    // worked and the two repos quote identically.

    @Test
    fun cross_repo_parity_canonical_parcel() {
        val q = QuoteEngine().quote(
            dims = ParcelDimensions(60.0, 40.0, 50.0, 2.0),
            settings = PricingSettingsDto(
                baseShippingPerKg = 8.0,
                baseHandlingFee = 3.0,
                cardProcessingPct = 0.03,
                dimDivisor = 5000.0,
            ),
            customsTiers = tiers,
            hsMap = hsMap,
            items = listOf(
                QuoteItemDto(hsCode = "8517", qty = 1, declaredValueGbp = 600.0),
                QuoteItemDto(hsCode = "6109", qty = 2, declaredValueGbp = 30.0),
            ),
            channel = PricingChannel.UK_AIR,
        )

        // Hand calc — see tests/unit/pricing.test.js in swiftcargo-main for the full work-through.
        //   chargeable = 24 kg
        //   shipping   = 192
        //   handling   = 3
        //   customs    ≈ 213.14
        //   subtotal   ≈ 408.14
        //   card_fee   ≈ 12.24
        //   total      ≈ 420.38
        assertEquals(24.0, q.volumetric.chargeableKg, "chargeable kg")
        assertClose(192.0, q.freight.major)
        assertEquals(300L, q.handling.minor) // £3 exact
        assertClose(213.14, q.customsEstimate.major, tol = 0.05)
        assertClose(12.24, q.cardFee.major, tol = 0.05)
        assertClose(420.38, q.total.major, tol = 0.05)

        // Both items present, classified by HS prefix.
        assertEquals(2, q.customsItems.size)
        assertEquals("electronics", q.customsItems[0].tierKey)
        assertEquals("clothing_textiles", q.customsItems[1].tierKey)
    }

    @Test
    fun quote_succeeds_with_no_items_legacy_path() {
        // No items → legacy single-tier customs path on the whole CIF.
        val q = QuoteEngine().quote(
            dims = ParcelDimensions(20.0, 20.0, 20.0, 1.0),
            settings = defaults,
            customsTiers = tiers,
            declaredValuePence = 50_000L, // £500
            hsTier = "general",
        )
        assertNotNull(q)
        assertEquals(1, q.customsItems.size)
        assertEquals("general", q.customsItems[0].tierKey)
        // Engine should still produce a non-zero customs estimate.
        assertTrue(q.customsEstimate.minor > 0)
    }
}
