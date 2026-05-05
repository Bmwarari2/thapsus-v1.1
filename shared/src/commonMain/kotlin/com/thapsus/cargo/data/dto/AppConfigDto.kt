package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape for `GET /api/app-config` (audit S2-3). Public, no-auth.
 *
 * The server has env-var-driven defaults so ops can rotate any of these
 * without shipping a new mobile build. Don't put secrets here — anything
 * that needs auth lives on a separate authenticated endpoint (e.g. the
 * Stripe publishable key on `/api/payments/config/stripe`, or the
 * customer's credit balance on `/api/payments/me/credit`).
 */
@Serializable
data class AppConfigResponse(
    val success: Boolean = true,
    val config: AppConfigDto = AppConfigDto()
)

@Serializable
data class AppConfigDto(
    @SerialName("warehouse_code") val warehouseCode: String = "STK-01",
    @SerialName("sku_prefix") val skuPrefix: String = "STK",
    @SerialName("support_whatsapp") val supportWhatsapp: String = "447424531483",
    @SerialName("support_email") val supportEmail: String = "support@thapsus.uk",
    @SerialName("otp_length")
    @Serializable(with = LooseIntSerializer::class)
    val otpLength: Int = 6
)
