package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LastMileRunDto(
    @SerialName("id") val id: String,
    /** Nullable since migration 013 — a planned run can sit unassigned. */
    @SerialName("rider_id") val riderId: String? = null,
    @SerialName("zone") val zone: String,
    @SerialName("run_date") val runDate: String,
    @SerialName("status") val status: RunStatus = RunStatus.PLANNED,
    @SerialName("total_stops") val totalStops: Int? = null,
    @SerialName("completed_stops") val completedStops: Int? = null,
    @SerialName("rider_name") val riderName: String? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    /**
     * Populated by `GET /api/last-mile/rider/today`, which embeds the
     * stop list per run.  Empty for endpoints that don't bundle parcels
     * (e.g. dispatch board's runs[]).
     */
    @SerialName("parcels") val parcels: List<DispatchParcelRow> = emptyList()
)

/**
 * Response shape for `GET /api/last-mile/rider/today` — the assigned
 * rider's runs for today, with the stop list embedded on each run.
 */
@Serializable
data class RiderTodayResponse(
    val success: Boolean = true,
    val runs: List<LastMileRunDto> = emptyList()
)

/**
 * Mirrors the Postgres `run_status` enum on prod
 * (planned / in_progress / completed / cancelled).  The previous
 * scheduled/en_route/partially_completed labels were aspirational —
 * the DB never used them.  When the dispatch endpoint returned
 * `"status": "planned"` kotlinx.serialization couldn't decode it,
 * the entire DispatchBoardResponse parse threw, and the iOS board
 * silently rendered as empty.
 */
@Serializable
enum class RunStatus {
    @SerialName("planned") PLANNED,
    @SerialName("in_progress") IN_PROGRESS,
    @SerialName("completed") COMPLETED,
    @SerialName("cancelled") CANCELLED
}

@Serializable
data class RunStopDto(
    @SerialName("id") val id: String,
    @SerialName("run_id") val runId: String,
    @SerialName("parcel_id") val parcelId: String,
    @SerialName("sequence") val sequence: Int,
    @SerialName("recipient_name") val recipientName: String? = null,
    @SerialName("recipient_phone") val recipientPhone: String? = null,
    @SerialName("address_line") val addressLine: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("status") val status: StopStatus = StopStatus.PENDING
)

@Serializable
enum class StopStatus {
    @SerialName("pending") PENDING,
    @SerialName("arrived") ARRIVED,
    @SerialName("delivered") DELIVERED,
    @SerialName("failed") FAILED,
    @SerialName("rescheduled") RESCHEDULED
}
