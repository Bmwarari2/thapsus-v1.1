// AdminUsersView.swift
// Mirrors the webapp Admin → Users tab. List + search, plus actions: provision,
// reset password, deactivate. Tap a row for detail (orders + emails sent).

import SwiftUI
import ThapsusShared

struct AdminUsersView: View {
    @State private var vm: AdminUsersViewModel? = nil
    @State private var stateObs: StateFlowObserver<AdminUsersViewModelUiState>? = nil
    @State private var actionObs: StateFlowObserver<AdminUsersViewModelActionState>? = nil
    @State private var query: String = ""
    @State private var showProvision: Bool = false
    @State private var resetTarget: AdminUserDto? = nil
    @State private var deleteTarget: AdminUserDto? = nil

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                EyebrowPill(label: "Admin", systemImage: "person.2.fill")
                EditorialHeader(title: "Users", subtitle: "Provision new accounts, reset passwords, view activity.")

                Button(action: { showProvision = true }) {
                    HStack(spacing: 8) {
                        Image(systemName: "person.crop.circle.badge.plus")
                        Text("Provision account")
                    }
                }
                .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))

                searchBar
                actionBanner
                content
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Users")
        .glassNavigationBar()
        .sheet(isPresented: $showProvision) {
            ProvisionUserSheet { name, email, phone, role in
                vm?.provision(name: name, email: email, phone: phone, role: role)
                showProvision = false
            }
        }
        .alert("Send password reset email?", isPresented: Binding(
            get: { resetTarget != nil },
            set: { if !$0 { resetTarget = nil } }
        ), presenting: resetTarget) { user in
            Button("Send to \(user.email)") {
                vm?.sendPasswordResetEmail(id: user.id)
                resetTarget = nil
            }
            Button("Cancel", role: .cancel) { resetTarget = nil }
        } message: { user in
            // Server picks the password — admin only triggers the email. The
            // user follows the link in their inbox to set their own. Audit
            // §3.5.4 fixed the prior anti-pattern of admins typing a new
            // password that the server silently dropped.
            Text("\(user.name) will receive a one-time link to set a new password. The link expires in 1 hour.")
        }
        .alert("Delete user?", isPresented: Binding(
            get: { deleteTarget != nil },
            set: { if !$0 { deleteTarget = nil } }
        ), presenting: deleteTarget) { user in
            Button("Delete \(user.name)", role: .destructive) {
                vm?.delete(id: user.id)
                deleteTarget = nil
            }
            Button("Cancel", role: .cancel) { deleteTarget = nil }
        } message: { user in
            Text("Permanently removes \(user.email), their orders, packages, transactions and wallet. This cannot be undone.")
        }
        .task { bootstrap() }
    }

    private var searchBar: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
            TextField("Search by name, email, or warehouse ID", text: $query)
                .textFieldStyle(.plain)
                .submitLabel(.search)
                .onSubmit { vm?.search(query: query) }
                .textInputAutocapitalization(.never)
            if !query.isEmpty {
                Button(action: { query = ""; vm?.load() }) {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary)
                }
                .accessibilityLabel("Clear search")
            }
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(.ultraThinMaterial))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(.white.opacity(0.5), lineWidth: 1))
    }

    @ViewBuilder
    private var actionBanner: some View {
        switch actionObs?.value {
        case let done as AdminUsersViewModelActionStateDone:
            CalloutBanner(icon: "checkmark.circle.fill", title: "Done", message: done.message)
        case let err as AdminUsersViewModelActionStateError:
            ErrorBanner(title: "Couldn't complete", message: err.message)
        case is AdminUsersViewModelActionStateInFlight:
            ProgressView().frame(maxWidth: .infinity)
        default: EmptyView()
        }
    }

    @ViewBuilder
    private var content: some View {
        switch stateObs?.value {
        case let loaded as AdminUsersViewModelUiStateLoaded:
            if loaded.users.isEmpty {
                CrystalCard {
                    Text("No users match \"\(loaded.query)\".").font(.subheadline).foregroundStyle(.secondary)
                }
            } else {
                ForEach(loaded.users, id: \.id) { user in
                    NavigationLink(destination: AdminUserDetailView(userId: user.id, name: user.name)) {
                        userRow(user)
                    }
                    .buttonStyle(.plain)
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) {
                            deleteTarget = user
                        } label: { Label("Delete", systemImage: "trash") }
                        if user.isActive {
                            Button {
                                vm?.setActive(id: user.id, isActive: false)
                            } label: { Label("Deactivate", systemImage: "person.slash") }
                            .tint(.gray)
                        } else {
                            Button {
                                vm?.setActive(id: user.id, isActive: true)
                            } label: { Label("Reactivate", systemImage: "person.fill.checkmark") }
                            .tint(.green)
                        }
                        Button { resetTarget = user } label: { Label("Reset", systemImage: "key") }
                            .tint(.blue)
                        Button { vm?.resendWelcome(id: user.id) } label: {
                            Label("Resend", systemImage: "envelope.arrow.triangle.branch")
                        }
                        .tint(Brand.orange)
                    }
                }
            }
        case is AdminUsersViewModelUiStateLoading:
            ProgressView().frame(maxWidth: .infinity).padding(.top, 30)
        case let err as AdminUsersViewModelUiStateError:
            ErrorBanner(title: "Couldn't load", message: err.message)
        default: EmptyView()
        }
    }

    private func userRow(_ user: AdminUserDto) -> some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(user.name).font(.headline).foregroundStyle(Brand.ink)
                    Spacer()
                    Text(user.role.uppercased())
                        .font(.caption2.weight(.heavy)).tracking(2)
                        .foregroundStyle(Brand.orange)
                }
                Text(user.email).font(.caption).foregroundStyle(.secondary)
                if let warehouse = user.warehouseId {
                    Text(warehouse).font(.caption.monospaced()).foregroundStyle(.tertiary)
                }
                if !user.isActive {
                    Text("INACTIVE")
                        .font(.caption2.weight(.heavy)).tracking(2)
                        .foregroundStyle(.red)
                }
            }
        }
    }

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.adminUsersViewModel()
        vm = model
        model.load()
        stateObs = StateFlowObserver(initial: model.state.value) { model.state }
        actionObs = StateFlowObserver(initial: model.action.value) { model.action }
    }
}

