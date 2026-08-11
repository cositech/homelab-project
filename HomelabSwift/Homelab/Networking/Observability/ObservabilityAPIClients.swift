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
