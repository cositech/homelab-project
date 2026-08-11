import CryptoKit
import Foundation

struct PrometheusTarget: Codable, Equatable, Sendable {
    let discoveredLabels: [String: String]?
    let labels: [String: String]?
    let scrapeUrl: String?
    let lastError: String?
    let lastScrape: String?
    let health: String?

    var job: String { labels?["job"] ?? discoveredLabels?["job"] ?? "unknown" }
    var instance: String { labels?["instance"] ?? discoveredLabels?["__address__"] ?? "unknown" }
    var identifier: String {
        let input = "\(job)|\(instance)|\(scrapeUrl ?? "")"
        return SHA256.hash(data: Data(input.utf8)).prefix(12).map { String(format: "%02x", $0) }.joined()
    }
}

struct PrometheusAlert: Codable, Equatable, Sendable {
    let labels: [String: String]?
    let annotations: [String: String]?
    let state: String?
    let activeAt: String?

    var name: String { labels?["alertname"] ?? "Unnamed alert" }
    var summary: String? { annotations?["summary"] ?? annotations?["description"] }
}

struct PrometheusOverview: Equatable, Sendable {
    let version: String?
    let targets: [PrometheusTarget]
    let alerts: [PrometheusAlert]
    let warnings: [String]
}

struct GrafanaDashboardAsset: Codable, Equatable, Sendable {
    let uid: String
    let title: String
    let folderTitle: String?
    let tags: [String]?
}

struct GrafanaDataSourceAsset: Codable, Equatable, Sendable {
    let id: Int?
    let uid: String?
    let name: String
    let type: String
    let isDefault: Bool?
    let readOnly: Bool?

    var identifier: String { uid ?? id.map { String($0) } ?? name }
}

struct GrafanaOverview: Equatable, Sendable {
    let version: String?
    let database: String?
    let dashboards: [GrafanaDashboardAsset]
    let dataSources: [GrafanaDataSourceAsset]
}

private struct PrometheusResponse<Value: Decodable & Sendable>: Decodable, Sendable {
    let status: String
    let data: Value
    let warnings: [String]?
}

private struct PrometheusBuildInfo: Codable, Sendable {
    let version: String?
}

private struct PrometheusTargetsData: Codable, Sendable {
    let activeTargets: [PrometheusTarget]
}

private struct PrometheusAlertsData: Codable, Sendable {
    let alerts: [PrometheusAlert]
}

private struct GrafanaHealthResponse: Codable, Sendable {
    let database: String?
    let version: String?
}

