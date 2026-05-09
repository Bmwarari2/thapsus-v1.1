// CustomerActivityHubView.swift
// Bottom-tab hub surfacing the three financial-activity surfaces that
// were previously buried inside other screens (Invoices under Orders,
// Transactions under Account → Credit, Buy-for-me under TrackingView).
// Each row deep-links to the dedicated screen.

import SwiftUI
import ThapsusShared

struct CustomerActivityHubView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text("Invoices, transactions, and Buy-for-me requests in one place.")
                    .font(.body(14, weight: .medium))
                    .foregroundStyle(LG.fg3)
                    .padding(.top, 4)
                    .padding(.bottom, 4)

                NavigationLink { CustomerInvoicesView() } label: {
                    HubCard(
                        icon: "doc.text.fill",
                        iconBg: Brand.orange,
                        title: "Invoices",
                        subtitle: "Active and past shipping invoices in one place."
                    )
                }
                .buttonStyle(.plain)

                NavigationLink { TransactionsView() } label: {
                    HubCard(
                        icon: "list.bullet.rectangle",
                        iconBg: Brand.ink,
                        title: "Transactions",
                        subtitle: "Every card or M-Pesa payment + your credit activity."
                    )
                }
                .buttonStyle(.plain)

                NavigationLink { BuyForMeView() } label: {
                    HubCard(
                        icon: "wand.and.stars",
                        iconBg: Color.purple,
                        title: "Buy-for-me requests",
                        subtitle: "Concierge orders — quotes, payments, and tracking."
                    )
                }
                .buttonStyle(.plain)
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Activity")
        .glassNavigationBar()
    }
}

private struct HubCard: View {
    let icon: String
    let iconBg: Color
    let title: String
    let subtitle: String

    var body: some View {
        CrystalCard {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.title3)
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous).fill(iconBg)
                    )
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.headline).foregroundStyle(Brand.ink)
                    Text(subtitle).font(.caption).foregroundStyle(.secondary)
                        .lineLimit(2).multilineTextAlignment(.leading)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.tertiary)
            }
        }
    }
}

// MARK: - Customer Invoices

/// Dedicated invoices archive — every shipping or standalone invoice the
/// customer has, split into Active (status == "invoiced") and Past
/// (status == "paid" or "shipped"). Replaces the old Activity → Invoices
/// row that was misrouting users into TrackingView.
///
/// Reads route through the same Supabase + Realtime path TrackingView
/// uses (`customerConsolidations.fetchForUser` + `observeForUser`) so a
/// fresh admin invoice or a status flip lands here without manual
/// refresh. RLS scopes both reads to the signed-in customer.
struct CustomerInvoicesView: View {
    @Environment(AppEnvironment.self) private var env

    @State private var consolidations: [CustomerConsolidationDto] = []
    @State private var observerTask: Task<Void, Never>?
    @State private var payTarget: PayTarget?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                EditorialHeader(
                    eyebrow: "Activity",
                    title: "Invoices",
                    subtitle: "Active charges to clear, plus everything you've already paid."
                )

                if active.isEmpty && past.isEmpty {
                    CrystalCard {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("No invoices yet").font(.headline).foregroundStyle(Brand.ink)
                            Text("Once admin issues an invoice for one of your consolidations or a standalone charge, it'll appear here.")
                                .font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                }

                if !active.isEmpty {
                    SectionHeader(
                        title: "Active",
                        subtitle: "Pay these to clear your batch for the next outgoing shipment."
                    )
                    ForEach(active, id: \.id) { c in
                        CustomerInvoiceCard(consolidation: c) {
                            payTarget = PayTarget.fromConsolidation(c)
                        }
                    }
                }

                if !past.isEmpty {
                    SectionHeader(
                        title: "Past invoices",
                        subtitle: "Paid + shipped — kept here for your records."
                    )
                    ForEach(past, id: \.id) { c in
                        CustomerInvoiceCard(consolidation: c) {
                            // Past rows are read-only; tapping the (still
                            // visible) action does nothing — but PayTarget
                            // creation is harmless even if the user fires
                            // the closure on a paid row, since the server
                            // side rejects duplicate payments.
                        }
                    }
                }

                Color.clear.frame(height: 24)
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Invoices")
        .glassNavigationBar()
        .sheet(item: $payTarget) { target in
            PayInvoiceView(
                targetKind: target.kind,
                targetId: target.id,
                targetTitle: target.title,
                amountKesGross: target.amountKes
            )
        }
        .task {
            guard observerTask == nil, let userID = env.currentUserID else { return }
            let repo = ThapsusSdk.shared.customerConsolidations()
            consolidations = (try? await repo.fetchForUser(userId: userID)) ?? []
            observerTask = Task { @MainActor in
                do {
                    for try await updated in repo.observeForUser(userId: userID) {
                        if let idx = consolidations.firstIndex(where: { $0.id == updated.id }) {
                            consolidations[idx] = updated
                        } else {
                            consolidations.insert(updated, at: 0)
                        }
                    }
                } catch {
                    print("[CustomerInvoicesView] observeForUser failed: \(error)")
                }
            }
        }
        .onDisappear {
            observerTask?.cancel()
            observerTask = nil
        }
    }

    /// Issued but unpaid — the customer needs to act on these.
    private var active: [CustomerConsolidationDto] {
        consolidations
            .filter { $0.status == "invoiced" }
            .sorted { ($0.createdAt ?? "") > ($1.createdAt ?? "") }
    }

    /// Settled invoices: paid or already attached to a shipping batch.
    /// `pending` rows are excluded because no invoice has been issued yet.
    private var past: [CustomerConsolidationDto] {
        consolidations
            .filter { $0.status == "paid" || $0.status == "shipped" }
            .sorted { ($0.invoicePaidAt ?? $0.updatedAt ?? "") > ($1.invoicePaidAt ?? $1.updatedAt ?? "") }
    }
}
