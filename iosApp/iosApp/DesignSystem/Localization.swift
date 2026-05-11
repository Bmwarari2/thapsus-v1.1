// Localization.swift
// Lightweight in-bundle string lookup so we can ship en + sw without standing
// up a full Apple String Catalog (which would require pbxproj surgery for
// the .xcstrings file). Strings keyed by stable identifier; each language map
// returns the localized variant. Fall back to English when sw is missing.
//
// Source of truth for the table is `client/src/context/LanguageContext.jsx`
// in the webapp — port new strings here as you add them on the web side.
//
// === S2-2 audit follow-up (2026-04-30) ===
//
// Convention: keys are dotted, prefixed by surface (e.g. `dashboard.*`,
// `wallet.*`, `auth.*`). One key per logical string — re-use across views.
// Always include the same key in BOTH dictionaries; if Swahili is unknown
// at write-time, leave the EN string in the SW slot so `t()` falls through
// gracefully. The translator follow-up will fill in real SW values.
//
// Migrate-a-view recipe:
//   1. Find every literal in a customer view (Text("..."), .navigationTitle("..."),
//      Button labels, alert messages). Operator/admin/agent surfaces stay EN.
//   2. Add a key here for each unique literal under the right prefix.
//   3. In the view, replace `Text("Wallet")` → `Text(T("wallet.title"))`.
//      Builders that take a String (eg EditorialHeader.title:, Button() Label
//      etc.) take `T("...")` directly.
//
// See `docs/localization_audit.md` for the per-view sweep status.

import Foundation
import SwiftUI

enum AppLanguage: String { case en, sw }

@Observable
@MainActor
final class LocalizationStore {
    static let shared = LocalizationStore()

    var language: AppLanguage = .en

    /// Sync the language from the user profile after sign-in.
    func apply(languagePref: String?) {
        switch languagePref?.lowercased() {
        case "sw": language = .sw
        default: language = .en
        }
    }

    func t(_ key: String) -> String {
        switch language {
        case .en: return Strings.en[key] ?? key
        case .sw: return Strings.sw[key] ?? Strings.en[key] ?? key
        }
    }
}

/// `T("key")` — terse global helper for SwiftUI string interpolation.
@MainActor
func T(_ key: String) -> String { LocalizationStore.shared.t(key) }

