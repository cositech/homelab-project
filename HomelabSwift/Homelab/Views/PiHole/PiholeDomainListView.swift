import SwiftUI

struct PiholeDomainListView: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var domains: [PiholeDomain] = []
    @State private var isLoading = true
    @State private var error: Error?
    @State private var selectedTab: PiholeDomainListType = .allow

    @State private var showingAddAlert = false
    @State private var newDomainText = ""
    @State private var pendingRemoval: PiholeDomain?

    var filteredDomains: [PiholeDomain] {
        domains.filter { $0.type == selectedTab }
    }

    var body: some View {
        Group {
            if isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let error = error {
                VStack(spacing: 12) {
                    Image(systemName: "exclamationmark.triangle")
                        .font(.largeTitle)
                        .foregroundStyle(AppTheme.stopped)
                        .accessibilityHidden(true)
                    Text(error.localizedDescription)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                        .multilineTextAlignment(.center)
                    Button(localizer.t.retry) {
                        Task { await fetchDomains() }
                    }
                    .buttonStyle(.bordered)
                }
                .padding()
            } else {
                VStack(spacing: 0) {
                    ViewThatFits(in: .horizontal) {
                        Picker(localizer.t.piholeListType, selection: $selectedTab) {
                            Text(localizer.t.piholeAllowed).tag(PiholeDomainListType.allow)
                            Text(localizer.t.piholeBlocked).tag(PiholeDomainListType.deny)
                        }
                        .pickerStyle(.segmented)

                        Picker(localizer.t.piholeListType, selection: $selectedTab) {
                            Text(localizer.t.piholeAllowed).tag(PiholeDomainListType.allow)
                            Text(localizer.t.piholeBlocked).tag(PiholeDomainListType.deny)
                        }
                        .pickerStyle(.menu)
                    }
                    .padding()

                    List {
                        let filtered = filteredDomains
                        if filtered.isEmpty {
                            Text(localizer.t.piholeNoDomains)
                                .font(.subheadline)
                                .foregroundStyle(AppTheme.textMuted)
                                .listRowBackground(Color.clear)
                        } else {
                            ForEach(filtered) { domain in
                                HStack {
                                    Image(systemName: selectedTab == .allow ? "checkmark.circle.fill" : "xmark.circle.fill")
                                        .foregroundStyle(selectedTab == .allow ? AppTheme.running : AppTheme.stopped)
                                        .accessibilityHidden(true)
                                    Text(domain.domain)
                                        .font(.body)
                                }
                                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                    Button(role: .destructive) {
                                        pendingRemoval = domain
                                    } label: {
                                        Label(localizer.t.delete, systemImage: "trash")
                                    }
                                }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
        }
        .navigationTitle(localizer.t.piholeDomainManagement)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    newDomainText = ""
                    showingAddAlert = true
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityLabel(localizer.t.piholeAddDomain)
            }
        }
        .task {
            await fetchDomains()
        }
        .alert(localizer.t.piholeAddDomain, isPresented: $showingAddAlert) {
            TextField(localizer.t.piholeDomainPlaceholder, text: $newDomainText)
                .keyboardType(.URL)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            Button(localizer.t.cancel, role: .cancel) { }
            Button(localizer.t.save) {
                Task { await addDomain() }
            }
            .disabled(newDomainText.trimmingCharacters(in: .whitespaces).isEmpty)
        } message: {
            Text(String(format: localizer.t.piholeAddDomainDesc, selectedTab == .allow ? localizer.t.piholeAllowed : localizer.t.piholeBlocked))
        }
        .confirmationDialog(
            localizer.t.delete,
            isPresented: Binding(
                get: { pendingRemoval != nil },
                set: { value in if !value { pendingRemoval = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button(localizer.t.delete, role: .destructive) {
                guard let item = pendingRemoval else { return }
                pendingRemoval = nil
                Task { await removeDomain(item, confirmed: true) }
            }
            Button(localizer.t.cancel, role: .cancel) { pendingRemoval = nil }
        } message: {
            Text(pendingRemoval?.domain ?? "")
        }
    }

    private func fetchDomains() async {
        isLoading = true
        error = nil
        do {
            guard let client = await servicesStore.piholeClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            domains = try await client.getDomains()
        } catch {
            self.error = error
        }
        isLoading = false
    }

    private func addDomain() async {
        guard !newDomainText.isEmpty else { return }
        let url = newDomainText.trimmingCharacters(in: .whitespaces)
        do {
            guard let client = await servicesStore.piholeClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            let result = await servicesStore.controlledActionCoordinator.execute(
                request: PiholeControlledDomainAction.add.request(
                    instanceId: instanceId,
                    domain: url,
                    listType: selectedTab,
                    confirmed: true
                ),
                actorRole: .admin,
                providerCapabilities: ProviderRegistry.descriptor(for: .pihole).capabilities
            ) {
                try await client.addDomain(domain: url, to: selectedTab)
            }
            guard result.state == .succeeded else {
                throw APIError.custom(String(format: localizer.t.errorActionFailed, result.reasonCode))
            }
            await fetchDomains()
        } catch {
            self.error = error
        }
    }

    private func removeDomain(_ item: PiholeDomain, confirmed: Bool) async {
        guard let type = item.type else { return }
        do {
            guard let client = await servicesStore.piholeClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            let result = await servicesStore.controlledActionCoordinator.execute(
                request: PiholeControlledDomainAction.remove.request(
                    instanceId: instanceId,
                    domain: item.domain,
                    listType: type,
                    confirmed: confirmed
                ),
                actorRole: .admin,
                providerCapabilities: ProviderRegistry.descriptor(for: .pihole).capabilities
            ) {
                try await client.removeDomain(domain: item.domain, from: type)
            }
            guard result.state == .succeeded else {
                throw APIError.custom(String(format: localizer.t.errorActionFailed, result.reasonCode))
            }
            if let index = domains.firstIndex(where: { $0.id == item.id }) {
                domains.remove(at: index)
            }
        } catch {
            self.error = error
        }
    }
}
