package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DsarRequestDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: String, // "export" | "erase"
    val status: String = "open",
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("fulfilled_at") val fulfilledAt: String? = null,
    @SerialName("export_url") val exportUrl: String? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    // Admin-side projection from /api/dsar/queue — the server joins users
    // onto the request so the queue UI can show who asked without a second
    // round-trip. Customer-side /api/dsar/me leaves these null.
    @SerialName("user_name") val userName: String? = null,
    @SerialName("user_email") val userEmail: String? = null
)

/**
 * Response shape for `GET /api/dsar/queue` (admin only). Same row shape as
 * the customer-side list but with `userName` / `userEmail` filled in.
 */
@Serializable
data class DsarQueueResponse(
    val success: Boolean = true,
    val requests: List<DsarRequestDto> = emptyList()
)

/** Body for `PATCH /api/dsar/:id`. */
@Serializable
data class UpdateDsarRequest(
    val status: String? = null,
    val notes: String? = null
)

/** Response from `POST /api/dsar/:id/export`. */
@Serializable
data class DsarExportResponse(
    val success: Boolean = true,
    @SerialName("export_url") val exportUrl: String? = null,
    val message: String? = null
)

@Serializable
data class CreateDsarRequest(
    val type: String,
    val notes: String? = null
)

@Serializable
data class DsarListResponse(
    val success: Boolean = true,
    val requests: List<DsarRequestDto> = emptyList()
)

@Serializable
data class DsarCreateResponse(
    val success: Boolean = true,
    val request: DsarRequestDto? = null,
    val message: String? = null
)
