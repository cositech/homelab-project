import SwiftUI
import Observation
import Foundation

// Maps to app/(tabs)/_layout.tsx
// iOS 26: TabView automatically gets Liquid Glass tab bar.
// The entire custom GlassTabBar.tsx (342 lines) is replaced by native TabView.

struct ContentView: View {
    @Environment(SettingsStore.self) private var settingsStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL

    var body: some View {
        ZStack {
            TabView {
                Tab(localizer.t.tabHome, systemImage: "house.fill") {
                    HomeView()
                }

                Tab("Operations", systemImage: "waveform.path.ecg") {
                    OperationsView()
                }

                Tab(localizer.t.tabMedia, systemImage: "play.tv.fill") {
                    MediaDashboardView()
                }

                Tab(localizer.t.tabBookmarks, systemImage: "bookmark.fill") {
                    BookmarksView()
                }

                Tab(localizer.t.tabSettings, systemImage: "gearshape.fill") {
                    SettingsView()
                }
            }
            .tabBarMinimizeBehavior(.onScrollDown)

            // Update popup overlay
            if settingsStore.showUpdatePopup, let version = settingsStore.availableUpdateVersion {
                UpdatePopupView(
                    version: version,
                    changelog: settingsStore.availableUpdateChangelog,
                    onUpdate: {
                        if let urlString = settingsStore.availableUpdateURL, let url = URL(string: urlString) {
                            openURL(url)
                        }
                        settingsStore.dismissUpdatePopup()
                    },
                    onDismiss: {
                        settingsStore.dismissUpdatePopup()
                    }
                )
                .transition(.opacity.combined(with: .scale(scale: 0.9)))
            }
        }
        .animation(.spring(duration: 0.35), value: settingsStore.showUpdatePopup)
        .preferredColorScheme(colorScheme)
        .onChange(of: scenePhase) { _, newPhase in
            switch newPhase {
            case .active:
                // Network lifecycle is managed at App level.
                Task { await settingsStore.checkForUpdatesIfNeeded() }
            default:
                break
            }
        }
    }

    private var colorScheme: ColorScheme? {
        switch settingsStore.theme {
        case .light: return .light
        case .dark: return .dark
        case .system: return nil
        }
    }
}

private enum OperationsSection: String, CaseIterable, Identifiable {
    case health = "Health"
    case alerts = "Alerts"
    case assets = "Assets"
    case search = "Search"
    case diagnostics = "Diagnostics"

    var id: String { rawValue }
}

@Observable
@MainActor
private final class OperationsWorkspace {
    var snapshot = OperationsSnapshot()
    var isRefreshing = false
    var errorMessage: String?

