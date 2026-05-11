package com.thapsus.cargo.domain.pricing

import com.thapsus.cargo.data.dto.CustomsTierDto
import com.thapsus.cargo.data.dto.ElectronicsFeeDto
import com.thapsus.cargo.data.dto.HsCodeTierDto
import com.thapsus.cargo.data.dto.InsuranceTier
import com.thapsus.cargo.data.dto.PricingChannel
import com.thapsus.cargo.data.dto.PricingSettingsDto
import com.thapsus.cargo.data.dto.QuoteItemDto
import com.thapsus.cargo.domain.model.Currency
import com.thapsus.cargo.domain.model.Money
import com.thapsus.cargo.domain.model.ParcelDimensions
import kotlin.math.max
import kotlin.math.round

/**
 * Server-side authoritative price function (per spec §4.7 + the
 * 2026-05 simplification round). Six admin-editable knobs replace
 * the previous weight-band ladder:
 *
 *   1. base_shipping_per_kg  (£/kg, multiplied by chargeable weight)
 *   2. base_handling_fee     (flat £)
 *   3. card_processing_pct   (% of FULL subtotal incl. customs)
 *   4. dim_divisor           (volumetric divisor)
 *   5. electronics_fees      (per-item-type flat £)
 *   6. customs_tiers + hs_code_tiers
 *
 * Formula:
 *
 *   chargeable_kg = max(actual_kg, (L × W × H) / dim_divisor)
 *   shipping      = base_shipping_per_kg × chargeable_kg
 *   handling      = base_handling_fee
 *   special       = Σ electronics_fees[item_key] (per flagged item)
 *   customs_est   = Σ per-item customs via HS code → tier
 *   subtotal      = shipping + handling + special + customs_est
 *   card_fee      = subtotal × card_processing_pct
 *   total         = subtotal + card_fee
 *
 * Cross-repo parity: identical settings + identical parcel produce
 * identical totals in swiftcargo-main. See QuoteEngineTest.
 */
class QuoteEngine {

    /**
     * The full quote. All money fields are GBP. Per-item customs lines
     * are surfaced so the UI can render either the rolled-up `customsEstimate`
     * or the per-item breakdown.
     */
    fun quote(
        dims: ParcelDimensions,
        settings: PricingSettingsDto = PricingSettingsDto.DEFAULT,
        customsTiers: Map<String, CustomsTierDto> = CustomsTierDto.defaults(),
        hsMap: List<HsCodeTierDto> = emptyList(),
        electronicsFees: Map<String, ElectronicsFeeDto> = emptyMap(),
        items: List<QuoteItemDto> = emptyList(),
        // Single-tier fallback path — used when `items` is empty.
        declaredValuePence: Long = 0L,
        insuranceTier: InsuranceTier = InsuranceTier.STANDARD,
        electronicsItemKey: String? = null,
        hsTier: String? = null,
        // Display-only — UK_air vs UK_sea no longer affect the price.
        // Kept so existing UIs that show "channel" don't break.
        channel: PricingChannel = PricingChannel.UK_AIR,
    ): Quote {
        val volumetric = VolumetricWeightCalculator.breakdown(dims, settings.dimDivisor)
        val chargeKg = volumetric.chargeableKg

        // ── 1. Shipping
        val freight = Money.gbpFromMajor(chargeKg * settings.baseShippingPerKg)

        // ── 2. Handling (flat fee)
        val handling = Money.gbpFromMajor(settings.baseHandlingFee)

        // ── 3. Special / electronics handling
        val electronicsCfg = electronicsItemKey?.let { electronicsFees[it] }
        val specialHandling = electronicsCfg
            ?.let { Money.gbpFromMajor(it.feeGbp) }
            ?: Money.gbp(0)

        // ── 4. Insurance (out-of-scope for this round — still hardcoded
        //                 PLUS=£8, PREMIER=3.5% of declared value).
        val insurancePremium = insurancePremium(insuranceTier, declaredValuePence)

        // ── 5. Customs estimate
        //   • items[] supplied → per-item HS-code path
        //   • else → legacy single-tier on (declared + insurance + freight) CIF
        val (customsTotal, customsItems) = if (items.isNotEmpty()) {
            calculateCustomsForItems(
                items = items,
                customsTiers = customsTiers,
                hsMap = hsMap,
                freightGbp = freight.major,
                insuranceGbp = insurancePremium.major,
            )
        } else {
            val tierKey = hsTier ?: if (electronicsCfg != null) "electronics" else "general"
            val tier = customsTiers[tierKey] ?: CustomsTierDto.GENERAL
            val cif = (declaredValuePence / 100.0) + insurancePremium.major + freight.major
            val c = customsOnCif(cif, tier)
            Pair(
                Money.gbpFromMajor(c.total),
                listOf(
                    CustomsItemBreakdown(
                        hsCode = null,
                        tierKey = tier.tierKey,
                        tierLabel = tier.label,
                        matchedPrefix = null,
                        qty = 1,
                        declaredValueGbp = declaredValuePence / 100.0,
                        cifGbp = cif,
                        total = Money.gbpFromMajor(c.total),
                        duty = Money.gbpFromMajor(c.duty),
                        vat = Money.gbpFromMajor(c.vat),
                        idf = Money.gbpFromMajor(c.idf),
                        rdl = Money.gbpFromMajor(c.rdl),
                    )
                )
            )
        }

        // ── 6. Subtotal + card fee on FULL subtotal incl customs
        val subtotal = freight + handling + specialHandling + insurancePremium + customsTotal
        val cardFee = subtotal.applyRate(settings.cardProcessingPct)

        val total = subtotal + cardFee

        return Quote(
            volumetric = volumetric,
            channel = channel,
            settings = settings,
            freight = freight,
            handling = handling,
            specialHandling = specialHandling,
            insurancePremium = insurancePremium,
            customsEstimate = customsTotal,
            cardFee = cardFee,
            subtotal = subtotal,
            total = total,
            customsItems = customsItems,
        )
    }

