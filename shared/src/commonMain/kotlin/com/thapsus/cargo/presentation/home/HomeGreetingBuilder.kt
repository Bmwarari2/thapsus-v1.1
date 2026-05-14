package com.thapsus.cargo.presentation.home

import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

/**
 * Pure function: turns a [HomeGreetingSnapshot] into the ordered list of
 * greetings to surface on the home carousel.
 *
 * Ladder is fixed at 25 variants — see [HomeGreeting]. Within a category
 * higher-priority entries pre-empt lower ones, but the builder emits up to
 * [maxCount] greetings (default 4) so the carousel always has something to
 * rotate through when multiple signals apply.
 *
 * The freshness rule applies only to [HomeGreeting.Category.Status] variants:
 * if the underlying event's timestamp is <= the last-seen marker for that
 * greeting id, the variant is dropped. [HomeGreeting.Category.Urgent] is
 * never dismissed by freshness — those keep firing until the upstream state
 * actually clears (invoice paid, ticket replied to, etc.).
 */
object HomeGreetingBuilder {

    private const val MAX_DEFAULT = 4
    private val LONG_IDLE = 30.days
    private val SHORT_IDLE = 7.days
    private val DELIVERED_FRESHNESS_WINDOW = 2.days

    fun build(
        snapshot: HomeGreetingSnapshot,
        seenAt: Map<String, Instant>,
        now: Instant,
        maxCount: Int = MAX_DEFAULT
    ): List<HomeGreeting> {
        val out = mutableListOf<HomeGreeting>()

        // --- Urgent (priority 1-8) — freshness-immune. ---

        snapshot.urgentInvoice?.let { invoice ->
            if (invoice.overdue) out += HomeGreeting.OverdueInvoice(invoice.id)
            else out += HomeGreeting.UnpaidInvoice(invoice.id)
        }
        snapshot.failedPaymentInvoiceId?.let {
            out += HomeGreeting.FailedPayment(it)
        }
        snapshot.mpesaPendingInvoiceId?.let {
            out += HomeGreeting.MpesaPending(it)
        }
        snapshot.quoteExpiringSoon?.let {
            out += HomeGreeting.QuoteExpiringSoon(it.orderId)
        }
        snapshot.quoteReady?.let {
            // Suppress the "ready" variant when the expiring-soon one is firing
            // for the same order — they'd otherwise stack.
            val expiringId = snapshot.quoteExpiringSoon?.orderId
            if (expiringId != it.orderId) {
                out += HomeGreeting.QuoteReady(it.orderId, it.amountGbp)
            }
        }
        snapshot.ticketWithUnreadReply?.let { ticketId ->
            // Class-level seen marker ("ticket_reply") + per-ticket updatedAt:
            // freshness fires whenever the latest-updated open ticket has a
            // newer timestamp than the user's last marker. TicketDetailScreen
            // writes that marker via ThapsusSdk.markHomeGreetingSeen on
            // appear; the reactive seenFlow propagates it back here.
            if (isFresh(HomeGreeting.TicketReply(ticketId).id, snapshot.ticketWithUnreadReplyAt, seenAt)) {
                out += HomeGreeting.TicketReply(ticketId)
            }
        }
        if (snapshot.dsarReady) out += HomeGreeting.DsarReady

        // --- Status (priority 9-18) — freshness-gated. ---

        if (snapshot.parcelsAtHubCount > 0 &&
            isFresh(HomeGreeting.ParcelsAtHub(snapshot.parcelsAtHubCount).id, snapshot.parcelsAtHubLatestAt, seenAt)
        ) {
            out += HomeGreeting.ParcelsAtHub(snapshot.parcelsAtHubCount)
        }
        if (snapshot.outForDeliveryCount > 0 &&
            isFresh(HomeGreeting.OutForDelivery.id, snapshot.outForDeliveryLatestAt, seenAt)
        ) {
            out += HomeGreeting.OutForDelivery
        }
        snapshot.consolidationStatus?.let { status ->
            val greeting = when (status) {
                HomeGreetingSnapshot.ConsolidationStatus.Ready -> HomeGreeting.ConsolidationReady
                HomeGreetingSnapshot.ConsolidationStatus.InTransit -> HomeGreeting.ConsolidationInTransit
                HomeGreetingSnapshot.ConsolidationStatus.Cleared -> HomeGreeting.ConsolidationCleared
            }
            if (isFresh(greeting.id, snapshot.consolidationStatusAt, seenAt)) {
                out += greeting
            }
        }
        snapshot.recentlyDeliveredAt?.let { deliveredAt ->
            val withinWindow = (now - deliveredAt) <= DELIVERED_FRESHNESS_WINDOW
            if (withinWindow && isFresh(HomeGreeting.RecentlyDelivered.id, deliveredAt, seenAt)) {
                out += HomeGreeting.RecentlyDelivered
            }
        }
        snapshot.bfmPurchased?.let {
            // BFM purchased/shipped are per-order; the seen-marker uses the
            // base greeting id, which means "I've seen this state at least
            // once across any order" dismisses until a fresh event lands.
            // That's intentional — keeps the carousel from looping the same
            // line forever when the user already has the info.
            out += HomeGreeting.BfmPurchased(it.orderId, it.retailer)
        }
        snapshot.bfmShippedToHub?.let {
            out += HomeGreeting.BfmShippedToHub(it.orderId, it.retailer)
        }
        if (snapshot.parcelsInTransitCount > 0 &&
            isFresh(HomeGreeting.ParcelsInTransit(snapshot.parcelsInTransitCount).id, snapshot.parcelsInTransitLatestAt, seenAt)
        ) {
            out += HomeGreeting.ParcelsInTransit(snapshot.parcelsInTransitCount)
        }
        if (snapshot.preRegisterProcessing &&
            isFresh(HomeGreeting.PreRegisterProcessing.id, snapshot.preRegisterAt, seenAt)
        ) {
            out += HomeGreeting.PreRegisterProcessing
        }

        // --- Engagement (priority 19-21). ---

        if (snapshot.creditBalanceKes >= 1L &&
            isFresh(HomeGreeting.CreditBalance(snapshot.creditBalanceKes).id, now, seenAt, sevenDayCooldown = true)
        ) {
            out += HomeGreeting.CreditBalance(snapshot.creditBalanceKes)
        }
        snapshot.referralMilestoneAt?.let {
            if (isFresh(HomeGreeting.ReferralMilestone.id, it, seenAt)) {
                out += HomeGreeting.ReferralMilestone
            }
        }
        if (snapshot.npsPromptDue) out += HomeGreeting.NpsPromptDue

        // --- Onboarding / re-engagement / fallback (priority 22-25). ---

        // These are mutually exclusive — only one onboarding/fallback line
        // ever fires.
        val hasUrgentOrStatus = out.any {
            it.category == HomeGreeting.Category.Urgent ||
                it.category == HomeGreeting.Category.Status
        }
        if (!hasUrgentOrStatus) {
            val onboarding = onboardingFallback(snapshot, now)
            if (onboarding != null) out += onboarding
        }

        return out
            .sortedBy { it.priority }
            .take(maxCount)
    }

