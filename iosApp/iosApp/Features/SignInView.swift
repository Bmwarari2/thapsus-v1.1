// SignInView.swift
// Liquid-glass redesign of the sign-in front door. Centered orange logo block,
// "Welcome back" headline, glass card with email/password fields, primary CTA,
// secondary "Create account" toggle. Forgot-password link is preserved.

import SwiftUI
import ThapsusShared

/// Identifiable wrapper used by SignInView's `.sheet(item:)` to present
/// the in-app Safari view for Terms / Privacy links during sign-up. The
/// `IdentifiableURL` defined in RoleHomeViews.swift is private to that
/// file, so we declare a parallel one here rather than cross-importing.
fileprivate struct SignInLegalURL: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

struct SignInView: View {
    @Environment(AppEnvironment.self) private var env

    @State private var email: String = ""
    @State private var password: String = ""
    @State private var confirmPassword: String = ""
    @State private var fullName: String = ""
    @State private var phone: String = ""
    @State private var country: String = ""
    @State private var isSignUp: Bool = false
    @State private var formObserver: StateFlowObserver<AuthViewModelFormState>?
    @State private var presentingForgot: Bool = false
    // Audit follow-up — graceful 401 UX. AuthEventFlags.sessionExpired
    // is set when the API client detects a 401 (token revoked, password
    // changed elsewhere, account disabled). The first time SignInView
    // appears after that, we surface a banner so the user knows why
    // they're back here. Read once, clear, never ask again until the
    // next involuntary sign-out.
    @State private var sessionExpiredBanner: Bool = false

    // Sign-up: user must explicitly agree to ToS + Privacy before the
    // Create-account submit unlocks. Reset back to false whenever the
    // user toggles back into sign-in mode so the agreement is always
    // a deliberate act for a fresh account.
    @State private var agreedToTerms: Bool = false
    @State private var legalSheetURL: SignInLegalURL?