    func refresh(using servicesStore: ServicesStore) async {
        guard !isRefreshing else { return }
        isRefreshing = true
        errorMessage = nil
        defer { isRefreshing = false }

        await servicesStore.checkAllReachability(force: true)
        let instances = servicesStore.allInstances
        let observedAt = Date()
        var health: [ProviderHealth] = []
        var alerts: [ProviderEvent] = []
        var assets: [ProviderResource] = []
        var diagnostics: [ProviderDiagnostic] = []

        for instance in instances {
            let descriptor = ProviderRegistry.descriptor(for: instance.type)
            var currentHealth = reachabilityHealth(
                instance: instance,
                providerId: descriptor.id,
                reachable: servicesStore.reachability(for: instance.id),
                observedAt: observedAt
            )

            do {
                switch instance.type {
                case .proxmox:
                    if let client = await servicesStore.proxmoxClient(instanceId: instance.id) {
                        currentHealth = await client.getNormalizedHealth()
                        let details = try await loadProxmox(client: client, instance: instance, observedAt: observedAt)
                        assets.append(contentsOf: details.assets)
                        alerts.append(contentsOf: details.alerts)
                    }
                case .proxmoxBackupServer:
                    if let client = await servicesStore.proxmoxBackupServerClient(instanceId: instance.id) {
                        currentHealth = await client.getNormalizedHealth()
                        let details = try await loadProxmoxBackupServer(client: client, instance: instance, observedAt: observedAt)
                        assets.append(contentsOf: details.assets)
                        alerts.append(contentsOf: details.alerts)
                    }
                case .uptimeKuma:
                    if let client = await servicesStore.uptimeKumaClient(instanceId: instance.id) {
                        currentHealth = await client.getNormalizedHealth()
                        let details = try await loadUptimeKuma(client: client, instance: instance, observedAt: observedAt)
                        assets.append(contentsOf: details.assets)
                        alerts.append(contentsOf: details.alerts)
                    }
                case .prometheus:
                    if let client = await servicesStore.prometheusClient(instanceId: instance.id) {
                        let overview = try await client.getOverview()
                        currentHealth = await client.normalizedHealth(for: overview)
                        let details = loadPrometheus(overview: overview, instance: instance, observedAt: observedAt)
                        assets.append(contentsOf: details.assets)
                        alerts.append(contentsOf: details.alerts)
                    }
                case .grafana:
                    if let client = await servicesStore.grafanaClient(instanceId: instance.id) {
                        let overview = try await client.getOverview()
                        currentHealth = await client.normalizedHealth(for: overview)
                        assets.append(contentsOf: loadGrafana(overview: overview, instance: instance))
                    }
                case .netbox, .zammad, .pegaprox, .opnsense, .oneuptime:
                    if let client = await servicesStore.infrastructureClient(instanceId: instance.id) {
                        let payload = try await client.getSnapshot()
                        currentHealth = payload.health
                        assets.append(contentsOf: payload.assets)
                        alerts.append(contentsOf: payload.alerts)
                    }
                default:
                    break
                }
            } catch {
                currentHealth = ProviderHealth(
                    providerId: descriptor.id,
                    instanceId: instance.id,
                    state: .unavailable,
                    message: error.localizedDescription,
                    observedAt: observedAt,
                    attributes: [:]
                )
            }

            health.append(currentHealth)
            assets.append(ProviderResource(
                providerId: descriptor.id,
                instanceId: instance.id,
                resourceType: "provider-instance",
                resourceId: instance.id.uuidString,
                name: instance.displayLabel,
                state: currentHealth.state.rawValue,
                attributes: ["serviceType": instance.type.rawValue]
            ))
            if currentHealth.state == .degraded || currentHealth.state == .unavailable {
                alerts.append(ProviderEvent(
                    providerId: descriptor.id,
                    instanceId: instance.id,
                    eventId: "health:\(instance.id.uuidString):\(currentHealth.state.rawValue)",
                    severity: currentHealth.state == .unavailable ? "critical" : "warning",
                    message: currentHealth.message ?? "\(descriptor.displayName) is \(currentHealth.state.rawValue)",
                    occurredAt: currentHealth.observedAt,
                    resourceId: instance.id.uuidString
                ))
            }
            diagnostics.append(ProviderDiagnostic(
                providerId: descriptor.id,
                instanceId: instance.id,
                displayName: instance.displayLabel,
                endpoint: safeEndpoint(instance.url),
                tlsMode: instance.tlsPolicy.mode,
                capabilities: descriptor.capabilities,
                state: currentHealth.state,
                message: currentHealth.message,
                observedAt: currentHealth.observedAt
            ))
        }

        var uniqueAssets: [String: ProviderResource] = [:]
        for asset in assets {
            uniqueAssets["\(asset.providerId):\(asset.instanceId):\(asset.resourceType):\(asset.resourceId)"] = asset
        }
        var uniqueAlerts: [String: ProviderEvent] = [:]
        for alert in alerts { uniqueAlerts[alert.eventId] = alert }

        snapshot = OperationsSnapshot(
            health: health.sorted { healthRank($0.state) < healthRank($1.state) },
            alerts: uniqueAlerts.values.sorted {
                let left = severityRank($0.severity)
                let right = severityRank($1.severity)
                return left == right ? $0.occurredAt > $1.occurredAt : left < right
            },
            assets: uniqueAssets.values.sorted {
                $0.resourceType == $1.resourceType
                    ? $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
                    : $0.resourceType < $1.resourceType
            },
            diagnostics: diagnostics.sorted { healthRank($0.state) < healthRank($1.state) },
            refreshedAt: observedAt
        )
    }

