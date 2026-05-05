// GlassChrome.swift
// View modifiers that apply iOS 26 Liquid Glass to NAVIGATION CHROME ONLY:
// nav bar, tab bar, modal sheets. Dense content cards use SoftCard / InkCard
// (see GlassDesignSystem.swift) to avoid stacking blur on blur.

import SwiftUI

extension View {
    /// Brand-styled navigation bar. Lets the OS apply its own Liquid Glass to
    /// the bar without our painting any custom solid background underneath.
    func glassNavigationBar(
        _ title: String? = nil,
        displayMode: NavigationBarItem.TitleDisplayMode = .large
    ) -> some View {
        self
            .navigationBarTitleDisplayMode(displayMode)
            .toolbarBackgroundVisibility(.automatic, for: .navigationBar)
    }

    /// Glass tab bar. The cream→peach gradient under it gives the OS something
    /// to refract — that's the whole point of moving away from the dark theme.
    func glassTabBar() -> some View {
        self.toolbarBackgroundVisibility(.automatic, for: .tabBar)
    }

    /// Glass modal sheet. Hands presentation to the OS; we set corner radius
    /// + detents and let SwiftUI install Liquid Glass automatically on iOS 26.
    func glassSheet(detents: Set<PresentationDetent> = [.large]) -> some View {
        self
            .presentationDetents(detents)
            .presentationCornerRadius(Layout.modalCorner)
            .presentationBackgroundInteraction(.enabled(upThrough: .medium))
    }
}
