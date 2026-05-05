package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * POD event audit row. As of the audit B1 follow-up the bucket is private —
 * the client persists the in-bucket *path* and the server / admin views mint
 * 5-minute signed download URLs on demand from
 * `POST /api/last-mile/pod/document-url`.
 *
 * The legacy `photo_url` / `signature_url` fields are kept on the wire for
 * backwards-compat during rollout (older clients / Android still send them
 * and the server still accepts them). New uploads SHOULD set `photo_path` /
 * `signature_path` instead — the server prefers the path on read and falls
 * back to extracting it from the URL when a row only has the legacy URL
 * shape.
 */
@Serializable
data class PodEventDto(
    @SerialName("id") val id: String,
    /** Representative parcel id (legacy + index for the outbox). When the
     *  capture covers multiple parcels (Phase 3 user-grouping), `parcelIds`
     *  carries the full list and this field holds the first element so
     *  older outbox-flush paths keep working. */
    @SerialName("parcel_id") val parcelId: String,
    /** Phase 3 — full set of parcel ids covered by this capture. Empty
     *  list means singleton (use `parcelId`). The flushOutbox path forwards
     *  whichever shape the server prefers. */
    @SerialName("parcel_ids") val parcelIds: List<String> = emptyList(),
    @SerialName("run_id") val runId: String,
    @SerialName("captured_at") val capturedAt: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("signature_url") val signatureUrl: String? = null,
    @SerialName("photo_path") val photoPath: String? = null,
    @SerialName("signature_path") val signaturePath: String? = null,
    @SerialName("otp_used") val otpUsed: String? = null,
    @SerialName("recipient_name") val recipientName: String? = null,
    @SerialName("recipient_phone") val recipientPhone: String? = null,
    @SerialName("result") val result: String = "delivered",
    @SerialName("rider_id") val riderId: String? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("captured_by") val capturedBy: String
)

/**
 * Body for `POST /api/last-mile/rider/runs/:runId/pod`. The server inserts the
 * `pod_events` row and flips `orders.status='delivered'`.
 *
 * iOS now sends bucket *paths* instead of public URLs (audit B1). The server
 * still accepts the legacy URL fields during rollout — Android may still send
 * them while the cross-platform fix lands.
 */
@Serializable
data class PodSubmitRequest(
    /** Primary / legacy field — singleton parcel id. Server still honours this
     *  for older clients. New iOS PodCapture sends `parcelIds` instead. */
    @SerialName("parcel_id") val parcelId: String,
    /** Phase 3 — when the rider's stop covers multiple parcels for one
     *  recipient, the iOS PodCaptureView batches them into a single capture
     *  and POSTs the full id list. Server prefers this when present and
     *  falls back to `parcelId` otherwise. Empty list = singleton. */
    @SerialName("parcel_ids") val parcelIds: List<String> = emptyList(),
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("signature_url") val signatureUrl: String? = null,
    @SerialName("photo_path") val photoPath: String? = null,
    @SerialName("signature_path") val signaturePath: String? = null,
    @SerialName("otp_used") val otpUsed: String? = null,
    @SerialName("recipient_name") val recipientName: String? = null,
    @SerialName("recipient_phone") val recipientPhone: String? = null,
    @SerialName("notes") val notes: String? = null
)

@Serializable
data class PodSubmitResponse(
    val success: Boolean,
    @SerialName("pod_id") val podId: String? = null,
    val fails: Int? = null,
    val message: String? = null
)

/** Body for `POST /api/last-mile/rider/runs/:runId/fail`. */
@Serializable
data class PodFailRequest(
    @SerialName("parcel_id") val parcelId: String,
    @SerialName("reason") val reason: String? = null
)

/**
 * Body for `POST /api/last-mile/runs` — operator dispatches a new rider run.
 * Replaces the previous PostgREST `INSERT INTO last_mile_runs` path which the
 * 2026-04-30 RLS lockdown silently 403'd. See audit §3.1 / S0-6.
 */
@Serializable
data class CreateRunRequest(
    @SerialName("rider_id") val riderId: String? = null,
    val zone: String,
    @SerialName("run_date") val runDate: String,
    @SerialName("parcel_ids") val parcelIds: List<String> = emptyList()
)

@Serializable
data class CreateRunResponse(
    val success: Boolean,
    @SerialName("run_id") val runId: String? = null,
    val message: String? = null
)

