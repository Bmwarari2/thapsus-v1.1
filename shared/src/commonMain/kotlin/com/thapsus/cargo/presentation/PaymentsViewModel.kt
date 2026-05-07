package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.CreatePaymentResponse
import com.thapsus.cargo.data.dto.PaymentDto
import com.thapsus.cargo.data.dto.UserCreditDto
import com.thapsus.cargo.data.repository.PaymentsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the customer-facing pay flow that replaces the wallet:
 *  - bootstrap() → fetches Stripe publishable key + credit + method matrix.
 *  - create(targetKind, targetId, method, phone?) → server mints a payment row.
 *      • stripe     → response.next.clientSecret powers PaymentSheet.
 *      • mpesa, manual provider → response.next.paybill/account/amount_due_kes;
 *                                  customer pastes the SMS afterwards.
 *      • mpesa, lipana provider → server fires the STK push; ViewModel
 *                                  transitions to LipanaStkInflight and
 *                                  polls /payments/:id until paid/failed.
 *  - submitMpesaConfirmation(...) → manual fallback for STK failures.
 *
 * The ViewModel is created PER target (each PayInvoice screen makes its
 * own) so the action state doesn't leak across unrelated payments.
 */
class PaymentsViewModel(
    private val payments: PaymentsRepository
) : SharedViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Ready(
            val publishableKey: String?,
            val creditBalanceKes: Long,
            // PR F: per-environment payment-method matrix. Null fields
            // mean "fall back to a sensible default" (used when the
            // server is older than PR F or /methods fetch fails).
            val stripeEnabled: Boolean = true,
            val mpesaEnabled:  Boolean = true,
            val mpesaTillNumber: String = "5530500",
            /**
             * Migration 038: 'manual' (paste-the-SMS) or 'lipana' (STK Push).
             * Drives which sheet PayInvoiceView mounts when the customer
             * picks M-Pesa.
             */
            val mpesaProvider: String = "manual"
        ) : UiState
    }

    sealed interface ActionState {
        data object Idle : ActionState
        data object Creating : ActionState
        data class StripeReady(val payment: PaymentDto, val clientSecret: String) : ActionState
        data class MpesaReady(
            val payment: PaymentDto,
            val paybill: String,
            val account: String,
            val amountDueKes: Long
        ) : ActionState
        /**
         * Lipana STK has been fired; the customer's phone is showing the
         * PIN prompt. iOS renders the awaiting-PIN sheet and the VM
         * polls /payments/:id every ~2s for the next ~90s.
         */
        data class LipanaStkInflight(
            val payment: PaymentDto,
            val lipanaTransactionId: String?,
            val lipanaCheckoutRequestId: String?,
            val amountDueKes: Long,
            val phone: String?
        ) : ActionState
        data object Submitting : ActionState
        data class Done(val message: String) : ActionState
        data class Error(val message: String) : ActionState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _action = MutableStateFlow<ActionState>(ActionState.Idle)
    val action: StateFlow<ActionState> = _action.asStateFlow()

    /** Optional: most recently created payment for the current target. */
    private var _lastPayment: PaymentDto? = null
    fun lastPayment(): PaymentDto? = _lastPayment

    /** Job for the current Lipana poll loop, if any. Cancelled on resetAction(). */
    private var _pollJob: Job? = null

    fun bootstrap() {
        _state.value = UiState.Loading
        scope.launch {
            // PR F: pull the full method matrix; fall back to legacy
            // /config/stripe shape if /methods isn't deployed yet.
            val matrix = payments.methods().getOrNull()?.methods
            val legacyCfg = if (matrix == null) payments.stripeConfig().getOrNull() else null
            val credit = payments.myCredit().getOrNull()
            if (matrix == null && legacyCfg == null && credit == null) {
                _state.value = UiState.Error("Couldn't reach payments service")
                return@launch
            }
            _state.value = UiState.Ready(
                publishableKey   = matrix?.stripe?.publishableKey ?: legacyCfg?.publishableKey,
                creditBalanceKes = credit?.balanceKes ?: 0L,
                stripeEnabled    = matrix?.stripe?.enabled ?: (legacyCfg != null),
                mpesaEnabled     = matrix?.mpesa?.enabled ?: true,
                mpesaTillNumber  = matrix?.mpesa?.tillNumber ?: "5530500",
                mpesaProvider    = matrix?.mpesa?.provider ?: "manual"
            )
        }
    }

    fun refreshCredit() {
        scope.launch {
            val current = (_state.value as? UiState.Ready) ?: return@launch
            payments.myCredit().onSuccess {
                _state.value = current.copy(creditBalanceKes = it.balanceKes)
            }
        }
    }

    fun create(
        targetKind: String,
        targetId: String,
        method: String,
        applyCredit: Boolean = true,
        phone: String? = null
    ) {
        _pollJob?.cancel()
        _action.value = ActionState.Creating
        scope.launch {
            payments.create(targetKind, targetId, method, applyCredit, phone)
                .onSuccess { resp ->
                    val next = mapNext(resp, phone)
                    _action.value = next
                    if (next is ActionState.LipanaStkInflight) startLipanaPoll(next)
                }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Payment creation failed") }
        }
    }

    fun submitMpesaConfirmation(paymentId: String, messageRaw: String) {
        _action.value = ActionState.Submitting
        scope.launch {
            payments.submitMpesaConfirmation(paymentId, messageRaw)
                .onSuccess { _action.value = ActionState.Done(it.message ?: "Submitted for review.") }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Couldn't submit") }
        }
    }

    fun resetAction() {
        _pollJob?.cancel()
        _pollJob = null
        _action.value = ActionState.Idle
    }

    /** Called from Swift after PaymentSheet returns .completed. */
    fun markStripeCompleted(message: String = "Payment received. Thanks!") {
        _action.value = ActionState.Done(message)
    }

    /**
     * Manual fallback for the STK flow — customer taps "Pay manually
     * instead" while the STK is in flight. Cancels the poll, re-uses
     * the existing pending payment row and switches the action state
     * to MpesaReady so the existing MpesaSubmitSheet can present.
     * Server-side, the payment row stays 'pending' until the SMS lands.
     */
    fun fallbackToManualMpesa(tillNumber: String) {
        val current = _action.value as? ActionState.LipanaStkInflight ?: return
        _pollJob?.cancel()
        _action.value = ActionState.MpesaReady(
            payment      = current.payment,
            paybill      = tillNumber,
            account      = current.payment.id,
            amountDueKes = current.amountDueKes
        )
    }

    private fun mapNext(resp: CreatePaymentResponse, phone: String?): ActionState {
        val payment = resp.payment ?: return ActionState.Error("Server returned no payment")
        _lastPayment = payment
        if (resp.fullyCoveredByCredit) {
            return ActionState.Done("Covered by your credit. Thanks!")
        }
        val next = resp.next ?: return ActionState.Error("Server returned no next-step")
        return when (next.kind) {
            "stripe" -> {
                val cs = next.clientSecret
                if (cs.isNullOrEmpty()) ActionState.Error("Stripe client_secret missing — refresh and retry")
                else ActionState.StripeReady(payment, cs)
            }
            "mpesa" -> ActionState.MpesaReady(
                payment      = payment,
                paybill      = next.paybill ?: "5530500",
                account      = next.account ?: payment.id,
                amountDueKes = next.amountDueKes ?: payment.amountDueKes
            )
            "mpesa_stk" -> ActionState.LipanaStkInflight(
                payment                 = payment,
                lipanaTransactionId     = next.lipanaTransactionId
                                            ?: payment.lipanaTransactionId,
                lipanaCheckoutRequestId = next.lipanaCheckoutRequestId
                                            ?: payment.lipanaCheckoutRequestId,
                amountDueKes            = next.amountDueKes ?: payment.amountDueKes,
                phone                   = phone ?: payment.mpesaPhoneUsed
            )
            else -> ActionState.Error("Unknown payment method '${next.kind}'")
        }
    }

    /**
     * Poll /payments/:id until status hits a terminal value or the
     * deadline elapses. Webhook is canonical (it's what flips the row);
     * polling is a UX safety net so the customer's spinner stops promptly.
     *
     * Per feedback_skie_bridging.md, throwing suspend funs need a
     * per-call-site catch-all so an unmapped throwable doesn't crash
     * the iOS process via the global unhandled-exception hook.
     */
    private fun startLipanaPoll(initial: ActionState.LipanaStkInflight) {
        val paymentId = initial.payment.id
        _pollJob?.cancel()
        _pollJob = scope.launch {
            try {
                val deadlineTicks = 45      // 45 × 2s = 90s budget
                var ticks = 0
                while (ticks < deadlineTicks) {
                    delay(2_000L)
                    ticks += 1
                    // Bail if the action state moved on (customer cancelled,
                    // tapped "Pay manually instead", or another flow took over).
                    if (_action.value !is ActionState.LipanaStkInflight) return@launch
                    val res = payments.detail(paymentId)
                    val payment = res.getOrNull() ?: continue
                    when (payment.status) {
                        "paid" -> {
                            _action.value = ActionState.Done("Payment received. Thanks!")
                            return@launch
                        }
                        "failed" -> {
                            _action.value = ActionState.Error(
                                "M-Pesa payment failed. Try again or use the manual option."
                            )
                            return@launch
                        }
                        "rejected", "cancelled" -> {
                            _action.value = ActionState.Error(
                                "M-Pesa payment was cancelled."
                            )
                            return@launch
                        }
                        else -> { /* still pending — keep polling */ }
                    }
                }
                // Deadline elapsed — surface a clear error. The webhook can
                // still settle later; the Transactions list will show paid
                // on next pull.
                if (_action.value is ActionState.LipanaStkInflight) {
                    _action.value = ActionState.Error(
                        "STK request timed out. Check your phone for the prompt, or use the manual option."
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (_action.value is ActionState.LipanaStkInflight) {
                    _action.value = ActionState.Error(
                        t.message ?: "Couldn't check payment status"
                    )
                }
            }
        }
    }
}