actor PrometheusAPIClient {
    private let instanceId: UUID
    private var baseURL = ""
    private var fallbackURL = ""
    private var bearerToken: String?
    private var engine: BaseNetworkEngine

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .prometheus, instanceId: instanceId)
    }

    func configure(url: String, fallbackUrl: String?, bearerToken: String?, allowSelfSigned: Bool, tlsPolicy: TLSPolicy? = nil) async {
        baseURL = Self.normalizeURL(url)
        fallbackURL = Self.normalizeURL(fallbackUrl ?? "")
        self.bearerToken = Self.clean(bearerToken)
        engine = BaseNetworkEngine(
            serviceType: .prometheus,
            instanceId: instanceId,
            tlsPolicy: tlsPolicy ?? (allowSelfSigned ? .insecureCompatibility : .system)
        )
    }

    func authenticate(url: String, bearerToken: String?, fallbackUrl: String?, allowSelfSigned: Bool) async throws {
        await configure(url: url, fallbackUrl: fallbackUrl, bearerToken: bearerToken, allowSelfSigned: allowSelfSigned)
        let _: PrometheusResponse<PrometheusBuildInfo> = try await request(path: "/api/v1/status/buildinfo")
    }

    func ping() async -> Bool {
        do {
            let _: PrometheusResponse<PrometheusBuildInfo> = try await request(path: "/api/v1/status/buildinfo")
            return true
        } catch { return false }
    }

    func getOverview() async throws -> PrometheusOverview {
        let build: PrometheusResponse<PrometheusBuildInfo> = try await request(path: "/api/v1/status/buildinfo")
        let targets: PrometheusResponse<PrometheusTargetsData> = try await request(path: "/api/v1/targets?state=active")
        let alerts: PrometheusResponse<PrometheusAlertsData> = try await request(path: "/api/v1/alerts")
        return PrometheusOverview(
            version: build.data.version,
            targets: targets.data.activeTargets.sorted {
                ($0.job, $0.instance) < ($1.job, $1.instance)
            },
            alerts: alerts.data.alerts,
            warnings: (targets.warnings ?? []) + (alerts.warnings ?? [])
        )
    }

    func normalizedHealth(for overview: PrometheusOverview) -> ProviderHealth {
        let unhealthy = overview.targets.filter { ($0.health ?? "unknown").lowercased() != "up" }.count
        let firing = overview.alerts.filter { ($0.state ?? "unknown").lowercased() == "firing" }.count
        let state: ProviderHealthState = overview.targets.isEmpty
            ? .unknown
            : (unhealthy > 0 || firing > 0 || !overview.warnings.isEmpty ? .degraded : .healthy)
        var attributes = ["targets": String(overview.targets.count), "firingAlerts": String(firing)]
        if let version = overview.version { attributes["version"] = version }
        return ProviderHealth(
            providerId: "prometheus",
            instanceId: instanceId,
            state: state,
            message: "\(overview.targets.count) target(s), \(unhealthy) unhealthy, \(firing) firing alert(s)",
            observedAt: Date(),
            attributes: attributes
        )
    }

    private func request<Value: Decodable & Sendable>(path: String) async throws -> Value {
        try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: path,
            headers: authHeaders()
        )
    }

    private func authHeaders() -> [String: String] {
        var headers = ["Accept": "application/json"]
        if let bearerToken { headers["Authorization"] = "Bearer \(bearerToken)" }
        return headers
    }

    fileprivate static func normalizeURL(_ raw: String) -> String {
        var value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return "" }
        if !value.hasPrefix("http://") && !value.hasPrefix("https://") { value = "https://" + value }
        return value.replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }

    fileprivate static func clean(_ value: String?) -> String? {
        let cleaned = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return cleaned.isEmpty ? nil : cleaned
    }
}

actor GrafanaAPIClient {
    private let instanceId: UUID
    private var baseURL = ""
    private var fallbackURL = ""
    private var serviceAccountToken: String?
    private var engine: BaseNetworkEngine

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .grafana, instanceId: instanceId)
    }

    func configure(url: String, fallbackUrl: String?, serviceAccountToken: String?, allowSelfSigned: Bool, tlsPolicy: TLSPolicy? = nil) async {
        baseURL = PrometheusAPIClient.normalizeURL(url)
        fallbackURL = PrometheusAPIClient.normalizeURL(fallbackUrl ?? "")
        self.serviceAccountToken = PrometheusAPIClient.clean(serviceAccountToken)
        engine = BaseNetworkEngine(
            serviceType: .grafana,
            instanceId: instanceId,
            tlsPolicy: tlsPolicy ?? (allowSelfSigned ? .insecureCompatibility : .system)
        )
    }

    func authenticate(url: String, serviceAccountToken: String, fallbackUrl: String?, allowSelfSigned: Bool) async throws {
        guard !serviceAccountToken.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw APIError.custom("Grafana service account token is required")
        }
        await configure(url: url, fallbackUrl: fallbackUrl, serviceAccountToken: serviceAccountToken, allowSelfSigned: allowSelfSigned)
        let _: GrafanaHealthResponse = try await request(path: "/api/health")
        let _: [GrafanaDashboardAsset] = try await request(path: "/api/search?type=dash-db&limit=1&page=1")
    }

    func ping() async -> Bool {
        do {
            let _: GrafanaHealthResponse = try await request(path: "/api/health")
            return true
        } catch { return false }
    }

    func getOverview() async throws -> GrafanaOverview {
        let health: GrafanaHealthResponse = try await request(path: "/api/health")
        let dashboards: [GrafanaDashboardAsset] = try await request(path: "/api/search?type=dash-db&limit=1000&page=1")
        let dataSources: [GrafanaDataSourceAsset] = try await request(path: "/api/datasources")
        return GrafanaOverview(
            version: health.version,
            database: health.database,
            dashboards: dashboards.sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending },
            dataSources: dataSources.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        )
    }

    func normalizedHealth(for overview: GrafanaOverview) -> ProviderHealth {
        var attributes = ["dashboards": String(overview.dashboards.count), "dataSources": String(overview.dataSources.count)]
        if let version = overview.version { attributes["version"] = version }
        if let database = overview.database { attributes["database"] = database }
        return ProviderHealth(
            providerId: "grafana",
            instanceId: instanceId,
            state: .healthy,
            message: "\(overview.dashboards.count) dashboard(s), \(overview.dataSources.count) data source(s)",
            observedAt: Date(),
            attributes: attributes
        )
    }

    private func request<Value: Decodable & Sendable>(path: String) async throws -> Value {
        guard let serviceAccountToken else { throw APIError.notConfigured }
        return try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: path,
            headers: ["Accept": "application/json", "Authorization": "Bearer \(serviceAccountToken)"]
        )
    }
}

