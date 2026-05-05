package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.CreateRunRequest
import com.thapsus.cargo.data.dto.CreateRunResponse
import com.thapsus.cargo.data.dto.DispatchBoardResponse
import com.thapsus.cargo.data.dto.GenericAckResponse
import com.thapsus.cargo.data.dto.LastMileRunDto
import com.thapsus.cargo.data.dto.PodDocumentUrlRequest
import com.thapsus.cargo.data.dto.PodDocumentUrlResponse
import com.thapsus.cargo.data.dto.PodEventDto
import com.thapsus.cargo.data.dto.PodFailRequest
import com.thapsus.cargo.data.dto.PodSubmitRequest
import com.thapsus.cargo.data.dto.PodSubmitResponse
import com.thapsus.cargo.data.dto.PodUploadUrlRequest
import com.thapsus.cargo.data.dto.PodUploadUrlResponse
import com.thapsus.cargo.data.dto.RiderDto
import com.thapsus.cargo.data.dto.RiderListResponse
import com.thapsus.cargo.data.dto.RiderTodayResponse
import com.thapsus.cargo.data.dto.RunParcelsResponse
import com.thapsus.cargo.data.dto.UpdateRunRequest
import com.thapsus.cargo.data.local.ThapsusLocalCache
import com.thapsus.cargo.data.local.ThapsusLocalCache.Companion.toDto
import com.thapsus.cargo.data.remote.ApiException
import com.thapsus.cargo.data.remote.Tables
import com.thapsus.cargo.data.remote.ThapsusApiClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Rider-facing repository. Mutations always land in the outbox first so a
 * dropped 4G signal in Karen or Eastlands cannot lose a POD capture. Flush
 * pushes through Express (`/api/last-mile/rider/runs/:runId/pod` and `/fail`)
 * so the server can also flip `orders.status='delivered'`, increment run
 * progress, and apply the two-fails-then-hold rule.
 */
