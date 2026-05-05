// DsarView.swift
// GDPR data subject access — request export or erase. Existing requests
// are listed below the form so the user can see status without contacting us.

import SwiftUI
import ThapsusShared

struct DsarView: View {
    @State private var vm: DsarViewModel? = nil
    @State private var stateObserver: StateFlowObserver<DsarViewModelUiState>? = nil
    @State private var formObserver: StateFlowObserver<DsarViewModelFormState>? = nil
    @State private var type: String = "export"
    @State private var notes: String = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "GDPR", systemImage: "lock.shield")
                EditorialHeader(title: "Your data, your rights",
                                subtitle: "Request a copy of your data, or ask us to erase your account. We respond within 30 days.")

                CrystalCard {
                    VStack(alignment: .leading, spacing: 14) {
                        Picker("Type", selection: $type) {
                            Text("Export").tag("export")
                            Text("Erase").tag("erase")
                        }
                        .pickerStyle(.segmented)

                        VStack(alignment: .leading, spacing: 6) {
                            Text("Notes (optional)".uppercased())
                                .font(.caption2.weight(.heavy)).tracking(2)
                                .foregroundStyle(Brand.ink.opacity(0.5))
                            TextEditor(text: $notes)
                                .frame(minHeight: 100)
                                .scrollContentBackground(.hidden)
                                .padding(8)
                                .background(
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .fill(Brand.cream.opacity(0.6))
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .stroke(Brand.ink.opacity(0.08), lineWidth: 1)
                                )
                        }

                        Button("Submit request") {
                            vm?.submit(type: type, notes: notes.isEmpty ? nil : notes)
                        }
                        .buttonStyle(GlassSheenButtonStyle())
                    }
                }

                formBanner

                SectionHeader(title: "Your requests")
                requestsList
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Data rights")
        .glassNavigationBar()
        .task { bootstrap() }
    }

    @ViewBuilder
    private var formBanner: some View {
        switch formObserver?.value {
        case let done as DsarViewModelFormStateSubmitted:
            CalloutBanner(icon: "checkmark.circle.fill", title: "Submitted", message: done.message)
        case let err as DsarViewModelFormStateError:
            ErrorBanner(title: "Couldn't submit", message: err.message)
        case is DsarViewModelFormStateSubmitting:
            ProgressView().frame(maxWidth: .infinity)
        default: EmptyView()
        }
    }

    @ViewBuilder
    private var requestsList: some View {
        switch stateObserver?.value {
        case let loaded as DsarViewModelUiStateLoaded:
            if loaded.requests.isEmpty {
                CrystalCard { Text("No previous requests.").font(.subheadline).foregroundStyle(.secondary) }
            } else {
                ForEach(loaded.requests, id: \.id) { req in
                    CrystalCard {
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text(req.type.capitalized).font(.headline).foregroundStyle(Brand.ink)
                                Spacer()
                                Text(req.status.uppercased())
                                    .font(.system(size: 9, weight: .heavy)).tracking(2)
                                    .foregroundStyle(.secondary)
                            }
                            if let created = req.createdAt {
                                Text(created).font(.caption).foregroundStyle(.secondary)
                            }
                            if let url = req.exportUrl {
                                Link("Download export", destination: URL(string: url) ?? URL(string: "https://thapsus.uk")!)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(Brand.orange)
                            }
                        }
                    }
                }
            }
        case is DsarViewModelUiStateLoading:
            ProgressView().frame(maxWidth: .infinity)
        default: EmptyView()
        }
    }

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.dsarViewModel()
        vm = model
        model.load()
        stateObserver = StateFlowObserver(initial: model.state.value) {
            model.state
        }
        formObserver = StateFlowObserver(initial: model.form.value) {
            model.form
        }
    }
}
