// ReferralView.swift
// Customer referral hub. Shows the user's code, total earnings, pending vs
// completed counts, and a feed of referees.

import SwiftUI
import ThapsusShared

struct ReferralView: View {
    @State private var vm: ReferralViewModel? = nil
    @State private var observer: StateFlowObserver<ReferralViewModelUiState>? = nil
    @State private var copied: Bool = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "Earn KES", systemImage: "person.2.fill")
                EditorialHeader(title: "Refer & earn",
                                subtitle: "KES 50 wallet credit for every friend who ships their first parcel with us.")
                content
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Referrals")
        .glassNavigationBar()
        .task { bootstrap() }
    }

    @ViewBuilder
    private var content: some View {
        switch observer?.value {
        case let loaded as ReferralViewModelUiStateLoaded:
            referralCard(loaded)
            statsRow(loaded)
            historyList(loaded)
        case is ReferralViewModelUiStateLoading:
            ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
        case let err as ReferralViewModelUiStateError:
            ErrorBanner(title: "Couldn't load", message: err.message)
        default: EmptyView()
        }
    }

    private func referralCard(_ data: ReferralViewModelUiStateLoaded) -> some View {
        InkFeatureCard {
            VStack(alignment: .leading, spacing: 16) {
                Text("Your code").font(.headline)
                Text(data.summary.referralCode ?? "—")
                    .font(.system(size: 36, weight: .heavy, design: .monospaced))
                    .foregroundStyle(Brand.orange)

                HStack(spacing: 10) {
                    Button(action: copyCode(data.summary.referralCode)) {
                        HStack(spacing: 8) {
                            Image(systemName: copied ? "checkmark.circle.fill" : "doc.on.doc")
                            Text(copied ? "Copied!" : "Copy")
                        }
                    }
                    .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))

                    if let code = data.summary.referralCode {
                        ShareLink(item: shareMessage(code: code)) {
                            HStack(spacing: 8) {
                                Image(systemName: "square.and.arrow.up")
                                Text("Share")
                            }
                        }
                        .buttonStyle(GlassSheenButtonStyle())
                    }
                }
            }
        }
    }

    private func statsRow(_ data: ReferralViewModelUiStateLoaded) -> some View {
        HStack(spacing: 12) {
            statTile(label: "Earnings",
                     value: formatKes(data.summary.totalEarningsKes),
                     unit: "KES",
                     accent: Brand.orange)
            statTile(label: "Friends",
                     value: "\(data.summary.totalReferrals)",
                     unit: nil,
                     accent: .blue)
            statTile(label: "Pending",
                     value: "\(data.summary.pendingCount)",
                     unit: nil,
                     accent: .gray)
        }
    }

    private func statTile(label: String, value: String, unit: String?, accent: Color) -> some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 6) {
                Text(label.uppercased())
                    .font(.system(size: 9, weight: .heavy))
                    .tracking(2)
                    .foregroundStyle(.secondary)
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    if let unit { Text(unit).font(.caption.weight(.heavy)).foregroundStyle(accent) }
                    Text(value).font(.title2.weight(.heavy)).foregroundStyle(Brand.ink)
                }
            }
        }
    }

    @ViewBuilder
    private func historyList(_ data: ReferralViewModelUiStateLoaded) -> some View {
        if !data.history.isEmpty {
            SectionHeader(title: "Recent referees")
            ForEach(data.history, id: \.id) { entry in
                CrystalCard {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(entry.refereeName ?? entry.refereeEmail ?? "Friend")
                                .font(.headline).foregroundStyle(Brand.ink)
                            Text(entry.createdAt ?? "")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text(entry.status.uppercased())
                            .font(.system(size: 9, weight: .heavy)).tracking(2)
                            .foregroundStyle(entry.status == "completed" ? .green : .orange)
                            .padding(.horizontal, 8).padding(.vertical, 4)
                            .background(Capsule().fill((entry.status == "completed" ? Color.green : Color.orange).opacity(0.16)))
                    }
                }
            }
        }
    }

    private func formatKes(_ amount: Double) -> String {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        return f.string(from: NSNumber(value: amount)) ?? "\(Int(amount))"
    }

    private func shareMessage(code: String) -> String {
        "Use my Thapsus Cargo code \(code) on signup — UK→Kenya cargo with KES 50 wallet credit waiting."
    }

    private func copyCode(_ code: String?) -> () -> Void {
        return {
            UIPasteboard.general.string = code ?? ""
            copied = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) { copied = false }
        }
    }

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.referralViewModel()
        vm = model
        model.load()
        observer = StateFlowObserver(initial: model.state.value) {
            model.state
        }
    }
}
