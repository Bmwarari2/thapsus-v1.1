package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.AdminFeeDto
import com.thapsus.cargo.data.dto.AdminPromotionDto
import com.thapsus.cargo.data.dto.AdminRevenueSummaryResponse
import com.thapsus.cargo.data.dto.AdminStatsResponse
import com.thapsus.cargo.data.dto.AdminUserDto
import com.thapsus.cargo.data.dto.AmlFlagDto
import com.thapsus.cargo.data.dto.CreatePricingTierRequest
import com.thapsus.cargo.data.dto.EmailConfigResponse
import com.thapsus.cargo.data.dto.ExchangeRateDto
import com.thapsus.cargo.data.dto.PricingChannel
import com.thapsus.cargo.data.dto.PricingTierDto
import com.thapsus.cargo.data.dto.ProhibitedItemDto
import com.thapsus.cargo.data.dto.UpdatePricingTierRequest
import com.thapsus.cargo.data.repository.AdminRepository
import com.thapsus.cargo.data.repository.PricingRepository
import com.thapsus.cargo.data.repository.PricingTiersRepository
import com.thapsus.cargo.data.repository.ProhibitedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminDashboardViewModel(
    private val admin: AdminRepository
) : SharedViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Loaded(
            val stats: AdminStatsResponse,
            val revenue: AdminRevenueSummaryResponse,
            val users: List<AdminUserDto>,
            val flags: List<AmlFlagDto>,
            val emailConfig: EmailConfigResponse
        ) : UiState
    }

    sealed interface ActionState {
        data object Idle : ActionState
        data object InFlight : ActionState
        data class Done(val message: String) : ActionState
        data class Error(val message: String) : ActionState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _action = MutableStateFlow<ActionState>(ActionState.Idle)
    val action: StateFlow<ActionState> = _action.asStateFlow()

    fun load() {
        scope.launch {
            _state.value = UiState.Loading
            val stats = admin.stats().getOrElse {
                _state.value = UiState.Error(it.message ?: "Failed to load stats")
                return@launch
            }
            val revenue = admin.revenueSummary().getOrNull() ?: AdminRevenueSummaryResponse()
            val users = admin.listUsers(page = 1, limit = 20).getOrNull().orEmpty()
            val flags = admin.amlFlags(status = "open").getOrNull().orEmpty()
            val emailCfg = admin.emailConfig().getOrNull() ?: EmailConfigResponse()
            _state.value = UiState.Loaded(stats, revenue, users, flags, emailCfg)
        }
    }

    fun resolveFlag(id: String, status: String) {
        scope.launch { admin.resolveAmlFlag(id, status); load() }
    }

    fun sendTestEmail(to: String) {
        scope.launch {
            _action.value = ActionState.InFlight
            admin.testEmail(to)
                .onSuccess { _action.value = ActionState.Done("Test email queued for $to.") }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Test email failed") }
        }
    }

    fun resetActionState() { _action.value = ActionState.Idle }
}

class OpsSettingsViewModel(
    private val pricing: PricingTiersRepository,
    private val pricingRead: PricingRepository,
    private val admin: AdminRepository,
    private val prohibitedRepo: ProhibitedRepository
) : SharedViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Loaded(
            val rates: List<ExchangeRateDto>,
            val fees: List<AdminFeeDto>,
            val tiers: List<PricingTierDto>,
            val promotions: List<AdminPromotionDto>,
            val prohibited: List<ProhibitedItemDto>
        ) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load() {
        scope.launch {
            _state.value = UiState.Loading
            val rates = admin.rates().getOrNull().orEmpty()
            val fees = pricing.fees().getOrNull().orEmpty()
            val tiers = pricingRead.fetchActiveTiers().getOrNull().orEmpty()
            val promos = pricing.promotions().getOrNull().orEmpty()
            val prohibited = prohibitedRepo.check("a").getOrNull().orEmpty()
            _state.value = UiState.Loaded(rates, fees, tiers, promos, prohibited)
        }
    }

    fun setRate(currencyPair: String, rate: Double) {
        scope.launch { admin.updateRate(currencyPair, rate); load() }
    }

    /**
     * Bulk update all four canonical pairs in one round-trip. Pairs whose
     * value is null/non-positive are dropped before the call so a partial
     * edit doesn't push zeros to the server.
     */
    fun setRates(rates: Map<String, Double>) {
        val sanitized = rates.filterValues { it > 0 }
        if (sanitized.isEmpty()) return
        scope.launch { admin.updateRates(sanitized); load() }
    }

    fun toggleFee(id: String, isActive: Boolean) {
        scope.launch { pricing.updateFee(id, amount = null, isActive = isActive); load() }
    }

    fun setFeeAmount(id: String, amount: Double) {
        scope.launch { pricing.updateFee(id, amount = amount, isActive = null); load() }
    }

    /**
     * Edit a fee's amount, percentage flag, and active state in one go.
     * The Ops Settings inline edit sheet uses this so a single Save commits
     * everything instead of three round-trips.
     */
    fun updateFee(id: String, amount: Double?, isPercentage: Boolean?, isActive: Boolean?) {
        scope.launch {
            pricing.updateFee(id, amount = amount, isActive = isActive, isPercentage = isPercentage)
            load()
        }
    }

    /** Create a new pricing tier and refresh. */
    fun createTier(channel: PricingChannel, minKg: Double, maxKg: Double, gbpPerKg: Double, notes: String? = null) {
        scope.launch {
            pricing.createTier(
                CreatePricingTierRequest(
                    channel = channel,
                    minKg = minKg,
                    maxKg = maxKg,
                    gbpPerKg = gbpPerKg,
                    notes = notes
                )
            )
            load()
        }
    }

    /** Edit an existing tier (gbp_per_kg, bounds, active flag, notes, effective_to). */
    fun updateTier(
        id: String,
        gbpPerKg: Double? = null,
        minKg: Double? = null,
        maxKg: Double? = null,
        isActive: Boolean? = null,
        notes: String? = null,
        effectiveTo: String? = null
    ) {
        scope.launch {
            pricing.updateTier(
                id,
                UpdatePricingTierRequest(
                    gbpPerKg = gbpPerKg,
                    minKg = minKg,
                    maxKg = maxKg,
                    isActive = isActive,
                    notes = notes,
                    effectiveTo = effectiveTo
                )
            )
            load()
        }
    }

    fun addProhibited(term: String, severity: String, reason: String?) {
        scope.launch {
            prohibitedRepo.create(term, severity, reason = reason)
            load()
        }
    }

    fun removeProhibited(id: String) {
        scope.launch { prohibitedRepo.delete(id); load() }
    }
}
