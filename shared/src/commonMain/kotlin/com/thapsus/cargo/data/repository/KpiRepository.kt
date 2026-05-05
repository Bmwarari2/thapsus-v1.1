package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.KpiBlock
import com.thapsus.cargo.data.dto.KpiMarketingResponse
import com.thapsus.cargo.data.dto.KpiResponse
import com.thapsus.cargo.data.dto.NpsSummaryBlock
import com.thapsus.cargo.data.dto.NpsSummaryResponse
import com.thapsus.cargo.data.dto.OpsTodayBlock
import com.thapsus.cargo.data.dto.OpsTodayResponse
import com.thapsus.cargo.data.dto.Retention90dDto
import com.thapsus.cargo.data.dto.UtmRowDto
import com.thapsus.cargo.data.remote.ThapsusApiClient

/**
 * Founder + ops dashboards. All endpoints are admin-only on the server (kpi
 * + nps/summary) or operator-only (ops/today). Response shapes are pg-aggregate
 * heavy — every numeric field uses Loose serializers in the DTOs.
 */
class KpiRepository(private val api: ThapsusApiClient) {

    suspend fun summary(): Result<KpiBlock> = runCatching {
        api.get<KpiResponse>("/kpi").kpi
    }

    suspend fun marketing(): Result<Pair<List<UtmRowDto>, Retention90dDto>> = runCatching {
        val resp = api.get<KpiMarketingResponse>("/kpi/marketing")
        resp.utm to resp.retention90d
    }

    suspend fun npsSummary(): Result<NpsSummaryBlock> = runCatching {
        api.get<NpsSummaryResponse>("/nps/summary").summary
    }

    suspend fun opsToday(): Result<OpsTodayBlock> = runCatching {
        api.get<OpsTodayResponse>("/ops/today").today
    }
}
