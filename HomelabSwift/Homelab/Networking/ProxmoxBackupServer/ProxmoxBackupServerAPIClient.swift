import Foundation

struct ProxmoxBackupDatastore: Codable, Equatable, Sendable {
    let store: String
    let totalBytes: Int64?
    let usedBytes: Int64?
    let availableBytes: Int64?
    let maintenance: String?

    var usageRatio: Double? {
        guard let totalBytes, totalBytes > 0, let usedBytes else { return nil }
        return Double(usedBytes) / Double(totalBytes)
    }

    enum CodingKeys: String, CodingKey {
        case store
        case totalBytes = "total"
        case usedBytes = "used"
        case availableBytes = "avail"
        case maintenance = "maintenance-mode"
    }
}

struct ProxmoxBackupServerDashboard: Equatable, Sendable {
    let version: String?
    let datastores: [ProxmoxBackupDatastore]
}

private struct PBSAPIResponse<Value: Decodable & Sendable>: Decodable, Sendable {
    let data: Value
}

private struct PBSVersion: Decodable, Sendable {
    let version: String?
}

actor ProxmoxBackupServerAPIClient {
    private let instanceId: UUID
    private var baseURL = ""
    private var fallbackURL = ""
    private var tokenId: String?
    private var tokenSecret: String?
    private var engine: BaseNetworkEngine

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .proxmoxBackupServer, instanceId: instanceId)
    }

    func configure(
        url: String,
        fallbackUrl: String? = nil,
        tokenId: String?,
        tokenSecret: String?,
        allowSelfSigned: Bool = false,
        tlsPolicy: TLSPolicy? = nil
    ) async {
        baseURL = Self.normalizeURL(url)
        fallbackURL = Self.normalizeURL(fallbackUrl ?? "")
        self.tokenId = Self.clean(tokenId)
        self.tokenSecret = Self.clean(tokenSecret)
        engine = BaseNetworkEngine(
            serviceType: .proxmoxBackupServer,
            instanceId: instanceId,
            tlsPolicy: tlsPolicy ?? (allowSelfSigned ? .insecureCompatibility : .system)
        )
    }

    func authenticate(
        url: String,
        tokenId: String,
        tokenSecret: String,
        fallbackUrl: String?,
        allowSelfSigned: Bool
    ) async throws {
        let normalizedTokenId = tokenId.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedSecret = tokenSecret.trimmingCharacters(in: .whitespacesAndNewlines)
        try Self.validateToken(tokenId: normalizedTokenId, tokenSecret: normalizedSecret)
        await configure(
            url: url,
            fallbackUrl: fallbackUrl,
            tokenId: normalizedTokenId,
            tokenSecret: normalizedSecret,
            allowSelfSigned: allowSelfSigned
        )
        let _: PBSAPIResponse<PBSVersion> = try await request(path: "/api2/json/version")
    }

    func ping() async -> Bool {
        do {
            let _: PBSAPIResponse<PBSVersion> = try await request(path: "/api2/json/version")
            return true
        } catch {
            return false
        }
    }

    func getDashboard() async throws -> ProxmoxBackupServerDashboard {
        let version: PBSAPIResponse<PBSVersion> = try await request(path: "/api2/json/version")
        let datastores: PBSAPIResponse<[ProxmoxBackupDatastore]> = try await request(path: "/api2/json/status/datastore-usage")
        return ProxmoxBackupServerDashboard(
            version: version.data.version,
            datastores: datastores.data.sorted {
                $0.store.localizedCaseInsensitiveCompare($1.store) == .orderedAscending
            }
        )
    }

    func getNormalizedHealth() async -> ProviderHealth {
        let descriptor = ProviderRegistry.descriptor(for: .proxmoxBackupServer)
        do {
            let dashboard = try await getDashboard()
            let maintenanceCount = dashboard.datastores.filter { $0.maintenance?.isEmpty == false }.count
            let highestUsage = dashboard.datastores.compactMap(\.usageRatio).max()
            let state: ProviderHealthState
            if dashboard.datastores.isEmpty {
                state = .unknown
            } else if maintenanceCount > 0 || (highestUsage ?? 0) >= 0.85 {
                state = .degraded
            } else {
                state = .healthy
            }
            let message: String
            if dashboard.datastores.isEmpty {
                message = "PBS reachable; no visible datastores"
            } else if maintenanceCount > 0 {
                message = "\(maintenanceCount) datastore(s) in maintenance"
            } else if let highestUsage {
                message = "\(dashboard.datastores.count) datastore(s); highest usage \(Self.formatPercent(highestUsage))"
            } else {
                message = "\(dashboard.datastores.count) datastore(s) available"
            }
            var attributes = ["datastores": String(dashboard.datastores.count)]
            if let version = dashboard.version { attributes["version"] = version }
            if let highestUsage { attributes["highestUsagePercent"] = Self.formatPercent(highestUsage) }
            return ProviderHealth(
                providerId: descriptor.id,
                instanceId: instanceId,
                state: state,
                message: message,
                observedAt: Date(),
                attributes: attributes
            )
        } catch {
            return ProviderHealth(
                providerId: descriptor.id,
                instanceId: instanceId,
                state: .unavailable,
                message: error.localizedDescription,
                observedAt: Date(),
                attributes: [:]
            )
        }
    }

    private func request<Value: Decodable & Sendable>(path: String) async throws -> Value {
        guard let tokenId, let tokenSecret else { throw APIError.notConfigured }
        try Self.validateToken(tokenId: tokenId, tokenSecret: tokenSecret)
        return try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: path,
            headers: [
                "Accept": "application/json",
                "Authorization": "PBSAPIToken=\(tokenId):\(tokenSecret)"
            ]
        )
    }

    private static func validateToken(tokenId: String, tokenSecret: String) throws {
        guard tokenId.contains("@"), tokenId.contains("!") else {
            throw APIError.custom("PBS token ID must use user@realm!token-name")
        }
        guard !tokenSecret.isEmpty else {
            throw APIError.custom("PBS token secret is required")
        }
    }

    private static func normalizeURL(_ raw: String) -> String {
        var clean = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return "" }
        if !clean.hasPrefix("http://") && !clean.hasPrefix("https://") { clean = "https://" + clean }
        return clean.replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }

    private static func clean(_ value: String?) -> String? {
        guard let value else { return nil }
        let cleaned = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return cleaned.isEmpty ? nil : cleaned
    }

    private static func formatPercent(_ ratio: Double) -> String {
        String(format: "%.1f", ratio * 100.0)
    }
}
