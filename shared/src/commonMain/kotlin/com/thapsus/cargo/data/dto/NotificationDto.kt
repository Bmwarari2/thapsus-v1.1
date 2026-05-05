package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors `routes/notifications.js` row shape. */
@Serializable
data class NotificationDto(
    val id: String,
    val type: String = "in_app",
    val message: String,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class NotificationListResponse(
    val success: Boolean = true,
    val notifications: List<NotificationDto> = emptyList(),
    @Serializable(with = LooseIntSerializer::class) val unread: Int = 0
)

@Serializable
data class NotificationAckResponse(
    val success: Boolean = true,
    val message: String? = null
)
