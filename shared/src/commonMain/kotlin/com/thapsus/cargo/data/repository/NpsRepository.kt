package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.NpsPendingResponse
import com.thapsus.cargo.data.dto.NpsSubmitRequest
import com.thapsus.cargo.data.dto.NpsSubmitResponse
import com.thapsus.cargo.data.remote.ThapsusApiClient

/**
 * Net Promoter Score capture. Triggered post-delivery via the in-app survey
 * modal, recorded server-side in `nps_responses`. Read-side (`/nps/summary`)
 * is admin-only and routed through KPI repos.
 */
class NpsRepository(private val api: ThapsusApiClient) {
    suspend fun submit(score: Int, comment: String? = null, parcelId: String? = null): Result<Unit> =
        runCatching {
            api.post<NpsSubmitResponse, NpsSubmitRequest>(
                "/nps",
                NpsSubmitRequest(score = score, comment = comment, parcelId = parcelId)
            )
        }

    /**
     * Returns the order ids the server has flagged for survey but the user
     * hasn't responded to yet. iOS uses this to decide which `delivered`
     * parcels in the local cache should pop the survey sheet — matches
     * cross-device because state lives on the server, not in UserDefaults.
     *
     * Returns an empty set if the call fails so the iOS modifier degrades
     * silently to local-only behaviour rather than blocking the dashboard.
     */
    suspend fun fetchPending(): Set<String> = try {
        val resp = api.get<NpsPendingResponse>("/nps/pending")
        resp.pending.map { it.orderId }.toSet()
    } catch (_: Throwable) {
        emptySet()
    }
}
