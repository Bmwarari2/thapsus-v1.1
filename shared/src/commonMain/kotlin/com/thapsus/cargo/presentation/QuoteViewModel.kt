package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.CustomsTierDto
import com.thapsus.cargo.data.dto.ElectronicsFeeDto
import com.thapsus.cargo.data.dto.FeeDto
import com.thapsus.cargo.data.dto.HsCodeTierDto
import com.thapsus.cargo.data.dto.InsuranceTier
import com.thapsus.cargo.data.dto.PricingChannel
import com.thapsus.cargo.data.dto.PricingSettingsDto
import com.thapsus.cargo.data.dto.PricingTierDto
import com.thapsus.cargo.data.dto.QuoteItemDto
import com.thapsus.cargo.data.repository.PricingRepository
import com.thapsus.cargo.domain.model.ParcelDimensions
import com.thapsus.cargo.domain.pricing.QuoteEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the public price calculator and the in-app quote step at parcel
 * pre-reg. Post-029 the engine reads:
 *   • pricing_settings (4 knobs) — single base £/kg replaces the tier ladder
 *   • customs_tiers — per-tier duty/VAT/IDF/RDL bands
 *   • hs_code_tiers — HS-prefix → tier_key mapping
 *   • electronics_fees — per-item-type flat handling £
 *
 * Legacy tiers + fees streams are kept (for operator UIs that browse the
 * historical ladder) but no longer drive the customer quote.
 */
class QuoteViewModel(
    private val pricing: PricingRepository,
    private val engine: QuoteEngine = QuoteEngine()
) : SharedViewModel() {

    // ── Legacy streams (kept for operator UIs)
    private val _tiers = MutableStateFlow<List<PricingTierDto>>(emptyList())
    val tiers: StateFlow<List<PricingTierDto>> = _tiers.asStateFlow()

    private val _fees = MutableStateFlow<List<FeeDto>>(emptyList())
    val fees: StateFlow<List<FeeDto>> = _fees.asStateFlow()

    // ── Six-knob model streams
    private val _settings = MutableStateFlow(PricingSettingsDto.DEFAULT)
    val settings: StateFlow<PricingSettingsDto> = _settings.asStateFlow()

    private val _customsTiers = MutableStateFlow(CustomsTierDto.defaults())
    val customsTiers: StateFlow<Map<String, CustomsTierDto>> = _customsTiers.asStateFlow()

    private val _hsMap = MutableStateFlow<List<HsCodeTierDto>>(emptyList())
    val hsMap: StateFlow<List<HsCodeTierDto>> = _hsMap.asStateFlow()

    private val _electronicsFees = MutableStateFlow<Map<String, ElectronicsFeeDto>>(emptyMap())
    val electronicsFees: StateFlow<Map<String, ElectronicsFeeDto>> = _electronicsFees.asStateFlow()

    /**
     * Live GBP→KES rate. The pricing engine is GBP-native; customer-facing
     * surfaces (calculator total, invoice summary) display KES by multiplying
     * engine output by this rate at render time. Null until the first load
     * succeeds — UI should fall back to £ in that window.
     */
    private val _gbpToKes = MutableStateFlow<Double?>(null)
    val gbpToKes: StateFlow<Double?> = _gbpToKes.asStateFlow()

    private val _quote = MutableStateFlow<QuoteEngine.Quote?>(null)
    val quote: StateFlow<QuoteEngine.Quote?> = _quote.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadPricing() {
        scope.launch {
            // Outer try/catch is defensive: a fix on 2026-04-30 traced an app-
            // freeze on the customer Calculator tab to an unhandled coroutine
            // exception landing in the K/N abort handler. If anything in this
            // scope throws, we now surface it to `_error` instead of taking
            // down the whole process.
            try {
                pricing.fetchSettings()
                    .onSuccess { _settings.value = it }
                    .onFailure { println("[QuoteViewModel] fetchSettings failed (using defaults): ${it.message}") }

                pricing.fetchCustomsTiers()
                    .onSuccess { fetched ->
                        if (fetched.isNotEmpty()) {
                            _customsTiers.value = fetched.associateBy { it.tierKey }
                        }
                    }
                    .onFailure { println("[QuoteViewModel] fetchCustomsTiers failed (using defaults): ${it.message}") }

                pricing.fetchHsCodes()
                    .onSuccess { _hsMap.value = it }
                    .onFailure { println("[QuoteViewModel] fetchHsCodes failed (using empty map): ${it.message}") }

                pricing.fetchElectronicsFees()
                    .onSuccess { _electronicsFees.value = it.associateBy { ef -> ef.itemKey } }
                    .onFailure { println("[QuoteViewModel] fetchElectronicsFees failed (using empty map): ${it.message}") }

                pricing.fetchExchangeRates()
                    .onSuccess { _gbpToKes.value = it["GBP_KES"] }
                    .onFailure { println("[QuoteViewModel] fetchExchangeRates failed (calculator will display £): ${it.message}") }

                // Legacy fetches — operator UI uses these. Failure is non-fatal
                // for the quote calculator since the new engine doesn't read them.
                pricing.fetchActiveTiers()
                    .onSuccess { _tiers.value = it }
                    .onFailure { println("[QuoteViewModel] fetchActiveTiers (legacy) failed: ${it.message}") }
                pricing.fetchActiveFees()
                    .onSuccess { _fees.value = it }
                    .onFailure { println("[QuoteViewModel] fetchActiveFees (legacy) failed: ${it.message}") }
            } catch (t: Throwable) {
                val msg = "Couldn't load pricing: ${t::class.simpleName}: ${t.message ?: "unknown"}"
                println("[QuoteViewModel] $msg")
                _error.value = msg
            }
        }
    }

    /**
     * Compute a quote using the six-knob model. Optional `items[]` enables
     * per-item HS-code customs; otherwise the engine uses a single hs_tier
     * over the total CIF.
     */
    fun computeQuote(
        dims: ParcelDimensions,
        channel: PricingChannel,
        insurance: InsuranceTier,
        declaredValuePence: Long,
        items: List<QuoteItemDto> = emptyList(),
        electronicsItemKey: String? = null,
        hsTier: String? = null,
        skipCustoms: Boolean = false,
    ) {
        scope.launch {
            try {
                // Lazy first-quote refresh — same pattern as the legacy path,
                // so a stale Calculator tab still produces a number.
                if (_settings.value === PricingSettingsDto.DEFAULT) {
                    pricing.fetchSettings().onSuccess { _settings.value = it }
                }
                _quote.value = engine.quote(
                    dims = dims,
                    settings = _settings.value,
                    customsTiers = _customsTiers.value,
                    hsMap = _hsMap.value,
                    electronicsFees = _electronicsFees.value,
                    items = items,
                    declaredValuePence = declaredValuePence,
                    insuranceTier = insurance,
                    electronicsItemKey = electronicsItemKey,
                    hsTier = hsTier,
                    channel = channel,
                    skipCustoms = skipCustoms,
                )
                _error.value = null
            } catch (t: Throwable) {
                val msg = "Quote failed: ${t::class.simpleName}: ${t.message ?: "unknown"}"
                println("[QuoteViewModel] $msg")
                _error.value = msg
                _quote.value = null
            }
        }
    }
}
