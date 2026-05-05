package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TudorInvoiceDto(
    @SerialName("id") val id: String,
    @SerialName("consolidation_id") val consolidationId: String,
    @SerialName("amount_gbp_pence")
    @Serializable(with = LooseLongSerializer::class)
    val amountGbpPence: Long,
    @SerialName("breakdown_json") val breakdownJson: String? = null,
    @SerialName("status") val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class DutyInvoiceDto(
    @SerialName("id") val id: String,
    @SerialName("parcel_id") val parcelId: String,
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("duty_kes_cents")
    @Serializable(with = LooseLongSerializer::class)
    val dutyKesCents: Long,
    @SerialName("admin_fee_kes_cents")
    @Serializable(with = LooseLongSerializer::class)
    val adminFeeKesCents: Long,
    @SerialName("total_kes_cents")
    @Serializable(with = LooseLongSerializer::class)
    val totalKesCents: Long,
    @SerialName("status") val status: String = "pending",
    @SerialName("paid_at") val paidAt: String? = null
)
