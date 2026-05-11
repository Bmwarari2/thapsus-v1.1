package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.CustomsTierDto
import com.thapsus.cargo.data.dto.ElectronicsFeeDto
import com.thapsus.cargo.data.dto.FeeDto
import com.thapsus.cargo.data.dto.HsCodeTierDto
import com.thapsus.cargo.data.dto.PricingSettingsDto
import com.thapsus.cargo.data.dto.PricingTierDto
import com.thapsus.cargo.data.remote.ThapsusApiClient
import kotlinx.serialization.Serializable

@Serializable
internal data class PricingTiersResponse(
    val success: Boolean = true,
    val tiers: List<PricingTierDto> = emptyList()
)

@Serializable
internal data class PricingFeesResponse(
    val success: Boolean = true,
    val fees: List<FeeDto> = emptyList()
)

@Serializable
internal data class PricingSettingsResponse(
    val success: Boolean = true,
    val settings: PricingSettingsDto = PricingSettingsDto.DEFAULT
)

@Serializable
internal data class CustomsTiersResponse(
    val success: Boolean = true,
    val tiers: List<CustomsTierDto> = emptyList()
)

@Serializable
internal data class HsCodesResponse(
    val success: Boolean = true,
    val mapping: List<HsCodeTierDto> = emptyList()
)

@Serializable
internal data class ElectronicsFeesResponse(
    val success: Boolean = true,
    val items: List<ElectronicsFeeDto> = emptyList()
)

/**
 * Reads the live pricing inputs so the QuoteEngine can run on-device.
 *
 * Legacy fetches (`fetchActiveTiers`, `fetchActiveFees`) stay for back-compat
 * with operator-side UIs that still browse the weight-band ladder. After
 * migration 029, `pricing_tiers` rows are archived (is_active=FALSE) so the
 * legacy endpoint returns an empty list and the QuoteEngine ignores them.
 *
 * Six-knob model fetches:
 *   • fetchSettings        → the four numeric tunables
 *   • fetchCustomsTiers    → duty/VAT/IDF/RDL bands
 *   • fetchHsCodes         → HS-prefix → tier_key mapping
 *   • fetchElectronicsFees → per-item-type flat handling £
 */
class PricingRepository(private val api: ThapsusApiClient) {

    suspend fun fetchActiveTiers(): Result<List<PricingTierDto>> = runCatching {
        api.get<PricingTiersResponse>("/pricing-tiers/tiers").tiers
    }

    suspend fun fetchActiveFees(): Result<List<FeeDto>> = runCatching {
        api.get<PricingFeesResponse>("/pricing-tiers/fees").fees
    }

    suspend fun fetchSettings(): Result<PricingSettingsDto> = runCatching {
        api.get<PricingSettingsResponse>("/pricing/settings").settings
    }

    suspend fun fetchCustomsTiers(): Result<List<CustomsTierDto>> = runCatching {
        api.get<CustomsTiersResponse>("/pricing/hs-tiers").tiers
    }

    suspend fun fetchHsCodes(): Result<List<HsCodeTierDto>> = runCatching {
        api.get<HsCodesResponse>("/pricing/hs-codes").mapping
    }

    suspend fun fetchElectronicsFees(): Result<List<ElectronicsFeeDto>> = runCatching {
        api.get<ElectronicsFeesResponse>("/pricing/electronics").items
    }
}