private enum Strings {
    static let en: [String: String] = [
        "common.cancel": "Cancel",
        "common.save": "Save",
        "common.submit": "Submit",
        "common.close": "Close",
        "common.loading": "Loading…",
        "common.required": "Required",
        "common.signOut": "Sign out",

        "auth.welcome": "Welcome",
        "auth.signIn": "Sign in",
        "auth.signUp": "Create an account",
        "auth.email": "Email",
        "auth.password": "Password",
        "auth.forgot": "Forgot password?",

        "dashboard.welcome": "Welcome",
        "dashboard.activeOrders": "Active orders",
        "dashboard.creditBalance": "My credit",
        "dashboard.warehouseAddress": "Your UK warehouse",
        "dashboard.recentOrders": "Recent orders",
        "dashboard.eyebrow": "Client Terminal",
        "dashboard.headerSubtitle": "Your global logistics overview and active shipments pipeline.",
        "dashboard.howItWorksHide": "Hide guide",
        "dashboard.howItWorksShow": "How it works",
        "dashboard.warehouseAddressHeader": "YOUR WAREHOUSE ADDRESS",
        "dashboard.copyAddress": "Copy address",
        "dashboard.copyAddressDone": "Copied!",
        "dashboard.activeOrdersEyebrow": "Active orders",
        "dashboard.inFlightEyebrow": "In flight",
        "dashboard.viewPackages": "View my packages",
        "dashboard.walletAndTopup": "Wallet & top-up",

        "home.howitworks": "How it works",
        "home.workflow": "Our workflow",
        "home.step1": "Shop from your favorite retailers",
        "home.step1.body": "Shop any UK brand. Use our warehouse address as yours.",
        "home.step2": "Ship to our warehouse",
        "home.step2.body": "We handle the heavy lifting. Your package is safely received and cataloged.",
        "home.step3": "We consolidate",
        "home.step3.body": "We combine your parcels into the next weekly UK→Nairobi flight.",
        "home.step4": "Doorstep delivery",
        "home.step4.body": "Customs cleared in Nairobi, then a rider hands it to you within 48 hours.",

        "tracking.title": "Track your package",
        "tracking.placeholder": "Enter a tracking number",
        "tracking.empty.title": "You don't have any packages yet.",
        "tracking.empty.cta": "Create first order",

        "wallet.title": "Wallet",
        "wallet.balance": "Balance",
        "wallet.topUp": "Top up",
        "wallet.history": "Transaction history",
        "wallet.paybillSection": "Pay by M-Pesa",
        "wallet.paybillTitle": "Paybill",
        "wallet.paybillAccount": "Account",
        "wallet.paybillBusiness": "Business",
        "wallet.paybillFetching": "Fetching paybill…",
        "wallet.paybillError": "Couldn't load paybill",
        "warehouse.title": "Your warehouse address",
        "warehouse.copy": "Copy address",
        "warehouse.eyebrow": "Client Terminal",
        "warehouse.copied": "Copied!",

        "neworder.title": "New order",
        "neworder.market": "Market",
        "neworder.retailer": "Retailer",
        "neworder.description": "Description",
        "neworder.submit": "Create order",
        "neworder.shippingSpeed": "Shipping speed",
        "neworder.weight": "Approx weight (kg)",
        "neworder.dimensions": "Approx dimensions (cm)",
        "neworder.declaredValue": "Declared value (£)",
        "neworder.electronics": "Electronics?",
        "neworder.failedTitle": "Couldn't create order",
        "neworder.placeholderRetailer": "Amazon, Argos, Currys…",

        "tickets.new": "New ticket",
        "tickets.subject": "Subject",
        "tickets.description": "Describe your issue",
        "tickets.submit": "Open ticket",
        "tickets.empty": "No tickets yet — open one if you need help.",
        "tickets.attachment": "Attachment",
        "tickets.attachmentUploading": "Uploading…",
        "referrals.title": "Refer & earn",
        "referrals.code": "Your referral code",
        "referrals.share": "Share invite",
        "prohibited.title": "Prohibited & restricted",
        "prohibited.placeholder": "Search lithium battery, perfume…",
        "buyForMe.title": "Buy for me",
        "buyForMe.retailerUrl": "Retailer URL",
        "buyForMe.itemName": "Item name",
        "buyForMe.size": "Size (optional)",
        "buyForMe.qty": "Quantity",
        "buyForMe.notes": "Notes",
        "buyForMe.submit": "Request a quote",
        "tracking.detailsTitle": "Tracking details",
        "tracking.statusLabel": "Current status",
        "nps.prompt": "How was your delivery?",
        "nps.score": "Score (0–10)",
        "nps.commentPlaceholder": "Anything we should know?",
        "nps.submit": "Send feedback",
        "nps.thanks": "Thanks for the feedback.",
        "dsar.title": "Privacy controls",
        "dsar.exportData": "Export my data",
        "dsar.deleteAccount": "Delete my account",

        "admin.console": "Console",
        "admin.users": "Users",
        "admin.orders": "Orders",
        "admin.payments": "Pending payments",
        "admin.errorLogs": "Error logs",
        "admin.signedIn": "Signed in",
    ]

