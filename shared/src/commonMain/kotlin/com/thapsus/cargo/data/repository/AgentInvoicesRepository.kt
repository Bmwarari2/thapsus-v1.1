package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.AgentInvoiceDto
import com.thapsus.cargo.data.dto.AgentInvoiceListResponse
import com.thapsus.cargo.data.dto.CreateAgentInvoiceRequest
import com.thapsus.cargo.data.dto.CreateAgentInvoiceResponse
import com.thapsus.cargo.data.remote.ThapsusApiClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PatchAgentInvoiceRequest(val status: String, val notes: String? = null)

@Serializable
internal data class GenericAck(val success: Boolean = true)

@Serializable
internal data class UploadUrlRequest(val filename: String? = null)

/**
 * Public — exposed through `requestUploadUrl()` so iOS / Android can PUT the
 * PDF bytes to the returned signed URL. The `path` round-trips back as the
 * `doc_url` we POST to `/agent-invoices` so the server records the
 * canonical in-bucket path (not a public URL).
 */
@Serializable
data class AgentInvoiceUploadUrl(
    val success: Boolean = true,
    val bucket: String,
    val path: String,
    @SerialName("signed_url") val signedUrl: String,
    val token: String? = null
)

@Serializable
data class AgentInvoiceDocumentUrl(
    val success: Boolean = true,
    @SerialName("signed_url") val signedUrl: String,
    @SerialName("expires_in_seconds") val expiresInSeconds: Int? = null
)

class AgentInvoicesRepository(private val api: ThapsusApiClient) {

    suspend fun listMine(): Result<List<AgentInvoiceDto>> = runCatching {
        api.get<AgentInvoiceListResponse>("/agent-invoices/mine").invoices
    }

    suspend fun listAll(status: String? = null): Result<List<AgentInvoiceDto>> = runCatching {
        val q = status?.let { "?status=$it" } ?: ""
        api.get<AgentInvoiceListResponse>("/agent-invoices$q").invoices
    }

    suspend fun submit(
        consolidationId: String?,
        invoiceNo: String?,
        amountKes: Double,
        docUrl: String? = null,
        notes: String? = null
    ): Result<String?> = runCatching {
        api.post<CreateAgentInvoiceResponse, CreateAgentInvoiceRequest>(
            "/agent-invoices",
            CreateAgentInvoiceRequest(
                consolidationId = consolidationId,
                invoiceNo = invoiceNo,
                amountKes = amountKes,
                docUrl = docUrl,
                notes = notes
            )
        ).invoiceId
    }

    suspend fun updateStatus(invoiceId: String, status: String, notes: String? = null): Result<Unit> = runCatching {
        api.patch<GenericAck, PatchAgentInvoiceRequest>(
            "/agent-invoices/$invoiceId",
            PatchAgentInvoiceRequest(status = status, notes = notes)
        )
    }

    /**
     * Asks the server to mint a signed-upload URL into the `agent-invoices`
     * bucket. iOS / Android then PUT the PDF bytes to `signedUrl` directly
     * (no Supabase JWT required at the upload site itself); the returned
     * `path` is what the client passes back as `doc_url` when calling
     * [submit].
     *
     * Replaces the previous direct-Storage upload that 403'd against the
     * 2026-04-30 RLS lockdown — see memory: agent_invoice_pdf_upload_debug.
     * Server endpoint: POST /api/agent-invoices/upload-url (PR #35).
     */
    /**
     * Returns the bare DTO and throws on failure. SKIE bridges `Result<T>` as
     * `Any?` on the Swift side, which doesn't support typed member access —
     * see memory `repos_branches.md` for the same pattern on
     * `NpsRepository.fetchPending` / `PackageRepository.fetchCustomer`. iOS
     * calls this with `try await` and catches inline.
     */
    suspend fun requestUploadUrl(filename: String? = null): AgentInvoiceUploadUrl =
        api.post<AgentInvoiceUploadUrl, UploadUrlRequest>(
            "/agent-invoices/upload-url",
            UploadUrlRequest(filename = filename)
        )

    /**
     * Server-issued 5-minute signed URL for downloading an invoice's PDF.
     * The bucket is private as of migration 005; rendering a stored
     * `doc_url` directly will 400. Owning agent or admin/operator only.
     */
    suspend fun requestDocumentUrl(invoiceId: String): AgentInvoiceDocumentUrl =
        api.get<AgentInvoiceDocumentUrl>("/agent-invoices/$invoiceId/document-url")
}