    private func loadProxmox(
        client: ProxmoxAPIClient,
        instance: ServiceInstance,
        observedAt: Date
    ) async throws -> (assets: [ProviderResource], alerts: [ProviderEvent]) {
        var assets: [ProviderResource] = []
        var alerts: [ProviderEvent] = []
        for node in try await client.getNodes() {
            assets.append(ProviderResource(
                providerId: "proxmox",
                instanceId: instance.id,
                resourceType: "node",
                resourceId: node.node,
                name: node.node,
                state: node.status,
                attributes: [
                    "cpuPercent": String(format: "%.1f", node.cpuPercent),
                    "memoryPercent": String(format: "%.1f", node.memPercent),
                    "uptime": node.formattedUptime
                ]
            ))
            if !node.isOnline {
                alerts.append(ProviderEvent(providerId: "proxmox", instanceId: instance.id, eventId: "node:\(node.node):offline", severity: "critical", message: "Proxmox node \(node.node) is offline", occurredAt: observedAt, resourceId: node.node))
            }
            for vm in try await client.getVMs(node: node.node) {
                assets.append(ProviderResource(providerId: "proxmox", instanceId: instance.id, resourceType: "virtual-machine", resourceId: String(vm.vmid), name: vm.displayName, state: vm.status, attributes: ["node": node.node]))
            }
            for lxc in try await client.getLXCs(node: node.node) {
                assets.append(ProviderResource(providerId: "proxmox", instanceId: instance.id, resourceType: "container", resourceId: String(lxc.vmid), name: lxc.displayName, state: lxc.status, attributes: ["node": node.node]))
            }
        }
        return (assets, alerts)
    }

    private func loadUptimeKuma(
        client: UptimeKumaAPIClient,
        instance: ServiceInstance,
        observedAt: Date
    ) async throws -> (assets: [ProviderResource], alerts: [ProviderEvent]) {
        var assets: [ProviderResource] = []
        var alerts: [ProviderEvent] = []
        let dashboard = try await client.getDashboard()
        for monitor in dashboard.monitors {
            var attributes: [String: String] = [:]
            if let type = monitor.type { attributes["type"] = type }
            if let target = monitor.target { attributes["target"] = target }
            if let response = monitor.responseTimeMs { attributes["responseTimeMs"] = String(format: "%.0f", response) }
            if let days = monitor.certDaysRemaining { attributes["certDaysRemaining"] = String(days) }
            let state = monitorState(monitor.state)
            assets.append(ProviderResource(providerId: "uptime-kuma", instanceId: instance.id, resourceType: "monitor", resourceId: monitor.id, name: monitor.name, state: state, attributes: attributes))
            if monitor.state == .down {
                alerts.append(ProviderEvent(providerId: "uptime-kuma", instanceId: instance.id, eventId: "monitor:\(monitor.id):down", severity: "critical", message: "\(monitor.name) is down", occurredAt: observedAt, resourceId: monitor.id))
            } else if monitor.state == .pending {
                alerts.append(ProviderEvent(providerId: "uptime-kuma", instanceId: instance.id, eventId: "monitor:\(monitor.id):pending", severity: "warning", message: "\(monitor.name) is pending", occurredAt: observedAt, resourceId: monitor.id))
            }
            if let days = monitor.certDaysRemaining, (0...30).contains(days) {
                alerts.append(ProviderEvent(providerId: "uptime-kuma", instanceId: instance.id, eventId: "monitor:\(monitor.id):certificate", severity: "warning", message: "\(monitor.name) certificate expires in \(days) days", occurredAt: observedAt, resourceId: monitor.id))
            }
        }
        return (assets, alerts)
    }

    private func loadProxmoxBackupServer(
        client: ProxmoxBackupServerAPIClient,
        instance: ServiceInstance,
        observedAt: Date
    ) async throws -> (assets: [ProviderResource], alerts: [ProviderEvent]) {
        var assets: [ProviderResource] = []
        var alerts: [ProviderEvent] = []
        let dashboard = try await client.getDashboard()

        for datastore in dashboard.datastores {
            let usage = datastore.usageRatio
            let maintenance = datastore.maintenance?.isEmpty == false
            let state: String
            if maintenance {
                state = "maintenance"
            } else if (usage ?? 0) >= 0.95 {
                state = "critical"
            } else if (usage ?? 0) >= 0.85 {
                state = "warning"
            } else {
                state = "healthy"
            }

            var attributes: [String: String] = [:]
            if let total = datastore.totalBytes { attributes["totalBytes"] = String(total) }
            if let used = datastore.usedBytes { attributes["usedBytes"] = String(used) }
            if let available = datastore.availableBytes { attributes["availableBytes"] = String(available) }
            if let usage { attributes["usagePercent"] = String(format: "%.1f", usage * 100) }
            if let maintenance = datastore.maintenance, !maintenance.isEmpty { attributes["maintenance"] = maintenance }

            assets.append(ProviderResource(
                providerId: "proxmox-backup-server",
                instanceId: instance.id,
                resourceType: "datastore",
                resourceId: datastore.store,
                name: datastore.store,
                state: state,
                attributes: attributes
            ))

            if maintenance {
                alerts.append(ProviderEvent(providerId: "proxmox-backup-server", instanceId: instance.id, eventId: "datastore:\(datastore.store):maintenance", severity: "warning", message: "PBS datastore \(datastore.store) is in maintenance", occurredAt: observedAt, resourceId: datastore.store))
            } else if let usage, usage >= 0.85 {
                let severity = usage >= 0.95 ? "critical" : "warning"
                alerts.append(ProviderEvent(providerId: "proxmox-backup-server", instanceId: instance.id, eventId: "datastore:\(datastore.store):capacity", severity: severity, message: "PBS datastore \(datastore.store) is \(String(format: "%.1f", usage * 100))% full", occurredAt: observedAt, resourceId: datastore.store))
            }
        }
        return (assets, alerts)
    }

