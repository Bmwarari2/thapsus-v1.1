package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.ClaimRequest
import com.thapsus.cargo.data.dto.ClaimResponse
import com.thapsus.cargo.data.dto.InsurancePolicyApiDto
import com.thapsus.cargo.data.dto.InsuranceQuoteDto
import com.thapsus.cargo.data.dto.InsuranceQuoteRequest
import com.thapsus.cargo.data.dto.InsuranceQuoteResponse
import com.thapsus.cargo.data.dto.IssuePolicyRequest
import com.thapsus.cargo.data.dto.IssuePolicyResponse
import com.thapsus.cargo.data.dto.PolicyListResponse
import com.thapsus.cargo.data.dto.PolicyRowDto
import com.thapsus.cargo.data.remote.ThapsusApiClient

class InsuranceRepository(private val api: ThapsusApiClient) {

    suspend fun quote(tier: String, declaredValueGbp: Double): Result<InsuranceQuoteDto> = runCatching {
        api.post<InsuranceQuoteResponse, InsuranceQuoteRequest>(
            "/insurance/quote",
            InsuranceQuoteRequest(tier = tier, declaredValueGbp = declaredValueGbp)
        ).quote
    }

    suspend fun issue(parcelId: String, tier: String, declaredValueGbp: Double):
        Result<InsurancePolicyApiDto> = runCatching {
        api.post<IssuePolicyResponse, IssuePolicyRequest>(
            "/insurance/policies",
            IssuePolicyRequest(
                parcelId = parcelId,
                tier = tier,
                declaredValueGbp = declaredValueGbp
            )
        ).policy ?: error("Issue policy: missing policy in response")
    }

    suspend fun listPolicies(): Result<List<PolicyRowDto>> = runCatching {
        api.get<PolicyListResponse>("/insurance/policies").policies
    }

    suspend fun claim(policyId: String, amountGbp: Double, notes: String? = null): Result<Unit> = runCatching {
        api.post<ClaimResponse, ClaimRequest>(
            "/insurance/policies/$policyId/claim",
            ClaimRequest(claimAmountGbp = amountGbp, notes = notes)
        )
    }
}
