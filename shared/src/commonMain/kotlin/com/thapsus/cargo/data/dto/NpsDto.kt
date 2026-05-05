package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `POST /api/nps` — customer-side post-delivery survey. The server stores the
 * row in `nps_responses`. Score is 0–10 inclusive (NPS convention: 0–6
 * detractor, 7–8 passive, 9–10 promoter).
 */
@Serializable
data class NpsSubmitRequest(
    @SerialName("score") val score: Int,
    @SerialName("comment") val comment: String? = null,
    @SerialName("parcel_id") val parcelId: String? = null
)

@Serializable
data class NpsSubmitResponse(
    val success: Boolean,
    val message: String? = null
)

/**
 * `GET /api/nps/pending` — outstanding NPS invitations for the authenticated
 * user. Server creates one row per delivered order; this endpoint is the
 * canonical "should I prompt for parcel X" source. iOS still uses
 * UserDefaults locally to avoid re-prompting in the same session, but the
 * server list is authoritative across devices and sessions.
 */
@Serializable
data class NpsPendingResponse(
    val success: Boolean,
    val pending: List<NpsPendingItem> = emptyList()
)

@Serializable
data class NpsPendingItem(
    @SerialName("order_id") val orderId: String,
    @SerialName("created_at") val createdAt: String
)
