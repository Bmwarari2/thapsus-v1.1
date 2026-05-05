package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.AdminPaymentApprovalResponse
import com.thapsus.cargo.data.dto.AdminPaymentApproveRequest
import com.thapsus.cargo.data.dto.AdminPaymentRejectRequest
import com.thapsus.cargo.data.dto.CreatePaymentRequest
import com.thapsus.cargo.data.dto.CreatePaymentResponse
import com.thapsus.cargo.data.dto.CreditLedgerEntryDto
import com.thapsus.cargo.data.dto.CreditLedgerResponse
import com.thapsus.cargo.data.dto.MpesaConfirmationRequest
import com.thapsus.cargo.data.dto.MpesaConfirmationResponse
import com.thapsus.cargo.data.dto.PaymentDetailResponse
import com.thapsus.cargo.data.dto.PaymentDto
import com.thapsus.cargo.data.dto.PaymentListResponse
import com.thapsus.cargo.data.dto.PaymentMethodsResponse
import com.thapsus.cargo.data.dto.StripePublicConfigResponse
import com.thapsus.cargo.data.dto.UserCreditDto
import com.thapsus.cargo.data.remote.ThapsusApiClient

/**
 * Customer + admin payments API. Single source for the new Stripe + M-Pesa
 * flow that replaced the wallet (migration 028 / server PR #61).
 *
 * All methods return Result<…> rather than throwing — Swift call sites
 * cannot bridge Kotlin Result via SKIE (see feedback_skie_result_bridge.md),
 * so the iOS surfaces should branch on the underlying Throwable / value
 * inside the VM, not in the View.
 */
class PaymentsRepository(
    private val api: ThapsusApiClient
) {

    /** Stripe publishable key + Apple Pay flag for the iOS PaymentSheet. */
    suspend fun stripeConfig(): Result<StripePublicConfigResponse> = runCatching {
        api.get<StripePublicConfigResponse>("/payments/config/stripe")
    }

    /**
     * GET /api/payments/methods — full payment-method matrix (PR F).
     * Tells the client which buttons to render based on per-environment
     * env-var kill-switches (PAYMENT_METHOD_STRIPE_ENABLED, etc.).
     * Falls back to a sensible default matrix on failure so the modal
     * never opens empty.
     */
    suspend fun methods(): Result<PaymentMethodsResponse> = runCatching {
        api.get<PaymentMethodsResponse>("/payments/methods")
    }

    /** Customer's running KES credit balance. */
    suspend fun myCredit(): Result<UserCreditDto> = runCatching {
        api.get<UserCreditDto>("/payments/me/credit")
    }

    /**
     * Create a payment row + Stripe PaymentIntent (or M-Pesa instructions).
     *  - method = "stripe"  → response.next.clientSecret powers PaymentSheet
     *  - method = "mpesa"   → response.next.paybill / account / amount_due_kes
     *                         Customer pays via M-Pesa, then calls
     *                         submitMpesaConfirmation() with the SMS text.
     */
    suspend fun create(
        targetKind: String,
        targetId: String,
        method: String,
        applyCredit: Boolean = true
    ): Result<CreatePaymentResponse> = runCatching {
        api.post<CreatePaymentResponse, CreatePaymentRequest>(
            "/payments",
            CreatePaymentRequest(targetKind, targetId, method, applyCredit)
        )
    }

    /** Customer pastes the M-Pesa confirmation SMS for an mpesa-method payment. */
    suspend fun submitMpesaConfirmation(
        paymentId: String,
        messageRaw: String
    ): Result<MpesaConfirmationResponse> = runCatching {
        api.post<MpesaConfirmationResponse, MpesaConfirmationRequest>(
            "/payments/$paymentId/mpesa-confirmation",
            MpesaConfirmationRequest(messageRaw)
        )
    }

    suspend fun list(): Result<List<PaymentDto>> = runCatching {
        api.get<PaymentListResponse>("/payments").payments
    }

    /**
     * GET /api/payments?status=…&limit=…&offset=…&group=… (PR 2 + PR 2.1)
     *
     * Customer transactions page uses this with pagination + (optional)
     * status filter. Each row carries a server-enriched `target_label`.
     *
     * Pass `group = "target"` to collapse rows by (target_kind, target_id) —
     * each row then includes `attemptsCount` so the UI can show
     * "+N earlier attempts".
     */
    suspend fun listPaged(
        status: String? = null,
        limit: Int = 20,
        offset: Int = 0,
        group: String? = null
    ): Result<List<PaymentDto>> = runCatching {
        val params = buildList {
            status?.takeIf { it.isNotBlank() }?.let { add("status=$it") }
            add("limit=$limit")
            add("offset=$offset")
            group?.takeIf { it.isNotBlank() }?.let { add("group=$it") }
        }.joinToString("&")
        api.get<PaymentListResponse>("/payments?$params").payments
    }

    /**
     * GET /api/payments/me/credit/ledger?limit=…&offset=… (PR 2)
     *
     * Paginated credit_ledger rows for the auth'd user.
     */
    suspend fun creditLedger(
        limit: Int = 50,
        offset: Int = 0
    ): Result<List<CreditLedgerEntryDto>> = runCatching {
        api.get<CreditLedgerResponse>(
            "/payments/me/credit/ledger?limit=$limit&offset=$offset"
        ).entries
    }

    suspend fun detail(id: String): Result<PaymentDto> = runCatching {
        api.get<PaymentDetailResponse>("/payments/$id").payment
            ?: error("payment missing in detail response")
    }

    // ── Admin-only ─────────────────────────────────────────────────────────

    suspend fun pendingMpesaQueue(): Result<List<PaymentDto>> = runCatching {
        api.get<PaymentListResponse>("/admin/payments/pending").payments
    }

    /**
     * Approve an M-Pesa payment.
     *
     * Pass [overrideReason] (>=10 chars) when the customer-claimed amount
     * is short of the invoice — server returns 409 `error: 'amount_mismatch'`
     * without it. Clean approvals call with `overrideReason = null`
     * (audit P1.2).
     */
    suspend fun approve(
        id: String,
        overrideReason: String? = null
    ): Result<AdminPaymentApprovalResponse> = runCatching {
        api.post<AdminPaymentApprovalResponse, AdminPaymentApproveRequest>(
            "/admin/payments/$id/approve",
            AdminPaymentApproveRequest(overrideReason)
        )
    }

    suspend fun reject(id: String, reason: String): Result<AdminPaymentApprovalResponse> = runCatching {
        api.post<AdminPaymentApprovalResponse, AdminPaymentRejectRequest>(
            "/admin/payments/$id/reject",
            AdminPaymentRejectRequest(reason)
        )
    }
}
