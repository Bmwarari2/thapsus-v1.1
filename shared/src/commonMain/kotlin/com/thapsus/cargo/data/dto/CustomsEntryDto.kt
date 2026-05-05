package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CustomsEntryDto(
    @SerialName("id") val id: String,
    @SerialName("parcel_id") val parcelId: String,
    @SerialName("agent_id") val agentId: String? = null,
    @SerialName("idf_no") val idfNo: String? = null,
    @SerialName("entry_no") val entryNo: String? = null,
    @SerialName("cif_kes")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val cifKes: Double? = 0.0,
    @SerialName("duty_kes")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val dutyKes: Double? = 0.0,
    @SerialName("vat_kes")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val vatKes: Double? = 0.0,
    @SerialName("idf_kes")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val idfKes: Double? = 0.0,
    @SerialName("rdl_kes")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val rdlKes: Double? = 0.0,
    @SerialName("admin_fee_kes")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val adminFeeKes: Double? = 0.0,
    @SerialName("status") val status: CustomsStatus = CustomsStatus.PRE_ALERT,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("notes") val notes: String? = null
)

/**
 * Mirrors the live Postgres `customs_status` enum exactly (see migration
 * 020). Order matches the enum sort order.
 */
@Serializable
enum class CustomsStatus {
    @SerialName("pre_alert") PRE_ALERT,
    @SerialName("idf_submitted") IDF_SUBMITTED,
    @SerialName("entry_filed") ENTRY_FILED,
    @SerialName("duty_assessed") DUTY_ASSESSED,
    @SerialName("duty_paid") DUTY_PAID,
    @SerialName("released") RELEASED,
    @SerialName("rejected") REJECTED
}
