package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProhibitedItemDto(
    @SerialName("id") val id: String,
    @SerialName("term") val term: String,
    @SerialName("severity") val severity: ProhibitedSeverity = ProhibitedSeverity.PROHIBITED,
    @SerialName("jurisdiction") val jurisdiction: String = "KE",
    @SerialName("language") val language: String = "en",
    @SerialName("reason") val reason: String? = null,
    @SerialName("last_reviewed_at") val lastReviewedAt: String? = null
)

@Serializable
enum class ProhibitedSeverity {
    @SerialName("prohibited") PROHIBITED,
    @SerialName("restricted") RESTRICTED,
    @SerialName("dangerous_goods") DANGEROUS_GOODS
}
