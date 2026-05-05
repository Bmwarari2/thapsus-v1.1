// OperatorScannerSheet.swift
// Hosts the camera scanner (SkuScannerView) and routes the lookup result:
//   • Mode A — pre_registered: dismiss into the existing Receive sheet
//   • Mode B — anything else: render a compact detail panel with status,
//     customer block, weights, consolidation linkage, hold reason, photo
//   • Mode C — not found / lookup error: red banner, scanner stays mounted
//
// Used from OperatorReceiveView's toolbar and the operator-role tab bar.

import SwiftUI
import ThapsusShared

struct OperatorScannerSheet: View {
    let onSelectForReceive: (PackageDto) -> Void
    let onClose: () -> Void

    @Environment(AppEnvironment.self) private var env

    @State private var vm: SkuScannerViewModel?
    @State private var stateObs: StateFlowObserver<SkuScannerViewModelState>?

    var body: some View {
        NavigationStack {
            ZStack {
                Color.black.ignoresSafeArea()
                content
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { toolbar }
        }
        .task { bootstrap() }
        .onDisappear {
            vm?.clear(); vm = nil; stateObs = nil
        }
    }

    @ViewBuilder
    private var content: some View {
        switch state {
        case .none, .some(is SkuScannerViewModelStateIdle):
            scanner
        case .some(let looking as SkuScannerViewModelStateLooking):
            scanner
                .overlay(alignment: .top) {
                    StatusPill(text: "Looking up \(looking.barcode)…", tint: Brand.orange)
                        .padding(.top, 12)
                }
        case .some(let notFound as SkuScannerViewModelStateNotFound):
            scanner
                .overlay(alignment: .top) {
                    VStack(spacing: 6) {
                        StatusPill(
                            text: "No parcel with \(notFound.barcode)",
                            tint: .red
                        )
                        Button("Try again") { vm?.reset() }
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.white)
                    }
                    .padding(.top, 12)
                }
        case .some(let failed as SkuScannerViewModelStateFailed):
            scanner
                .overlay(alignment: .top) {
                    VStack(spacing: 6) {
                        StatusPill(
                            text: "\(failed.barcode): \(failed.message)",
                            tint: .red
                        )
                        Button("Try again") { vm?.reset() }
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.white)
                    }
                    .padding(.top, 12)
                }
        case .some(let found as SkuScannerViewModelStateFound):
            ScannedParcelDetailView(
                parcel: found.parcel,
                onSelectForReceive: handleReceive,
                onScanAgain: { vm?.reset() }
            )
        case .some:
            scanner
        }
    }

    private var scanner: some View {
        SkuScannerView(
            onScan: { sku in vm?.onScanned(rawBarcode: sku) },
            onCancel: onClose
        )
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .principal) {
            Text("Scan SKU")
                .font(.headline.weight(.semibold))
                .foregroundStyle(.white)
        }
        ToolbarItem(placement: .topBarTrailing) {
            Button("Close") { onClose() }
                .tint(.white)
        }
    }

    private var state: SkuScannerViewModelState? { stateObs?.value }

    private func bootstrap() {
        if vm == nil {
            let v = ThapsusSdk.shared.skuScannerViewModel()
            vm = v
            stateObs = StateFlowObserver(initial: v.state.value) { v.state }
        }
    }

    private func handleReceive(_ parcel: OpsScannedParcelDto) {
        // Bridge the scanner DTO into PackageDto so the existing
        // ReceiveLabelSheet/IntakeViewModel pipeline doesn't need to fork.
        onSelectForReceive(parcel.toPackageDto())
    }
}

private struct StatusPill: View {
    let text: String
    let tint: Color
    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(tint.opacity(0.85), in: Capsule())
    }
}