    /** Spec §4.9 insurance pricing. Out of scope for the 2026-05 simplification
     *  round — still hardcoded; flagged for follow-up. */
    fun insurancePremium(tier: InsuranceTier, declaredValuePence: Long): Money = when (tier) {
        InsuranceTier.STANDARD -> Money.gbp(0)
        InsuranceTier.PLUS -> Money.gbp(800) // £8 flat
        InsuranceTier.PREMIER -> Money.gbp(round(declaredValuePence * 0.035).toLong())
        InsuranceTier.CUSTOM -> Money.gbp(0) // manual quote — placeholder
    }

    /**
     * Resolve an HS code to a customs tier_key using longest-prefix match.
     * Returns `(tier_key, matched_prefix)` — items whose hs_code does not
     * match any prefix fall back to 'general' so we never have an item
     * without a tier.
     */
    private fun resolveHsTier(hsCode: String?, hsMap: List<HsCodeTierDto>): Pair<String, String?> {
        if (hsCode.isNullOrBlank()) return Pair("general", null)
        val normalised = hsCode.filter { it.isLetterOrDigit() }
        // Sort by descending prefix length so the longest match wins. We do this
        // here rather than at fetch time so callers can pass a raw API response.
        val sorted = hsMap.sortedByDescending { it.hsPrefix.length }
        for (entry in sorted) {
            if (normalised.startsWith(entry.hsPrefix)) {
                return Pair(entry.tierKey, entry.hsPrefix)
            }
        }
        return Pair("general", null)
    }

