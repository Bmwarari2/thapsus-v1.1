package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConsolidationDto(
    @SerialName("id") val id: String,
    @SerialName("week_start") val weekStart: String,
    @SerialName("cutoff_at") val cutoffAt: String,
    @SerialName("departure_at") val departureAt: String? = null,
    @SerialName("status") val status: ConsolidationStatus = ConsolidationStatus.OPEN,
    @SerialName("total_kg")
    @Serializable(with = LooseDoubleSerializer::class)
    val totalKg: Double = 0.0,
    @SerialName("total_parcels")
    @Serializable(with = LooseIntSerializer::class)
    val totalParcels: Int = 0,
    @SerialName("master_awb_no") val masterAwbNo: String? = null,
    // DB column is `master_awb_pdf` (legacy `master_awb_pdf_url` was renamed
    // before this iOS DTO was written; routes/consolidationsV2.js uses the
    // short name). Same story for the other two PDF columns.
    @SerialName("master_awb_pdf") val masterAwbPdfUrl: String? = null,
    @SerialName("tudor_invoice_no") val tudorInvoiceNo: String? = null,
    @SerialName("tudor_invoice_pdf") val tudorInvoicePdfUrl: String? = null,
    @SerialName("manifest_pdf") val manifestPdfUrl: String? = null,
    @SerialName("arrival_at") val arrivalAt: String? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("assigned_agent_id") val assignedAgentId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/**
 * Mirrors the Postgres `consolidation_status` enum exactly. The labels and
 * the constant names are kept in lock-step so `enumValue.name.lowercase()`
 * produces the wire literal the server expects in PATCH bodies — the
 * earlier divergence (`LOCKED`, `ARRIVED_JKIA`) silently failed enum
 * coercion in `routes/consolidationsV2.js` (see incident 2026-04-30).
 *
 * Webapp source of truth: `database/schema.sql` enum `consolidation_status`.
 */
@Serializable
enum class ConsolidationStatus {
    @SerialName("open") OPEN,
    @SerialName("cutoff_locked") CUTOFF_LOCKED,
    @SerialName("manifested") MANIFESTED,
    @SerialName("handed_to_tudor") HANDED_TO_TUDOR,
    @SerialName("in_transit") IN_TRANSIT,
    @SerialName("jkia_arrived") JKIA_ARRIVED,
    @SerialName("cleared") CLEARED,
    @SerialName("closed") CLOSED
}

@Serializable
data class PalletDto(
    @SerialName("id") val id: String,
    @SerialName("consolidation_id") val consolidationId: String,
    @SerialName("label") val label: String,
    @SerialName("weight_kg")
    @Serializable(with = LooseDoubleSerializer::class)
    val weightKg: Double = 0.0,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)
