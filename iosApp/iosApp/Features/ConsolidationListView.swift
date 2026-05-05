// ConsolidationListView.swift
// Spec §3.3 /ops/consolidations. Lists every flight unit; tap one to land
// on the manifest builder. Operators can open a new consolidation on demand
// (no Monday cadence — cut-off is whatever they pick).

import SwiftUI
import ThapsusShared

struct ConsolidationListView: View {
    @State private var vm: ConsolidationListViewModel?
    @State private var list: StateFlowObserver<[ConsolidationDto]>?
    @State private var createState: StateFlowObserver<ConsolidationListViewModelCreateState>?
    @State private var showCreate: Bool = false

    var body: some View {
        ScrollView {
            GlassEffectContainer(spacing: 16) {
                VStack(spacing: 16) {
                    SectionHeader(
                        title: "Consolidations",
                        subtitle: "Flight units"
                    )

                    if let err = (createState?.value as? ConsolidationListViewModelCreateStateError)?.message {
                        GlassCard {
                            Label("Create failed: \(err)", systemImage: "exclamationmark.triangle.fill")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.red)
                        }
                    }

                    if let rows = list?.value, !rows.isEmpty {
                        ForEach(rows, id: \.id) { c in
                            NavigationLink {
                                ConsolidationDetailView(consolidationId: c.id)
                            } label: {
                                ConsolidationRow(c: c)
                            }
                            .buttonStyle(.plain)
                        }
                    } else {
                        GlassCard {
                            Text("No consolidations yet. Tap + to start one whenever you're ready.")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .padding(20)
            }
        }
        .navigationTitle("Consolidations")
        .glassNavigationBar()
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { showCreate = true } label: {
                    Label("New", systemImage: "plus.circle.fill")
                }
            }
        }
        .sheet(isPresented: $showCreate) {
            CreateConsolidationSheet { weekStart, cutoffAt, departureAt, notes in
                vm?.create(weekStart: weekStart, cutoffAt: cutoffAt, departureAt: departureAt, notes: notes)
            }
        }
        .refreshable { vm?.refresh() }
        .task {
            guard vm == nil else { return }
            let v = ThapsusSdk.shared.consolidationListViewModel()
            self.vm = v
            self.list = StateFlowObserver(initial: []) { v.list }
            // Initial value uses the SKIE-bridged Idle subclass so the Swift
            // generic locks to the protocol's concrete type at compile time.
            self.createState = StateFlowObserver(
                initial: ConsolidationListViewModelCreateStateIdle()
            ) { v.createState }
        }
        .onDisappear { vm?.clear(); vm = nil; list = nil; createState = nil }
    }
}

private struct ConsolidationRow: View {
    let c: ConsolidationDto

    var body: some View {
        GlassCard {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Week of \(c.weekStart)").font(.headline)
                    Text("Cut-off \(c.cutoffAt)").font(.caption).foregroundStyle(.secondary)
                    HStack(spacing: 6) {
                        GlassChip(title: friendly(c.status), systemImage: "shippingbox.fill", tint: tint(c.status))
                        if let awb = c.masterAwbNo, !awb.isEmpty {
                            GlassChip(title: "AWB \(awb)", systemImage: "airplane")
                        }
                    }
                    .padding(.top, 4)
                }
                Spacer()
                VStack(alignment: .trailing) {
                    Text("\(c.totalParcels) parcels").font(.subheadline.weight(.semibold))
                    Text(String(format: "%.1f kg", Double(truncating: c.totalKg as NSNumber)))
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
                Image(systemName: "chevron.right").foregroundStyle(.tertiary)
            }
        }
    }

    private func friendly(_ s: ConsolidationStatus) -> String {
        switch s {
        case .open: return "Open"
        case .cutoffLocked: return "Locked"
        case .manifested: return "Manifested"
        case .handedToTudor: return "Handed to Tudor"
        case .inTransit: return "In transit"
        case .jkiaArrived: return "Arrived JKIA"
        case .cleared: return "Cleared"
        case .closed: return "Closed"
        @unknown default: return "—"
        }
    }

    private func tint(_ s: ConsolidationStatus) -> Color? {
        switch s {
        case .open: return Brand.gold
        case .inTransit: return .blue.opacity(0.4)
        case .closed: return .green.opacity(0.4)
        default: return nil
        }
    }
}

/// Sheet for starting a new consolidation. `cutoffAt` defaults to 7 days out;
/// `weekStart` defaults to today. Both are editable so an operator can open a
/// consolidation any day of the week.
private struct CreateConsolidationSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var weekStart: Date = Date()
    @State private var cutoffAt: Date = Calendar.current.date(byAdding: .day, value: 7, to: Date()) ?? Date()
    @State private var hasDeparture: Bool = false
    @State private var departureAt: Date = Calendar.current.date(byAdding: .day, value: 8, to: Date()) ?? Date()
    @State private var notes: String = ""
    @State private var submitted: Bool = false

    let onSubmit: (_ weekStart: String, _ cutoffAt: String, _ departureAt: String?, _ notes: String?) -> Void

    private static let dateOnly: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withFullDate]
        return f
    }()
    private static let isoTimestamp: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    var body: some View {
        NavigationStack {
            Form {
                Section("Schedule") {
                    DatePicker("Week start", selection: $weekStart, displayedComponents: .date)
                    DatePicker("Cut-off", selection: $cutoffAt)
                    Toggle("Set departure date", isOn: $hasDeparture)
                    if hasDeparture {
                        DatePicker("Departure", selection: $departureAt)
                    }
                }
                Section("Notes (optional)") {
                    TextField("e.g. flight, broker note", text: $notes, axis: .vertical)
                        .lineLimit(2...5)
                }
            }
            .navigationTitle("New consolidation")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(submitted ? "Creating…" : "Create") {
                        guard !submitted else { return }
                        submitted = true
                        onSubmit(
                            Self.dateOnly.string(from: weekStart),
                            Self.isoTimestamp.string(from: cutoffAt),
                            hasDeparture ? Self.isoTimestamp.string(from: departureAt) : nil,
                            notes.isEmpty ? nil : notes
                        )
                        dismiss()
                    }
                    .disabled(submitted)
                }
            }
        }
    }
}
