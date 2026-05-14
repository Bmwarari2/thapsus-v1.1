package com.thapsus.cargo.presentation.home

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class HomeGreetingBuilderTest {

    private val now = Instant.parse("2026-05-14T10:00:00Z")
    private val emptySeen = emptyMap<String, Instant>()

    private fun invoiceRef(
        kind: String = "consolidation",
        targetId: String = "c-1",
        amountKes: Long = 12_500L,
        title: String? = "Shipping invoice"
    ) = HomeGreeting.InvoiceRef(targetKind = kind, targetId = targetId, amountKes = amountKes, title = title)

    // ---------------- Ladder ordering ----------------

    @Test
    fun `urgent invoice beats every status signal`() {
        val snap = HomeGreetingSnapshot(
            urgentInvoice = HomeGreetingSnapshot.UrgentInvoice(invoiceRef(), overdue = false),
            parcelsAtHubCount = 5,
            parcelsAtHubLatestAt = now,
            parcelsInTransitCount = 3,
            parcelsInTransitLatestAt = now,
            consolidationStatus = HomeGreetingSnapshot.ConsolidationStatus.Ready,
            consolidationStatusAt = now
        )
        val out = HomeGreetingBuilder.build(snap, emptySeen, now)
        assertTrue(out.first() is HomeGreeting.UnpaidInvoice, "got: $out")
    }

    @Test
    fun `overdue invoice beats unpaid invoice`() {
        val snap = HomeGreetingSnapshot(
            urgentInvoice = HomeGreetingSnapshot.UrgentInvoice(invoiceRef(), overdue = true)
        )
        val out = HomeGreetingBuilder.build(snap, emptySeen, now)
        assertTrue(out.first() is HomeGreeting.OverdueInvoice)
    }

    @Test
    fun `invoice greeting destination carries full pay-sheet payload`() {
        val ref = invoiceRef(
            kind = "consolidation",
            targetId = "con-42",
            amountKes = 12_500L,
            title = "May shipment"
        )
        val snap = HomeGreetingSnapshot(
            urgentInvoice = HomeGreetingSnapshot.UrgentInvoice(ref, overdue = false)
        )
        val greeting = HomeGreetingBuilder.build(snap, emptySeen, now).first() as HomeGreeting.UnpaidInvoice
        val dest = greeting.destination as HomeGreetingDestination.PayInvoice
        assertEquals("consolidation", dest.targetKind)
        assertEquals("con-42", dest.targetId)
        assertEquals(12_500L, dest.amountKes)
        assertEquals("May shipment", dest.title)
    }

    @Test
    fun `urgent priority — failed payment, mpesa pending, quote expiring, quote ready`() {
        val snap = HomeGreetingSnapshot(
            failedPayment = invoiceRef(targetId = "p-fail"),
            mpesaPending = invoiceRef(targetId = "p-mpesa"),
            quoteExpiringSoon = HomeGreetingSnapshot.PendingQuote("o-1", 100.0),
            quoteReady = HomeGreetingSnapshot.PendingQuote("o-2", 200.0)
        )
        val out = HomeGreetingBuilder.build(snap, emptySeen, now)
        assertEquals(HomeGreeting.FailedPayment(invoiceRef()).id, out[0].id)
        assertEquals(HomeGreeting.MpesaPending(invoiceRef()).id, out[1].id)
        assertEquals(HomeGreeting.QuoteExpiringSoon("o-1").id, out[2].id)
        assertEquals(HomeGreeting.QuoteReady("o-2", 200.0).id, out[3].id)
    }

    @Test
    fun `expiring-soon for same order id suppresses the duplicate quote ready entry`() {
        val snap = HomeGreetingSnapshot(
            quoteExpiringSoon = HomeGreetingSnapshot.PendingQuote("o-1", 100.0),
            quoteReady = HomeGreetingSnapshot.PendingQuote("o-1", 100.0)
        )
        val out = HomeGreetingBuilder.build(snap, emptySeen, now)
        assertEquals(1, out.size)
        assertTrue(out.first() is HomeGreeting.QuoteExpiringSoon)
    }

    @Test
    fun `top-4 cap leaves the lowest-priority signals out of the carousel`() {
        // Six urgent signals fire (priorities 2, 3, 4, 6, 7, 8). The top-4
        // cap keeps the four highest — 2, 3, 4, 6 — so QuoteReady (P=6) sits
        // at the tail and TicketReply (P=7) + DsarReady (P=8) get dropped.
        val snap = HomeGreetingSnapshot(
            urgentInvoice = HomeGreetingSnapshot.UrgentInvoice(invoiceRef(), overdue = false),
            failedPayment = invoiceRef(targetId = "p-fail"),
            mpesaPending = invoiceRef(targetId = "p-mp"),
            quoteReady = HomeGreetingSnapshot.PendingQuote("o", 100.0),
            ticketWithUnreadReply = "t-1",
            dsarReady = true
        )
        val out = HomeGreetingBuilder.build(snap, emptySeen, now)
        assertEquals(4, out.size)
        assertTrue(out.last() is HomeGreeting.QuoteReady, "got: $out")
        assertTrue(out.none { it is HomeGreeting.TicketReply })
        assertTrue(out.none { it is HomeGreeting.DsarReady })
    }

    // ---------------- Freshness ----------------

    @Test
    fun `parcels-at-hub fires when event timestamp is newer than the seen marker`() {
        val arrivedAt = Instant.parse("2026-05-14T08:00:00Z")
        val snap = HomeGreetingSnapshot(
            parcelsAtHubCount = 3,
            parcelsAtHubLatestAt = arrivedAt
        )
        // No marker yet → fires.
        val firstRun = HomeGreetingBuilder.build(snap, emptySeen, now)
        assertTrue(firstRun.any { it is HomeGreeting.ParcelsAtHub })

        // After the user opened Parcels at 09:00, a fresh arrival at 08:00
        // is older than the marker → suppressed.
        val seenAt09 = mapOf("parcels_at_hub" to Instant.parse("2026-05-14T09:00:00Z"))
        val secondRun = HomeGreetingBuilder.build(snap, seenAt09, now)
        assertTrue(secondRun.none { it is HomeGreeting.ParcelsAtHub })
    }

    @Test
    fun `parcels-at-hub re-fires when a fresher arrival lands after the seen marker`() {
        val seenAt = Instant.parse("2026-05-14T09:00:00Z")
        val freshArrival = Instant.parse("2026-05-14T09:30:00Z")
        val snap = HomeGreetingSnapshot(
            parcelsAtHubCount = 1,
            parcelsAtHubLatestAt = freshArrival
        )
        val out = HomeGreetingBuilder.build(
            snap,
            mapOf("parcels_at_hub" to seenAt),
            now
        )
        assertTrue(out.any { it is HomeGreeting.ParcelsAtHub })
    }

    @Test
    fun `urgent invoice ignores seen marker — keeps firing until paid`() {
        val snap = HomeGreetingSnapshot(
            urgentInvoice = HomeGreetingSnapshot.UrgentInvoice(invoiceRef(), overdue = false)
        )
        val seenForever = mapOf(
            "unpaid_invoice" to Instant.parse("2030-01-01T00:00:00Z")
        )
        val out = HomeGreetingBuilder.build(snap, seenForever, now)
        assertTrue(out.first() is HomeGreeting.UnpaidInvoice)
    }

    @Test
    fun `ticket reply is freshness-gated via per-ticket updatedAt`() {
        val replyAt = Instant.parse("2026-05-14T09:00:00Z")
        val snap = HomeGreetingSnapshot(
            ticketWithUnreadReply = "t-1",
            ticketWithUnreadReplyAt = replyAt
        )
        // No marker yet → fires.
        val firstRun = HomeGreetingBuilder.build(snap, emptySeen, now)
        assertTrue(firstRun.any { it is HomeGreeting.TicketReply })

        // User opened the ticket at 09:30 — marker is newer than the reply,
        // greeting drops.
        val seen = mapOf("ticket_reply" to Instant.parse("2026-05-14T09:30:00Z"))
        val secondRun = HomeGreetingBuilder.build(snap, seen, now)
        assertTrue(secondRun.none { it is HomeGreeting.TicketReply })

        // A fresher admin reply arrives — bumps updatedAt past the marker,
        // greeting re-fires.
        val refreshed = snap.copy(ticketWithUnreadReplyAt = Instant.parse("2026-05-14T09:45:00Z"))
        val thirdRun = HomeGreetingBuilder.build(refreshed, seen, now)
        assertTrue(thirdRun.any { it is HomeGreeting.TicketReply })
    }

    @Test
    fun `delivered greeting drops once outside the 48h window`() {
        val justNow = now - 1.days // within window
        val stale = now - 3.days   // outside the 2-day window

        val withinSnap = HomeGreetingSnapshot(recentlyDeliveredAt = justNow)
        val outsideSnap = HomeGreetingSnapshot(recentlyDeliveredAt = stale)

        assertTrue(
            HomeGreetingBuilder.build(withinSnap, emptySeen, now)
                .any { it is HomeGreeting.RecentlyDelivered }
        )
        assertTrue(
            HomeGreetingBuilder.build(outsideSnap, emptySeen, now)
                .none { it is HomeGreeting.RecentlyDelivered }
        )
    }

    // ---------------- Onboarding / fallback ----------------

    @Test
    fun `brand-new account with no activity surfaces the welcome line`() {
        val snap = HomeGreetingSnapshot(
            accountCreatedAt = now - 2.days,
            lastActivityAt = null
        )
        val out = HomeGreetingBuilder.build(snap, emptySeen, now)
        assertEquals(HomeGreeting.BrandNew, out.first())
    }

    @Test
    fun `long-idle return fires after 30 days of silence`() {
        val snap = HomeGreetingSnapshot(
            accountCreatedAt = now - 200.days,
            lastActivityAt = now - 45.days
        )
        val out = HomeGreetingBuilder.build(snap, emptySeen, now)
        assertEquals(HomeGreeting.LongIdleReturn, out.first())
    }

    @Test
    fun `short-idle return fires for 7-30 days of silence`() {
        val snap = HomeGreetingSnapshot(
            accountCreatedAt = now - 200.days,
            lastActivityAt = now - 10.days
        )
        val out = HomeGreetingBuilder.build(snap, emptySeen, now)
        assertEquals(HomeGreeting.ShortIdleReturn, out.first())
    }

    @Test
    fun `default fallback when nothing applies`() {
        val out = HomeGreetingBuilder.build(HomeGreetingSnapshot(), emptySeen, now)
        assertEquals(1, out.size)
        assertEquals(HomeGreeting.Default, out.first())
    }

    @Test
    fun `onboarding is suppressed when an urgent or status signal is firing`() {
        val snap = HomeGreetingSnapshot(
            accountCreatedAt = now - 1.days,
            lastActivityAt = null,
            parcelsAtHubCount = 2,
            parcelsAtHubLatestAt = now
        )
        val out = HomeGreetingBuilder.build(snap, emptySeen, now)
        // Status signal wins, BrandNew is dropped.
        assertTrue(out.any { it is HomeGreeting.ParcelsAtHub })
        assertTrue(out.none { it is HomeGreeting.BrandNew })
    }

    // ---------------- Body rendering ----------------

    @Test
    fun `parcels-at-hub pluralisation`() {
        assertEquals(
            "1 parcel has arrived at our Manchester hub.",
            HomeGreeting.ParcelsAtHub(1).body
        )
        assertEquals(
            "5 parcels have arrived at our Manchester hub.",
            HomeGreeting.ParcelsAtHub(5).body
        )
    }

    @Test
    fun `parcels-in-transit uses present-tense verb`() {
        assertEquals(
            "1 parcel is on the way to our hub.",
            HomeGreeting.ParcelsInTransit(1).body
        )
        assertEquals(
            "3 parcels are on the way to our hub.",
            HomeGreeting.ParcelsInTransit(3).body
        )
    }

    @Test
    fun `quote-ready includes amount when provided, drops it otherwise`() {
        assertEquals(
            "Your buy-for-me quote is ready (£42.50) — review and approve.",
            HomeGreeting.QuoteReady("o", 42.5).body
        )
        assertEquals(
            "Your buy-for-me quote is ready — review and approve.",
            HomeGreeting.QuoteReady("o", null).body
        )
    }

    @Test
    fun `credit balance renders KES with thousands separators (server ledger is KES)`() {
        assertEquals(
            "You have KES 250 in credit ready to spend.",
            HomeGreeting.CreditBalance(250L).body
        )
        assertEquals(
            "You have KES 1,500 in credit ready to spend.",
            HomeGreeting.CreditBalance(1_500L).body
        )
        assertEquals(
            "You have KES 2,500,000 in credit ready to spend.",
            HomeGreeting.CreditBalance(2_500_000L).body
        )
    }

    @Test
    fun `credit-balance greeting only fires when balance is at least 1 KES`() {
        val snapNone = HomeGreetingSnapshot(creditBalanceKes = 0L)
        val snapOne = HomeGreetingSnapshot(creditBalanceKes = 1L)
        assertTrue(
            HomeGreetingBuilder.build(snapNone, emptySeen, now)
                .none { it is HomeGreeting.CreditBalance }
        )
        assertTrue(
            HomeGreetingBuilder.build(snapOne, emptySeen, now)
                .any { it is HomeGreeting.CreditBalance }
        )
    }

    @Test
    fun `referral milestone fires off referralMilestoneAt and is freshness-gated`() {
        val joinedAt = now - 2.days
        val snap = HomeGreetingSnapshot(referralMilestoneAt = joinedAt)
        val firstRun = HomeGreetingBuilder.build(snap, emptySeen, now)
        assertTrue(firstRun.any { it is HomeGreeting.ReferralMilestone })

        // After the user opens the destination an hour after the join, the
        // greeting drops — until a fresher referee joins.
        val seenAfter = mapOf("referral_milestone" to (joinedAt + 1.hours))
        val secondRun = HomeGreetingBuilder.build(snap, seenAfter, now)
        assertTrue(secondRun.none { it is HomeGreeting.ReferralMilestone })
    }

    @Test
    fun `nps prompt keeps firing while pending — seen-marker doesn't suppress it`() {
        val snap = HomeGreetingSnapshot(npsPromptDue = true)
        val seenForever = mapOf(
            "nps_prompt_due" to Instant.parse("2030-01-01T00:00:00Z")
        )
        val out = HomeGreetingBuilder.build(snap, seenForever, now)
        assertTrue(out.any { it is HomeGreeting.NpsPromptDue })
    }

    @Test
    fun `dsar ready surfaces as an urgent signal when feed flags it`() {
        val snap = HomeGreetingSnapshot(dsarReady = true)
        val out = HomeGreetingBuilder.build(snap, emptySeen, now)
        assertEquals(HomeGreeting.DsarReady, out.first())
    }

    @Test
    fun `pre-register processing is status-gated by preRegisterAt`() {
        val submittedAt = now - 1.days
        val snap = HomeGreetingSnapshot(
            preRegisterProcessing = true,
            preRegisterAt = submittedAt
        )
        val firstRun = HomeGreetingBuilder.build(snap, emptySeen, now)
        assertTrue(firstRun.any { it is HomeGreeting.PreRegisterProcessing })

        val seenAfter = mapOf("pre_register_processing" to now)
        val secondRun = HomeGreetingBuilder.build(snap, seenAfter, now)
        assertTrue(secondRun.none { it is HomeGreeting.PreRegisterProcessing })
    }

    @Test
    fun `bfm purchased without retailer falls back to generic`() {
        assertEquals(
            "We've purchased your buy-for-me order.",
            HomeGreeting.BfmPurchased("o", null).body
        )
        assertEquals(
            "We've purchased your order from amazon.co.uk.",
            HomeGreeting.BfmPurchased("o", "amazon.co.uk").body
        )
    }
}