    private func reachabilityHealth(instance: ServiceInstance, providerId: String, reachable: Bool?, observedAt: Date) -> ProviderHealth {
        ProviderHealth(
            providerId: providerId,
            instanceId: instance.id,
            state: reachable == true ? .healthy : reachable == false ? .unavailable : .unknown,
            message: reachable == true ? "\(instance.displayLabel) reachable" : reachable == false ? "\(instance.displayLabel) unreachable" : "\(instance.displayLabel) has not been checked",
            observedAt: observedAt,
            attributes: [:]
        )
    }

    private func loadPrometheus(
        overview: PrometheusOverview,
        instance: ServiceInstance,
        observedAt: Date
    ) -> (assets: [ProviderResource], alerts: [ProviderEvent]) {
        var assets: [ProviderResource] = []
        var alerts: [ProviderEvent] = []
        for target in overview.targets {
            let health = target.health ?? "unknown"
            var attributes = ["job": target.job, "instance": target.instance]
            if let lastScrape = target.lastScrape { attributes["lastScrape"] = lastScrape }
            assets.append(ProviderResource(
                providerId: "prometheus",
                instanceId: instance.id,
                resourceType: "scrape-target",
                resourceId: target.identifier,
                name: "\(target.job) / \(target.instance)",
                state: health.lowercased(),
                attributes: attributes
            ))
            if health.lowercased() != "up" {
                alerts.append(ProviderEvent(
                    providerId: "prometheus",
                    instanceId: instance.id,
                    eventId: "target:\(target.identifier):down",
                    severity: "critical",
                    message: "Prometheus target \(target.job) / \(target.instance) is \(health)",
                    occurredAt: observedAt,
                    resourceId: target.identifier
                ))
            }
        }
        for (index, alert) in overview.alerts.enumerated() {
            let state = alert.state ?? "unknown"
            alerts.append(ProviderEvent(
                providerId: "prometheus",
                instanceId: instance.id,
                eventId: "alert:\(index):\(state.lowercased())",
                severity: state.lowercased() == "firing" ? "critical" : "warning",
                message: alert.summary ?? "Prometheus alert \(alert.name) is \(state)",
                occurredAt: observedAt,
                resourceId: alert.name
            ))
        }
        return (assets, alerts)
    }

    private func loadGrafana(overview: GrafanaOverview, instance: ServiceInstance) -> [ProviderResource] {
        let dashboards = overview.dashboards.map { dashboard in
            var attributes: [String: String] = [:]
            if let folder = dashboard.folderTitle { attributes["folder"] = folder }
            if let tags = dashboard.tags, !tags.isEmpty { attributes["tags"] = tags.joined(separator: ", ") }
            return ProviderResource(
                providerId: "grafana",
                instanceId: instance.id,
                resourceType: "dashboard",
                resourceId: dashboard.uid,
                name: dashboard.title,
                state: "available",
                attributes: attributes
            )
        }
        let dataSources = overview.dataSources.map { dataSource in
            ProviderResource(
                providerId: "grafana",
                instanceId: instance.id,
                resourceType: "data-source",
                resourceId: dataSource.identifier,
                name: dataSource.name,
                state: "configured",
                attributes: [
                    "type": dataSource.type,
                    "default": String(dataSource.isDefault ?? false),
                    "readOnly": String(dataSource.readOnly ?? false)
                ]
            )
        }
        return dashboards + dataSources
    }

