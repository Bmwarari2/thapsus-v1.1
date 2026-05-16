package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.AccountDeletionExportResponse
import com.thapsus.cargo.data.dto.AccountDeletionRequestDto
import com.thapsus.cargo.data.dto.AccountDeletionRequestEnvelope
import com.thapsus.cargo.data.dto.CancelAccountDeletionBody
import com.thapsus.cargo.data.remote.ThapsusApiClient

/**
 * Customer-facing account-deletion repository. Backs the
 * `account_deletion_requests` table via the four endpoints under
 * `/api/account/deletion-request`. The server enforces the 14-day
 * cooldown and the "one active request per user" rule; the client
 * just mirrors the surface.
 */
class AccountDeletionRepository(private val api: ThapsusApiClient) {

    /** Current active or most-recent request, or null if there has never been one. */
    suspend fun current(): Result<AccountDeletionRequestDto?> = runCatching {
        api.get<AccountDeletionRequestEnvelope>("/account/deletion-request").request
    }

    /** Start the 14-day cooldown. Server generates + emails the HTML export. */
    suspend fun start(): Result<AccountDeletionRequestDto> = runCatching {
        val resp = api.post<AccountDeletionRequestEnvelope, Unit>(
            "/account/deletion-request",
            Unit
        )
        resp.request ?: error(resp.message ?: "Server didn't return a request.")
    }

    /** Cancel the active request. Reason is optional but recorded for audit. */
    suspend fun cancel(reason: String? = null): Result<AccountDeletionRequestDto> = runCatching {
        val resp = api.delete<AccountDeletionRequestEnvelope, CancelAccountDeletionBody>(
            "/account/deletion-request",
            CancelAccountDeletionBody(reason = reason)
        )
        resp.request ?: error(resp.message ?: "Cancel didn't return the updated request.")
    }

    /** Re-mint the signed download URL for the existing HTML export. */
    suspend fun refreshExportUrl(): Result<String> = runCatching {
        val resp = api.get<AccountDeletionExportResponse>("/account/deletion-request/export")
        resp.exportSignedUrl ?: error("Server didn't return a signed URL.")
    }
}
