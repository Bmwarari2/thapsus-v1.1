package com.thapsus.cargo.presentation.home

/**
 * One snippet of the rotating home greeting carousel.
 *
 * Built by [HomeGreetingBuilder] from the customer's current state. Each
 * variant carries:
 *   - [id]: stable string used by the seen-marker table (freshness rule)
 *   - [priority]: lower wins — see the ladder in HomeGreetingBuilder
 *   - [category]: drives whether the freshness rule applies
 *   - [body]: the rendered, sentence-cased context line ("Your shipment
 *     is on its way to Kenya.") — the time-of-day prefix
 *     ("Good morning, Brian.") is rendered separately by the UI, and the
 *     two are typically concatenated into one continuous text block
 *   - [destination]: where to navigate on tap
 *
 * Catalogue (25 variants) is grouped:
 *   Urgent      — invoices, failed payment, M-Pesa pending, quote expiry,
 *                 quote ready, ticket reply, DSAR ready
 *   Status      — parcel/consolidation/BFM progress updates (freshness-gated)
 *   Engagement  — credit balance, referral, NPS prompt
 *   Onboarding  — brand-new account, returning re-engagement
 *   Fallback    — nothing active
 */
sealed class HomeGreeting(
    val id: String,
    val priority: Int,
    val category: Category,
    val body: String,
    val destination: HomeGreetingDestination
) {
    enum class Category {
        /** Never filtered by the seen-marker — keeps firing until state clears. */
        Urgent,
        /** Filtered by freshness — dismissed once the user opens the destination. */
        Status,
        Engagement,
        Onboarding,
        Fallback
    }

    // ---------- Urgent (1-8) ----------

    /**
     * P=1. Unpaid invoice past its due date. Body branches on the underlying
     * payment's `target_kind` so a stuck buy-for-me order reads as a
     * concierge-specific reminder rather than a generic "your invoice is
     * overdue" line. For BFM invoices the original quote's GBP amount is
     * woven into the headline ("£42.30 buy-for-me payment is overdue…")
     * so the customer sees the price they originally agreed on the home
     * screen — the source of truth is the BFM order's `estimateGbp`, not
     * a KES→GBP back-conversion, so the greeting matches the quote card.
     */
    class OverdueInvoice(ref: InvoiceRef) : HomeGreeting(
        id = "overdue_invoice",
        priority = 1,
        category = Category.Urgent,
        body = if (ref.targetKind == "buy_for_me")
            bfmInvoiceBody(ref.amountGbp, overdue = true)
        else "Your invoice is overdue — please settle to avoid storage fees.",
        destination = ref.toDestination()
    )

    /**
     * P=2. Plain unpaid invoice. Body branches on the underlying payment's
     * `target_kind` so a buy-for-me order awaiting payment reads distinctly
     * from a shipping/consolidation invoice — fixes the "I accepted a BFM
     * quote but the home tab doesn't tell me it's awaiting payment" gap.
     * BFM variants include the original quote's GBP amount; see
     * [OverdueInvoice] for the rationale.
     */
    class UnpaidInvoice(ref: InvoiceRef) : HomeGreeting(
        id = "unpaid_invoice",
        priority = 2,
        category = Category.Urgent,
        body = if (ref.targetKind == "buy_for_me")
            bfmInvoiceBody(ref.amountGbp, overdue = false)
        else "You have a pending invoice that needs your attention.",
        destination = ref.toDestination()
    )

    /** P=3. Most recent payment attempt failed. */
    class FailedPayment(ref: InvoiceRef) : HomeGreeting(
        id = "failed_payment",
        priority = 3,
        category = Category.Urgent,
        body = if (ref.targetKind == "buy_for_me")
            "Your buy-for-me payment didn't go through — please try again."
        else "Your last payment didn't go through — please try again.",
        destination = ref.toDestination()
    )

    /** P=4. M-Pesa STK push pending confirmation. */
    class MpesaPending(ref: InvoiceRef) : HomeGreeting(
        id = "mpesa_pending",
        priority = 4,
        category = Category.Urgent,
        body = "We're waiting on your M-Pesa confirmation.",
        destination = ref.toDestination()
    )

    /**
     * Reusable bundle of the fields the pay-invoice deep-link needs.
     * Constructed once by the snapshot assembler from a `PaymentDto` row
     * and threaded through every urgent-invoice greeting variant.
     */
    data class InvoiceRef(
        val targetKind: String,
        val targetId: String,
        val amountKes: Long,
        val title: String?,
        /**
         * Original quote price in GBP for buy-for-me invoices. Sourced
         * from the matching `BuyForMeOrderDto.estimateGbp` at snapshot-
         * assembly time so the customer sees the same number on the home
         * greeting as on their quote card — not a KES→GBP back-conversion
         * via live FX (which would drift as exchange rates move).
         * Null for non-BFM invoices (consolidations, orders) or when the
         * matching BFM order can't be located in the snapshot.
         */
        val amountGbp: Double? = null
    ) {
        internal fun toDestination(): HomeGreetingDestination.PayInvoice =
            HomeGreetingDestination.PayInvoice(
                targetKind = targetKind,
                targetId = targetId,
                amountKes = amountKes,
                title = title
            )
    }

    /** P=5. Quoted BFM order whose quote is within 24h of expiry. */
    class QuoteExpiringSoon(orderId: String) : HomeGreeting(
        id = "quote_expiring_soon",
        priority = 5,
        category = Category.Urgent,
        body = "Your buy-for-me quote expires tomorrow — approve to lock in the price.",
        destination = HomeGreetingDestination.BuyForMeOrder(orderId)
    )

    /** P=6. Quoted BFM order ready for the customer to accept or reject. */
    class QuoteReady(orderId: String, amountGbp: Double?) : HomeGreeting(
        id = "quote_ready",
        priority = 6,
        category = Category.Urgent,
        body = if (amountGbp != null && amountGbp > 0)
            "Your buy-for-me quote is ready (£${formatGbp(amountGbp)}) — review and approve."
        else "Your buy-for-me quote is ready — review and approve.",
        destination = HomeGreetingDestination.BuyForMeOrder(orderId)
    )

    /** P=7. Support ticket has a new reply from staff. */
    class TicketReply(ticketId: String) : HomeGreeting(
        id = "ticket_reply",
        priority = 7,
        category = Category.Urgent,
        body = "Your support ticket has a new response.",
        destination = HomeGreetingDestination.TicketDetail(ticketId)
    )

    /** P=8. DSAR request is ready for the customer to download. */
    data object DsarReady : HomeGreeting(
        id = "dsar_ready",
        priority = 8,
        category = Category.Urgent,
        body = "Your data request is ready to download.",
        destination = HomeGreetingDestination.Dsar
    )

    // ---------- Status (9-18) — freshness-gated ----------

    /** P=9. New parcels just arrived at the UK hub. */
    class ParcelsAtHub(val count: Int) : HomeGreeting(
        id = "parcels_at_hub",
        priority = 9,
        category = Category.Status,
        body = "${parcelNoun(count)} arrived at our Manchester hub.",
        destination = HomeGreetingDestination.Parcels
    )

    /** P=10. Last-mile out-for-delivery today. */
    data object OutForDelivery : HomeGreeting(
        id = "out_for_delivery",
        priority = 10,
        category = Category.Status,
        body = "Your parcels are out for delivery today.",
        destination = HomeGreetingDestination.Parcels
    )

    /** P=11. Consolidation is built and ready to ship. */
    data object ConsolidationReady : HomeGreeting(
        id = "consolidation_ready",
        priority = 11,
        category = Category.Status,
        body = "Your consolidation is ready to ship to Nairobi.",
        destination = HomeGreetingDestination.Consolidations
    )

    /** P=12. Consolidation in transit to Kenya. */
    data object ConsolidationInTransit : HomeGreeting(
        id = "consolidation_in_transit",
        priority = 12,
        category = Category.Status,
        body = "Your shipment is on its way to Kenya.",
        destination = HomeGreetingDestination.Consolidations
    )

    /** P=13. Consolidation cleared customs. */
    data object ConsolidationCleared : HomeGreeting(
        id = "consolidation_cleared",
        priority = 13,
        category = Category.Status,
        body = "Your shipment has cleared customs.",
        destination = HomeGreetingDestination.Consolidations
    )

    /** P=14. Delivered in the last 48h. */
    data object RecentlyDelivered : HomeGreeting(
        id = "recently_delivered",
        priority = 14,
        category = Category.Status,
        body = "Your parcels were delivered — thanks for choosing us.",
        destination = HomeGreetingDestination.Transactions
    )

    /** P=15. BFM purchased (we placed the order). */
    class BfmPurchased(orderId: String, retailer: String?) : HomeGreeting(
        id = "bfm_purchased",
        priority = 15,
        category = Category.Status,
        body = if (!retailer.isNullOrBlank())
            "We've purchased your order from $retailer."
        else "We've purchased your buy-for-me order.",
        destination = HomeGreetingDestination.BuyForMeOrder(orderId)
    )

    /** P=16. BFM order shipped to UK hub. */
    class BfmShippedToHub(orderId: String, retailer: String?) : HomeGreeting(
        id = "bfm_shipped_to_hub",
        priority = 16,
        category = Category.Status,
        body = if (!retailer.isNullOrBlank())
            "Your order from $retailer is on its way to our hub."
        else "Your buy-for-me order is on its way to our hub.",
        destination = HomeGreetingDestination.BuyForMeOrder(orderId)
    )

    /** P=17. Pre-registered parcels in transit but not yet at hub. */
    class ParcelsInTransit(val count: Int) : HomeGreeting(
        id = "parcels_in_transit",
        priority = 17,
        category = Category.Status,
        body = "${parcelNoun(count, presentTense = true)} on the way to our hub.",
        destination = HomeGreetingDestination.Parcels
    )

    /** P=18. Pre-registration submitted; awaiting hub intake. */
    data object PreRegisterProcessing : HomeGreeting(
        id = "pre_register_processing",
        priority = 18,
        category = Category.Status,
        body = "Your pre-registration is being processed.",
        destination = HomeGreetingDestination.ActivityHub
    )

    // ---------- Engagement (19-21) ----------

    /**
     * P=19. Credit balance available to spend. Denominated in KES because the
     * server-side credit ledger and `/payments/me/credit` operate in KES, and
     * the credit offsets KES invoices — showing £ would be a misleading
     * client-side conversion. £ is reserved for buy-for-me quotes.
     */
    class CreditBalance(val balanceKes: Long) : HomeGreeting(
        id = "credit_balance",
        priority = 19,
        category = Category.Engagement,
        body = "You have KES ${formatKes(balanceKes)} in credit ready to spend.",
        destination = HomeGreetingDestination.CreditCenter
    )

    /** P=20. Referral milestone hit. */
    data object ReferralMilestone : HomeGreeting(
        id = "referral_milestone",
        priority = 20,
        category = Category.Engagement,
        body = "Your friend just signed up — you've earned a credit.",
        destination = HomeGreetingDestination.Referral
    )

    /** P=21. NPS survey prompt due. */
    data object NpsPromptDue : HomeGreeting(
        id = "nps_prompt_due",
        priority = 21,
        category = Category.Engagement,
        body = "Got a minute? Share how we're doing.",
        destination = HomeGreetingDestination.NpsSurvey
    )

    // ---------- Onboarding / re-engagement (22-24) ----------

    /** P=22. Brand-new account, zero activity. */
    data object BrandNew : HomeGreeting(
        id = "brand_new",
        priority = 22,
        category = Category.Onboarding,
        body = "Welcome aboard. Let's get your first parcel shipped to Kenya.",
        destination = HomeGreetingDestination.Shop
    )

    /** P=23. Returning after 30+ days idle. */
    data object LongIdleReturn : HomeGreeting(
        id = "long_idle_return",
        priority = 23,
        category = Category.Onboarding,
        body = "Welcome back. Lots of new UK retailers in our catalogue.",
        destination = HomeGreetingDestination.Shop
    )

    /** P=24. Returning after 7-30 days idle. */
    data object ShortIdleReturn : HomeGreeting(
        id = "short_idle_return",
        priority = 24,
        category = Category.Onboarding,
        body = "Welcome back. Ready to place another order?",
        destination = HomeGreetingDestination.Shop
    )

    // ---------- Fallback (25) ----------

    /** P=25. Default — returning customer with no active state. */
    data object Default : HomeGreeting(
        id = "default",
        priority = 25,
        category = Category.Fallback,
        body = "Ready to place an order with us?",
        destination = HomeGreetingDestination.Shop
    )
}