    private func safeEndpoint(_ raw: String) -> String {
        guard let components = URLComponents(string: raw), let scheme = components.scheme, let host = components.host else { return "invalid-endpoint" }
        return "\(scheme)://\(host)\(components.port.map { ":\($0)" } ?? "")"
    }

    private func monitorState(_ state: UptimeKumaMonitorState) -> String {
        switch state {
        case .up: return "up"
        case .down: return "down"
        case .pending: return "pending"
        case .maintenance: return "maintenance"
        case .unknown: return "unknown"
        }
    }

    private func healthRank(_ state: ProviderHealthState) -> Int {
        switch state {
        case .unavailable: return 0
        case .degraded: return 1
        case .unknown: return 2
        case .healthy: return 3
        }
    }

    private func severityRank(_ severity: String) -> Int {
        switch severity.lowercased() {
        case "critical": return 0
        case "warning": return 1
        default: return 2
        }
    }
}

struct OperationsView: View {
    @Environment(ServicesStore.self) private var servicesStore
    @State private var workspace = OperationsWorkspace()
    @State private var section: OperationsSection = .health
    @State private var query = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HStack {
                    Text("\(workspace.snapshot.health.count) providers")
                    Spacer()
                    Text("\(workspace.snapshot.alerts.count) alerts")
                    Spacer()
                    Text("\(workspace.snapshot.assets.count) assets")
                }
                .font(.caption.bold())
                .foregroundStyle(.secondary)
                .padding(.horizontal, 18)
                .padding(.vertical, 10)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(OperationsSection.allCases) { candidate in
                            Button(candidate.rawValue) { section = candidate }
                                .buttonStyle(.borderedProminent)
                                .tint(section == candidate ? AppTheme.accent : Color.secondary.opacity(0.25))
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 10)
                }

                if workspace.isRefreshing { ProgressView().padding(.vertical, 4) }
                if let error = workspace.errorMessage {
                    Text(error).font(.caption).foregroundStyle(.red).padding(.horizontal, 16)
                }
                content
            }
            .background(AppTheme.background)
            .navigationTitle("Operations")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { Task { await workspace.refresh(using: servicesStore) } } label: { Image(systemName: "arrow.clockwise") }
                        .disabled(workspace.isRefreshing)
                }
            }
            .searchable(text: $query, prompt: "Search operations data")
            .task { await workspace.refresh(using: servicesStore) }
        }
    }

    @ViewBuilder private var content: some View {
        let results = workspace.snapshot.search(query)
        ScrollView {
            LazyVStack(spacing: 10) {
                if section == .search {
                    if query.isEmpty {
                        empty("Search providers, alerts, assets and diagnostics")
                    } else if results.isEmpty {
                        empty("No matching operations data")
                    } else {
                        ForEach(results.health, id: \.instanceId) { healthCard($0) }
                        ForEach(results.alerts, id: \.eventId) { alertCard($0) }
                        ForEach(Array(results.assets.enumerated()), id: \.offset) { _, asset in assetCard(asset) }
                        ForEach(results.diagnostics, id: \.instanceId) { diagnosticCard($0) }
                    }
                } else if section == .health {
                    if workspace.snapshot.health.isEmpty { empty("No provider health data") }
                    ForEach(workspace.snapshot.health, id: \.instanceId) { healthCard($0) }
                } else if section == .alerts {
                    if workspace.snapshot.alerts.isEmpty { empty("No active alerts") }
                    ForEach(workspace.snapshot.alerts, id: \.eventId) { alertCard($0) }
                } else if section == .assets {
                    if workspace.snapshot.assets.isEmpty { empty("No assets discovered") }
                    ForEach(Array(workspace.snapshot.assets.enumerated()), id: \.offset) { _, asset in assetCard(asset) }
                } else {
                    if workspace.snapshot.diagnostics.isEmpty { empty("No diagnostics available") }
                    ForEach(workspace.snapshot.diagnostics, id: \.instanceId) { diagnosticCard($0) }
                }
            }
            .padding(16)
        }
        .refreshable { await workspace.refresh(using: servicesStore) }
    }

    private func healthCard(_ item: ProviderHealth) -> some View {
        OperationsCard(title: item.providerId, subtitle: item.message ?? item.state.rawValue, trailing: item.state.rawValue, state: item.state)
    }

    private func alertCard(_ item: ProviderEvent) -> some View {
        OperationsCard(title: item.message, subtitle: "\(item.providerId) · \(item.resourceId ?? item.instanceId.uuidString)", trailing: item.severity, state: item.severity.lowercased() == "critical" ? .unavailable : .degraded)
    }

    private func assetCard(_ item: ProviderResource) -> some View {
        let state: ProviderHealthState = ["offline", "down", "unavailable", "critical"].contains(item.state?.lowercased() ?? "") ? .unavailable : ["degraded", "pending", "paused", "warning", "maintenance"].contains(item.state?.lowercased() ?? "") ? .degraded : ["online", "up", "running", "healthy"].contains(item.state?.lowercased() ?? "") ? .healthy : .unknown
        return OperationsCard(title: item.name, subtitle: "\(item.providerId) · \(item.resourceType) · \(item.resourceId)", trailing: item.state ?? item.resourceType, state: state)
    }

    private func diagnosticCard(_ item: ProviderDiagnostic) -> some View {
        OperationsCard(title: item.displayName, subtitle: "\(item.endpoint) · TLS \(item.tlsMode.rawValue) · \(item.capabilities.count) capabilities", trailing: item.providerId, state: item.state)
    }

    private func empty(_ text: String) -> some View {
        Text(text).foregroundStyle(.secondary).frame(maxWidth: .infinity).padding(.vertical, 60)
    }
}

