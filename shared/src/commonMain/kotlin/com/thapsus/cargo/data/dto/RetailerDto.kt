package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirror of `retailers` (migration 029). Curated catalog backing the
 * Buy-for-me create form's picker. Customers pick from this list (which
 * resolves server-side to a `retailer_url`) or pick "Other" and paste
 * a free-text URL.
 */
@Serializable
data class RetailerDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("country") val country: String,         // 'UK' | 'Other'
    @SerialName("base_url") val baseUrl: String,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 100
)

@Serializable
data class RetailersListResponse(
    val success: Boolean = true,
    val retailers: List<RetailerDto> = emptyList()
)
