// AddressCaptureSheet.swift
// Post-authentication address-capture prompt. Surfaces once after the
// user signs in or registers, only when their `delivery_address` is
// blank on the server. Users can save the address or dismiss; either
// path persists a "asked" flag in UserDefaults so we don't pester on
// every cold launch. The Account tab's "Edit profile" entry stays the
// canonical way to update the address later (this sheet just kicks the
// first capture).
//
// Submits via the same `ProfileEditViewModel.save(deliveryAddress:)`
// path as `ProfileEditView`, so the server-side route + KMP plumbing
// for saving is already audited and consistent.

import SwiftUI
import ThapsusShared

struct AddressCaptureSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var deliveryAddress: String = ""
    @State private var vm: ProfileEditViewModel? = nil
    @State private var observer: StateFlowObserver<ProfileEditViewModelFormState>? = nil
    @FocusState private var addressFocused: Bool

    /// Called when the user successfully saves or chooses to skip. The
    /// caller is responsible for flipping the persisted "asked" flag so
    /// the sheet isn't re-presented on the next cold launch.
    var onResolved: () -> Void

    var body: some View {
        NavigationStack {
            ZStack {
                AppBackground().ignoresSafeArea()

                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Where should we deliver?")
                                .font(.editorialTitle)
                                .foregroundStyle(Brand.ink)
                                .fixedSize(horizontal: false, vertical: true)
                            Text("Add your Kenya delivery address so our riders know where to drop your parcels. You can change it any time from the Account tab.")
                                .font(.subheadline)
                                .foregroundStyle(Brand.ink.opacity(0.7))
                                .fixedSize(horizontal: false, vertical: true)
                        }

                        VStack(alignment: .leading, spacing: 8) {
                            Text("Kenya delivery address".uppercased())
                                .font(.caption2.weight(.heavy))
                                .tracking(2)
                                .foregroundStyle(Brand.ink.opacity(0.55))
                            TextField(
                                "e.g. Westlands, Nairobi — Apartment 4B",
                                text: $deliveryAddress,
                                axis: .vertical
                            )
                            .lineLimit(3...6)
                            .textFieldStyle(.plain)
                            .focused($addressFocused)
                            .padding(14)
                            .background(
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .fill(Brand.cream.opacity(0.7))
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .stroke(Brand.ink.opacity(0.10), lineWidth: 1)
                            )
                            Text("Include landmarks, gate numbers, or anything that helps a rider find you in Nairobi.")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }

                        statusBanner

                        Button(action: save) {
                            Text(isSubmitting ? "Saving…" : "Save address")
                                .font(.headline)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 4)
                        }
                        .buttonStyle(InkButtonStyle())
                        .disabled(trimmedAddress.isEmpty || isSubmitting)

                        Button(action: skip) {
                            Text("Skip for now")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Brand.ink.opacity(0.65))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 22)
                    .padding(.top, 24)
                    .padding(.bottom, 36)
                }
            }
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.fraction(0.85), .large])
        .presentationDragIndicator(.visible)
        .interactiveDismissDisabled(false)
        .task { bootstrap() }
        .onChange(of: observer?.value.map { String(describing: type(of: $0)) }) { _, _ in
            if case is ProfileEditViewModelFormStateSaved = observer?.value {
                AppHaptics.fire(.success)
                onResolved()
                dismiss()
            }
        }
        .onAppear { addressFocused = true }
    }

    // MARK: - Helpers

    private var trimmedAddress: String {
        deliveryAddress.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var isSubmitting: Bool {
        observer?.value is ProfileEditViewModelFormStateSubmitting
    }

    @ViewBuilder
    private var statusBanner: some View {
        if case let err as ProfileEditViewModelFormStateError = observer?.value {
            ErrorBanner(title: "Couldn't save", message: err.message)
        }
    }

    private func save() {
        guard !trimmedAddress.isEmpty else { return }
        vm?.save(
            name: nil,
            phone: nil,
            languagePref: nil,
            deliveryAddress: trimmedAddress
        )
    }

    private func skip() {
        AppHaptics.fire(.tap)
        onResolved()
        dismiss()
    }

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.profileEditViewModel()
        vm = model
        observer = StateFlowObserver(initial: model.form.value) {
            model.form
        }
    }
}