    private fun onboardingFallback(
        snapshot: HomeGreetingSnapshot,
        now: Instant
    ): HomeGreeting? {
        val createdAt = snapshot.accountCreatedAt
        val lastActivity = snapshot.lastActivityAt

        // Brand-new: account < 7 days old AND no recorded activity.
        if (createdAt != null && (now - createdAt) <= SHORT_IDLE && lastActivity == null) {
            return HomeGreeting.BrandNew
        }
        // Long idle: 30+ days since last activity.
        if (lastActivity != null && (now - lastActivity) >= LONG_IDLE) {
            return HomeGreeting.LongIdleReturn
        }
        // Short idle: 7-30 days since last activity.
        if (lastActivity != null && (now - lastActivity) >= SHORT_IDLE) {
            return HomeGreeting.ShortIdleReturn
        }
        return HomeGreeting.Default
    }

    /**
     * Status-greeting freshness check: returns true when the event timestamp
     * is newer than the last-seen marker (or there's no marker yet). When
     * [sevenDayCooldown] is set, dismisses for 7 days after the user opened
     * the destination — used for the credit-balance ping that we don't want
     * to nag about daily.
     */
    private fun isFresh(
        greetingId: String,
        eventAt: Instant?,
        seenAt: Map<String, Instant>,
        sevenDayCooldown: Boolean = false
    ): Boolean {
        val seen = seenAt[greetingId] ?: return true
        if (sevenDayCooldown) {
            val cooldownEnd = seen + SHORT_IDLE
            // Need an "event now" reference — callers pass `now` for cooldown
            // variants, so this is effectively `now > cooldownEnd`.
            return (eventAt ?: seen) > cooldownEnd
        }
        if (eventAt == null) return false // status greeting with no timestamp → suppress once seen
        return eventAt > seen
    }
}
