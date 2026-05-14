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

    /** Pay-invoice flow targeting a specific invoice. */
    data class PayInvoice(val invoiceId: String) : HomeGreetingDestination()

    /** Support ticket detail. */
    data class TicketDetail(val ticketId: String) : HomeGreetingDestination()

    /** Data-subject-access-request hub. */
    data object Dsar : HomeGreetingDestination()

    /** Referral / share-and-earn screen. */
    data object Referral : HomeGreetingDestination()

    /** Trigger the NPS survey sheet (not a route — UI handles as a sheet). */
    data object NpsSurvey : HomeGreetingDestination()
}