    private let countries: [(code: String, label: String)] = [
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
            VStack(spacing: 0) {
                heroBlock
                    .padding(.top, 60)
                    .padding(.bottom, 28)

                formCard
                    .padding(.horizontal, 18)

                if sessionExpiredBanner {
                    LGStatusBanner(
                        tone: .info,
                        title: "Your session expired",
                        message: "Please sign in again to continue where you left off."
                    )
                    .padding(.horizontal, 18)
                    .padding(.top, 14)
                }

                if let verif = formObserver?.value as? AuthViewModelFormStateVerificationSent {
                    verificationPendingBanner(email: verif.email)
                        .padding(.horizontal, 18)
                        .padding(.top, 14)
                } else if let err = formObserver?.value as? AuthViewModelFormStateError {
                    if err.code == "email_unverified" {
                        emailUnverifiedBanner(email: email)
                            .padding(.horizontal, 18)
                            .padding(.top, 14)
                    } else {
                        LGStatusBanner(tone: .err, title: "Couldn't sign you in", message: err.message)
                            .padding(.horizontal, 18)
                            .padding(.top, 14)
                    }
                } else if let sent = formObserver?.value as? AuthViewModelFormStateSent {
                    LGStatusBanner(tone: .info, title: "Check your inbox", message: sent.message)
                        .padding(.horizontal, 18)
                        .padding(.top, 14)
                }

                HStack(spacing: 6) {
                    Text(isSignUp ? "Already have an account?" : "New here?")
                        .foregroundStyle(LG.fg3)
                    Button(isSignUp ? "Sign in" : "Create account") {
                        withAnimation(LG.animation) {
                            isSignUp.toggle()
                            // Always require a fresh, deliberate
                            // agreement when entering the sign-up
                            // path — never carry over a tick from a
                            // previous session.
                            agreedToTerms = false
                        }
                    }
                    .foregroundStyle(LG.accent2)
                    .fontWeight(.bold)
                }
                .font(.body(14, weight: .medium))
                .padding(.top, 28)
            }
            .padding(.bottom, 40)
        }
        .scrollContentBackground(.hidden)
        .background(LiquidGlassBackground())
        .sheet(isPresented: $presentingForgot) {
            NavigationStack { ForgotPasswordView() }
        }
        .sheet(item: $legalSheetURL) { wrapper in
            // Re-uses the SafariView wrapper defined in
            // RoleHomeViews.swift so terms/privacy open in-app rather
            // than kicking the user out to system Safari.
            SafariView(url: wrapper.url)
                .ignoresSafeArea()
        }
        .task {
            env.bootstrap()
            guard let vm = env.authVM, formObserver == nil else { return }
            formObserver = StateFlowObserver(initial: vm.form.value) { vm.form }
        }
        .onChange(of: verificationJustSent) { _, sent in
            // When the server accepts the registration but flags it as
            // awaiting verification, leave the sign-up form behind. The
            // user came here to create an account; they've done that.
            // Now they wait for the email + sign in. We:
            //   - flip back to sign-in mode (clears the sign-up-only
            //     section of the card)
            //   - clear the password fields so the next person on a
            //     shared device can't lift them
            //   - keep the email pre-filled so they only have to type
            //     the password when the activation lands
            // The verificationPendingBanner stays visible above the
            // sign-in form — it lives outside the formCard.
            guard sent else { return }
            withAnimation(LG.animation) {
                isSignUp = false
                password = ""
                confirmPassword = ""
                fullName = ""
                phone = ""
                country = ""
                agreedToTerms = false
            }
        }
        .onAppear {
            // Audit follow-up — read + clear the AuthEventFlags
            // sessionExpired one-shot. Setting both the local @State
            // and the static flag back to false means the banner is
            // shown once per server-driven sign-out and not on every
            // navigate to /sign-in.
            if AuthEventFlags.shared.sessionExpired {
                sessionExpiredBanner = true
                AuthEventFlags.shared.sessionExpired = false
            }
        }
    }

    private var heroBlock: some View {
        VStack(spacing: 24) {
            LGLogoBlock(size: 72)
            VStack(spacing: 8) {
                Text(isSignUp ? "Create account" : "Welcome back")
                    .font(.display(30, weight: .heavy))
                    .foregroundStyle(LG.fg)
                Text(isSignUp
                     ? "Sign up to start shipping with Thapsus."
                     : "Sign in to your Thapsus Cargo account")
                    .font(.body(15, weight: .medium))
                    .foregroundStyle(LG.fg3)
                    .multilineTextAlignment(.center)
            }
        }
    }

    private var formCard: some View {
        GlassPanel(corner: LG.Radius.xl, padding: 18) {
            VStack(spacing: 12) {
                if isSignUp {
                    LGTextField(label: "Full name", placeholder: "Alex Mwangi",
                                text: $fullName, capitalization: .words)
                }

                LGTextField(label: "Email", placeholder: "you@email.com",
                            text: $email, keyboard: .emailAddress, capitalization: .never)

                if isSignUp {
                    LGTextField(label: "Phone", placeholder: "+254…",
                                text: $phone, keyboard: .phonePad, capitalization: .never)
                    countryPicker
                }

                LGTextField(label: "Password", placeholder: "••••••••",
                            text: $password, capitalization: .never, isSecure: true)

                if isSignUp {
                    LGTextField(label: "Confirm password", placeholder: "••••••••",
                                text: $confirmPassword, capitalization: .never, isSecure: true)
                    passwordRequirements
                        .padding(.top, 2)
                    if !confirmPassword.isEmpty && !passwordsMatch {
                        HStack(spacing: 8) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.red)
                            Text("Passwords don't match.")
                                .font(.body(12, weight: .medium))
                                .foregroundStyle(.red)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.leading, 4)
                    }
                }

                if !isSignUp {
                    HStack {
                        Spacer()
                        Button("Forgot password?") { presentingForgot = true }
                            .font(.body(13, weight: .semibold))
                            .foregroundStyle(LG.accent2)
                    }
                    .padding(.top, -2)
                }

                if isSignUp {
                    termsAgreementBlock
                        .padding(.top, 4)
                }

                if let busy = formObserver?.value, busy is AuthViewModelFormStateSubmitting {
                    HStack { Spacer(); ProgressView().tint(LG.accent); Spacer() }
                        .padding(.vertical, 8)
                } else {
                    Button(action: submit) {
                        HStack(spacing: 6) {
                            Text(isSignUp ? "Create account" : "Sign in")
                            Image(systemName: "arrow.right")
                                .font(.footnote.weight(.bold))
                        }
                    }
                    .buttonStyle(LGPrimaryButtonStyle())
                    .padding(.top, 6)
                    .disabled(isSignUp && !signUpReady)
                    .opacity(isSignUp && !signUpReady ? 0.5 : 1.0)
                }
            }
        }
    }

    // Live password requirements driven by the shared `PasswordPolicy`.
    // Same source-of-truth (and same wording / order) Android renders,
    // so the customer sees the identical checklist on both platforms.
    // Each rule turns from a hollow circle (pending, muted) into a
    // filled check (passed, accent) as the user types. The Create
    // account button stays disabled until every rule passes (see the
    // `.disabled(...)` modifier on the submit button above).
    @ViewBuilder
    private var passwordRequirements: some View {
        let passed = PasswordPolicy.shared.check(password: password)
        VStack(alignment: .leading, spacing: 6) {
            ForEach(PasswordPolicy.shared.rules, id: \.label) { rule in
                let isPassed = passed.contains(rule)
                HStack(spacing: 8) {
                    Image(systemName: isPassed ? "checkmark.circle.fill" : "circle")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(isPassed ? LG.accent2 : LG.fgMute)
                    Text(rule.label)
                        .font(.body(12, weight: isPassed ? .semibold : .regular))
                        .foregroundStyle(isPassed ? LG.fg : LG.fg3)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.leading, 4)
    }

    // Sign-up agreement row. A square checkbox + the legal disclosure
    // text with two inline buttons that open Terms / Privacy in the
    // in-app SFSafariViewController (re-uses SafariView from
    // RoleHomeViews.swift). Submit gating is on the @State above.
    @ViewBuilder
    private var termsAgreementBlock: some View {
        HStack(alignment: .top, spacing: 12) {
            Button {
                withAnimation(LG.animation) { agreedToTerms.toggle() }
            } label: {
                Image(systemName: agreedToTerms ? "checkmark.square.fill" : "square")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(agreedToTerms ? LG.accent2 : LG.fgMute)
                    .padding(.top, 1)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("I agree to the Terms of Service and Privacy Policy")
            .accessibilityAddTraits(agreedToTerms ? [.isSelected] : [])

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 0) {
                    Text("I agree to Thapsus Cargo's ")
                        .font(.body(13, weight: .medium))
                        .foregroundStyle(LG.fg2)
                    Button("Terms of Service") {
                        legalSheetURL = SignInLegalURL(url: URL(string: "https://thapsus.uk/terms")!)
                    }
                    .font(.body(13, weight: .semibold))
                    .foregroundStyle(LG.accent2)
                    .buttonStyle(.plain)
                }
                HStack(spacing: 0) {
                    Text("and ")
                        .font(.body(13, weight: .medium))
                        .foregroundStyle(LG.fg2)
                    Button("Privacy Policy") {
                        legalSheetURL = SignInLegalURL(url: URL(string: "https://thapsus.uk/privacy")!)
                    }
                    .font(.body(13, weight: .semibold))
                    .foregroundStyle(LG.accent2)
                    .buttonStyle(.plain)
                    Text(".")
                        .font(.body(13, weight: .medium))
                        .foregroundStyle(LG.fg2)
                }
            }
            Spacer(minLength: 0)
        }
        .contentShape(Rectangle())
    }

    @ViewBuilder
    private var countryPicker: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("COUNTRY")
                .font(.body(11, weight: .bold))
                .tracking(0.6)
                .foregroundStyle(LG.fg3)
            Menu {
                ForEach(countries, id: \.code) { c in
                    Button(c.label) { country = c.code }
                }
            } label: {
                HStack {
                    Text(countries.first { $0.code == country }?.label ?? "Select country")
                        .foregroundStyle(country.isEmpty ? LG.fgMute : LG.fg)
                    Spacer()
                    Image(systemName: "chevron.down")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(LG.fgMute)
                }
                .font(.body(15, weight: .medium))
                .padding(.vertical, 13)
                .padding(.horizontal, 16)
                .background(
                    ZStack {
                        RoundedRectangle(cornerRadius: LG.Radius.lg, style: .continuous)
                            .fill(.ultraThinMaterial)
                        RoundedRectangle(cornerRadius: LG.Radius.lg, style: .continuous)
                            .fill(LG.glassBg)
                    }
                    .allowsHitTesting(false)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: LG.Radius.lg, style: .continuous)
                        .strokeBorder(LG.glassBorder, lineWidth: 1)
                )
            }
        }
    }

    private func submit() {
        guard let vm = env.authVM else { return }
        if isSignUp {
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

    /// True when both password fields are non-empty and match
    /// character-for-character. The mismatch banner above and the
    /// Create-account disable gate read from the same source so the
    /// affordance + the warning never disagree.
    private var passwordsMatch: Bool {
        !password.isEmpty && password == confirmPassword
    }

    /// Bool projection of `FormState.VerificationSent` used as the diff
    /// key on the `onChange` that flips us out of sign-up mode (see the
    /// view's task chain). Same workaround pattern as
    /// `AddressCaptureSheet.didSucceed` — Swift's optional dot-chain
    /// binds `.map` to the inner value rather than the Optional, so a
    /// computed Bool is the cleaner diff key.
    private var verificationJustSent: Bool {
        formObserver?.value is AuthViewModelFormStateVerificationSent
    }

    /// All-or-nothing gate for the Create-account button: terms ticked,
    /// password meets the shared policy (used for parity with Android),
    /// and the confirm-password field matches. The OR-fallback existed
    /// before email-verification landed but the explicit `passwordsMatch`
    /// is new in PR P so the user can't slip a typo'd password through.
    private var signUpReady: Bool {
        agreedToTerms
            && PasswordPolicy.shared.isValid(password: password)
            && passwordsMatch
    }

    /// Banner shown after a successful sign-up. The account exists on the
    /// server but is awaiting activation via the link in
    /// sendEmailVerificationEmail. We surface the email address that was
    /// registered (helps the user recognise the inbox to check) plus a
    /// "Resend activation" button wired to AuthViewModel.resendVerification.
    @ViewBuilder
    private func verificationPendingBanner(email: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            LGStatusBanner(
                tone: .info,
                title: "Check your inbox",
                message: "We sent an activation link to \(email). Tap it to finish setting up your account."
            )
            Button("Resend activation email") {
                env.authVM?.resendVerification(email: email)
            }
            .font(.body(13, weight: .semibold))
            .foregroundStyle(LG.accent2)
        }
    }

    /// Login error banner for the email_unverified server code. Same
    /// recovery affordance as verificationPendingBanner — resend the
    /// activation email — but with a sign-in-context headline so the
    /// user knows why the password they remember isn't getting them in.
    @ViewBuilder
    private func emailUnverifiedBanner(email: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            LGStatusBanner(
                tone: .err,
                title: "Email not verified",
                message: "Activate your account from the link we emailed to \(email.isEmpty ? "your inbox" : email), then sign in again."
            )
            if !email.isEmpty {
                Button("Resend activation email") {
                    env.authVM?.resendVerification(email: email)
                }
                .font(.body(13, weight: .semibold))
                .foregroundStyle(LG.accent2)
            }
        }
    }
}

