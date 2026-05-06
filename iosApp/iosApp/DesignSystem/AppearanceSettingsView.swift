// AppearanceSettingsView.swift
// User-facing toggle for the app theme (System / Light / Dark). Reachable
// from every role's Account hub via "Appearance".

import SwiftUI

struct AppearanceSettingsView: View {
    @Environment(AppearanceSettings.self) private var appearance

    var body: some View {
        @Bindable var appearance = appearance

        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 6) {
                    LGEyebrow(text: "Appearance")
                    Text("Choose your theme")
                        .font(.heading(24, weight: .heavy))
                        .foregroundStyle(LG.fg)
                    Text("System matches your device. Light and Dark override it.")
                        .font(.body(14, weight: .medium))
                        .foregroundStyle(LG.fg3)
                }
                .padding(.top, 8)

                GlassPanel(corner: LG.Radius.xl, padding: 6) {
                    VStack(spacing: 0) {
                        ForEach(Array(ThapsusTheme.allCases.enumerated()), id: \.element.id) { idx, theme in
                            Button {
                                withAnimation(LG.animation) { appearance.theme = theme }
                            } label: {
                                HStack(spacing: 14) {
                                    ZStack {
                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                            .fill(LG.glassBgStrong)
                                            .frame(width: 36, height: 36)
                                        Image(systemName: icon(for: theme))
                                            .font(.system(size: 16, weight: .semibold))
                                            .foregroundStyle(LG.accent2)
                                    }
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(theme.label)
                                            .font(.body(15, weight: .bold))
                                            .foregroundStyle(LG.fg)
                                        Text(detail(for: theme))
                                            .font(.body(12.5, weight: .medium))
                                            .foregroundStyle(LG.fg3)
                                    }
                                    Spacer()
                                    if appearance.theme == theme {
                                        ZStack {
                                            Circle().fill(LG.accentGradient)
                                            Image(systemName: "checkmark")
                                                .font(.system(size: 11, weight: .bold))
                                                .foregroundStyle(Color.white)
                                        }
                                        .frame(width: 22, height: 22)
                                    } else {
                                        Circle()
                                            .strokeBorder(LG.lineStrong, lineWidth: 2)
                                            .frame(width: 22, height: 22)
                                    }
                                }
                                .padding(12)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            if idx < ThapsusTheme.allCases.count - 1 {
                                Rectangle()
                                    .fill(LG.line)
                                    .frame(height: 1)
                                    .padding(.horizontal, 14)
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 40)
        }
        .scrollContentBackground(.hidden)
        .background(LiquidGlassBackground())
        .navigationTitle("Appearance")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func icon(for theme: ThapsusTheme) -> String {
        switch theme {
        case .system: return "circle.lefthalf.filled"
        case .light:  return "sun.max.fill"
        case .dark:   return "moon.fill"
        }
    }

    private func detail(for theme: ThapsusTheme) -> String {
        switch theme {
        case .system: return "Follow iOS setting"
        case .light:  return "Beige → mint background, ink text"
        case .dark:   return "Warm charcoal background, cream text"
        }
    }
}
