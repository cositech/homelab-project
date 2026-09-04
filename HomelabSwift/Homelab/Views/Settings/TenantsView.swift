import SwiftUI

/// The default tenant's stored name is the fixed English `"Default"`; render it localized.
func tenantDisplayName(_ tenant: Tenant, localizer: Localizer) -> String {
    tenant.isDefault ? localizer.t.badgeDefault : tenant.name
}

/// Phase 4 tenant management. A single-tenant install (the implicit `default` tenant only) reaches
/// this screen only through its entry point in Settings; every list it shows always includes the
/// default tenant, which can be activated but never renamed or removed.
struct TenantsView: View {
    @Environment(TenantStore.self) private var tenantStore
    @Environment(Localizer.self) private var localizer

    @State private var showingAddTenant = false
    @State private var editingTenant: Tenant?
    @State private var tenantPendingDelete: Tenant?

    var body: some View {
        ZStack {
            AppTheme.background.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 14) {
                    ForEach(tenantStore.selection.tenants) { tenant in
                        tenantCard(tenant)
                    }

                    Button {
                        showingAddTenant = true
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "plus")
                            Text(localizer.t.tenantsAddTenant)
                        }
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                    }
                    .buttonStyle(.borderedProminent)
                }
                .padding(16)
                .padding(.bottom, 32)
            }
        }
        .navigationTitle(localizer.t.settingsTenants)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showingAddTenant) {
            TenantFormView(tenantToEdit: nil)
        }
        .sheet(item: $editingTenant) { tenant in
            TenantFormView(tenantToEdit: tenant)
        }
        .alert(localizer.t.delete, isPresented: .init(
            get: { tenantPendingDelete != nil },
            set: { if !$0 { tenantPendingDelete = nil } }
        )) {
            Button(localizer.t.cancel, role: .cancel) { }
            Button(localizer.t.delete, role: .destructive) {
                if let tenant = tenantPendingDelete {
                    tenantStore.removeTenant(id: tenant.id)
                }
            }
        } message: {
            Text(String(format: localizer.t.tenantsDeleteConfirmMessage, tenantPendingDelete?.name ?? ""))
        }
    }

    private func tenantCard(_ tenant: Tenant) -> some View {
        let isActive = tenant.id == tenantStore.selection.activeTenantId

        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(tenantDisplayName(tenant, localizer: localizer))
                        .font(.headline.weight(.bold))
                    Text(tenant.kind == .customer ? localizer.t.tenantsKindCustomer : localizer.t.tenantsKindPersonal)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer(minLength: 0)

                if isActive {
                    Text(localizer.t.tenantsActive)
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(AppTheme.accent)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(AppTheme.accent.opacity(0.15), in: Capsule())
                }
            }

            HStack(spacing: 8) {
                if !isActive {
                    Button(localizer.t.tenantsSetActive) {
                        tenantStore.setActiveTenant(id: tenant.id)
                    }
                    .buttonStyle(.bordered)
                }
                if !tenant.isDefault {
                    Button(localizer.t.tenantsRename) {
                        editingTenant = tenant
                    }
                    .buttonStyle(.bordered)

                    Button(localizer.t.delete, role: .destructive) {
                        tenantPendingDelete = tenant
                    }
                    .buttonStyle(.bordered)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .glassCard()
    }
}

private struct TenantFormView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(TenantStore.self) private var tenantStore
    @Environment(Localizer.self) private var localizer

    var tenantToEdit: Tenant?

    @State private var name: String = ""
    @State private var kind: TenantKind = .customer

    private var isEditing: Bool { tenantToEdit != nil }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(localizer.t.tenantsName, text: $name)
                } header: {
                    Text(localizer.t.tenantsName)
                }

                if !isEditing {
                    Section {
                        Picker("", selection: $kind) {
                            Text(localizer.t.tenantsKindPersonal).tag(TenantKind.personal)
                            Text(localizer.t.tenantsKindCustomer).tag(TenantKind.customer)
                        }
                        .pickerStyle(.segmented)
                        .labelsHidden()
                    }
                }
            }
            .navigationTitle(isEditing ? localizer.t.tenantsRename : localizer.t.tenantsAddTenant)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizer.t.cancel) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(localizer.t.save) {
                        save()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .onAppear {
                if let tenant = tenantToEdit {
                    name = tenant.name
                    kind = tenant.kind
                }
            }
        }
    }

    private func save() {
        if let tenant = tenantToEdit {
            tenantStore.renameTenant(id: tenant.id, name: name)
        } else {
            tenantStore.addTenant(name: name, kind: kind)
        }
        dismiss()
    }
}
