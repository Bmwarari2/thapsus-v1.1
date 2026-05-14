package com.thapsus.cargo.presentation.home

/**
 * Where the customer home greeting should send the user when tapped.
 *
 * Each variant carries the IDs needed for navigation so the Compose /
 * SwiftUI side just maps it to the appropriate route — no business logic
 * decisions about "where does this greeting go" should live in the UI.
 */
sealed class HomeGreetingDestination {
    /** Default — Shop tab (the buy-for-me hub). */
    data object Shop : HomeGreetingDestination()

    /** Activity hub root (parcel tracking · pre-register · invoices · transactions). */
    data object ActivityHub : HomeGreetingDestination()

    /** Activity → Parcels list. */
    data object Parcels : HomeGreetingDestination()

    /** Activity → Invoices list. */
    data object Invoices : HomeGreetingDestination()

    /** Activity → Transactions list. */
    data object Transactions : HomeGreetingDestination()

    /** Activity → Consolidations list. */
    data object Consolidations : HomeGreetingDestination()

    /** Wallet / credit centre. */
    data object CreditCenter : HomeGreetingDestination()

    /** Buy-for-me order detail. */
    data class BuyForMeOrder(val orderId: String) : HomeGreetingDestination()

    /**
     * Pay-invoice flow targeting a specific invoice. Carries the full set of
     * fields the iOS `PayInvoiceView` and Android `payInvoice(...)` route
     * already require so the UI can deep-link straight to the pay sheet
     * instead of bouncing through the invoices list.
     *
     * - `targetKind`: payments.target_kind on the server ('order' |
     *   'consolidation' | 'buy_for_me').
     * - `targetId`: the row id matching the kind (consolidation id, etc.).
     * - `amountKes`: KES amount due — drives the pay-sheet summary line.
     * - `title`: human-readable label ("Shipping invoice", retailer name,
     *   etc.) for the sheet header; null falls back to a generic title.
     */
    data class PayInvoice(
        val targetKind: String,
        val targetId: String,
        val amountKes: Long,
        val title: String?
    ) : HomeGreetingDestination()

    /** Support ticket detail. */
    data class TicketDetail(val ticketId: String) : HomeGreetingDestination()

    /** Data-subject-access-request hub. */
    data object Dsar : HomeGreetingDestination()

    /** Referral / share-and-earn screen. */
    data object Referral : HomeGreetingDestination()

    /** Trigger the NPS survey sheet (not a route — UI handles as a sheet). */
    data object NpsSurvey : HomeGreetingDestination()
}
