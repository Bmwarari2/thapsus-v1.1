package com.thapsus.cargo.data.dto

/**
 * UI-facing snapshot of a terminally-failed outbox mutation. Populated
 * from the `OutboxFailureEntity` SQLDelight table when the worker hits a
 * 4xx response from the server (audit M2).
 *
 * Not serialised over the wire — the outbox is a purely client-side
 * concept. Lives in the DTO package so view models / Swift bridge code
 * doesn't have to depend on the SQLDelight-generated entity type.
 */
data class OutboxFailureDto(
    /** Same id as the original PendingMutation row. Stable across retries. */
    val mutationId: String,
    /** "pod_event", "customs_entry", etc. — see LastMileRepository. */
    val kind: String,
    /** Encoded payload, kept verbatim so a retry can re-enqueue without rebuilding the DTO. */
    val payloadJson: String,
    /** PostgREST table the worker was trying to hit. */
    val targetTable: String,
    /** Parcel id / event id for join-friendly UI lookups. */
    val targetId: String?,
    /** HTTP status returned by the server when the mutation was rejected. */
    val errorStatus: Int,
    /** User-facing message from the server's error envelope, if any. */
    val errorMessage: String?,
    /** Epoch millis the failure was recorded. */
    val failedAtEpochMs: Long
)