    static let sw: [String: String] = [
        "common.cancel": "Ghairi",
        "common.save": "Hifadhi",
        "common.submit": "Wasilisha",
        "common.close": "Funga",
        "common.loading": "Inapakia…",
        "common.required": "Inahitajika",
        "common.signOut": "Ondoka",

        "auth.welcome": "Karibu",
        "auth.signIn": "Ingia",
        "auth.signUp": "Fungua akaunti",
        "auth.email": "Barua pepe",
        "auth.password": "Nenosiri",
        "auth.forgot": "Umesahau nenosiri?",

        "dashboard.welcome": "Karibu",
        "dashboard.activeOrders": "Maagizo yanayotumika",
        "dashboard.creditBalance": "Mkopo wangu",
        "dashboard.warehouseAddress": "Anwani yako ya ghala UK",
        "dashboard.recentOrders": "Maagizo ya hivi karibuni",
        // S2-2: post-translator keys — EN fallback until SW translation lands.
        "dashboard.eyebrow": "Client Terminal",
        "dashboard.headerSubtitle": "Your global logistics overview and active shipments pipeline.",
        "dashboard.howItWorksHide": "Hide guide",
        "dashboard.howItWorksShow": "How it works",
        "dashboard.warehouseAddressHeader": "YOUR WAREHOUSE ADDRESS",
        "dashboard.copyAddress": "Copy address",
        "dashboard.copyAddressDone": "Copied!",
        "dashboard.activeOrdersEyebrow": "Active orders",
        "dashboard.inFlightEyebrow": "In flight",
        "dashboard.viewPackages": "View my packages",
        "dashboard.walletAndTopup": "Wallet & top-up",

        "home.howitworks": "Jinsi inavyofanya kazi",
        "home.workflow": "Mfumo wetu",
        "home.step1": "Nunua kutoka kwa wauzaji unaopenda",
        "home.step1.body": "Nunua bidhaa yoyote ya UK. Tumia anwani yetu ya ghala kama yako.",
        "home.step2": "Tuma kwenye ghala letu",
        "home.step2.body": "Tunashughulikia kazi nzito. Kifurushi chako kinapokelewa na kuandikishwa.",
        "home.step3": "Tunakusanya pamoja",
        "home.step3.body": "Tunaunganisha vifurushi vyako kwenye safari ya wiki UK→Nairobi.",
        "home.step4": "Utoaji nyumbani",
        "home.step4.body": "Forodha imekamilika Nairobi, kisha mpiga mpira anakukabidhi ndani ya saa 48.",

        "tracking.title": "Fuatilia kifurushi chako",
        "tracking.placeholder": "Weka nambari ya ufuatiliaji",
        "tracking.empty.title": "Bado huna vifurushi.",
        "tracking.empty.cta": "Tengeneza agizo la kwanza",

        "wallet.title": "Pochi",
        "wallet.balance": "Balance",
        "wallet.topUp": "Top up",
        "wallet.history": "Transaction history",
        "wallet.paybillSection": "Pay by M-Pesa",
        "wallet.paybillTitle": "Paybill",
        "wallet.paybillAccount": "Account",
        "wallet.paybillBusiness": "Business",
        "wallet.paybillFetching": "Fetching paybill…",
        "wallet.paybillError": "Couldn't load paybill",
        "warehouse.title": "Anwani ya ghala lako",
        "warehouse.copy": "Nakili anwani",
        "warehouse.eyebrow": "Kituo cha Mteja",
        "warehouse.copied": "Copied!",

        "neworder.title": "Agizo jipya",
        "neworder.market": "Soko",
        "neworder.retailer": "Muuzaji",
        "neworder.description": "Maelezo",
        "neworder.submit": "Tengeneza agizo",
        "neworder.shippingSpeed": "Shipping speed",
        "neworder.weight": "Approx weight (kg)",
        "neworder.dimensions": "Approx dimensions (cm)",
        "neworder.declaredValue": "Declared value (£)",
        "neworder.electronics": "Electronics?",
        "neworder.failedTitle": "Couldn't create order",
        "neworder.placeholderRetailer": "Amazon, Argos, Currys…",

        "tickets.new": "Tikiti mpya",
        "tickets.subject": "Subject",
        "tickets.description": "Describe your issue",
        "tickets.submit": "Open ticket",
        "tickets.empty": "No tickets yet — open one if you need help.",
        "tickets.attachment": "Attachment",
        "tickets.attachmentUploading": "Uploading…",
        "referrals.title": "Pendekeza & pata",
        "referrals.code": "Your referral code",
        "referrals.share": "Share invite",
        "prohibited.title": "Zilizopigwa marufuku & zenye masharti",
        "prohibited.placeholder": "Tafuta betri ya lithium, manukato…",
        "buyForMe.title": "Buy for me",
        "buyForMe.retailerUrl": "Retailer URL",
        "buyForMe.itemName": "Item name",
        "buyForMe.size": "Size (optional)",
        "buyForMe.qty": "Quantity",
        "buyForMe.notes": "Notes",
        "buyForMe.submit": "Request a quote",
        "tracking.detailsTitle": "Tracking details",
        "tracking.statusLabel": "Current status",
        "nps.prompt": "How was your delivery?",
        "nps.score": "Score (0–10)",
        "nps.commentPlaceholder": "Anything we should know?",
        "nps.submit": "Send feedback",
        "nps.thanks": "Thanks for the feedback.",
        "dsar.title": "Privacy controls",
        "dsar.exportData": "Export my data",
        "dsar.deleteAccount": "Delete my account",

        "admin.console": "Kituo",
        "admin.users": "Watumiaji",
        "admin.orders": "Maagizo",
        "admin.payments": "Malipo yanayosubiri",
        "admin.errorLogs": "Magogo ya makosa",
        "admin.signedIn": "Umeingia",
    ]
}