/**
 * Renders a GBP figure: integer when whole ("£15"), 2-decimal when fractional
 * ("£15.50", "£42.99"). Avoids the dotted-zero ("15.0") look that double's
 * default toString gives — money is one place where strict formatting reads
 * cleaner than the friendly drop-trailing-zeros style.
 */
private fun formatGbp(amount: Double): String {
    val rounded = (amount * 100).toLong()
    val whole = rounded / 100
    val frac = (rounded % 100).let { if (it < 0) -it else it }.toInt()
    if (frac == 0) return "$whole"
    val pad = if (frac < 10) "0$frac" else "$frac"
    return "$whole.$pad"
}

/**
 * Composes the BFM unpaid/overdue invoice greeting body. Includes the
 * original quote's GBP amount in parentheses when the snapshot was able
 * to resolve the matching BuyForMeOrderDto; falls back to a price-less
 * sentence otherwise so an FX-or-data-race never strands the customer
 * with a blank £ placeholder.
 */
private fun bfmInvoiceBody(amountGbp: Double?, overdue: Boolean): String {
    val priceClause = if (amountGbp != null && amountGbp > 0)
        " (£${formatGbp(amountGbp)})"
    else ""
    return if (overdue)
        "Your buy-for-me payment$priceClause is overdue — settle to keep the order moving."
    else
        "Your buy-for-me order$priceClause is awaiting payment — complete to ship."
}

/**
 * Renders a KES figure with thousands separators ("1,250", "0", "2,500,000").
 * KES is a whole-currency value server-side, so no decimal handling.
 */
private fun formatKes(amount: Long): String {
    val abs = if (amount < 0) -amount else amount
    val raw = abs.toString()
    val sb = StringBuilder()
    val len = raw.length
    for (i in 0 until len) {
        if (i > 0 && (len - i) % 3 == 0) sb.append(',')
        sb.append(raw[i])
    }
    return if (amount < 0) "-$sb" else sb.toString()
}

/**
 * "1 parcel has" / "3 parcels have" — pluralisation helper. Toggle the
 * verb tense via [presentTense] (true → "are", false → "have").
 */
private fun parcelNoun(count: Int, presentTense: Boolean = false): String {
    val (parcel, verb) = if (count == 1) {
        "1 parcel" to if (presentTense) "is" else "has"
    } else {
        "$count parcels" to if (presentTense) "are" else "have"
    }
    return "$parcel $verb"
}
