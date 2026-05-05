package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.FeeDto
import com.thapsus.cargo.data.dto.InsuranceTier
import com.thapsus.cargo.data.dto.PricingChannel
import com.thapsus.cargo.data.dto.PricingTierDto
import com.thapsus.cargo.data.repository.PricingRepository
import com.thapsus.cargo.domain.model.ParcelDimensions
import com.thapsus.cargo.domain.pricing.QuoteEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Drives the public price calculator and the in-app quote step at parcel pre-reg. */
class QuoteViewModel(
    private val pricing: PricingRepository,
    private val engine: QuoteEngine = QuoteEngine()
) : SharedViewModel() {

    private val _tiers = MutableStateFlow<List<PricingTierDto>>(emptyList())
    val tiers: StateFlow<List<PricingTierDto>> = _tiers.asStateFlow()

    private val _fees = MutableStateFlow<List<FeeDto>>(emptyList())
    val fees: StateFlow<List<FeeDto>> = _fees.asStateFlow()

    private val _quote = MutableStateFlow<QuoteEngine.Quote?>(null)
    val quote: StateFlow<QuoteEngine.Quote?> = _quote.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadPricing() {
        scope.launch {
            // Outer try/catch is defensive: every individual call is already
            // a Result, but a fix on 2026-04-30 traced an app-freeze on the
            // customer Calculator tab to an unhandled coroutine exception
            // landing in the K/N abort handler. If anything in this scope
            // ever throws, we now surface it to `_error` instead of taking
            // down the whole process.
            try {
                pricing.fetchActiveTiers()
                    .onSuccess { _tiers.value = it }
                    .onFailure {
                        println("[QuoteViewModel] fetchActiveTiers failed: ${it::class.simpleName}: ${it.message}")
                    }
                pricing.fetchActiveFees()
                    .onSuccess { _fees.value = it }
                    .onFailure {
                        println("[QuoteViewModel] fetchActiveFees failed (non-fatal): ${it.message}")
                    }
            } catch (t: Throwable) {
                val msg = "Couldn't load pricing: ${t::class.simpleName}: ${t.message ?: "unknown"}"
                println("[QuoteViewModel] $msg")
                _error.value = msg
            }
        }
    }

    fun computeQuote(
        dims: ParcelDimensions,
        channel: PricingChannel,
        insurance: InsuranceTier,
        declaredValuePence: Long
    ) {
        scope.launch {
            try {
                // Lazy-load on first calculate so a stale screen still works.
                if (_tiers.value.isEmpty()) {
                    pricing.fetchActiveTiers().onSuccess { _tiers.value = it }
                }
                if (_fees.value.isEmpty()) {
                    pricing.fetchActiveFees().onSuccess { _fees.value = it }
                }
                if (_tiers.value.isEmpty()) {
                    _error.value = "No pricing tiers available. Try again in a moment, or contact support."
                    _quote.value = null
                    return@launch
                }
                _quote.value = engine.quote(
                    dims = dims,
                    channel = channel,
                    tiers = _tiers.value,
                    fees = _fees.value,
                    insuranceTier = insurance,
                    declaredValuePence = declaredValuePence
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