    /**
     * Per-item customs: each item gets its own CIF (declared + freight share +
     * insurance share, apportioned by declared value) and its own tier resolved
     * via HS-code prefix match.
     */
    private fun calculateCustomsForItems(
        items: List<QuoteItemDto>,
        customsTiers: Map<String, CustomsTierDto>,
        hsMap: List<HsCodeTierDto>,
        freightGbp: Double,
        insuranceGbp: Double,
    ): Pair<Money, List<CustomsItemBreakdown>> {
        val totalDeclared = items.sumOf {
            max(0.0, it.declaredValueGbp) * max(1, it.qty)
        }

        if (totalDeclared <= 0.0) {
            // No declared value across all items → customs is zero. Mirror the
            // single-tier path's zero-on-zero behaviour, and still produce per-
            // item rows so the UI shows the items.
            return Pair(
                Money.gbp(0),
                items.map {
                    CustomsItemBreakdown(
                        hsCode = it.hsCode,
                        tierKey = "general",
                        tierLabel = CustomsTierDto.GENERAL.label,
                        matchedPrefix = null,
                        qty = max(1, it.qty),
                        declaredValueGbp = 0.0,
                        cifGbp = 0.0,
                        total = Money.gbp(0),
                        duty = Money.gbp(0), vat = Money.gbp(0),
                        idf = Money.gbp(0), rdl = Money.gbp(0),
                    )
                }
            )
        }

        var runningTotal = 0.0
        val perItem = items.map { it ->
            val qty = max(1, it.qty)
            val declared = max(0.0, it.declaredValueGbp) * qty
            val share = declared / totalDeclared
            val itemFreight = freightGbp * share
            val itemInsurance = insuranceGbp * share
            val cif = declared + itemFreight + itemInsurance
            val (tierKey, matchedPrefix) = resolveHsTier(it.hsCode, hsMap)
            val tier = customsTiers[tierKey] ?: CustomsTierDto.GENERAL
            val c = customsOnCif(cif, tier)
            runningTotal += c.total

            CustomsItemBreakdown(
                hsCode = it.hsCode,
                tierKey = tierKey,
                tierLabel = tier.label,
                matchedPrefix = matchedPrefix,
                qty = qty,
                declaredValueGbp = declared,
                cifGbp = cif,
                total = Money.gbpFromMajor(c.total),
                duty = Money.gbpFromMajor(c.duty),
                vat = Money.gbpFromMajor(c.vat),
                idf = Money.gbpFromMajor(c.idf),
                rdl = Money.gbpFromMajor(c.rdl),
            )
        }
        return Pair(Money.gbpFromMajor(runningTotal), perItem)
    }

    /** Pure helper — KRA-style customs on a single CIF + tier. */
    private fun customsOnCif(cifGbp: Double, tier: CustomsTierDto): CustomsLine {
        val cif = max(0.0, cifGbp)
        if (cif == 0.0) return CustomsLine(0.0, 0.0, 0.0, 0.0, 0.0)
        val duty = cif * tier.dutyPct
        val idf = cif * tier.idfPct
        val rdl = cif * tier.rdlPct
        val vatBase = cif + duty + idf + rdl
        val vat = if (tier.vatZeroRated) 0.0 else vatBase * tier.vatPct
        return CustomsLine(duty + vat + idf + rdl, duty, vat, idf, rdl)
    }

    private data class CustomsLine(
        val total: Double, val duty: Double, val vat: Double, val idf: Double, val rdl: Double
    )

    data class Quote(
        val volumetric: VolumetricWeightCalculator.Breakdown,
        val channel: PricingChannel,
        val settings: PricingSettingsDto,
        val freight: Money,
        val handling: Money,
        val specialHandling: Money,
        val insurancePremium: Money,
        val customsEstimate: Money,
        val cardFee: Money,
        val subtotal: Money,
        val total: Money,
        val customsItems: List<CustomsItemBreakdown> = emptyList(),
    ) {
        init {
            val all = listOf(freight, handling, specialHandling, insurancePremium, customsEstimate, cardFee, subtotal, total)
            require(all.all { it.currency == Currency.GBP }) {
                "Quote pricing must be GBP-only at v1"
            }
        }

        /**
         * Back-compat alias. Older Swift / Android UIs reference `processingFee` —
         * keep the same Money exposed under that name so existing code keeps
         * compiling without a rename across the surface.
         */
        val processingFee: Money get() = cardFee

        /** Back-compat alias for the old `perKgFee` field (was trunking). */
        val perKgFee: Money get() = Money.gbp(0)
    }

    data class CustomsItemBreakdown(
        val hsCode: String?,
        val tierKey: String,
        val tierLabel: String,
        val matchedPrefix: String?,
        val qty: Int,
        val declaredValueGbp: Double,
        val cifGbp: Double,
        val total: Money,
        val duty: Money,
        val vat: Money,
        val idf: Money,
        val rdl: Money,
    )
}