class LastMileRepository(
    private val supabase: SupabaseClient,
    private val cache: ThapsusLocalCache,
    private val api: ThapsusApiClient,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    fun observeRunsForRider(riderId: String): Flow<List<LastMileRunDto>> =
        cache.observeRunsForRider(riderId).map { rows -> rows.map { it.toDto() } }

    /**
     * Pulls the rider's runs for today via Express
     * (`GET /api/last-mile/rider/today`) and refreshes the local cache.
     *
     * The previous Supabase direct-read path (`supabase.from(LAST_MILE_RUNS)
     * .select()`) was silently 403'd by the 2026-04-30 RLS lockdown — there
     * is no SELECT policy for the authenticated role on `last_mile_runs`
     * and won't be.  The Express endpoint is the rider's authoritative
     * source: it scopes by `rider_id = req.user.id` and embeds the stop
     * list per run.
     *
     * `runDate` is currently unused on the server (it always returns
     * CURRENT_DATE for the authed rider), but kept on the signature so
     * callers don't have to change.
     */
    suspend fun refreshTodayForRider(
        @Suppress("UNUSED_PARAMETER") riderId: String,
        @Suppress("UNUSED_PARAMETER") runDate: String
    ): Result<List<LastMileRunDto>> = runCatching {
        val now = Clock.System.now().toEpochMilliseconds()
        val resp: RiderTodayResponse = api.get<RiderTodayResponse>("/last-mile/rider/today")
        val rows = resp.runs
        rows.forEach { cache.upsertRun(it, now) }
        rows
    }

    /**
     * Operator/admin/assigned-rider view of which parcels are scheduled for
     * a particular run (server endpoint added in PR #36 — paired S1-9).
     * Returns the run plus the list of parcel rows joined to user contact
     * info so the iOS dispatch UI can render addresses without an extra hop.
     *
     * Returns the bare DTO and throws on failure — SKIE bridges
     * `Result<T>` as `Any?` on the Swift side, see memory
     * `repos_branches.md`.
     */
    suspend fun fetchRunParcels(runId: String): RunParcelsResponse =
        api.get<RunParcelsResponse>("/last-mile/runs/$runId/parcels")

    /**
     * Operator dispatch board — single GET that returns dispatch-ready
     * parcels, every active run with its rider name, and the static zone
     * catalogue. Backed by `GET /api/last-mile/dispatch`.
     *
     * Replaces the previous "filter local cache for status='released'"
     * path which never matched on prod (the customs / duty-payment flow
     * leaves orders.status='customs' with hold_reason cleared, not
     * packages.status='released').
     */
    suspend fun fetchDispatchBoard(): DispatchBoardResponse =
        api.get<DispatchBoardResponse>("/last-mile/dispatch")

    /**
     * Operator-side: create a new rider run via Express. The server inserts
     * the `last_mile_runs` row, flips the included parcels to
     * `out_for_delivery`, and returns the new run id.
     *
     * Replaces the previous `supabase.from(LAST_MILE_RUNS).insert(...)` path
     * which the 2026-04-30 RLS lockdown silently 403'd (no INSERT policy on
     * `last_mile_runs` for the authenticated role).
     */
    suspend fun createRun(
        riderId: String?,
        zone: String,
        runDate: String,
        parcelIds: List<String>
    ): Result<String> = runCatching {
        val resp: CreateRunResponse = api.post<CreateRunResponse, CreateRunRequest>(
            "/last-mile/runs",
            CreateRunRequest(
                riderId = riderId?.takeIf { it.isNotBlank() },
                zone = zone,
                runDate = runDate,
                parcelIds = parcelIds
            )
        )
        resp.runId ?: error("server returned no run_id")
    }

    /**
     * Operator/admin partial update on an existing run — primarily used to
     * (re)assign a rider after the run was created. Routes through Express
     * (`PATCH /api/last-mile/runs/:id`); the server vets the field list.
     */
    suspend fun updateRun(runId: String, body: UpdateRunRequest): Result<Unit> = runCatching {
        api.patch<GenericAckResponse, UpdateRunRequest>("/last-mile/runs/$runId", body)
        Unit
    }

    /** Convenience: assign or reassign a rider on an existing run. */
    suspend fun assignRider(runId: String, riderId: String?): Result<Unit> =
        updateRun(runId, UpdateRunRequest(riderId = riderId))

    /**
     * Convenience: flip a planned run to in_progress.  Server-side this
     * triggers activateRunDispatch — orders/packages flip to
     * out_for_delivery, OTPs are minted, recipient notifications go out.
     * Only valid on a run that already has a rider assigned (the server
     * skips the activation otherwise).
     */
    suspend fun startRun(runId: String): Result<Unit> =
        updateRun(runId, UpdateRunRequest(status = "in_progress"))

    /**
     * Mints a signed-upload URL for a POD photo or signature.  The native
     * iOS / Android client then PUTs the bytes directly to `signedUrl` via
     * URLSession / OkHttp — bypassing the K/N ByteArray bridge that froze
     * the app on multi-MB JPEGs (see audit D19, agent_invoice_pdf_upload
     * memory).
     *
     * `kind` must be "photo" or "signature".
     *
     * Returns the bare DTO and throws on failure (SKIE-friendly).
     */
    suspend fun requestPodUploadUrl(parcelId: String, kind: String): PodUploadUrlResponse =
        api.post<PodUploadUrlResponse, PodUploadUrlRequest>(
            "/last-mile/pod/upload-url",
            PodUploadUrlRequest(parcelId = parcelId, kind = kind)
        )

    /**
     * Mints a 5-minute signed *download* URL for a previously-uploaded POD
     * asset (photo or signature). The bucket is private as of the B1
     * follow-up, so a stored bucket path can't be opened directly — admin /
     * operator views call this to render the asset and the URL expires
     * shortly after.
     *
     * Server endpoint: `POST /api/last-mile/pod/document-url`. Returns the
     * bare DTO and throws on failure (SKIE-friendly).
     */
    suspend fun requestPodDocumentUrl(
        parcelId: String,
        kind: String,
        path: String
    ): PodDocumentUrlResponse =
        api.post<PodDocumentUrlResponse, PodDocumentUrlRequest>(
            "/last-mile/pod/document-url",
            PodDocumentUrlRequest(parcelId = parcelId, kind = kind, path = path)
        )

    /**
     * Operator+admin rider list, sourced from `GET /api/last-mile/riders`
     * (added 2026-04-30). Falls back to an empty list on failure so the
     * dispatch picker can still render a "no riders found" hint.
     */
    suspend fun fetchRiders(): List<RiderDto> = try {
        api.get<RiderListResponse>("/last-mile/riders").riders
    } catch (_: Throwable) {
        emptyList()
    }

    /**
     * Capture a POD photo + signature + OTP. Writes the event to the outbox first
     * (offline-safe) and lets the worker push it to Supabase when connectivity
     * returns.
     */
    suspend fun capturePod(event: PodEventDto): Result<Unit> = runCatching {
        val now = Clock.System.now().toEpochMilliseconds()
        cache.enqueueMutation(
            id = event.id,
            kind = "pod_event",
            payloadJson = json.encodeToString(event),
            targetTable = Tables.POD_EVENTS,
            // Stamp the parcel id (not the event UUID) as target_id so a
            // 4xx-recorded outbox failure can be looked up on the POD
            // capture screen by parcel id. Audit follow-up: previously
            // target_id = event.id (random UUID) which made
            // outboxFailuresForParcel(parcelId) silently return empty.
            targetId = event.parcelId,
            nowMs = now
        )
    }

    /**
     * Worker-side flush: drains the outbox in oldest-first order. POD events
     * post to the Express endpoint so the server runs the side-effects
     * (order status flip, run progress, two-fails-then-hold) — the iOS path
     * never writes `pod_events` directly anymore.
     */
    suspend fun flushOutbox(maxBatch: Int = 20): Int =
        flushRows(cache.dequeueDue(Clock.System.now().toEpochMilliseconds(), maxBatch))

    /**
     * Manual-flush variant invoked from the rider's Outbox screen.
     * Bypasses `next_attempt_at_epoch_ms` so a tap-to-flush always tries
     * every queued row immediately — exponential backoff still applies to
     * background flushes via [flushOutbox]. Without this the outbox felt
     * "stuck" once the backoff hit 64 s.
     */
    suspend fun flushOutboxForce(maxBatch: Int = 20): Int =
        flushRows(cache.dequeueAll(maxBatch))

    private suspend fun flushRows(due: List<com.thapsus.cargo.db.PendingMutationEntity>): Int {
        val now = Clock.System.now().toEpochMilliseconds()
        var sent = 0
        for (m in due) {
            try {
                when (m.kind) {
                    "pod_event" -> {
                        val event = json.decodeFromString<PodEventDto>(m.payload_json)
                        if (event.result == "failed") {
                            api.post<PodSubmitResponse, PodFailRequest>(
                                "/last-mile/rider/runs/${event.runId}/fail",
                                PodFailRequest(parcelId = event.parcelId, reason = event.notes)
                            )
                        } else {
                            api.post<PodSubmitResponse, PodSubmitRequest>(
                                "/last-mile/rider/runs/${event.runId}/pod",
                                PodSubmitRequest(
                                    parcelId = event.parcelId,
                                    // Phase 3 — forward the full parcel-id
                                    // group when the capture covered multiple
                                    // parcels (one POD per recipient across
                                    // their bundle). Server prefers parcelIds
                                    // when non-empty; older clients keep using
                                    // parcelId as the singleton.
                                    parcelIds = event.parcelIds,
                                    // Send paths if the event has them (post-B1
                                    // captures) and URLs only as legacy fallback
                                    // for events queued before the upgrade. The
                                    // server prefers paths.
                                    photoUrl = event.photoUrl,
                                    signatureUrl = event.signatureUrl,
                                    photoPath = event.photoPath,
                                    signaturePath = event.signaturePath,
                                    otpUsed = event.otpUsed,
                                    recipientName = event.recipientName,
                                    recipientPhone = event.recipientPhone,
                                    notes = event.notes
                                )
                            )
                        }
                    }
                    else -> {
                        if (m.attempts >= 5) {
                            cache.removeMutation(m.id)
                            continue
                        }
                    }
                }
                cache.removeMutation(m.id)
                sent++
            } catch (t: Throwable) {
                // 4xx is a terminal rejection — the server actively refused
                // the payload (duplicate id, OTP mismatch, parcel not on
                // this run). Retrying forever burns battery + inflates
                // server logs without fixing the cause; surface it to the
                // UI instead so the rider can intervene (audit M2).
                val status = (t as? ApiException)?.status
                if (status != null && status in 400..499) {
                    cache.recordOutboxFailure(
                        mutationId = m.id,
                        kind = m.kind,
                        payloadJson = m.payload_json,
                        targetTable = m.target_table,
                        targetId = m.target_id,
                        errorStatus = status,
                        errorMessage = t.message,
                        nowMs = now
                    )
                    cache.removeMutation(m.id)
                } else {
                    val backoff = (1L shl m.attempts.toInt().coerceAtMost(6)) * 1_000L
                    cache.bumpRetry(m.id, now + backoff, t.message)
                }
            }
        }
        return sent
    }

    /**
     * Re-enqueue a previously-failed mutation. The user tapped the inline
     * "POD didn't sync — tap to retry" banner on the stop list; we drop the
     * failure row and put the original payload back on the queue so the
     * next outbox flush retries it (audit M2).
     */
    suspend fun retryFailedMutation(mutationId: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        val failed = cache.selectOutboxFailures().firstOrNull { it.mutation_id == mutationId } ?: return
        cache.enqueueMutation(
            id = failed.mutation_id,
            kind = failed.kind,
            payloadJson = failed.payload_json,
            targetTable = failed.target_table,
            targetId = failed.target_id,
            nowMs = now
        )
        cache.removeOutboxFailure(mutationId)
    }

    /** Drop a recorded outbox failure without retrying. */
    fun dismissFailedMutation(mutationId: String) {
        cache.removeOutboxFailure(mutationId)
    }

    fun observeOutboxFailures() =
        cache.observeOutboxFailures()

    /**
     * Snapshot lookup: any terminal outbox failures whose targetId matches
     * the supplied parcel id. Used by PodCaptureView right after
     * `flushOutbox` so the rider sees the actual server rejection
     * ("OTP not issued", "parcel not on this run", "duplicate POD") instead
     * of the generic "Couldn't save POD locally" banner. Audit G2.
     */
    fun outboxFailuresForParcel(parcelId: String): List<com.thapsus.cargo.data.dto.OutboxFailureDto> =
        cache.selectOutboxFailuresForTarget(parcelId).map { e ->
            // Mirror the established `OutboxFailureEntity.toDto()` mapping
            // in ThapsusLocalCache — the SQLDelight column is `mutation_id`,
            // not `id`. The field-rename caused the iOSArm64 compile to
            // fail with "Unresolved reference 'id'."
            com.thapsus.cargo.data.dto.OutboxFailureDto(
                mutationId = e.mutation_id,
                kind = e.kind,
                payloadJson = e.payload_json,
                targetTable = e.target_table,
                targetId = e.target_id,
                errorStatus = e.error_status.toInt(),
                errorMessage = e.error_message,
                failedAtEpochMs = e.failed_at_epoch_ms
            )
        }
}
