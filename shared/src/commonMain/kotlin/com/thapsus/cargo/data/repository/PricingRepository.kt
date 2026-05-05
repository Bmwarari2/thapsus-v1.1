package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.FeeDto
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

/**
 * Reads the live pricing ladder + fees so the QuoteEngine can run on-device.
 * Switched from direct PostgREST to the public Express endpoints so that:
 *  • RLS doesn't have to be relaxed for anonymous reads, and
 *  • the iOS DTO shape matches the API contract documented in
 *    `routes/pricingTiers.js` (`tiers`, `fees`).
 */
class PricingRepository(private val api: ThapsusApiClient) {

    suspend fun fetchActiveTiers(): Result<List<PricingTierDto>> = runCatching {
        api.get<PricingTiersResponse>("/pricing-tiers/tiers").tiers
    }

    suspend fun fetchActiveFees(): Result<List<FeeDto>> = runCatching {
        api.get<PricingFeesResponse>("/pricing-tiers/fees").fees
    }
}
