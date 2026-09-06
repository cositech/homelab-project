import SwiftUI

/// Manages the `Site`s belonging to a single tenant, reached by tapping that tenant's "Sites"
/// button on `TenantsView`. A site is an optional attribute an instance can be assigned to - there
/// is no "active site" concept the way there is for tenants.
struct SitesView: View {
    @Environment(SiteStore.self) private var siteStore
    @Environment(TenantStore.self) private var tenantStore
    @Environment(Localizer.self) private var localizer

    let tenantId: String

    @State private var showingAddSite = false
    @State private var editingSite: Site?
    @State private var sitePendingDelete: Site?

    private var tenant: Tenant {
        tenantStore.selection.tenants.first { $0.id == tenantId } ?? Tenant.default
    }

    private var sites: [Site] {
        siteStore.registry.sites(forTenant: tenant.id)
    }

    var body: some View {
        ZStack {
            AppTheme.background.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 14) {
                    if sites.isEmpty {
                        Text(localizer.t.sitesEmpty)
                            .font(.body)
                            .foregroundStyle(AppTheme.textSecondary)
                            .padding(.top, 24)
                    }

                    ForEach(sites) { site in
                        siteCard(site)
                    }

                    Button {
                        showingAddSite = true
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "plus")
                            Text(localizer.t.sitesAddSite)
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
        .navigationTitle(String(format: localizer.t.settingsSitesForTenant, tenantDisplayName(tenant, localizer: localizer)))
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showingAddSite) {
            SiteFormView(tenantId: tenant.id, siteToEdit: nil)
        }
        .sheet(item: $editingSite) { site in
            SiteFormView(tenantId: tenant.id, siteToEdit: site)
        }
        .alert(localizer.t.delete, isPresented: .init(
            get: { sitePendingDelete != nil },
            set: { if !$0 { sitePendingDelete = nil } }
        )) {
            Button(localizer.t.cancel, role: .cancel) { }
            Button(localizer.t.delete, role: .destructive) {
                if let site = sitePendingDelete {
                    siteStore.removeSite(id: site.id)
                }
            }
        } message: {
            Text(String(format: localizer.t.tenantsDeleteConfirmMessage, sitePendingDelete?.name ?? ""))
        }
    }

    private func siteCard(_ site: Site) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(site.name)
                .font(.headline.weight(.bold))

            HStack(spacing: 8) {
                Button(localizer.t.tenantsRename) {
                    editingSite = site
                }
                .buttonStyle(.bordered)

                Button(localizer.t.delete, role: .destructive) {
                    sitePendingDelete = site
                }
                .buttonStyle(.bordered)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .glassCard()
    }
}

private struct SiteFormView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(SiteStore.self) private var siteStore
    @Environment(Localizer.self) private var localizer

    let tenantId: String
    var siteToEdit: Site?

    @State private var name: String = ""

    private var isEditing: Bool { siteToEdit != nil }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(localizer.t.tenantsName, text: $name)
                } header: {
                    Text(localizer.t.tenantsName)
                }
            }
            .navigationTitle(isEditing ? localizer.t.tenantsRename : localizer.t.sitesAddSite)
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
                if let site = siteToEdit {
                    name = site.name
                }
            }
        }
    }

    private func save() {
        if let site = siteToEdit {
            siteStore.renameSite(id: site.id, name: name)
        } else {
            siteStore.addSite(tenantRef: tenantId, name: name)
        }
        dismiss()
    }
}
