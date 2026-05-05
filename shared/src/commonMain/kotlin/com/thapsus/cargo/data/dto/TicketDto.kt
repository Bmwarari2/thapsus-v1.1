package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TicketDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val subject: String,
    val description: String,
    val status: String = "open",
    val priority: String = "medium",
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class TicketMessageDto(
    val id: String,
    val message: String,
    @SerialName("attachment_url") val attachmentUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val email: String? = null,
    val name: String? = null,
    val role: String? = null
)

/**
 * Raw `ticket_messages` row from the Supabase Realtime stream — no JOIN
 * with `users`, so email/name/role are not available. Repository wraps
 * this into [TicketMessageDto] before caching; admin/customer attribution
 * fills in on the next bootstrap.
 */
@Serializable
data class TicketMessageRowDto(
    val id: String,
    @SerialName("ticket_id") val ticketId: String,
    @SerialName("sender_id") val senderId: String? = null,
    val message: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class TicketListResponse(
    val success: Boolean = true,
    val tickets: List<TicketDto> = emptyList()
)

@Serializable
data class TicketDetailResponse(
    val success: Boolean = true,
    val ticket: TicketDto? = null,
    val messages: List<TicketMessageDto> = emptyList()
)

@Serializable
data class CreateTicketRequest(
    val subject: String,
    val description: String,
    val priority: String? = null
)

@Serializable
data class CreateTicketResponse(
    val success: Boolean = true,
    val message: String? = null,
    val ticket: TicketDto? = null
)

@Serializable
data class PostMessageRequest(
    val message: String,
    @SerialName("attachment_url") val attachmentUrl: String? = null
)

/** Body for POST /api/tickets/attachments/upload-url. */
@Serializable
data class TicketAttachmentUploadUrlRequest(val filename: String? = null)

@Serializable
data class TicketAttachmentUploadUrl(
    val success: Boolean = true,
    val bucket: String,
    val path: String,
    @SerialName("signed_url") val signedUrl: String,
    val token: String? = null
)

@Serializable
data class TicketAttachmentDownloadUrl(
    val success: Boolean = true,
    @SerialName("signed_url") val signedUrl: String,
    @SerialName("expires_in_seconds") val expiresInSeconds: Int? = null
)

@Serializable
data class PostMessageResponse(
    val success: Boolean = true,
    @SerialName("message_id") val messageId: String? = null
)
