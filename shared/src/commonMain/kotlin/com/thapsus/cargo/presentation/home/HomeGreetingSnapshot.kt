package com.thapsus.cargo.presentation.home

import kotlinx.datetime.Instant

/**
 * Plain immutable bundle of everything the [HomeGreetingBuilder] needs to
 * decide which greetings to surface. The view-model assembles one of these
 * per emission from the various repository flows + cached pulls; nothing
 * here is Kotlin-coroutines-aware, so it's trivially testable.
 *
 * All counts default to zero and all timestamps default to null so callers
 * only fill in the slots they have data for — the builder treats absent
 * fields as "no signal" rather than "explicit zero."
 */
data class HomeGreetingSnapshot(
    /** Account creation time; drives the brand-new vs. returning split. */
    val accountCreatedAt: Instant? = null,
    /**
     * Most recent customer-visible activity (last parcel update, last
     * order, etc.). Used to fire the long-idle re-engagement greetings.
     */
    val lastActivityAt: Instant? = null,

    // ----- Urgent -----
    val urgentInvoice: UrgentInvoice? = null,
    val failedPayment: HomeGreeting.InvoiceRef? = null,
    val mpesaPending: HomeGreeting.InvoiceRef? = null,
    val quoteExpiringSoon: PendingQuote? = null,
    val quoteReady: PendingQuote? = null,
    val ticketWithUnreadReply: String? = null,
    /**
     * `updated_at` of the ticket carried in [ticketWithUnreadReply]. The
     * builder compares this against the local `"ticket_reply"` seen-marker
     * to decide whether to fire the greeting — newer than seen → fires.
     */
    val ticketWithUnreadReplyAt: Instant? = null,
    val dsarReady: Boolean = false,

    // ----- Status -----
    /** Most recent parcel `received_at_warehouse` timestamp. */
    val parcelsAtHubCount: Int = 0,
    val parcelsAtHubLatestAt: Instant? = null,
    val outForDeliveryCount: Int = 0,
    val outForDeliveryLatestAt: Instant? = null,
    /** Consolidation status snapshot — drives the three consolidation greetings. */
    val consolidationStatus: ConsolidationStatus? = null,
    val consolidationStatusAt: Instant? = null,
    val recentlyDeliveredAt: Instant? = null,
    val bfmPurchased: BfmOrderRef? = null,
    val bfmShippedToHub: BfmOrderRef? = null,
    val parcelsInTransitCount: Int = 0,
    val parcelsInTransitLatestAt: Instant? = null,
    val preRegisterProcessing: Boolean = false,
    val preRegisterAt: Instant? = null,

    // ----- Engagement -----
    /** KES credit balance from `/payments/me/credit`. Greeting fires when >= 1 KES. */
    val creditBalanceKes: Long = 0,
    /**
     * Most recent referee-join timestamp. When newer than the seen-marker the
     * milestone greeting fires; the marker then dismisses it until the *next*
     * referee joins.
     */
    val referralMilestoneAt: Instant? = null,
    val npsPromptDue: Boolean = false
) {
    data class UrgentInvoice(
        val ref: HomeGreeting.InvoiceRef,
        /** True when the invoice is past its due date. */
        val overdue: Boolean
    )

    data class PendingQuote(
        val orderId: String,
        val amountGbp: Double?
    )

    data class BfmOrderRef(
        val orderId: String,
        val retailer: String?
    )

    enum class ConsolidationStatus { Ready, InTransit, Cleared }
}
