// SignInView.swift
// Real Supabase Auth flow. Phase 3 binds this to AuthViewModel; the dev role-pick
// is gone — sign in or create an account.

import SwiftUI
import ThapsusShared

struct SignInView: View {
    @Environment(AppEnvironment.self) private var env

    @State private var email: String = ""
    @State private var password: String = ""
    @State private var fullName: String = ""
    @State private var phone: String = ""
    @State private var country: String = ""
    @State private var isSignUp: Bool = false
    @State private var formObserver: StateFlowObserver<AuthViewModelFormState>?
    @State private var presentingForgot: Bool = false

    /// Country list mirrors the webapp dropdown — keep the pair in sync when
    /// adding a new market. The empty entry lets users skip the field.
    private let countries: [(code: String, label: String)] = [
        ("", "Country (optional)"),
        ("KE", "Kenya"),
        ("UG", "Uganda"),
        ("TZ", "Tanzania"),
        ("RW", "Rwanda"),
        ("GB", "United Kingdom"),
        ("US", "United States"),
        ("OTHER", "Other")
    ]

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                VStack(alignment: .leading, spacing: 16) {
                    BrandWordmark(size: .medium)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    EyebrowPill(label: "Welcome", systemImage: "airplane.departure")
                    Text("UK → Kenya,\nin one weekly flight.")
                        .font(.editorialDisplay)
                        .foregroundStyle(Brand.ink)
                        .lineLimit(2)
                    Text("Sign in to track your shipments and manage your wallet.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, 40)

                SoftCard {
                    VStack(spacing: 14) {
                        if isSignUp {
                            inputField("Full name", text: $fullName)
                                .textInputAutocapitalization(.words)
                        }

                        inputField("Email", text: $email)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)

                        if isSignUp {
                            inputField("Phone (optional)", text: $phone)
                                .keyboardType(.phonePad)
                            countryPicker
                        }

                        SecureField("Password", text: $password)
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

                        if let busy = formObserver?.value, busy is AuthViewModelFormStateSubmitting {
                            ProgressView().tint(Brand.ink)
                                .padding(.vertical, 4)
                        } else {
                            Button(action: submit) {
                                Text(isSignUp ? "Create account" : "Sign in")
                            }
                            .buttonStyle(InkButtonStyle())
                        }

                        Button(isSignUp ? "Already have an account? Sign in" : "Create an account") {
                            isSignUp.toggle()
                        }
                        .font(.footnote)
                        .foregroundStyle(Brand.orange)

                        if !isSignUp {
                            // Audit §2.6 / S0-10: forgot-password used to be
                            // reachable only from the authenticated profile
                            // screen, locking out anyone who'd actually
                            // forgotten their credentials. The reset email is
                            // sent by /auth/forgot-password — the deep-link
                            // handler for /reset/<token> is a separate
                            // follow-up that needs server AASA work.
                            Button("Forgot password?") { presentingForgot = true }
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                if let err = formObserver?.value as? AuthViewModelFormStateError {
                    ErrorBanner(title: "Couldn't sign you in", message: err.message)
                } else if let sent = formObserver?.value as? AuthViewModelFormStateSent {
                    CalloutBanner(
                        icon: "envelope.badge",
                        title: "Check your inbox",
                        message: sent.message
                    )
                }

                HowItWorksView()
                    .padding(.top, 16)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 40)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .sheet(isPresented: $presentingForgot) {
            NavigationStack { ForgotPasswordView() }
        }
        .task {
            env.bootstrap()
            guard let vm = env.authVM, formObserver == nil else { return }
            formObserver = StateFlowObserver(initial: vm.form.value) {
                vm.form
            }
        }
    }

    private func submit() {
        guard let vm = env.authVM else { return }
        if isSignUp {
            // Forward optional fields to AuthRepository; server's RegisterRequest
            // accepts name/phone/country_of_residence, used to populate the
            // public.users row + future M-Pesa SMS / customs declarations.
            vm.signUp(
                email: email,
                password: password,
                name: fullName,
                phone: phone,
                countryOfResidence: country.isEmpty ? nil : country
            )
        } else {
            vm.signIn(email: email, password: password)
        }
    }

    @ViewBuilder
    private func inputField(_ placeholder: String, text: Binding<String>) -> some View {
        TextField(placeholder, text: text)
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
    private var countryPicker: some View {
        Picker("Country", selection: $country) {
            ForEach(countries, id: \.code) { c in
                Text(c.label).tag(c.code)
            }
        }
        .pickerStyle(.menu)
        .tint(Brand.ink)
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
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