private struct OperationsCard: View {
    let title: String
    let subtitle: String
    let trailing: String
    let state: ProviderHealthState

    var body: some View {
        HStack(spacing: 12) {
            Circle().fill(color).frame(width: 10, height: 10)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.subheadline.bold()).lineLimit(2)
                Text(subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(2)
            }
            Spacer(minLength: 8)
            Text(trailing).font(.caption2.bold()).foregroundStyle(color)
        }
        .padding(14)
        .frame(maxWidth: .infinity)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var color: Color {
        switch state {
        case .healthy: return .green
        case .degraded: return .orange
        case .unavailable: return .red
        case .unknown: return .secondary
        }
    }
}

// MARK: - Update Popup

struct UpdatePopupView: View {
    let version: String
    let changelog: String?
    let onUpdate: () -> Void
    let onDismiss: () -> Void

    @Environment(Localizer.self) private var localizer

    var body: some View {
        ZStack {
            // Dimmed background
            Color.black.opacity(0.55)
                .ignoresSafeArea()
                .onTapGesture { onDismiss() }

            VStack(spacing: 0) {
                // Colored header section
                ZStack(alignment: .topTrailing) {
                    VStack(spacing: 12) {
                        // Icon
                        ZStack {
                            Circle()
                                .fill(.tint.opacity(0.15))
                                .frame(width: 80, height: 80)
                            Image(systemName: "arrow.down.app.fill")
                                .font(.system(size: 38, weight: .semibold))
                                .foregroundStyle(.tint)
                        }

                        // Title
                        Text(localizer.t.updatePopupTitle)
                            .font(.title2.bold())
                            .multilineTextAlignment(.center)

                        // Version badge
                        Text("v\(version)")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 7)
                            .background(Capsule().fill(.tint))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, 36)
                    .padding(.bottom, 20)
                    .padding(.horizontal, 24)

                    // Close button top-right
                    Button(action: onDismiss) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title2)
                            .symbolRenderingMode(.hierarchical)
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 14)
                    .padding(.trailing, 16)
                }

                Divider()
                    .padding(.horizontal, 20)

                // Changelog
                if let changelog, !changelog.isEmpty {
                    ScrollView {
                        Text(changelog)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.leading)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(16)
                    }
                    .frame(minHeight: 180, maxHeight: 380)
                    .background(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(.quaternary.opacity(0.5))
                    )
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                } else {
                    Text(localizer.t.updatePopupBody)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                        .padding(.top, 16)
                }

                // Update button
                Button(action: onUpdate) {
                    Text(localizer.t.settingsUpdateAction)
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 28)
            }
            .background(
                RoundedRectangle(cornerRadius: 32, style: .continuous)
                    .fill(.regularMaterial)
            )
            .clipShape(RoundedRectangle(cornerRadius: 32, style: .continuous))
            .shadow(color: .black.opacity(0.3), radius: 40, y: 12)
            .padding(.horizontal, 20)
        }
    }
}
