// ParcelDetailView.swift
// Lands when a customer taps a parcel in the dashboard. Renders the timeline
// (received → photographed → … → delivered) and surfaces the volumetric breakdown
// per spec §9 ("Volumetric-weight transparency").
//
// Loading model:
//   - On appear, kick off `refreshForUser` so the local cache is populated
//     even if the user landed here via a deep link (i.e. the dashboard list
//     never primed the cache for this row).
//   - Observe `observeOne(id:)` from the cache; that gives us live updates
//     when RealtimeSync writes through.
//   - If the cache still doesn't yield the row within ~4 seconds, surface a
//     real "couldn't load" error with a retry button instead of an infinite
//     spinner that looks like a redirect.

import SwiftUI
import ThapsusShared

struct ParcelDetailView: View {
    let parcelID: String

    @Environment(AppEnvironment.self) private var env
    @Environment(\.dismiss) private var dismiss

    @State private var pkg: PackageDto?
    @State private var observerTask: Task<Void, Never>? = nil
    @State private var refreshFailureMessage: String? = nil
    @State private var loadingTimedOut: Bool = false
    @State private var signInRequired: Bool = false
    /// Server-issued POD summary for delivered parcels — fetched lazily
    /// on appear when pkg.status == .delivered. The signed photo /
    /// signature URLs are short-lived (5 min) so we never persist them
    /// past the view's lifetime.
    @State private var pod: PodDetailDto? = nil
    @State private var podError: String? = nil
    /// Distinguishes "fetch returned nil (404 — no POD recorded)" from
    /// "still loading" so the missing-POD UI doesn't flash on first appear.
    @State private var podMissing: Bool = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if let pkg {
                    header(pkg)
                    if let cid = pkg.consolidationId, !cid.isEmpty {
                        NavigationLink {
                            CustomerConsolidationView(consolidationId: cid)
                        } label: {
                            CalloutBanner(
                                tint: Brand.orange.opacity(0.16),
                                icon: "airplane",
                                title: "On a weekly flight",
                                message: "Tap to see your consolidation summary."
                            )
                        }
                        .buttonStyle(.plain)
                    }
                    if pkg.status == .awaitingDutyPayment {
                        NavigationLink {
                            PublicPaymentView(orderId: pkg.id)
                        } label: {
                            CalloutBanner(
                                tint: Color.red.opacity(0.12),
                                icon: "creditcard.fill",
                                title: "Duty payable",
                                message: "Tap to settle the customs invoice and release this parcel."
                            )
                        }
                        .buttonStyle(.plain)
                    }
                    if pkg.status == .delivered {
                        podCard(pkg)
                    }
                    weights(pkg)
                    timeline(pkg)
                } else if signInRequired {
                    signInRequiredCard
                } else if loadingTimedOut || refreshFailureMessage != nil {
                    failureCard
                } else {
                    ProgressView()
                        .tint(Brand.ink)
                        .padding(40)
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(20)
        }
        .navigationTitle("Parcel")
        .glassNavigationBar(displayMode: .inline)
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .task(id: parcelID) {
            await load()
        }
        .onDisappear {
            observerTask?.cancel()
            observerTask = nil
        }
    }

    // MARK: - Loading

    private func load() async {
        observerTask?.cancel()
        loadingTimedOut = false
        refreshFailureMessage = nil
        signInRequired = false

        // 0. If the user landed here via a deep link without an active session,
        //    fast-fail with a sign-in prompt rather than burning the 4-second
        //    timeout on a refresh that can't authenticate.
        guard let userId = env.currentUserID else {
            signInRequired = true
            return
        }

        // 1. Subscribe to the cache so we get updates as soon as the row lands.
        let repo = ThapsusSdk.shared.packages()
        observerTask = Task { @MainActor in
            for await snapshot in repo.observeOne(id: parcelID) {
                if let snapshot {
                    self.pkg = snapshot
                    self.loadingTimedOut = false
                    self.refreshFailureMessage = nil
                }
                if Task.isCancelled { break }
            }
        }

        // 2. Force a server refresh so the cache is populated when the user
        //    landed here via a deep link or after a sign-out cycle. Kotlin
        //    Result wrappers bridge to Swift as throwing functions via SKIE.
        do {
            _ = try await repo.refreshForUser(userId: userId)
        } catch {
            if pkg == nil {
                refreshFailureMessage = error.localizedDescription
            }
        }

        // 3. Time-out fallback so the spinner doesn't sit forever.
        try? await Task.sleep(nanoseconds: 4_000_000_000)
        if pkg == nil && !Task.isCancelled {
            loadingTimedOut = true
        }
    }

    // MARK: - Failure card

    @ViewBuilder
    private var signInRequiredCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            ErrorBanner(
                title: "Sign in required",
                message: "Sign in to your Thapsus account to view this parcel."
            )
            Button("Sign in") { dismiss() }
                .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
        }
    }

    @ViewBuilder
    private var failureCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            ErrorBanner(title: "Couldn't load this parcel", message: refreshFailureMessage
                    ?? "We couldn't find a parcel with this ID. It may have been removed, or the network is offline.")
            HStack(spacing: 10) {
                Button("Retry") {
                    pkg = nil
                    Task { await load() }
                }
                .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))

                Button("Back to tracking") { dismiss() }
                    .buttonStyle(.bordered).tint(.gray)
            }
        }
    }

    // MARK: - Proof of Delivery

    /// Renders the POD photo + recipient + delivered-on date for a
    /// delivered parcel. Hits `/api/orders/:id/pod` lazily (with a
    /// 5-min signed photo URL) on first appear; refresh after a sign-in
    /// cycle re-fetches because @State resets when the view is rebuilt.
    @ViewBuilder
    private func podCard(_ p: PackageDto) -> some View {
        SoftCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark.seal.fill").foregroundStyle(.green)
                    Text("Proof of delivery").font(.headline).foregroundStyle(Brand.ink)
                }
                if let pod {
                    if let raw = pod.photoUrl, let url = URL(string: raw) {
                        AsyncImage(url: url) { phase in
                            switch phase {
                            case .empty:
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(Brand.cream.opacity(0.5))
                                    .frame(height: 200)
                                    .overlay(ProgressView().tint(Brand.ink))
                            case .success(let img):
                                img.resizable().scaledToFill()
                                    .frame(height: 240)
                                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                            case .failure:
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(Brand.cream.opacity(0.5))
                                    .frame(height: 200)
                                    .overlay(
                                        Label("Photo unavailable", systemImage: "photo")
                                            .font(.caption).foregroundStyle(.secondary)
                                    )
                            @unknown default:
                                EmptyView()
                            }
                        }
                    }
                    if let recipient = pod.recipientName, !recipient.isEmpty {
                        Text("Received by \(recipient)").font(.subheadline).foregroundStyle(Brand.ink)
                    }
                    if let when = pod.capturedAt {
                        Text(when).font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                    }
                    if let notes = pod.notes, !notes.isEmpty {
                        Text(notes).font(.caption).foregroundStyle(.secondary)
                    }
                } else if let podError {
                    // Real failure (auth expired, network, 5xx) — bubbles up
                    // now that OrdersRepository.fetchPod re-throws non-404s.
                    VStack(alignment: .leading, spacing: 6) {
                        Text(podError).font(.caption).foregroundStyle(.secondary)
                        Button {
                            Task { await reloadPod(p) }
                        } label: {
                            Label("Try again", systemImage: "arrow.clockwise")
                                .font(.caption.weight(.semibold))
                        }
                        .buttonStyle(.bordered)
                        .tint(Brand.ink)
                    }
                } else if podMissing {
                    // 404 — parcel is marked delivered but no POD row was
                    // captured (admin set status manually, or rider POD
                    // upload silently failed). Still useful to confirm the
                    // recorded delivery date so the customer doesn't think
                    // the screen is broken.
                    VStack(alignment: .leading, spacing: 4) {
                        Text("No delivery photo on file for this parcel.")
                            .font(.subheadline).foregroundStyle(Brand.ink)
                        if let when = p.updatedAt {
                            Text("Recorded as delivered \(when).")
                                .font(.caption.monospacedDigit())
                                .foregroundStyle(.secondary)
                        }
                        Text("If you didn't receive this parcel, contact support — the rider POD capture may have failed to upload.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                } else {
                    HStack { ProgressView().tint(Brand.ink); Text("Loading proof of delivery…").font(.caption).foregroundStyle(.secondary) }
                }
            }
        }
        .task(id: p.id) {
            await reloadPod(p)
        }
    }

    /// Re-fetch on parcel change so the same view bound to a different
    /// deep-link id refreshes its POD too. Signed URLs expire in ~5 min so
    /// a stale `pod` from a previous visit shouldn't be reused.
    private func reloadPod(_ p: PackageDto) async {
        pod = nil
        podError = nil
        podMissing = false
        // PackageDto.id is the `packages.id` row; POD events are keyed
        // against `pod_events.parcel_id` which references `orders.id`.
        // Fall back to pkg.id only if orderId is somehow missing (legacy
        // rows pre-orders/packages split).
        let lookupId: String = {
            if let oid = p.orderId, !oid.isEmpty { return oid }
            return p.id
        }()
        do {
            let result = try await ThapsusSdk.shared.orders().fetchPod(orderId: lookupId)
            pod = result
            podMissing = (result == nil)
        } catch {
            // OrdersRepository.fetchPod re-throws non-404s as ApiException
            // — feedback_skie_bridging.md.
            podError = "Couldn't load proof of delivery: \(error.localizedDescription)"
        }
    }

    // MARK: - Sections (unchanged)

    @ViewBuilder
    private func header(_ p: PackageDto) -> some View {
        SoftCard {
            VStack(alignment: .leading, spacing: 6) {
                Text(p.description_ ?? "Parcel").font(.title3.weight(.semibold)).foregroundStyle(Brand.ink)
                if let r = p.retailer { Text(r).font(.subheadline).foregroundStyle(.secondary) }
                HStack(spacing: 8) {
                    if let bc = p.barcode { GlassChip(title: bc, systemImage: "barcode") }
                    GlassChip(title: friendly(p.status), systemImage: "shippingbox.fill", tint: Brand.orange.opacity(0.4))
                }
                .padding(.top, 4)
            }
        }
    }

    @ViewBuilder
    private func weights(_ p: PackageDto) -> some View {
        if let charge = p.chargeableKg?.doubleValue {
            SoftCard {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Chargeable mass").font(.headline).foregroundStyle(Brand.ink)
                    Text(String(format: "%.2f kg", charge))
                        .font(.system(size: 40, weight: .bold, design: .rounded))
                        .foregroundStyle(Brand.ink)
                        .contentTransition(.numericText())
                    if let actual = p.actualKg?.doubleValue, let vol = p.volumetricKg?.doubleValue {
                        HStack(spacing: 12) {
                            statPill("Actual", String(format: "%.2f kg", actual))
                            statPill("Volumetric", String(format: "%.2f kg", vol))
                            if vol > actual {
                                GlassChip(title: "Volumetric rules", systemImage: "ruler", tint: Brand.orange.opacity(0.4))
                            }
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func timeline(_ p: PackageDto) -> some View {
        SoftCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("Timeline").font(.headline).foregroundStyle(Brand.ink)
                ForEach(timelineSteps, id: \.0) { step in
                    HStack(spacing: 12) {
                        Image(systemName: passed(step.0, p.status) ? "checkmark.circle.fill" : "circle.dotted")
                            .foregroundStyle(passed(step.0, p.status) ? Brand.orange : .secondary)
                        Text(step.1)
                            .font(.subheadline)
                            .foregroundStyle(passed(step.0, p.status) ? Brand.ink : .secondary)
                    }
                }
            }
        }
    }

    private var timelineSteps: [(PackageStatus, String)] {
        [
            (.preRegistered, "Pre-registered"),
            (.receivedAtWarehouse, "Received at Stockport"),
            (.photographed, "Photographed"),
            (.weighed, "Weighed"),
            (.screened, "Screened"),
            (.manifested, "On manifest"),
            (.inTransit, "In transit"),
            (.jkiaArrived, "Arrived JKIA"),
            (.released, "Customs cleared"),
            (.outForDelivery, "Out for delivery"),
            (.delivered, "Delivered")
        ]
    }

    private func passed(_ step: PackageStatus, _ current: PackageStatus) -> Bool {
        rank(current) >= rank(step)
    }

    private func rank(_ s: PackageStatus) -> Int {
        switch s {
        case .preRegistered: return 0
        case .receivedAtWarehouse: return 1
        case .photographed: return 2
        case .weighed: return 3
        case .screened: return 4
        case .manifested: return 5
        case .inTransit: return 6
        case .jkiaArrived: return 7
        case .awaitingDutyPayment: return 8
        case .released: return 9
        case .outForDelivery: return 10
        case .delivered: return 11
        default: return 0
        }
    }

    private func friendly(_ s: PackageStatus) -> String {
        switch s {
        case .delivered: return "Delivered"
        case .inTransit: return "In transit"
        default: return "In progress"
        }
    }

    @ViewBuilder
    private func statPill(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.subheadline.weight(.semibold)).foregroundStyle(Brand.ink)
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Brand.cream.opacity(0.6))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Brand.ink.opacity(0.08), lineWidth: 1)
        )
    }
}