private struct ScannedParcelDetailView: View {
    let parcel: OpsScannedParcelDto
    let onSelectForReceive: (OpsScannedParcelDto) -> Void
    let onScanAgain: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                statusBanner
                customerCard
                parcelMetaCard
                if hasWeights { weightsCard }
                if let url = parcel.photoUrl, !url.isEmpty { photoCard(url) }
                if parcel.consolidationId != nil { consolidationCard }
                timelineCard
                actionRow
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
    }

    private var status: String {
        parcel.packageStatus ?? parcel.orderStatus ?? "unknown"
    }

    private var isHeld: Bool {
        if let r = parcel.holdReason, !r.isEmpty,
           parcel.holdResolvedAt == nil {
            return true
        }
        return false
    }

    private var statusTint: Color {
        if isHeld { return .red }
        switch status {
        case "pre_registered": return Brand.orange
        case "received_at_warehouse": return .green
        case "manifested", "consolidating": return Brand.orange
        case "in_transit": return .blue
        case "delivered": return .green
        default: return Brand.ink
        }
    }

    private var statusLabel: String {
        if isHeld { return "HELD: \(parcel.holdReason ?? "manual hold")" }
        switch status {
        case "pre_registered": return "Pre-registered — ready to receive"
        case "received_at_warehouse": return "Received at warehouse"
        case "manifested": return "In manifest"
        case "consolidating": return "Consolidating"
        case "in_transit": return "In transit"
        case "delivered": return "Delivered"
        default: return status.uppercased()
        }
    }

    private var statusBanner: some View {
        HStack(spacing: 12) {
            Image(systemName: isHeld ? "exclamationmark.triangle.fill" : "checkmark.seal.fill")
                .font(.title3)
                .foregroundStyle(.white)
            Text(statusLabel)
                .font(.headline)
                .foregroundStyle(.white)
            Spacer()
        }
        .padding(14)
        .background(statusTint.opacity(0.9), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private var customerCard: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 6) {
                Text("Customer").font(.caption.weight(.heavy)).tracking(2).foregroundStyle(.secondary)
                Text(parcel.customer?.fullName ?? "Unknown").font(.title3.weight(.semibold)).foregroundStyle(Brand.ink)
                if let code = parcel.customer?.warehouseId, !code.isEmpty {
                    Text(code).font(.callout.monospaced()).foregroundStyle(Brand.orange)
                }
                if let email = parcel.customer?.email, !email.isEmpty {
                    Text(email).font(.caption).foregroundStyle(.secondary)
                }
                if let phone = parcel.customer?.phone, !phone.isEmpty {
                    Text(phone).font(.caption).foregroundStyle(.secondary)
                }
            }
        }
    }

    private var parcelMetaCard: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 8) {
                Text("Parcel").font(.caption.weight(.heavy)).tracking(2).foregroundStyle(.secondary)
                if let bc = parcel.barcode, !bc.isEmpty {
                    Text(bc).font(.title3.monospaced()).foregroundStyle(Brand.orange)
                }
                if let track = parcel.trackingNumber, !track.isEmpty {
                    Text(track).font(.caption.monospaced()).foregroundStyle(.secondary)
                }
                if let r = parcel.retailer, !r.isEmpty {
                    metaRow("Retailer", r)
                }
                if let d = parcel.description_, !d.isEmpty {
                    metaRow("Description", d)
                }
                if let value = parcel.declaredValueGbpPence?.int64Value, value > 0 {
                    metaRow("Declared value", "£\(String(format: "%.2f", Double(value)/100.0))")
                }
                if let m = parcel.market, !m.isEmpty {
                    metaRow("Market", m)
                }
            }
        }
    }

    private var hasWeights: Bool {
        (parcel.actualKg?.doubleValue ?? 0) > 0
            || (parcel.chargeableKg?.doubleValue ?? 0) > 0
            || (parcel.volumetricKg?.doubleValue ?? 0) > 0
    }

    private var weightsCard: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 8) {
                Text("Weights").font(.caption.weight(.heavy)).tracking(2).foregroundStyle(.secondary)
                HStack(spacing: 16) {
                    weightTile("Actual", parcel.actualKg?.doubleValue, suffix: "kg")
                    weightTile("Volumetric", parcel.volumetricKg?.doubleValue, suffix: "kg")
                    weightTile("Chargeable", parcel.chargeableKg?.doubleValue, suffix: "kg")
                }
                if let l = parcel.lengthCm?.doubleValue,
                   let w = parcel.widthCm?.doubleValue,
                   let h = parcel.heightCm?.doubleValue,
                   l > 0 || w > 0 || h > 0 {
                    Text(String(format: "%.0f × %.0f × %.0f cm", l, w, h))
                        .font(.caption.monospaced())
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private var consolidationCard: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 6) {
                Text("Consolidation").font(.caption.weight(.heavy)).tracking(2).foregroundStyle(.secondary)
                if let awb = parcel.consolidationMasterAwb, !awb.isEmpty {
                    Text(awb).font(.headline).foregroundStyle(Brand.ink)
                }
                if let flight = parcel.consolidationFlightDate, !flight.isEmpty {
                    metaRow("Flight", flight)
                }
                if let cs = parcel.consolidationStatus, !cs.isEmpty {
                    metaRow("Status", cs.replacingOccurrences(of: "_", with: " "))
                }
            }
        }
    }

    private var timelineCard: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 8) {
                Text("Timeline").font(.caption.weight(.heavy)).tracking(2).foregroundStyle(.secondary)
                if let s = parcel.receivedAt, !s.isEmpty {
                    timelineRow("Received", s)
                }
                if let s = parcel.photographedAt, !s.isEmpty {
                    timelineRow("Photographed", s)
                }
                if let s = parcel.holdResolvedAt, !s.isEmpty {
                    timelineRow("Hold cleared", s)
                }
            }
        }
    }

    @ViewBuilder
    private func photoCard(_ url: String) -> some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 6) {
                Text("Receive photo").font(.caption.weight(.heavy)).tracking(2).foregroundStyle(.secondary)
                AsyncImage(url: URL(string: url)) { phase in
                    switch phase {
                    case .empty: ProgressView().frame(maxWidth: .infinity, minHeight: 160)
                    case .success(let image): image.resizable().scaledToFit().clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    case .failure: Image(systemName: "photo").foregroundStyle(.secondary)
                    @unknown default: EmptyView()
                    }
                }
            }
        }
    }

    private var actionRow: some View {
        VStack(spacing: 10) {
            if status == "pre_registered" {
                Button { onSelectForReceive(parcel) } label: {
                    Label("Receive this parcel", systemImage: "checkmark.seal.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
            }
            Button { onScanAgain() } label: {
                Label("Scan another", systemImage: "barcode.viewfinder")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(GlassSheenButtonStyle())
        }
    }

    private func metaRow(_ label: String, _ value: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            Spacer()
            Text(value).font(.callout).foregroundStyle(Brand.ink).multilineTextAlignment(.trailing)
        }
    }

    private func weightTile(_ label: String, _ value: Double?, suffix: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption2).foregroundStyle(.secondary)
            Text(value.map { String(format: "%.2f %@", $0, suffix) } ?? "—")
                .font(.callout.weight(.semibold))
                .foregroundStyle(Brand.ink)
        }
    }

    private func timelineRow(_ label: String, _ value: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            Spacer()
            Text(value.prefix(19).replacingOccurrences(of: "T", with: " "))
                .font(.caption.monospaced())
                .foregroundStyle(.secondary)
        }
    }
}
