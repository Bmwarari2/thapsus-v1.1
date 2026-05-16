package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape for `GET/POST /api/account/deletion-request`. One row per
 * cooldown the customer ever started — only the most recent row is
 * returned by the customer-facing endpoints. The server enforces "one
 * active request per user" via a partial unique index.
 */
@Serializable
data class AccountDeletionRequestDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("requested_at") val requestedAt: String,
    @SerialName("scheduled_deletion_at") val scheduledDeletionAt: String,
    @SerialName("cancelled_at") val cancelledAt: String? = null,
    @SerialName("cancel_reason") val cancelReason: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("export_signed_url") val exportSignedUrl: String? = null,
    @SerialName("export_url_expires_at") val exportUrlExpiresAt: String? = null,
    @SerialName("export_generated_at") val exportGeneratedAt: String? = null,
    @SerialName("export_emailed_at") val exportEmailedAt: String? = null,
    @SerialName("days_remaining") val daysRemaining: Int = 0,
    /** "pending" | "cancelled" | "completed" — derived by the server. */
    @SerialName("status") val status: String = "pending"
)

@Serializable
data class AccountDeletionRequestEnvelope(
    val success: Boolean = true,
    val request: AccountDeletionRequestDto? = null,
    val message: String? = null
)

@Serializable
data class CancelAccountDeletionBody(
    val reason: String? = null
)

@Serializable
data class AccountDeletionExportResponse(
    val success: Boolean = true,
    @SerialName("export_signed_url") val exportSignedUrl: String? = null,
    @SerialName("export_url_expires_at") val exportUrlExpiresAt: String? = null
)