extension AdminUserDto: @retroactive Identifiable {}

private struct ProvisionUserSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var name: String = ""
    @State private var email: String = ""
    @State private var phone: String = ""
    @State private var role: String = "customer"
    let onSubmit: (String, String, String?, String) -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section("Profile") {
                    TextField("Full name", text: $name)
                    TextField("Email", text: $email)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                    TextField("Phone (optional)", text: $phone)
                        .keyboardType(.phonePad)
                }
                Section("Role") {
                    Picker("Role", selection: $role) {
                        Text("Customer").tag("customer")
                        Text("Operator").tag("operator")
                        Text("Clearing agent").tag("clearing_agent")
                        Text("Rider").tag("rider")
                        Text("Admin").tag("admin")
                    }
                }
            }
            .navigationTitle("Provision account")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Provision") {
                        onSubmit(name, email, phone.isEmpty ? nil : phone, role)
                    }
                    .disabled(name.isEmpty || email.isEmpty)
                }
            }
        }
    }
}

struct AdminUserDetailView: View {
    let userId: String
    let name: String

    @Environment(\.dismiss) private var dismiss
    @State private var vm: AdminUserDetailViewModel? = nil
    @State private var observer: StateFlowObserver<AdminUserDetailViewModelUiState>? = nil
    @State private var actionObs: StateFlowObserver<AdminUserDetailViewModelActionState>? = nil
    @State private var showResetSheet: Bool = false
    @State private var showRoleSheet: Bool = false
    @State private var showDeleteAlert: Bool = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                actionBanner
                resendButton
                content
                accountControls
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle(name)
        .glassNavigationBar()
        .alert("Send password reset email?", isPresented: $showResetSheet) {
            Button("Send to \(currentUser?.email ?? name)") {
                vm?.sendPasswordResetEmail()
                showResetSheet = false
            }
            Button("Cancel", role: .cancel) { showResetSheet = false }
        } message: {
            Text("\(name) will receive a one-time link to set a new password. The link expires in 1 hour.")
        }
        .sheet(isPresented: $showRoleSheet) {
            if let current = currentUser?.role {
                ChangeRoleSheet(currentRole: current) { newRole in
                    vm?.setRole(role: newRole)
                    showRoleSheet = false
                }
            }
        }
        .alert("Delete this user?", isPresented: $showDeleteAlert) {
            Button("Delete", role: .destructive) {
                vm?.delete(onDeleted: { dismiss() })
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Permanently removes \(name) and every order, package, transaction, ticket and wallet row attached. Cannot be undone.")
        }
        .task { bootstrap() }
    }

