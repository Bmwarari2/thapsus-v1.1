package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Customer-facing proof-of-delivery summary returned by
 * `GET /api/orders/:id/pod`. The photo + signature URLs are signed
 * downloads with a ~5-minute TTL; the iOS UI should fetch them lazily
 * when the user opens the past-deliveries detail card and never persist
 * them.
 *
 * Server gates ownership via the parent `orders.user_id` row, so a
 * customer can only see PODs for their own parcels.
 */
@Serializable
data class PodDetailResponse(
    val success: Boolean = true,
    val pod: PodDetailDto? = null
)

@Serializable
data class PodDetailDto(
    val id: String,
    @SerialName("captured_at") val capturedAt: String? = null,
    @SerialName("recipient_name") val recipientName: String? = null,
    @SerialName("recipient_phone") val recipientPhone: String? = null,
    val notes: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("signature_url") val signatureUrl: String? = null,
    @SerialName("tracking_number") val trackingNumber: String? = null
)