struct InfrastructureOperationsPayload: Sendable {
    let health: ProviderHealth
    let assets: [ProviderResource]
    let alerts: [ProviderEvent]
}

actor InfrastructureOperationsAPIClient {
    private let instanceId: UUID
    private let serviceType: ServiceType
    private var baseURL = ""
    private var fallbackURL = ""
    private var apiToken: String?
    private var apiSecret: String?
    private var engine: BaseNetworkEngine

    init(instanceId: UUID, serviceType: ServiceType) {
        precondition(Self.supportedTypes.contains(serviceType))
        self.instanceId = instanceId
        self.serviceType = serviceType
        self.engine = BaseNetworkEngine(serviceType: serviceType, instanceId: instanceId)
    }

    func configure(
        url: String,
        fallbackUrl: String?,
        apiToken: String?,
        apiSecret: String? = nil,
        allowSelfSigned: Bool,
        tlsPolicy: TLSPolicy? = nil
    ) async {
        baseURL = PrometheusAPIClient.normalizeURL(url)
        fallbackURL = PrometheusAPIClient.normalizeURL(fallbackUrl ?? "")
        self.apiToken = PrometheusAPIClient.clean(apiToken)
        self.apiSecret = PrometheusAPIClient.clean(apiSecret)
        engine = BaseNetworkEngine(
            serviceType: serviceType,
            instanceId: instanceId,
            tlsPolicy: tlsPolicy ?? (allowSelfSigned ? .insecureCompatibility : .system)
        )
    }

    func authenticate(
        url: String,
        apiToken: String,
        apiSecret: String? = nil,
        fallbackUrl: String?,
        allowSelfSigned: Bool
    ) async throws {
        guard !apiToken.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw APIError.custom("A read-only API token is required")
        }
        if serviceType == .opnsense, PrometheusAPIClient.clean(apiSecret) == nil {
            throw APIError.custom("An OPNsense API secret is required")
        }
        await configure(url: url, fallbackUrl: fallbackUrl, apiToken: apiToken, apiSecret: apiSecret, allowSelfSigned: allowSelfSigned)
        if serviceType == .oneuptime {
            _ = try await oneUptimeList(path: "/api/monitor/get-list?skip=0&limit=1", select: Self.oneUptimeMonitorSelect)
        } else {
            _ = try await requestJSON(path: authenticationPath)
        }
    }

    func ping() async -> Bool {
        do {
            if serviceType == .oneuptime {
                _ = try await oneUptimeList(path: "/api/monitor/get-list?skip=0&limit=1", select: Self.oneUptimeMonitorSelect)
            } else {
                _ = try await requestJSON(path: authenticationPath)
            }
            return true
        } catch {
            return false
        }
    }

    func getSnapshot() async throws -> InfrastructureOperationsPayload {
        switch serviceType {
        case .netbox: return try await netBoxSnapshot()
        case .zammad: return try await zammadSnapshot()
        case .pegaprox: return try await pegaProxSnapshot()
        case .opnsense: return try await opnSenseSnapshot()
        case .oneuptime: return try await oneUptimeSnapshot()
        default: throw APIError.notConfigured
        }
    }

    private func opnSenseSnapshot() async throws -> InfrastructureOperationsPayload {
        let firmware = try await requestObject(path: "/api/core/firmware/status")
        let root = try await requestObject(path: "/api/interfaces/overview/interfacesInfo")
        let values: [[String: Any]]
        if let rows = root["interfaces"] as? [[String: Any]] ?? root["rows"] as? [[String: Any]] {
            values = rows
        } else if let keyed = root["interfaces"] as? [String: [String: Any]] {
            values = Array(keyed.values)
        } else {
            values = root.values.compactMap { $0 as? [String: Any] }
        }
        let assets = values.prefix(Self.maxItems).enumerated().map { index, value in
            let id = Self.string(value, "identifier") ?? Self.string(value, "name") ?? Self.string(value, "device") ?? "interface-\(index)"
            var attributes: [String: String] = [:]
            for key in ["device", "ipv4", "ipv6", "media"] {
                if let item = Self.string(value, key) { attributes[key] = item }
            }
            return ProviderResource(
                providerId: "opnsense",
                instanceId: instanceId,
                resourceType: "interface",
                resourceId: id,
                name: Self.string(value, "description") ?? Self.string(value, "name") ?? id,
                state: Self.string(value, "status") ?? (Self.bool(value, "up") ? "up" : "unknown"),
                attributes: attributes
            )
        }
        let down = assets.filter { ["down", "no carrier"].contains($0.state?.lowercased() ?? "") }.count
        let status = Self.string(firmware, "status")?.lowercased()
        let firmwareOK = status.map { ["ok", "none", "done"].contains($0) } ?? true
        var attributes = [
            "interfaces": String(assets.count),
            "interfacesDown": String(down),
            "requestMode": "read-only-get"
        ]
        if let version = Self.string(firmware, "product_version") ?? Self.string(firmware, "productVersion") {
            attributes["version"] = version
        }
        return InfrastructureOperationsPayload(
            health: ProviderHealth(
                providerId: "opnsense",
                instanceId: instanceId,
                state: firmwareOK && down == 0 ? .healthy : .degraded,
                message: "\(assets.count) interface(s), \(down) down",
                observedAt: Date(),
                attributes: attributes
            ),
            assets: assets,
            alerts: []
        )
    }

    private func oneUptimeSnapshot() async throws -> InfrastructureOperationsPayload {
        let monitors = try await oneUptimeList(path: "/api/monitor/get-list?skip=0&limit=\(Self.oneUptimeLimit)", select: Self.oneUptimeMonitorSelect)
        let alerts = try await oneUptimeList(path: "/api/alert/get-list?skip=0&limit=\(Self.oneUptimeLimit)", select: Self.oneUptimeAlertSelect)
        let incidents = try await oneUptimeList(path: "/api/incident/get-list?skip=0&limit=\(Self.oneUptimeLimit)", select: Self.oneUptimeIncidentSelect)
        let assets = monitors.compactMap { monitor -> ProviderResource? in
            guard let id = Self.string(monitor, "_id") else { return nil }
            var attributes = ["endpointDetailsRedacted": "true"]
            if let type = Self.string(monitor, "monitorType") { attributes["monitorType"] = type }
            if let project = Self.string(monitor, "projectId") { attributes["projectId"] = project }
            return ProviderResource(
                providerId: "oneuptime",
                instanceId: instanceId,
                resourceType: "monitor",
                resourceId: id,
                name: Self.string(monitor, "name") ?? "Monitor \(id.prefix(8))",
                state: Self.bool(monitor, "disableActiveMonitoring") ? "disabled" : (Self.string(monitor, "currentMonitorStatusId") ?? "unknown"),
                attributes: attributes
            )
        }
        let now = Date()
        let events = alerts.compactMap { alert -> ProviderEvent? in
            guard let id = Self.string(alert, "_id") else { return nil }
            let number = Self.string(alert, "alertNumber") ?? String(id.prefix(8))
            return ProviderEvent(providerId: "oneuptime", instanceId: instanceId, eventId: "alert:\(id)", severity: "warning", message: "Alert #\(number)", occurredAt: now, resourceId: id)
        } + incidents.compactMap { incident -> ProviderEvent? in
            guard let id = Self.string(incident, "_id") else { return nil }
            let number = Self.string(incident, "incidentNumber") ?? String(id.prefix(8))
            return ProviderEvent(providerId: "oneuptime", instanceId: instanceId, eventId: "incident:\(id)", severity: "critical", message: "Incident #\(number)", occurredAt: now, resourceId: id)
        }
        let disabled = assets.filter { $0.state == "disabled" }.count
        return InfrastructureOperationsPayload(
            health: ProviderHealth(
                providerId: "oneuptime",
                instanceId: instanceId,
                state: disabled > 0 ? .degraded : .healthy,
                message: "\(assets.count) monitor(s), \(alerts.count) alert(s), \(incidents.count) incident(s)",
                observedAt: now,
                attributes: [
                    "monitors": String(assets.count),
                    "disabledMonitors": String(disabled),
                    "alerts": String(alerts.count),
                    "incidents": String(incidents.count),
                    "contentRedacted": "true",
                    "requestMode": "allowlisted-read-post"
                ]
            ),
            assets: assets,
            alerts: events
        )
    }

    private func netBoxSnapshot() async throws -> InfrastructureOperationsPayload {
        let status = try await requestObject(path: "/api/status/")
        let devices = try await netBoxPages(path: "/api/dcim/devices/?exclude=config_context")
        let virtualMachines = try await netBoxPages(path: "/api/virtualization/virtual-machines/?exclude=config_context")
        let assets = devices.compactMap { netBoxAsset($0, type: "device") }
            + virtualMachines.compactMap { netBoxAsset($0, type: "virtual-machine") }
        var attributes = [
            "devices": String(devices.count),
            "virtualMachines": String(virtualMachines.count),
            "resultLimit": String(Self.maxItems)
        ]
        if let version = Self.string(status, "netbox-version") ?? Self.string(status, "netbox_version") ?? Self.string(status, "version") {
            attributes["version"] = version
        }
        return InfrastructureOperationsPayload(
            health: ProviderHealth(
                providerId: "netbox",
                instanceId: instanceId,
                state: .healthy,
                message: "\(devices.count) device(s), \(virtualMachines.count) virtual machine(s)",
                observedAt: Date(),
                attributes: attributes
            ),
            assets: assets,
            alerts: []
        )
    }

    private func zammadSnapshot() async throws -> InfrastructureOperationsPayload {
        let me = try await requestObject(path: "/api/v1/users/me")
        let tickets = try await arrayPages(path: "/api/v1/tickets?expand=true")
        let now = Date()
        let assets = tickets.compactMap { ticket -> ProviderResource? in
            guard let id = Self.string(ticket, "id") else { return nil }
            let number = Self.string(ticket, "number") ?? id
            var attributes = ["contentRedacted": "true"]
            if let priority = Self.string(ticket, "priority") { attributes["priority"] = priority }
            if let group = Self.string(ticket, "group") { attributes["group"] = group }
            if let created = Self.string(ticket, "created_at") { attributes["createdAt"] = created }
            if let updated = Self.string(ticket, "updated_at") { attributes["updatedAt"] = updated }
            return ProviderResource(
                providerId: "zammad",
                instanceId: instanceId,
                resourceType: "ticket",
                resourceId: id,
                name: "Ticket #\(number)",
                state: Self.string(ticket, "state") ?? Self.string(ticket, "state_id") ?? "unknown",
                attributes: attributes
            )
        }
        let alerts = tickets.compactMap { ticket -> ProviderEvent? in
            guard let escalation = Self.string(ticket, "escalation_at"), !escalation.isEmpty,
                  let id = Self.string(ticket, "id") else { return nil }
            let number = Self.string(ticket, "number") ?? id
            return ProviderEvent(
                providerId: "zammad",
                instanceId: instanceId,
                eventId: "ticket:\(id):escalated",
                severity: "warning",
                message: "Ticket #\(number) is escalated",
                occurredAt: now,
                resourceId: id
            )
        }
        var attributes = [
            "tickets": String(tickets.count),
            "escalated": String(alerts.count),
            "piiRedacted": "true",
            "resultLimit": String(Self.maxItems)
        ]
        if let login = Self.string(me, "login") { attributes["authenticatedUser"] = login }
        return InfrastructureOperationsPayload(
            health: ProviderHealth(
                providerId: "zammad",
                instanceId: instanceId,
                state: alerts.isEmpty ? .healthy : .degraded,
                message: "\(tickets.count) visible ticket(s), \(alerts.count) escalated",
                observedAt: now,
                attributes: attributes
            ),
            assets: assets,
            alerts: alerts
        )
    }

    private func pegaProxSnapshot() async throws -> InfrastructureOperationsPayload {
        let clusters = Array(try await requestArray(path: "/api/clusters").prefix(Self.maxClusters))
        var assets: [ProviderResource] = []
        var alerts: [ProviderEvent] = []
        var disconnected = 0
        for cluster in clusters {
            guard let id = Self.string(cluster, "id"), Self.safePathSegment(id) else { continue }
            let name = Self.string(cluster, "display_name")?.nilIfEmpty ?? Self.string(cluster, "name") ?? id
            let connected = Self.bool(cluster, "connected")
            if !connected { disconnected += 1 }
            let encoded = id.addingPercentEncoding(withAllowedCharacters: .alphanumerics.union(CharacterSet(charactersIn: "._-"))) ?? id
            let health = try? await requestObject(path: "/api/clusters/\(encoded)/health")
            var clusterAttributes = ["tenantScoped": "true"]
            if let type = Self.string(cluster, "cluster_type") { clusterAttributes["clusterType"] = type }
            if let score = health.flatMap({ Self.string($0, "score") }) { clusterAttributes["healthScore"] = score }
            assets.append(ProviderResource(
                providerId: "pegaprox",
                instanceId: instanceId,
                resourceType: "cluster",
                resourceId: id,
                name: name,
                state: health.flatMap { Self.string($0, "band") } ?? (connected ? "connected" : "disconnected"),
                attributes: clusterAttributes
            ))
            if let resources = try? await requestArray(path: "/api/clusters/\(encoded)/resources") {
                assets.append(contentsOf: resources.prefix(Self.maxResourcesPerCluster).compactMap { pegaProxResource($0, clusterId: id) })
            }
            if let alertRoot = try? await requestObject(path: "/api/clusters/\(encoded)/active-alerts"),
               let active = alertRoot["active_alerts"] as? [[String: Any]] {
                for alert in active.prefix(Self.maxAlertsPerCluster) {
                    guard let alertId = Self.string(alert, "id") ?? Self.string(alert, "alert_id") else { continue }
                    alerts.append(ProviderEvent(
                        providerId: "pegaprox",
                        instanceId: instanceId,
                        eventId: "cluster:\(id):alert:\(alertId)",
                        severity: Self.severity(Self.string(alert, "severity")),
                        message: String((Self.string(alert, "message") ?? "Active PegaProx alert").prefix(240)),
                        occurredAt: Date(),
                        resourceId: Self.string(alert, "target_name") ?? id
                    ))
                }
            }
        }
        let state: ProviderHealthState = clusters.isEmpty ? .unknown : (disconnected > 0 || !alerts.isEmpty ? .degraded : .healthy)
        return InfrastructureOperationsPayload(
            health: ProviderHealth(
                providerId: "pegaprox",
                instanceId: instanceId,
                state: state,
                message: "\(clusters.count) scoped cluster(s), \(disconnected) disconnected, \(alerts.count) active alert(s)",
                observedAt: Date(),
                attributes: [
                    "clusters": String(clusters.count),
                    "disconnected": String(disconnected),
                    "activeAlerts": String(alerts.count),
                    "serverSideScope": "required"
                ]
            ),
            assets: assets,
            alerts: alerts
        )
    }

    private func netBoxPages(path: String) async throws -> [[String: Any]] {
        var results: [[String: Any]] = []
        var offset = 0
        while results.count < Self.maxItems {
            let separator = path.contains("?") ? "&" : "?"
            let root = try await requestObject(path: "\(path)\(separator)limit=\(Self.pageSize)&offset=\(offset)")
            let page = root["results"] as? [[String: Any]] ?? []
            results.append(contentsOf: page.prefix(Self.maxItems - results.count))
            if page.count < Self.pageSize || root["next"] is NSNull || root["next"] == nil { break }
            offset += Self.pageSize
        }
        return results
    }

    private func arrayPages(path: String) async throws -> [[String: Any]] {
        var results: [[String: Any]] = []
        var pageNumber = 1
        while results.count < Self.maxItems {
            let separator = path.contains("?") ? "&" : "?"
            let page = try await requestArray(path: "\(path)\(separator)page=\(pageNumber)&per_page=\(Self.pageSize)")
            results.append(contentsOf: page.prefix(Self.maxItems - results.count))
            if page.count < Self.pageSize { break }
            pageNumber += 1
        }
        return results
    }

    private func netBoxAsset(_ value: [String: Any], type: String) -> ProviderResource? {
        guard let id = Self.string(value, "id") else { return nil }
        var attributes: [String: String] = [:]
        for (key, source) in [("site", "site"), ("role", "role"), ("tenant", "tenant"), ("cluster", "cluster")] {
            if let text = Self.related(value[source]) { attributes[key] = text }
        }
        if let ip = Self.string(value, "primary_ip4") { attributes["primaryIp4"] = ip }
        if let ip = Self.string(value, "primary_ip6") { attributes["primaryIp6"] = ip }
        return ProviderResource(
            providerId: "netbox",
            instanceId: instanceId,
            resourceType: type,
            resourceId: id,
            name: Self.string(value, "name") ?? Self.string(value, "display") ?? id,
            state: Self.related(value["status"]) ?? "unknown",
            attributes: attributes
        )
    }

    private func pegaProxResource(_ value: [String: Any], clusterId: String) -> ProviderResource? {
        guard let id = Self.string(value, "vmid") ?? Self.string(value, "id") else { return nil }
        let type = Self.string(value, "type") ?? "guest"
        var attributes = ["clusterId": clusterId, "tenantScoped": "true"]
        if let node = Self.string(value, "node") { attributes["node"] = node }
        if let template = Self.string(value, "template") { attributes["template"] = template }
        return ProviderResource(
            providerId: "pegaprox",
            instanceId: instanceId,
            resourceType: type,
            resourceId: "\(clusterId):\(id)",
            name: Self.string(value, "name") ?? "\(type) \(id)",
            state: Self.string(value, "status") ?? "unknown",
            attributes: attributes
        )
    }

    private var authenticationPath: String {
        switch serviceType {
        case .netbox: return "/api/status/"
        case .zammad: return "/api/v1/users/me"
        case .pegaprox: return "/api/clusters"
        case .opnsense: return "/api/core/firmware/status"
        default: return "/"
        }
    }

    private func requestJSON(path: String) async throws -> Any {
        guard let apiToken else { throw APIError.notConfigured }
        var headers = ["Accept": "application/json"]
        if serviceType == .oneuptime {
            headers["ApiKey"] = apiToken
        } else {
            headers["Authorization"] = try Self.authorization(serviceType, apiToken, apiSecret)
        }
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: path,
            headers: headers
        )
        return try JSONSerialization.jsonObject(with: data)
    }

    private func oneUptimeList(path: String, select: [String: Bool]) async throws -> [[String: Any]] {
        guard Self.oneUptimeReadPaths.contains(path.components(separatedBy: "?")[0]), let apiToken else {
            throw APIError.notConfigured
        }
        let body = try JSONSerialization.data(withJSONObject: ["select": select, "query": [:], "sort": ["createdAt": -1]])
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: path,
            method: "POST",
            headers: ["Accept": "application/json", "Content-Type": "application/json", "ApiKey": apiToken],
            body: body
        )
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let values = root["data"] as? [[String: Any]] else {
            throw APIError.custom("Expected OneUptime list response")
        }
        return Array(values.prefix(Self.oneUptimeLimit))
    }

    private func requestObject(path: String) async throws -> [String: Any] {
        guard let value = try await requestJSON(path: path) as? [String: Any] else {
            throw APIError.custom("Expected JSON object")
        }
        return value
    }

    private func requestArray(path: String) async throws -> [[String: Any]] {
        guard let value = try await requestJSON(path: path) as? [[String: Any]] else {
            throw APIError.custom("Expected JSON array")
        }
        return value
    }

    private static func authorization(_ type: ServiceType, _ token: String, _ secret: String?) throws -> String {
        switch type {
        case .netbox: return token.hasPrefix("nbt_") ? "Bearer \(token)" : "Token \(token)"
        case .zammad: return "Token token=\(token)"
        case .pegaprox: return "Bearer \(token)"
        case .opnsense:
            guard let secret else { throw APIError.notConfigured }
            return "Basic " + Data("\(token):\(secret)".utf8).base64EncodedString()
        default: return ""
        }
    }

    private static func string(_ value: [String: Any], _ key: String) -> String? {
        guard let raw = value[key], !(raw is NSNull) else { return nil }
        if let string = raw as? String { return string }
        if let number = raw as? NSNumber { return number.stringValue }
        return nil
    }

    private static func bool(_ value: [String: Any], _ key: String) -> Bool {
        (value[key] as? Bool) ?? (value[key] as? NSNumber)?.boolValue ?? false
    }

    private static func related(_ raw: Any?) -> String? {
        if let value = raw as? String { return value }
        guard let value = raw as? [String: Any] else { return nil }
        return string(value, "display") ?? string(value, "name") ?? string(value, "label") ?? string(value, "value")
    }

    private static func safePathSegment(_ value: String) -> Bool {
        value.range(of: "^[A-Za-z0-9._-]{1,128}$", options: .regularExpression) != nil
    }

    private static func severity(_ raw: String?) -> String {
        switch raw?.lowercased() {
        case "critical", "error", "high": return "critical"
        case "warning", "warn", "medium": return "warning"
        default: return "info"
        }
    }

    private static let supportedTypes: Set<ServiceType> = [.netbox, .zammad, .pegaprox, .opnsense, .oneuptime]
    private static let pageSize = 100
    private static let maxItems = 500
    private static let maxClusters = 50
    private static let maxResourcesPerCluster = 1_000
    private static let maxAlertsPerCluster = 200
    private static let oneUptimeLimit = 100
    private static let oneUptimeReadPaths: Set<String> = ["/api/monitor/get-list", "/api/alert/get-list", "/api/incident/get-list"]
    private static let oneUptimeMonitorSelect = ["currentMonitorStatusId": true, "disableActiveMonitoring": true, "monitorType": true, "name": true, "projectId": true]
    private static let oneUptimeAlertSelect = ["alertSeverityId": true, "currentAlertStateId": true, "projectId": true, "alertNumber": true]
    private static let oneUptimeIncidentSelect = ["currentIncidentStateId": true, "declaredAt": true, "incidentSeverityId": true, "projectId": true, "incidentNumber": true]
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