/**
 * Response shape for `GET /api/last-mile/runs/:id/parcels` (S1-9). The
 * server reads the parcel set from the last_mile_run_parcels join table
 * (migration 012) ordered by `position`, joining to orders + users so
 * the dispatch UI can render addresses without a second roundtrip.
 */
@Serializable
data class RunParcelsResponse(
    val success: Boolean = true,
    val run: LastMileRunDto? = null,
    val parcels: List<DispatchParcelRow> = emptyList()
)

/**
 * Response shape for `GET /api/last-mile/dispatch` — operator dispatch
 * board.  `pending` is the list of parcels that are dispatch-ready
 * (customs-cleared and not yet on any active run); `runs` is every
 * planned/in_progress run with its rider name; `zones` is the static
 * Nairobi zone catalogue used by the run-builder picker.
 */
@Serializable
data class DispatchBoardResponse(
    val success: Boolean = true,
    val pending: List<DispatchParcelRow> = emptyList(),
    val runs: List<LastMileRunDto> = emptyList(),
    val zones: List<String> = emptyList()
)

/**
 * Request body for `POST /api/last-mile/pod/upload-url`.  `kind` is either
 * "photo" (the JPEG capture) or "signature" (the PNG pad output).
 */
@Serializable
data class PodUploadUrlRequest(
    @SerialName("parcel_id") val parcelId: String,
    val kind: String
)

/**
 * Response from `POST /api/last-mile/pod/upload-url` — a 5-minute
 * signed upload URL into the private `pods` Storage bucket. The client PUTs
 * the bytes to `signedUrl` and persists `path` on the PodEventDto. Admin /
 * staff render the asset by exchanging the path for a fresh signed download
 * URL via `POST /api/last-mile/pod/document-url` (audit B1).
 *
 * `publicUrl` was removed in the B1 follow-up: the bucket is private and
 * stale public URLs were leaking into pod_events causing broken admin
 * thumbnails.
 */
@Serializable
data class PodUploadUrlResponse(
    val success: Boolean = true,
    val bucket: String? = null,
    val path: String? = null,
    @SerialName("signed_url") val signedUrl: String? = null,
    val token: String? = null
)

/**
 * Body for `POST /api/last-mile/pod/document-url` (audit B1). The client
 * holds the in-bucket *path* recorded on the PodEventDto and asks the server
 * for a short-lived signed download URL on demand — same shape as the
 * agent-invoice document-url endpoint. `kind` is "photo" or "signature";
 * the server uses it only for diagnostic logging since the path is
 * canonical.
 */
@Serializable
data class PodDocumentUrlRequest(
    @SerialName("parcel_id") val parcelId: String,
    val kind: String,
    val path: String
)

@Serializable
data class PodDocumentUrlResponse(
    val success: Boolean = true,
    @SerialName("signed_url") val signedUrl: String,
    @SerialName("expires_at") val expiresAt: String? = null
)

@Serializable
data class DispatchParcelRow(
    val id: String,
    @SerialName("tracking_number") val trackingNumber: String? = null,
    val description: String? = null,
    val status: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val name: String? = null,
    val phone: String? = null,
    @SerialName("delivery_address") val deliveryAddress: String? = null,
    /** Per-parcel drop-point override set on PATCH /runs/:id/parcels.
     *  Falls back to the user's delivery_address when null. */
    @SerialName("delivery_address_override") val deliveryAddressOverride: String? = null,
    /** Stop order within the run; lower is earlier. */
    val position: Int? = null,
    @SerialName("has_pod") val hasPod: Boolean = false
) {
    /** Convenience: the address the rider should actually navigate to. */
    val effectiveAddress: String?
        get() = deliveryAddressOverride?.takeIf { it.isNotBlank() } ?: deliveryAddress
}

/**
 * Minimal rider row for the dispatch picker. Sourced from
 * `GET /api/last-mile/riders` — operator-callable, narrow projection.
 */
@Serializable
data class RiderDto(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null
)

@Serializable
internal data class RiderListResponse(
    val success: Boolean = true,
    val riders: List<RiderDto> = emptyList()
)

/** Body for `PATCH /api/last-mile/runs/:id` — operator updates run fields. */
@Serializable
data class UpdateRunRequest(
    @SerialName("rider_id") val riderId: String? = null,
    val zone: String? = null,
    @SerialName("run_date") val runDate: String? = null,
    val status: String? = null,
    val notes: String? = null
)
