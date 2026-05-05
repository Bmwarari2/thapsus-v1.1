package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequest(
    val name: String? = null,
    val phone: String? = null,
    @SerialName("language_pref") val languagePref: String? = null,
    @SerialName("delivery_address") val deliveryAddress: String? = null
)

@Serializable
data class UpdateProfileResponse(
    val success: Boolean = true,
    val message: String? = null,
    val user: ScUserDto? = null,
    /** Re-minted sc_token reflecting any changes to the JWT claims (T21). */
    val token: String? = null
)

@Serializable
data class ChangePasswordRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String
)

@Serializable
data class ForgotPasswordRequest(val email: String)

/**
 * Body for `POST /api/auth/reset-password` — landing page for the
 * password-reset email link. The token is the opaque hex string the
 * email link carries; the server SHA-256s it and matches against
 * password_reset_tokens.token_sha256.
 */
@Serializable
data class ResetPasswordRequest(
    val token: String,
    @SerialName("new_password") val newPassword: String
)

@Serializable
data class GenericAckResponse(
    val success: Boolean = true,
    val message: String? = null
)
