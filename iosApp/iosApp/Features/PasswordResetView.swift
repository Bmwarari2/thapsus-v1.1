// PasswordResetView.swift
// Universal-link landing for the password-reset email. The email's link is
//   https://www.thapsus.uk/reset-password?token=<hex>
// which the apple-app-site-association manifest binds to this app
// (`/reset-password*` component). RootView pulls the `token` query param off
// the inbound URL and presents this view as a sheet.
//
// The same path also backs the welcome / setup-account email that admin
// sends when creating a user (routes/admin.js — sendWelcomeAccountEmail).
// Both flows hit POST /api/auth/reset-password with the same token + new
// password contract, so a single screen handles both.

import SwiftUI
import ThapsusShared

struct PasswordResetView: View {
    let token: String

    @Environment(\.dismiss) private var dismiss

    @State private var newPassword: String = ""
    @State private var confirmPassword: String = ""
    @State private var submitting: Bool = false
    @State private var done: Bool = false
    @State private var errorMessage: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                BrandWordmark(size: .medium)

                EditorialHeader(
                    eyebrow: "Reset password",
                    title: "Choose a new\npassword",
                    subtitle: done
                        ? "All set. You can sign in with your new password."
                        : "Pick a password you'll remember. Six characters minimum."
                )

                if !done {
                    SoftCard {
                        VStack(alignment: .leading, spacing: 12) {
                            SecureField("New password", text: $newPassword)
                                .textFieldStyle(.plain)
                                .padding(12)
                                .background(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(Brand.cream.opacity(0.6))
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .stroke(Brand.ink.opacity(0.08), lineWidth: 1)
                                )

                            SecureField("Confirm new password", text: $confirmPassword)
                                .textFieldStyle(.plain)
                                .padding(12)
                                .background(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(Brand.cream.opacity(0.6))
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .stroke(Brand.ink.opacity(0.08), lineWidth: 1)
                                )

                            if let errorMessage {
                                ErrorBanner(title: "Couldn't reset", message: errorMessage)
                            }

                            Button(action: submit) {
                                if submitting {
                                    HStack { ProgressView().tint(Brand.cream); Text("Saving…") }
                                } else {
                                    Text("Set new password")
                                }
                            }
                            .buttonStyle(InkButtonStyle())
                            .disabled(submitting || !isValid)
                        }
                    }
                } else {
                    Button {
                        dismiss()
                    } label: {
                        Text("Sign in")
                    }
                    .buttonStyle(InkButtonStyle())
                }
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .appBackground()
    }

    private var isValid: Bool {
        newPassword.count >= 6 && newPassword == confirmPassword
    }

    private func submit() {
        // Client-side guard duplicates the server's rule
        // (routes/auth.js:357) so the user gets the message inline rather
        // than waiting for the round-trip.
        guard newPassword.count >= 6 else {
            errorMessage = "Password must be at least 6 characters."
            return
        }
        guard newPassword == confirmPassword else {
            errorMessage = "Passwords don't match."
            return
        }

        submitting = true
        errorMessage = nil

        Task {
            do {
                _ = try await ThapsusSdk.shared.auth()
                    .resetPassword(token: token, newPassword: newPassword)
                await MainActor.run {
                    submitting = false
                    done = true
                }
            } catch {
                await MainActor.run {
                    submitting = false
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
}