    private var currentUser: AdminUserDto? {
        (observer?.value as? AdminUserDetailViewModelUiStateLoaded)?.user
    }

    @ViewBuilder
    private var actionBanner: some View {
        switch actionObs?.value {
        case let done as AdminUserDetailViewModelActionStateDone:
            CalloutBanner(icon: "checkmark.circle.fill", title: "Sent", message: done.message)
        case let err as AdminUserDetailViewModelActionStateError:
            ErrorBanner(title: "Failed", message: err.message)
        case is AdminUserDetailViewModelActionStateInFlight:
            ProgressView().frame(maxWidth: .infinity)
        default: EmptyView()
        }
    }

    private var resendButton: some View {
        Button(action: { vm?.resendWelcome() }) {
            HStack(spacing: 8) {
                Image(systemName: "envelope.arrow.triangle.branch")
                Text("Resend welcome email")
            }
        }
        .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
    }

    @ViewBuilder
    private var content: some View {
        switch observer?.value {
        case let loaded as AdminUserDetailViewModelUiStateLoaded:
            CrystalCard {
                VStack(alignment: .leading, spacing: 6) {
                    Text(loaded.user.name).font(.title2.weight(.heavy)).foregroundStyle(Brand.ink)
                    Text(loaded.user.email).font(.subheadline).foregroundStyle(.secondary)
                    if let phone = loaded.user.phone { Text(phone).font(.subheadline).foregroundStyle(.secondary) }
                    HStack(spacing: 8) {
                        Text(loaded.user.role.uppercased())
                            .font(.caption2.weight(.heavy)).tracking(2)
                            .foregroundStyle(Brand.orange)
                            .padding(.horizontal, 8).padding(.vertical, 4)
                            .background(Capsule().fill(Brand.orange.opacity(0.16)))
                        if !loaded.user.isActive {
                            Text("INACTIVE")
                                .font(.caption2.weight(.heavy)).tracking(2)
                                .foregroundStyle(.red)
                        }
                    }
                    if let warehouse = loaded.user.warehouseId {
                        Text(warehouse).font(.caption.monospaced()).foregroundStyle(.tertiary)
                    }
                    if let address = loaded.user.deliveryAddress?
                        .trimmingCharacters(in: .whitespacesAndNewlines), !address.isEmpty {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Delivery address".uppercased())
                                .font(.caption2.weight(.heavy)).tracking(2)
                                .foregroundStyle(Brand.ink.opacity(0.55))
                            Text(address)
                                .font(.subheadline)
                                .foregroundStyle(Brand.ink)
                                .fixedSize(horizontal: false, vertical: true)
                                .textSelection(.enabled)
                        }
                        .padding(.top, 8)
                    }
                    // Wallet row removed in PR B — users.wallet_balance was
                    // dropped in migration 028 / Swiftcargo PR #61. Customer
                    // credit (replacement) is per-user via /api/payments/me/credit
                    // and surfaced on the customer-facing CreditCenterView, not
                    // the admin profile.
                }
            }

            SectionHeader(title: "Recent emails", subtitle: loaded.emails.isEmpty ? "No emails sent yet." : nil)
            ForEach(loaded.emails, id: \.id) { email in
                CrystalCard {
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(email.subject).font(.subheadline.weight(.semibold)).foregroundStyle(Brand.ink)
                            Spacer()
                            Text(email.status.uppercased())
                                .font(.caption2.weight(.heavy)).tracking(2)
                                .foregroundStyle(email.status == "sent" ? .green : .red)
                        }
                        HStack {
                            Text(email.emailType.uppercased())
                                .font(.caption2.weight(.heavy)).tracking(2)
                                .foregroundStyle(.secondary)
                            Spacer()
                            if let created = email.createdAt {
                                Text(created).font(.caption2).foregroundStyle(.secondary)
                            }
                        }
                        if let errorMessage = email.errorMessage {
                            InlineFieldError(message: errorMessage)
                        }
                    }
                }
            }
        case is AdminUserDetailViewModelUiStateLoading:
            ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
        case let err as AdminUserDetailViewModelUiStateError:
            ErrorBanner(title: "Couldn't load", message: err.message)
        default: EmptyView()
        }
    }

    @ViewBuilder
    private var accountControls: some View {
        if let user = currentUser {
            VStack(alignment: .leading, spacing: 10) {
                SectionHeader(title: "Account controls")
                CrystalCard {
                    VStack(spacing: 0) {
                        controlRow(icon: "key", tint: .blue, title: "Reset password") {
                            showResetSheet = true
                        }
                        Divider().background(Brand.ink.opacity(0.08))
                        controlRow(icon: "person.badge.shield.checkmark", tint: Brand.orange, title: "Change role (\(user.role))") {
                            showRoleSheet = true
                        }
                        Divider().background(Brand.ink.opacity(0.08))
                        if user.isActive {
                            controlRow(icon: "person.slash", tint: .gray, title: "Deactivate account") {
                                vm?.setActive(isActive: false)
                            }
                        } else {
                            controlRow(icon: "person.fill.checkmark", tint: .green, title: "Reactivate account") {
                                vm?.setActive(isActive: true)
                            }
                        }
                        Divider().background(Brand.ink.opacity(0.08))
                        controlRow(icon: "trash", tint: .red, title: "Delete account…") {
                            showDeleteAlert = true
                        }
                    }
                }
            }
        }
    }

    private func controlRow(icon: String, tint: Color, title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.headline)
                    .foregroundStyle(tint)
                    .frame(width: 28)
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Brand.ink)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.secondary)
            }
            .padding(.vertical, 12)
        }
        .buttonStyle(.plain)
    }

    private func format(_ amount: Double) -> String {
        let f = NumberFormatter(); f.numberStyle = .decimal
        return f.string(from: NSNumber(value: amount)) ?? "\(Int(amount))"
    }

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.adminUserDetailViewModel(userId: userId)
        vm = model
        observer = StateFlowObserver(initial: model.state.value) { model.state }
        actionObs = StateFlowObserver(initial: model.action.value) { model.action }
    }
}

private struct ChangeRoleSheet: View {
    let currentRole: String
    let onSubmit: (String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var role: String

    init(currentRole: String, onSubmit: @escaping (String) -> Void) {
        self.currentRole = currentRole
        self.onSubmit = onSubmit
        _role = State(initialValue: currentRole)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Role") {
                    Picker("Role", selection: $role) {
                        Text("Customer").tag("customer")
                        Text("Operator").tag("operator")
                        Text("Clearing agent").tag("clearing_agent")
                        Text("Rider").tag("rider")
                        Text("Admin").tag("admin")
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                }
            }
            .navigationTitle("Change role")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { onSubmit(role) }.disabled(role == currentRole)
                }
            }
        }
    }
}
