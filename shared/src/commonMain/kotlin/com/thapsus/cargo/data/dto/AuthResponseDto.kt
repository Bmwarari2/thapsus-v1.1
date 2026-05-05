package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the Express response shape from `routes/auth.js`:
 *   {
 *     success: true,
 *     message: "...",
 *     token: "<sc_token>",
 *     supabase_token: "<supabase JWT>",
 *     supabase_token_expires_at: 1735689600,
 *     user: { id, email, name, role, warehouse_id, referral_code,
 *             language_pref, wallet_balance }
 *   }
 */
@Serializable
data class AuthResponseDto(
    val success: Boolean = true,
    val message: String? = null,
    @SerialName("token") val scToken: String? = null,
    @SerialName("supabase_token") val supabaseToken: String? = null,
    @SerialName("supabase_token_expires_at") val supabaseTokenExpiresAt: Long? = null,
    @SerialName("user") val user: ScUserDto? = null
)

@Serializable
data class ScUserDto(
    val id: String,
    val email: String? = null,
    val name: String? = null,
    val phone: String? = null,
    val role: String = "customer",
    @SerialName("warehouse_id") val warehouseId: String? = null,
    @SerialName("referral_code") val referralCode: String? = null,
    @SerialName("language_pref") val languagePref: String = "en",
    @SerialName("delivery_address") val deliveryAddress: String? = null,
    @SerialName("country_of_residence") val countryOfResidence: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
    val phone: String? = null,
    @SerialName("country_of_residence") val countryOfResidence: String? = null
)

@Serializable
data class SupabaseTokenResponse(
    val success: Boolean = true,
    @SerialName("supabase_token") val supabaseToken: String? = null,
    @SerialName("supabase_token_expires_at") val supabaseTokenExpiresAt: Long? = null
)
