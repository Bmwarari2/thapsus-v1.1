package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.ParcelUploadUrlRequest
import com.thapsus.cargo.data.dto.ParcelUploadUrlResponse
import com.thapsus.cargo.data.remote.ThapsusApiClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.util.decodeBase64Bytes
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Wraps Supabase Storage uploads. Photos for POD events go to the `pods` bucket;
 * parcel-condition photos at intake go to `parcels`. Buckets must already exist
 * (provisioned via migration) and have RLS that allows authenticated uploads.
 *
 * Functions return the public URL on success and throw on failure — Swift
 * callers use `try await uploadPodPhoto(...)`. We deliberately do not wrap in
 * Kotlin `Result` because SKIE 0.10 does not bridge it cleanly to Swift.
 */
@Serializable
internal data class DeleteAgentInvoiceAssetRequest(val path: String)

@Serializable
internal data class DeleteAgentInvoiceAssetResponse(val success: Boolean = true)

class StorageRepository(
    private val supabase: SupabaseClient,
    private val api: ThapsusApiClient
) {

    /**
     * Wraps every throwable in RuntimeException so SKIE bridges them to
     * Swift's `async throws`.  Without this wrapper, supabase-kt's typed
     * exceptions (RLS denied, bucket missing, oversized payload) bubble up
     * to the K/N coroutine root and terminate the app via
     * `Kotlin_processUnhandledException` — the rider sees a frozen camera
     * sheet.  Same pattern memory `agent_invoice_pdf_upload_debug.md`
     * documents for uploadAgentInvoiceDoc.
     */
    suspend fun uploadPodPhoto(parcelId: String, bytes: ByteArray): String = try {
        val ts = Clock.System.now().toEpochMilliseconds()
        val path = "pod/$parcelId/$ts.jpg"
        val bucket = supabase.storage.from(POD_BUCKET)
        bucket.upload(path, bytes) { upsert = true }
        bucket.publicUrl(path)
    } catch (t: Throwable) {
        val why = t.message?.takeIf { it.isNotBlank() } ?: t::class.simpleName ?: "unknown"
        throw RuntimeException("POD photo upload failed: $why")
    }

    suspend fun uploadParcelPhoto(parcelId: String, bytes: ByteArray): String = try {
        val ts = Clock.System.now().toEpochMilliseconds()
        val path = "parcels/$parcelId/$ts.jpg"
        val bucket = supabase.storage.from(PARCEL_BUCKET)
        bucket.upload(path, bytes) { upsert = true }
        bucket.publicUrl(path)
    } catch (t: Throwable) {
        val why = t.message?.takeIf { it.isNotBlank() } ?: t::class.simpleName ?: "unknown"
        throw RuntimeException("Parcel photo upload failed: $why")
    }

    suspend fun uploadSignature(parcelId: String, bytes: ByteArray): String = try {
        val ts = Clock.System.now().toEpochMilliseconds()
        val path = "signatures/$parcelId/$ts.png"
        val bucket = supabase.storage.from(POD_BUCKET)
        bucket.upload(path, bytes) { upsert = true }
        bucket.publicUrl(path)
    } catch (t: Throwable) {
        val why = t.message?.takeIf { it.isNotBlank() } ?: t::class.simpleName ?: "unknown"
        throw RuntimeException("Signature upload failed: $why")
    }

    /**
     * Asks the server for a signed-upload URL into the `parcels` bucket so
     * iOS can `URLSession.upload` raw `Data` directly — bypassing the K/N
     * `KotlinByteArray.from(Data)` bridge that froze the app on multi-MB
     * photos (audit D19 / N1).  Returns the bare DTO and throws on failure
     * (SKIE bridges `Result<T>` as `Any?`, so a typed return is friendlier
     * to Swift `try await`).
     *
     * The bucket is public, so `publicUrl` on the response is what gets
     * stamped on `packages.intake_photo_url` after the PUT completes —
     * no companion document-url endpoint needed (unlike pods).
     */
    suspend fun requestParcelUploadUrl(parcelId: String): ParcelUploadUrlResponse =
        api.post<ParcelUploadUrlResponse, ParcelUploadUrlRequest>(
            "/parcels/upload-url",
            ParcelUploadUrlRequest(parcelId = parcelId)
        )

    /**
     * Best-effort delete of a previously-uploaded POD asset. Used by the iOS
     * PodCaptureView when:
     *  - the rider's session has expired between picking the photo and
     *    tapping "Capture POD" (audit T19), or
     *  - the outbox enqueue fails (cache lock, disk full, audit M1).
     *
     * Without it the bytes would sit orphaned in the private `pods` bucket
     * forever.
     *
     * Accepts the in-bucket *path* (canonical post-B1 — the bucket is
     * private so public URLs no longer exist on the wire). Failures
     * (network, RLS, file missing) are swallowed: the caller has already
     * shown an error to the user and a stale orphan is recoverable via a
     * periodic sweep.
     */
    suspend fun deletePodAsset(path: String) {
        val cleaned = path.trim().ifBlank { return }
        val bucket = supabase.storage.from(POD_BUCKET)
        try {
            bucket.delete(cleaned)
        } catch (_: Throwable) {
            // intentionally swallowed — see kdoc
        }
    }

    /**
     * Uploads a clearing-agent invoice PDF and returns the public URL.
     * Bucket policy (migration 022): staff-only INSERT/UPDATE; bucket is
     * public so admin/operator can render the PDF without signing URLs.
     *
     * Wraps every throwable so SKIE bridges them cleanly to Swift's
     * `async throws`. Without this, Supabase storage's typed exceptions
     * (RLS denied, bucket missing, oversized) bubbled up to the K/N
     * coroutine root and terminated the app via
     * `Kotlin_processUnhandledException`.
     */
    suspend fun uploadAgentInvoiceDoc(agentId: String, bytes: ByteArray): String = try {
        val ts = Clock.System.now().toEpochMilliseconds()
        val path = "$agentId/$ts.pdf"
        val bucket = supabase.storage.from(AGENT_INVOICE_BUCKET)
        bucket.upload(path, bytes) {
            upsert = true
            contentType = io.ktor.http.ContentType.Application.Pdf
        }
        bucket.publicUrl(path)
    } catch (t: Throwable) {
        val why = t.message?.takeIf { it.isNotBlank() } ?: t::class.simpleName ?: "unknown"
        throw RuntimeException("Upload failed: $why")
    }

    /**
     * Best-effort delete of a previously-uploaded agent-invoice PDF that
     * never made it into a submitted invoice row (sheet dismissed, submit
     * failed, user picked a different file). Mirrors `deletePodAsset` but
     * targets the private `agent-invoices` bucket (audit M3).
     *
     * Routes through the server (`DELETE /agent-invoices/upload-url`) so
     * the storage admin client does the deletion — the agent's user JWT
     * doesn't have RLS DELETE on the bucket.
     *
     * Failures (network, RLS, file missing) are swallowed: the caller has
     * already moved on and a stale orphan is recoverable via a periodic
     * sweep.
     */
    suspend fun deleteAgentInvoiceAsset(path: String) {
        val cleaned = path.trim().ifBlank { return }
        try {
            api.delete<DeleteAgentInvoiceAssetResponse, DeleteAgentInvoiceAssetRequest>(
                "/agent-invoices/upload-url",
                DeleteAgentInvoiceAssetRequest(path = cleaned)
            )
        } catch (_: Throwable) {
            // intentionally swallowed — see kdoc
        }
    }

    /**
     * Swift-friendly variant — accepts a base64-encoded PDF so the iOS side
     * can hand off the bytes as a single bridged String instead of looping
     * `KotlinByteArray.set` per byte (which freezes the app on multi-MB
     * uploads). Uses ktor's stable base64 decoder rather than the
     * still-experimental `kotlin.io.encoding.Base64`.
     */
    suspend fun uploadAgentInvoiceDocBase64(agentId: String, base64: String): String = try {
        val raw = base64.decodeBase64Bytes()
        uploadAgentInvoiceDoc(agentId, raw)
    } catch (t: Throwable) {
        val why = t.message?.takeIf { it.isNotBlank() } ?: t::class.simpleName ?: "unknown"
        throw RuntimeException("Upload failed: $why")
    }

    companion object {
        const val POD_BUCKET = "pods"
        const val PARCEL_BUCKET = "parcels"
        const val AGENT_INVOICE_BUCKET = "agent-invoices"
    }
}