/// Glass-tinted status banner used by sign-in (and other forms) for inline
/// success / error feedback that doesn't need a sheet.
struct LGStatusBanner: View {
    let tone: PillTone
    let title: String
    let message: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.body.weight(.semibold))
                .foregroundStyle(toneColor)
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.body(14, weight: .bold))
                    .foregroundStyle(LG.fg)
                Text(message)
                    .font(.body(13, weight: .regular))
                    .foregroundStyle(LG.fg2)
            }
            Spacer()
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: LG.Radius.lg, style: .continuous)
                .fill(toneColor.opacity(0.14))
        )
        .overlay(
            RoundedRectangle(cornerRadius: LG.Radius.lg, style: .continuous)
                .strokeBorder(toneColor.opacity(0.30), lineWidth: 1)
        )
    }

    private var icon: String {
        switch tone {
        case .err: return "exclamationmark.triangle.fill"
        case .ok: return "checkmark.circle.fill"
        case .warn: return "exclamationmark.circle.fill"
        case .info, .accent, .neutral: return "info.circle.fill"
        }
    }
    private var toneColor: Color {
        switch tone {
        case .err: return LG.err
        case .ok: return LG.ok
        case .warn: return LG.warn
        case .info: return LG.info
        case .accent: return LG.accent2
        case .neutral: return LG.fg3
        }
    }
}
