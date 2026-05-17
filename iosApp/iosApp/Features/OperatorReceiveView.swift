// OperatorReceiveView.swift
// Phase C — operator picks a pre-registered parcel from the warehouse intake
// queue, mints a Thapsus warehouse SKU, AirPrints the label onto the box,
// and posts the receive call (which cascades order + package status). No
// barcode scanning, no customer-side label printing.

import SwiftUI
import ThapsusShared
import UIKit

struct OperatorReceiveView: View {
    @Environment(AppEnvironment.self) private var env

    @State private var todayVm: OperatorTodayViewModel?
    @State private var todayState: StateFlowObserver<TodayState>?

    @State private var intakeVm: IntakeViewModel?
    @State private var intakeState: StateFlowObserver<IntakeState>?

    @State private var sheetPackage: PackageDto?
    @State private var showScanner: Bool = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                EditorialHeader(
                    eyebrow: "Hub operations",
                    title: "Receive",
                    subtitle: "Pick a parcel, print its SKU label, mark received."
                )

                CalloutBanner(
                    tint: Brand.orange.opacity(0.12),
                    icon: "printer.fill",
                    title: "How this works",
                    message: "Match the parcel to a row by description or sender. Tap it, print the SKU label, stick it on the box. The order will move to Received."
                )

                Button { showScanner = true } label: {
                    Label("Scan SKU", systemImage: "barcode.viewfinder")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))

                queueSection
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Receive")
        .glassNavigationBar()
        .refreshable { todayVm?.refresh() }
        .task(id: env.currentUserID) { bootstrap() }
        .onDisappear {
            todayVm?.clear(); todayVm = nil; todayState = nil
            intakeVm?.clear(); intakeVm = nil; intakeState = nil
        }
        .sheet(item: $sheetPackage) { pkg in
            ReceiveLabelSheet(
                pkg: pkg,
                intakeVm: intakeVm,
                intakeState: intakeState,
                onDone: {
                    sheetPackage = nil
                    todayVm?.refresh()
                }
            )
        }
        .fullScreenCover(isPresented: $showScanner) {
            OperatorScannerSheet(
                onSelectForReceive: { pkg in
                    showScanner = false
                    sheetPackage = pkg
                },
                onClose: { showScanner = false }
            )
        }
    }

    @ViewBuilder
    private var queueSection: some View {
        // The receive queue must include every pre-registered parcel
        // regardless of age. TodayState.from() partitions pre-registered
        // rows older than LATE_THRESHOLD_DAYS into `late`, which left the
        // operator with no UI to receive parcels that had been pending
        // for over a week (they silently fell off this screen). Merge
        // expected + late so any pre-registered row remains receivable;
        // the row's "late" badge makes the age visible at a glance.
        let snapshot = todayState?.value
        let expected = snapshot?.expectedToday ?? []
        let late     = snapshot?.late ?? []
        let parcels  = expected + late
        let lateIds  = Set(late.map { $0.id })
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Expected (\(parcels.count))").font(.headline).foregroundStyle(Brand.ink)
                Spacer()
                Button { todayVm?.refresh() } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .tint(Brand.orange)
                .accessibilityLabel("Refresh")
            }
            if parcels.isEmpty {
                CrystalCard {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Nothing waiting.").font(.headline).foregroundStyle(Brand.ink)
                        Text("Pre-registered parcels will appear here once customers create orders.")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                }
            } else {
                ForEach(parcels, id: \.id) { p in
                    Button { sheetPackage = p } label: {
                        row(p, isLate: lateIds.contains(p.id))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func row(_ p: PackageDto, isLate: Bool) -> some View {
        CrystalCard {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(p.description_ ?? p.retailer ?? "Parcel")
                            .font(.headline).foregroundStyle(Brand.ink).lineLimit(1)
                        if isLate {
                            Text("LATE")
                                .font(.caption2.weight(.heavy)).tracking(2)
                                .foregroundStyle(.red)
                                .padding(.horizontal, 6).padding(.vertical, 2)
                                .background(Capsule().fill(Color.red.opacity(0.16)))
                        }
                    }
                    HStack(spacing: 8) {
                        if let r = p.retailer { Text(r).font(.caption).foregroundStyle(.secondary) }
                        if let bc = p.barcode {
                            Text(bc).font(.caption.monospaced()).foregroundStyle(Brand.orange)
                        } else {
                            Text("No SKU yet").font(.caption2.weight(.heavy)).tracking(2)
                                .foregroundStyle(Brand.orange.opacity(0.8))
                        }
                    }
                }
                Spacer()
                Image(systemName: "printer.fill")
                    .font(.title3)
                    .foregroundStyle(Brand.orange)
            }
        }
    }

    private func bootstrap() {
        if todayVm == nil {
            let v = ThapsusSdk.shared.operatorTodayViewModel()
            todayVm = v
            todayState = StateFlowObserver(initial: TodayState.companion.empty()) { v.state }
        }
        // The intake VM stamps each receive with the operator's user id —
        // RootTabView only renders this view for an authenticated operator,
        // so currentUserID is expected to be non-nil. If it ever isn't,
        // defer creation rather than fall back to a literal "operator" id
        // (which would mis-attribute every receive in audit/KPI tables).
        if intakeVm == nil, let operatorId = env.currentUserID {
            let v = ThapsusSdk.shared.intakeViewModel(operatorId: operatorId)
            intakeVm = v
            intakeState = StateFlowObserver(initial: v.state.value) { v.state }
        }
    }
}

