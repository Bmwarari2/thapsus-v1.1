package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateProhibitedRequest(
    val term: String,
    val severity: String = "prohibited",
    val jurisdiction: String = "KE",
    val language: String = "en",
    val reason: String? = null
)

@Serializable
data class ProhibitedAdminAckResponse(
    val success: Boolean = true,
    val id: String? = null
)
