// HomeGreetingNavigation.swift
// Maps a `HomeGreetingDestination` (Kotlin sealed class, bridged via SKIE)
// to a SwiftUI destination view. Tapping a greeting on the home carousel
// pushes the resulting view onto the Home tab's NavigationStack.
//
// `PayInvoice` and `TicketDetail` deep-link straight to the per-record
// screens (PR D — `HomeGreetingDestination` carries the payload now).
// `BuyForMeOrder` keeps the list as the entry point because the iOS BFM
// flow doesn't have a dedicated per-order detail view.
// `NpsSurvey` is intentionally not handled by a push — the carousel host
// (`CustomerDashboardView`) intercepts that destination and presents the
// survey sheet instead; this fallback exists only for safety.

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
        case let pay as HomeGreetingDestinationPayInvoice:
            PayInvoiceView(
                targetKind: pay.targetKind,
                targetId: pay.targetId,
                targetTitle: pay.title ?? "Invoice",
                amountKesGross: pay.amountKes
            )
        case let ticket as HomeGreetingDestinationTicketDetail:
            // `subject` is the nav-title hint — TicketDetailViewModel
            // loads the real ticket on appear, so an empty initial
            // label resolves once the data arrives.
            TicketDetailView(ticketId: ticket.ticketId, subject: "", asAdmin: false)
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
