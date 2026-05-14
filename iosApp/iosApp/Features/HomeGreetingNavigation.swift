// HomeGreetingNavigation.swift
// Maps a `HomeGreetingDestination` (Kotlin sealed class, bridged via SKIE)
// to a SwiftUI destination view. Tapping a greeting on the home carousel
// pushes the resulting view onto the Home tab's NavigationStack.
//
// Per-record deep-links (specific invoice / BFM order / ticket) fall back
// to the corresponding list screen for v1 — the customer reaches the
// record with one more tap from there. A follow-up PR can wire the
// deep-link DTOs through and replace the list-level fallbacks with the
// detail screens.

import SwiftUI
import ThapsusShared

extension HomeGreetingDestination {
    /// Returns the SwiftUI destination view associated with this greeting.
    @ViewBuilder
    func makeDestinationView() -> some View {
        switch self {
        case _ as HomeGreetingDestinationShop:
            BuyForMeView()
        case _ as HomeGreetingDestinationActivityHub:
            CustomerActivityHubView()
        case _ as HomeGreetingDestinationParcels:
            TrackingView()
        case _ as HomeGreetingDestinationInvoices:
            CustomerInvoicesView()
        case _ as HomeGreetingDestinationTransactions:
            TransactionsView()
        case _ as HomeGreetingDestinationConsolidations:
            CustomerActivityHubView()
        case _ as HomeGreetingDestinationCreditCenter:
            CreditCenterView()
        case _ as HomeGreetingDestinationBuyForMeOrder:
            BuyForMeView()
        case _ as HomeGreetingDestinationPayInvoice:
            CustomerInvoicesView()
        case _ as HomeGreetingDestinationTicketDetail:
            TicketsListView()
        case _ as HomeGreetingDestinationDsar:
            DsarView()
        case _ as HomeGreetingDestinationReferral:
            ReferralView()
        case _ as HomeGreetingDestinationNpsSurvey:
            CustomerActivityHubView()
        default:
            BuyForMeView()
        }
    }
}
