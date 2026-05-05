package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WarehouseAddressDto(
    val name: String? = null,
    val lines: List<String> = emptyList(),
    val country: String? = null,
    val postcode: String? = null,
    @SerialName("contact_phone") val contactPhone: String? = null,
    @SerialName("opening_hours") val openingHours: String? = null
)

@Serializable
data class WarehouseAddressesResponse(
    val success: Boolean = true,
    val addresses: Map<String, WarehouseAddressDto> = emptyMap()
)
