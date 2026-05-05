package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AmlFlagDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("parcel_id") val parcelId: String? = null,
    val reason: String,
    val status: String = "open",
    val notes: String? = null,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("user_email") val userEmail: String? = null,
    @SerialName("resolved_at") val resolvedAt: String? = null,
    @SerialName("resolved_by") val resolvedBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AmlFlagListResponse(
    val success: Boolean = true,
    val flags: List<AmlFlagDto> = emptyList()
)

@Serializable
data class UpdateAmlFlagRequest(val status: String, val notes: String? = null)
