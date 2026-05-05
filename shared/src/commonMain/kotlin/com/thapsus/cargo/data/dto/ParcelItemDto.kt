package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ParcelItemDto(
    @SerialName("id") val id: String,
    @SerialName("parcel_id") val parcelId: String,
    @SerialName("description") val description: String,
    @SerialName("qty")
    @Serializable(with = LooseIntSerializer::class)
    val qty: Int,
    @SerialName("unit_value_gbp_pence")
    @Serializable(with = LooseLongSerializer::class)
    val unitValueGbpPence: Long,
    @SerialName("hs_code") val hsCode: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)
