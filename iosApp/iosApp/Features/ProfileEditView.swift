// ProfileEditView.swift
// Edit profile (name/phone/language) and change password. Forgot-password
// flow is exposed via a separate ForgotPasswordView reachable from SignIn.

import SwiftUI
import ThapsusShared

struct ProfileEditView: View {
    @Environment(AppEnvironment.self) private var env

    @State private var vm: ProfileEditViewModel? = nil
    @State private var observer: StateFlowObserver<ProfileEditViewModelFormState>? = nil
    @State private var name: String = ""
    @State private var phone: String = ""
    @State private var language: String = "en"
    @State private var deliveryAddress: String = ""
    @State private var currentPassword: String = ""
    @State private var newPassword: String = ""
    @State private var profileSeeded: Bool = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "Account", systemImage: "person.text.rectangle")
                EditorialHeader(title: "Edit profile", subtitle: "Update your contact details and password.")

                // Hoisted above the form so the user sees the success banner
                // (or error) without scrolling — previously sat at the bottom
                // of the ScrollView and was invisible behind the keyboard.
                statusBanner

                CrystalCard {
                    VStack(spacing: 14) {
                        labelledField(label: "Full name", value: $name)
                        labelledField(label: "Phone", value: $phone)
                        Picker("Language", selection: $language) {
                            Text("English").tag("en")
                            Text("Swahili").tag("sw")
                        }
                        .pickerStyle(.segmented)

                        VStack(alignment: .leading, spacing: 6) {
                            Text("Kenya delivery address".uppercased())
                                .font(.caption2.weight(.heavy))
                                .tracking(2)
                                .foregroundStyle(Brand.ink.opacity(0.5))
                            TextField("e.g. Westlands, Nairobi — Apartment 4B", text: $deliveryAddress, axis: .vertical)
                                .lineLimit(2...4)
                                .textFieldStyle(.plain)
                                .padding(12)
                                .background(
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .fill(Brand.cream.opacity(0.6))
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .stroke(Brand.ink.opacity(0.08), lineWidth: 1)
                                )
                            Text("Riders use this to find you in Nairobi. Leave blank if you collect from the warehouse.")
                                .font(.caption2).foregroundStyle(.secondary)
                        }

                        Button("Save profile") {
                            vm?.save(
                                name: name.isEmpty ? nil : name,
                                phone: phone.isEmpty ? nil : phone,
                                languagePref: language,
                                // Pass the empty string through (not nil) so
                                // the server treats it as a deliberate clear
                                // when the user wipes the field.
                                deliveryAddress: deliveryAddress
                            )
                        }
                        .buttonStyle(GlassSheenButtonStyle())
                    }
                }

                SectionHeader(title: "Change password")
                CrystalCard {
                    VStack(spacing: 14) {
                        secureField("Current password", text: $currentPassword)
                        secureField("New password (8+ chars)", text: $newPassword)
                        Button("Change password") {
                            vm?.changePassword(current: currentPassword, new: newPassword)
                        }
                        .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
                        .disabled(currentPassword.isEmpty || newPassword.count < 8)
                    }
                }
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Profile")
        .glassNavigationBar()
        .task { bootstrap() }
    }

    private func labelledField(label: String, value: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label.uppercased())
                .font(.caption2.weight(.heavy))
                .tracking(2)
                .foregroundStyle(Brand.ink.opacity(0.5))
            TextField(label, text: value)
                .textFieldStyle(.plain)
                .padding(12)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(Brand.cream.opacity(0.6))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(Brand.ink.opacity(0.08), lineWidth: 1)
                )
        }
    }

    private func secureField(_ placeholder: String, text: Binding<String>) -> some View {
        SecureField(placeholder, text: text)
            .textFieldStyle(.plain)
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Brand.cream.opacity(0.6))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(Brand.ink.opacity(0.08), lineWidth: 1)
            )
    }

    @ViewBuilder
    private var statusBanner: some View {
        switch observer?.value {
        case let saved as ProfileEditViewModelFormStateSaved:
            CalloutBanner(icon: "checkmark.circle.fill", title: "Saved", message: saved.message)
        case let err as ProfileEditViewModelFormStateError:
            ErrorBanner(title: "Couldn't save", message: err.message)
        case is ProfileEditViewModelFormStateSubmitting:
            ProgressView().frame(maxWidth: .infinity)
        default: EmptyView()
        }
    }

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.profileEditViewModel()
        vm = model
        observer = StateFlowObserver(initial: model.form.value) {
            model.form
        }
        // Seed the editable fields from the current AuthSession so the user
        // sees their existing values rather than blank inputs. Only runs
        // once per view-mount; subsequent saves rely on FormState updates.
        if !profileSeeded, let auth = env.session as? AuthSessionAuthenticated, let p = auth.profile {
            name             = p.fullName ?? ""
            phone            = p.phone ?? ""
            language         = p.languagePref
            deliveryAddress  = p.deliveryAddress ?? ""
            profileSeeded    = true
        }
    }
}

struct ForgotPasswordView: View {
    @State private var vm: ProfileEditViewModel? = nil
    @State private var observer: StateFlowObserver<ProfileEditViewModelFormState>? = nil
    @State private var email: String = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                EyebrowPill(label: "Recovery", systemImage: "key.fill")
                EditorialHeader(title: "Forgot password?", subtitle: "We'll email you a reset link.")

                CrystalCard {
                    VStack(spacing: 14) {
                        TextField("Email", text: $email)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .textFieldStyle(.plain)
                            .padding(12)
                            .background(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(Brand.cream.opacity(0.6))
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .stroke(Brand.ink.opacity(0.08), lineWidth: 1)
                            )

                        Button("Send reset link") { vm?.forgotPassword(email: email) }
                            .buttonStyle(GlassSheenButtonStyle())
                            .disabled(email.isEmpty)
                    }
                }

                statusBanner
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Reset password")
        .glassNavigationBar()
        .task { bootstrap() }
    }

    @ViewBuilder
    private var statusBanner: some View {
        switch observer?.value {
        case let saved as ProfileEditViewModelFormStateSaved:
            CalloutBanner(icon: "envelope.badge", title: "Sent", message: saved.message)
        case let err as ProfileEditViewModelFormStateError:
            ErrorBanner(title: "Couldn't send", message: err.message)
        case is ProfileEditViewModelFormStateSubmitting:
            ProgressView().frame(maxWidth: .infinity)
        default: EmptyView()
        }
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
