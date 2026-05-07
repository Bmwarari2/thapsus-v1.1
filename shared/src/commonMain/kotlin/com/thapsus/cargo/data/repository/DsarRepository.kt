package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.CreateDsarRequest
import com.thapsus.cargo.data.dto.DsarCreateResponse
import com.thapsus.cargo.data.dto.DsarExportResponse
import com.thapsus.cargo.data.dto.DsarListResponse
import com.thapsus.cargo.data.dto.DsarQueueResponse
import com.thapsus.cargo.data.dto.DsarRequestDto
import com.thapsus.cargo.data.dto.GenericAckResponse
import com.thapsus.cargo.data.dto.UpdateDsarRequest
import com.thapsus.cargo.data.remote.ThapsusApiClient

class DsarRepository(private val api: ThapsusApiClient) {

    suspend fun listMine(): Result<List<DsarRequestDto>> = runCatching {
        api.get<DsarListResponse>("/dsar/me").requests
    }

    suspend fun create(type: String, notes: String? = null): Result<DsarRequestDto> = runCatching {
        val resp = api.post<DsarCreateResponse, CreateDsarRequest>(
            "/dsar",
            CreateDsarRequest(type = type, notes = notes)
        )
        resp.request ?: error("DSAR create: missing request in response")
    }

    /** Admin DSAR queue (Spec §4.11). Returns every open request joined to user. */
    suspend fun queue(): List<DsarRequestDto> =
        api.get<DsarQueueResponse>("/dsar/queue").requests

    /** Admin marks a request fulfilled or rejected. */
    suspend fun updateStatus(id: String, status: String, notes: String? = null) {
        api.patch<GenericAckResponse, UpdateDsarRequest>(
            "/dsar/$id",
            UpdateDsarRequest(status = status, notes = notes)
        )
    }

    /**
     * Admin triggers the export — the server sanitises the user's data,
     * emails it to them as a JSON attachment, and marks the request fulfilled.
     * Returns the human-readable confirmation the server produced (e.g.
     * "Export emailed to alice@example.com.").
     */
    suspend fun export(id: String): String {
        val resp = api.post<DsarExportResponse, Unit>("/dsar/$id/export", Unit)
        return resp.message
            ?: resp.exportUrl
            ?: "Export emailed to the customer."
    }
}