extension PackageDto: @retroactive Identifiable {}

private struct ReceiveLabelSheet: View {
    let pkg: PackageDto
    let intakeVm: IntakeViewModel?
    let intakeState: StateFlowObserver<IntakeState>?
    let onDone: () -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var sku: String = WarehouseSku.mint()
    @State private var actualKg: Double = 1.0
    @State private var lengthCm: Double = 30
    @State private var widthCm: Double = 20
    @State private var heightCm: Double = 20
    /// Customs duty (KES) the operator stamps at receive — fed into the
    /// Phase 2 invoice prefill via OpsReceiveRequest.customs_duty.
    @State private var customsDutyKes: Double = 0
    /// Audit P2.3: BFM auto-create stubs every parcel as hs_tier='general'
    /// at accept time. Receive is the operator's first physical look at
    /// the box and the canonical place to correct the tier so duty/VAT
    /// on the eventual invoice prefill matches the goods. Empty string
    /// means "leave unchanged" — keeps a tier an admin already set.
    @State private var hsTier: String = ""
    @State private var printed: Bool = false
    @State private var customerFullName: String?
    @State private var customerWarehouseCode: String?
    @State private var customerDeliveryAddress: String?
    @State private var welcomeMessage: String = LabelWelcome.random()
    @State private var photo: UIImage?
    @State private var photoURL: String?
    @State private var photoUploading: Bool = false
    @State private var photoUploadError: String?
    @State private var presentingCamera: Bool = false
    @State private var cameraDenied: Bool = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    summaryCard
                    labelPreviewCard
                    measurementsCard
                    customsTierCard
                    photoCard
                    statusBanner
                    actionRow
                }
                .padding(20)
            }
            .scrollContentBackground(.hidden)
            .liquidBackdrop()
            .navigationTitle("Receive parcel")
            .glassNavigationBar(displayMode: .inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .onChange(of: isDone) { _, done in
                if done {
                    onDone()
                    dismiss()
                }
            }
            .task { await loadCustomer() }
            .sheet(isPresented: $presentingCamera) {
                CameraPickerView(source: .camera) { data, failure in
                    presentingCamera = false
                    if let failure {
                        cameraDenied = (failure == .cameraAccessDenied)
                        return
                    }
                    guard let data, let img = UIImage(data: data) else { return }
                    photo = img
                    Task { await uploadPhoto(data: data) }
                }
                .ignoresSafeArea()
            }
        }
        .glassSheet(detents: [.large])
    }

    @ViewBuilder
    private var customsTierCard: some View {
        // Buy-for-me parcels arrive with hs_tier='general' from
        // markPaymentPaid.maybeCreatePreRegisteredParcelForBfm — the
        // BFM concierge thread doesn't carry an HS code so we stub one
        // and let the operator correct it here. Pre-registered customer
        // orders also pass through this sheet, so the picker doubles as
        // a chance to override an mis-categorised customer pick.
        CrystalCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Image(systemName: "checkmark.shield.fill").foregroundStyle(Brand.orange)
                    Text("Customs category").font(.headline).foregroundStyle(Brand.ink)
                }
                Picker("HS tier", selection: $hsTier) {
                    Text("Leave unchanged").tag("")
                    ForEach(hsCategories) { cat in
                        Text(cat.label).tag(cat.key)
                    }
                }
                .pickerStyle(.menu)
                if let active = hsCategories.first(where: { $0.key == hsTier }) {
                    Text(active.note)
                        .font(.caption2).foregroundStyle(.secondary)
                } else {
                    Text("Pick the tier that matches the goods inside. Used to size duty + VAT on the customer's invoice prefill.")
                        .font(.caption2).foregroundStyle(.secondary)
                }
            }
        }
    }

    @ViewBuilder
    private var photoCard: some View {
        // Server already accepts photoUrl on the receive payload
        // (OpsReceiveRequest.photo_url) and the webapp captures it at intake;
        // the iOS path was the one outlier sending nil. Photo is optional —
        // intake still succeeds without one — so the action row stays enabled.
        CrystalCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Image(systemName: "camera.fill").foregroundStyle(Brand.orange)
                    Text("Intake photo").font(.headline).foregroundStyle(Brand.ink)
                    Spacer()
                    if photoUploading {
                        ProgressView().tint(Brand.ink)
                    } else if photoURL != nil {
                        Label("Uploaded", systemImage: "checkmark.icloud.fill")
                            .font(.caption).foregroundStyle(.green)
                    }
                }
                if let photo {
                    Image(uiImage: photo)
                        .resizable()
                        .scaledToFill()
                        .frame(height: 160)
                        .frame(maxWidth: .infinity)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                } else {
                    Text("Optional. Helps ops resolve disputes if a parcel arrives damaged or mismatched.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                if cameraDenied {
                    CalloutBanner(
                        icon: "camera.badge.ellipsis",
                        title: "Camera access denied",
                        message: "Open Settings to enable camera access for Thapsus."
                    )
                    Button("Open Settings") {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }
                    .buttonStyle(.bordered)
                }
                if let msg = photoUploadError {
                    InlineFieldError(message: msg)
                }
                Button(photo == nil ? "Take photo" : "Re-take") {
                    presentingCamera = true
                }
                .buttonStyle(OrangeButtonStyle())
            }
        }
    }

    private func uploadPhoto(data: Data) async {
        await MainActor.run {
            photoUploading = true
            photoUploadError = nil
        }
        let lookupId = pkg.orderId ?? pkg.id
        do {
            let url = try await Self.uploadIntakePhotoViaSignedUrl(
                parcelId: lookupId,
                data: data
            )
            await MainActor.run {
                photoURL = url
                photoUploading = false
            }
        } catch {
            await MainActor.run {
                // Receive itself succeeds without a photo (server treats
                // photo_url as optional), so don't block the intake — just
                // tell the operator the photo didn't make it.
                photoUploadError = "Couldn't upload intake photo (\(error.localizedDescription)). Receive will still save without it."
                photoUploading = false
            }
        }
    }

    /// Asks the server for a signed PUT URL into the public `parcels`
    /// bucket, then uploads the JPEG bytes directly via URLSession —
    /// bypassing the K/N `KotlinByteArray.from(Data)` bridge that froze
    /// the app on multi-MB photos (audit D19 / N1).  Returns the public
    /// URL the server stamps on the response so the receive payload's
    /// `photo_url` field can be set.
    fileprivate static func uploadIntakePhotoViaSignedUrl(
        parcelId: String,
        data: Data
    ) async throws -> String {
        let resp = try await ThapsusSdk.shared.storage()
            .requestParcelUploadUrl(parcelId: parcelId)
        guard let signed = resp.signedUrl, let url = URL(string: signed) else {
            throw NSError(
                domain: "OperatorReceiveView",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "Server did not return a signed URL"]
            )
        }
        var req = URLRequest(url: url)
        req.httpMethod = "PUT"
        req.setValue("image/jpeg", forHTTPHeaderField: "Content-Type")
        req.setValue("3600", forHTTPHeaderField: "Cache-Control")
        let (_, httpResp) = try await URLSession.shared.upload(for: req, from: data)
        if let http = httpResp as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw NSError(
                domain: "OperatorReceiveView",
                code: http.statusCode,
                userInfo: [NSLocalizedDescriptionKey: "Upload rejected (HTTP \(http.statusCode))"]
            )
        }
        // Bucket is public — the public URL resolves directly. Fall back
        // to the path if the server didn't echo a public URL (defensive).
        return resp.publicUrl ?? resp.path ?? signed
    }

    private func loadCustomer() async {
        // PackageDto.id is the package uuid; the customer endpoint keys on the
        // order uuid. Fall back to id only for legacy rows where backfill
        // aligned the two (the 2026-04-29 orphan repair).
        let lookupId = pkg.orderId ?? pkg.id
        let result = try? await ThapsusSdk.shared.packages().fetchCustomer(orderId: lookupId)
        guard let result else { return }
        customerFullName = result.fullName
        customerWarehouseCode = result.warehouseId
        customerDeliveryAddress = result.deliveryAddress
    }

    private var intakeStateValue: IntakeState? { intakeState?.value }
    private var isDone: Bool { intakeStateValue is IntakeStateDone }

    private var customerName: String {
        // Server lookup populates this. Fall back to a placeholder while the
        // fetch is in flight so the preview never silently shows the
        // retailer in place of the customer.
        if let name = customerFullName, !name.isEmpty { return name }
        return "Customer"
    }

    private var summaryCard: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 6) {
                Text(pkg.description_ ?? "Parcel").font(.headline).foregroundStyle(Brand.ink)
                if let r = pkg.retailer {
                    Text(r).font(.subheadline).foregroundStyle(.secondary)
                }
                Text("Order \(pkg.id.prefix(8))…").font(.caption.monospaced()).foregroundStyle(.secondary)

                // Customer delivery address — populated alongside the label
                // fields via /api/ops/parcels/:id/customer. Hidden when the
                // customer hasn't filled an address on their profile; we
                // don't render an empty "deliver to: —" placeholder because
                // the operator's mental model is "address known or not".
                if let address = customerDeliveryAddress?
                    .trimmingCharacters(in: .whitespacesAndNewlines), !address.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Deliver to".uppercased())
                            .font(.caption2.weight(.heavy)).tracking(2)
                            .foregroundStyle(Brand.ink.opacity(0.55))
                        Text(address)
                            .font(.subheadline)
                            .foregroundStyle(Brand.ink)
                            .fixedSize(horizontal: false, vertical: true)
                            .textSelection(.enabled)
                    }
                    .padding(.top, 8)
                }
            }
        }
    }

    private var labelPreviewCard: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("Label preview").font(.headline).foregroundStyle(Brand.ink)
                    Spacer()
                    Button { sku = WarehouseSku.mint() } label: {
                        Label("Re-mint", systemImage: "arrow.triangle.2.circlepath")
                            .font(.caption.weight(.semibold))
                    }
                    .tint(Brand.orange)
                }
                WarehouseLabel(
                    sku: sku,
                    customerName: customerName,
                    customerWarehouseCode: customerWarehouseCode,
                    warehouseCode: ThapsusSdk.shared.appConfig().config.value.warehouseCode,
                    retailer: pkg.retailer,
                    descriptionText: pkg.description_,
                    welcomeMessage: welcomeMessage
                )
                .scaleEffect(0.55)
                .frame(height: 320)
            }
        }
    }

    private var measurementsCard: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Measurements").font(.headline).foregroundStyle(Brand.ink)
                HStack(spacing: 8) {
                    measurementField("L cm", value: $lengthCm)
                    measurementField("W cm", value: $widthCm)
                    measurementField("H cm", value: $heightCm)
                    measurementField("kg", value: $actualKg, format: "%.2f")
                }
                // Customs duty in KES. Operator-stamped at receive (the
                // customer no longer enters weight/dims at order time, so
                // every cost-side input settles here). Feeds the Phase 2
                // invoice prefill on the admin side.
                HStack(spacing: 8) {
                    measurementField("Duty (KES)", value: $customsDutyKes, format: "%.0f")
                }
                Text("Duty is what KRA charges on this parcel — used to prefill the customer's invoice.")
                    .font(.caption2).foregroundStyle(.secondary)
            }
        }
    }

    @ViewBuilder
    private var statusBanner: some View {
        if intakeStateValue is IntakeStateSubmitting {
            CalloutBanner(
                tint: Brand.orange.opacity(0.16),
                icon: "paperplane.fill",
                title: "Saving…",
                message: "Posting receive to the server."
            )
        } else if let failed = intakeStateValue as? IntakeStateFailed {
            ErrorBanner(title: "Couldn't save", message: failed.message)
        }
    }

    private var actionRow: some View {
        VStack(spacing: 10) {
            Button {
                let label = WarehouseLabel(
                    sku: sku,
                    customerName: customerName,
                    customerWarehouseCode: customerWarehouseCode,
                    warehouseCode: ThapsusSdk.shared.appConfig().config.value.warehouseCode,
                    retailer: pkg.retailer,
                    descriptionText: pkg.description_,
                    welcomeMessage: welcomeMessage
                )
                LabelPrinter.print(label) { ok in
                    if ok { printed = true }
                }
            } label: {
                Label("Print label", systemImage: "printer.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))

            Button {
                submit()
            } label: {
                Label(printed ? "Mark received" : "Mark received (skip print)",
                      systemImage: "checkmark.seal.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(GlassSheenButtonStyle())
            .disabled(intakeStateValue is IntakeStateSubmitting)
        }
    }

    private func submit() {
        let dims = ParcelDimensions(
            lengthCm: lengthCm,
            widthCm: widthCm,
            heightCm: heightCm,
            actualKg: actualKg
        )
        intakeVm?.selectExisting(pkg: pkg)
        intakeVm?.submitMeasurements(
            existing: pkg,
            dims: dims,
            photoUrl: photoURL,
            screening: ScreeningResult.clean,
            barcode: sku,
            // Pass nil when the operator left it at 0 so we don't
            // overwrite a duty an admin already stamped via another
            // path. The shared repo treats 0 vs nil identically server-
            // side (the COALESCE skips updates on null) but explicit
            // nil keeps the wire payload smaller.
            customsDutyKes: customsDutyKes > 0 ? KotlinDouble(value: customsDutyKes) : nil,
            // Empty string means "leave unchanged" so a re-receive
            // doesn't downgrade an admin-corrected tier. Server
            // COALESCEs nulls.
            hsTier: hsTier.isEmpty ? nil : hsTier
        )
    }

    @ViewBuilder
    private func measurementField(_ label: String, value: Binding<Double>, format: String = "%.0f") -> some View {
        VStack(spacing: 4) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            TextField(label, value: value, format: .number.precision(.fractionLength(0...2)))
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.center)
                .padding(10)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(Brand.cream.opacity(0.6))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .stroke(Brand.ink.opacity(0.08), lineWidth: 1)
                )
        }
    }
}
