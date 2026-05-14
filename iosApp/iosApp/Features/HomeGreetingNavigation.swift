// HomeGreetingNavigation.swift
// Maps a `HomeGreetingDestination` (Kotlin sealed class, bridged via SKIE)
// to a SwiftUI destination view. Tapping a greeting on the home carousel
// pushes the resulting view onto the Home tab's NavigationStack.
//
// SKIE 0.10 bridges `sealed class` variants as Swift **nested** types
// (e.g. `HomeGreetingDestination.Shop`) rather than the flattened
// names (`HomeGreetingDestinationShop`) it generates for `sealed
// interface` variants. The dot syntax below is the load-bearing
// difference — flattened-name pattern matching does not compile.
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
        case _ as HomeGreetingDestination.Shop:
            BuyForMeView()
        case _ as HomeGreetingDestination.ActivityHub:
            CustomerActivityHubView()
        case _ as HomeGreetingDestination.Parcels:
            TrackingView()
        case _ as HomeGreetingDestination.Invoices:
            CustomerInvoicesView()
        case _ as HomeGreetingDestination.Transactions:
            TransactionsView()
        case _ as HomeGreetingDestination.Consolidations:
            CustomerActivityHubView()
        case _ as HomeGreetingDestination.CreditCenter:
            CreditCenterView()
        case _ as HomeGreetingDestination.BuyForMeOrder:
            BuyForMeView()
        case let pay as HomeGreetingDestination.PayInvoice:
            PayInvoiceView(
                targetKind: pay.targetKind,
                targetId: pay.targetId,
                targetTitle: pay.title ?? "Invoice",
                amountKesGross: pay.amountKes
            )
        case let ticket as HomeGreetingDestination.TicketDetail:
            // `subject` is the nav-title hint — TicketDetailViewModel
            // loads the real ticket on appear, so an empty initial
            // label resolves once the data arrives.
            TicketDetailView(ticketId: ticket.ticketId, subject: "", asAdmin: false)
        case _ as HomeGreetingDestination.Dsar:
            DsarView()
        case _ as HomeGreetingDestination.Referral:
            ReferralView()
        case _ as HomeGreetingDestination.NpsSurvey:
            CustomerActivityHubView()
        default:
            BuyForMeView()
        }
    }
}
