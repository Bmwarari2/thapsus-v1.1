package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.ReferralHistoryResponse
import com.thapsus.cargo.data.dto.ReferralSummaryResponse
import com.thapsus.cargo.data.remote.ThapsusApiClient

class ReferralsRepository(private val api: ThapsusApiClient) {

    suspend fun summary(): Result<ReferralSummaryResponse> = runCatching {
        api.get<ReferralSummaryResponse>("/referral")
    }

    suspend fun history(): Result<ReferralHistoryResponse> = runCatching {
        api.get<ReferralHistoryResponse>("/referral/history")
    }
}
