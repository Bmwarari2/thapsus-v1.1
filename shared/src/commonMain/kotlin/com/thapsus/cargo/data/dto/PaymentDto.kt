package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirror of `payments` (migration 028). One row per "money in" attempt;
 * keyed by (target_kind, target_id) so the same target can have multiple
 * tries (e.g. failed Stripe → retry as M-Pesa).
 */
@Serializable
data class PaymentDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("target_kind") val targetKind: String,           // 'order' | 'consolidation' | 'buy_for_me'
    @SerialName("target_id") val targetId: String,
    @SerialName("amount_gross_kes") val amountGrossKes: Long,
    @SerialName("amount_credit_kes") val amountCreditKes: Long = 0,
    @SerialName("amount_due_kes") val amountDueKes: Long,
    @SerialName("currency") val currency: String = "KES",
    @SerialName("method") val method: String,                    // 'stripe' | 'mpesa'
    @SerialName("status") val status: String = "pending",
    @SerialName("stripe_payment_intent_id") val stripePaymentIntentId: String? = null,
    @SerialName("stripe_amount_pence_gbp") val stripeAmountPenceGbp: Long? = null,
    @SerialName("stripe_fx_rate_kes_gbp")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val stripeFxRateKesGbp: Double? = null,
    @SerialName("mpesa_reference") val mpesaReference: String? = null,
    @SerialName("mpesa_phone") val mpesaPhone: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("paid_at") val paidAt: String? = null,
    /** Operator/admin queue join only — null on customer-facing reads. */
    @SerialName("user_email") val userEmail: String? = null,
    @SerialName("user_name") val userName: String? = null,
    /** Parsed M-Pesa amount the customer claimed; admin compares to amount_due_kes. */
    @SerialName("mpesa_message_amount_kes") val mpesaMessageAmountKes: Long? = null,
    @SerialName("mpesa_message_raw") val mpesaMessageRaw: String? = null,
    /** Server-enriched (PR 2): tracking number / item name / consolidation prefix. */
    @SerialName("target_label") val targetLabel: String? = null,
    /** Server-enriched (PR 2.1, only present when ?group=target): total
     *  payments rows for the same target. UI shows "+N earlier attempts"
     *  when > 1. */
    @SerialName("attempts_count") val attemptsCount: Int? = null
)

/** Body for POST /api/payments. */
@Serializable
data class CreatePaymentRequest(
    @SerialName("target_kind") val targetKind: String,
    @SerialName("target_id") val targetId: String,
    @SerialName("method") val method: String,                    // 'stripe' | 'mpesa'
    @SerialName("apply_credit") val applyCredit: Boolean = true
)

/**
 * Wraps the create-payment response. `next` is method-specific:
 *   stripe → { kind:'stripe', amount_pence_gbp, fx_rate_kes_gbp,
 *              client_secret? (server doesn't echo on subsequent retrieves —
 *              re-issue create if you've lost it) }
 *   mpesa  → { kind:'mpesa', paybill, account, amount_due_kes }
 */
@Serializable
data class CreatePaymentResponse(
    val success: Boolean = true,
    val payment: PaymentDto? = null,
    val next: PaymentNextStep? = null,
    @SerialName("fully_covered_by_credit") val fullyCoveredByCredit: Boolean = false,
    @SerialName("target_paid") val targetPaid: Boolean = false,
    val message: String? = null
)

@Serializable
data class PaymentNextStep(
    val kind: String,                                            // 'stripe' | 'mpesa'
    @SerialName("client_secret") val clientSecret: String? = null,
    @SerialName("amount_pence_gbp") val amountPenceGbp: Long? = null,
    @SerialName("fx_rate_kes_gbp")
    @Serializable(with = NullableLooseDoubleSerializer::class)
    val fxRateKesGbp: Double? = null,
    val paybill: String? = null,
    val account: String? = null,
    @SerialName("amount_due_kes") val amountDueKes: Long? = null
)

@Serializable
data class PaymentListResponse(
    val success: Boolean = true,
    val payments: List<PaymentDto> = emptyList()
)

@Serializable
data class PaymentDetailResponse(
    val success: Boolean = true,
    val payment: PaymentDto? = null
)

@Serializable
data class MpesaConfirmationRequest(
    @SerialName("message_raw") val messageRaw: String
)

@Serializable
data class MpesaConfirmationResponse(
    val success: Boolean = true,
    val message: String? = null
)

@Serializable
data class StripePublicConfigResponse(
    val success: Boolean = true,
    @SerialName("publishable_key") val publishableKey: String,
    @SerialName("apple_pay") val applePay: Boolean = false
)

/**
 * GET /api/payments/methods — per-environment payment-method matrix
 * shipped in PR F. iOS PayInvoiceView reads this at bootstrap to
 * decide which method buttons to render. Replaces the legacy
 * /payments/config/stripe single-method endpoint, but server keeps
 * both for older client compatibility.
 */
@Serializable
data class PaymentMethodsResponse(
    val success: Boolean = true,
    val methods: PaymentMethodMatrix = PaymentMethodMatrix()
)

@Serializable
data class PaymentMethodMatrix(
    val stripe: StripeMethodConfig = StripeMethodConfig(),
    val mpesa:  MpesaMethodConfig  = MpesaMethodConfig()
)

@Serializable
data class StripeMethodConfig(
    val enabled: Boolean = false,
    @SerialName("publishable_key") val publishableKey: String? = null,
    @SerialName("apple_pay") val applePay: Boolean = false
)

@Serializable
data class MpesaMethodConfig(
    val enabled: Boolean = true,
    @SerialName("till_number") val tillNumber: String = "5530500"
)

@Serializable
data class UserCreditDto(
    @SerialName("balance_kes") val balanceKes: Long = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
    val success: Boolean = true
)

@Serializable
data class AdminPaymentApprovalResponse(
    val success: Boolean = true,
    @SerialName("alreadyPaid") val alreadyPaid: Boolean = false,
    @SerialName("target_kind") val targetKind: String? = null,
    @SerialName("target_id") val targetId: String? = null,
    @SerialName("override_applied") val overrideApplied: Boolean = false,
    val message: String? = null
)

/**
 * Body of POST /admin/payments/:id/approve. `overrideReason` is required
 * (>=10 chars) when the customer-claimed M-Pesa amount is short of the
 * invoice — server returns 409 `error: 'amount_mismatch'` without it
 * (audit P1.2). Clean approvals send an empty body / null reason.
 */
@Serializable
data class AdminPaymentApproveRequest(
    @SerialName("override_reason") val overrideReason: String? = null
)

@Serializable
data class AdminPaymentRejectRequest(
    val reason: String
)

/**
 * One entry in the user's credit_ledger (referral / consumed_payment / refund /
 * manual). Negative `delta_kes` = credit consumed; positive = credit earned.
 */
@Serializable
data class CreditLedgerEntryDto(
    @SerialName("id") val id: String,
    @SerialName("delta_kes") val deltaKes: Long,
    @SerialName("reason") val reason: String,
    @SerialName("source_id") val sourceId: String? = null,
    @SerialName("note") val note: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CreditLedgerResponse(
    val success: Boolean = true,
    val entries: List<CreditLedgerEntryDto> = emptyList()
)
