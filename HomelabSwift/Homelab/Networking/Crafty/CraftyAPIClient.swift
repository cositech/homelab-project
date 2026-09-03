import Foundation

actor CraftyAPIClient {
    private let instanceId: UUID
    private var engine: BaseNetworkEngine
    private var storedAllowSelfSigned = true
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var username: String = ""
    private var password: String = ""
    private var token: String = ""

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .craftyController, instanceId: instanceId)
    }

    func configure(
        url: String,
        username: String,
        password: String,
        token: String,
        fallbackUrl: String? = nil
    , allowSelfSigned: Bool? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.username = username
        self.password = password
        self.token = token
    
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: .craftyController, instanceId: self.instanceId, allowSelfSigned: self.storedAllowSelfSigned)
    }

    func ping() async -> Bool {
        guard !baseURL.isEmpty else { return false }
        let primary = await engine.pingURL(
            "\(baseURL)/api/v2/servers",
            extraHeaders: authHeaders()
        )
        if primary { return true }
        guard !fallbackURL.isEmpty else { return false }
        return await engine.pingURL(
            "\(fallbackURL)/api/v2/servers",
            extraHeaders: authHeaders()
        )
    }

    func authenticate(
        url: String,
        username: String,
        password: String,
        fallbackUrl: String? = nil
    ) async throws -> String {
        let cleanURL = Self.cleanURL(url)
        let body = try JSONEncoder().encode(CraftyLoginRequest(username: username, password: password))
        let response: CraftyEnvelope<CraftyLoginData> = try await engine.request(
            baseURL: cleanURL,
            fallbackURL: Self.cleanURL(fallbackUrl ?? ""),
            path: "/api/v2/auth/login",
            method: "POST",
            headers: ["Content-Type": "application/json"],
            body: body
        )
        guard let token = response.data.token, !token.isEmpty else {
            throw APIError.custom("Crafty login failed")
        }
        return token
    }

    func getServers() async throws -> [CraftyServer] {
        let response: CraftyEnvelope<[CraftyServer]> = try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v2/servers",
            headers: authHeaders()
        )
        return response.data
    }

    func getServerStats(serverId: String) async throws -> CraftyServerStats {
        let response: CraftyEnvelope<CraftyServerStats> = try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v2/servers/\(serverId)/stats",
            headers: authHeaders()
        )
        return response.data
    }

    func getServerLogs(serverId: String, file: Bool = false, raw: Bool = false) async throws -> [String] {
        let response: CraftyEnvelope<[String]> = try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v2/servers/\(serverId)/logs?file=\(file)&raw=\(raw)",
            headers: authHeaders()
        )
        return response.data
    }

    private func reachableMutationBaseURL() async throws -> String {
        guard !baseURL.isEmpty else { throw APIError.notConfigured }
        let headers = authHeaders()
        if await engine.pingURL("\(baseURL)/api/v2/servers", extraHeaders: headers) {
            return baseURL
        }
        if !fallbackURL.isEmpty,
           fallbackURL != baseURL,
           await engine.pingURL("\(fallbackURL)/api/v2/servers", extraHeaders: headers) {
            return fallbackURL
        }
        throw APIError.networkError(URLError(.cannotConnectToHost))
    }

    func sendCommand(serverId: String, command: String) async throws {
        let trimmed = command
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: #"^/+"#, with: "", options: .regularExpression)
        guard !trimmed.isEmpty else { return }

        var headers = authHeaders()
        headers["Content-Type"] = "text/plain"

        let mutationBaseURL = try await reachableMutationBaseURL()
        let response: CraftyStatusResponse = try await engine.request(
            baseURL: mutationBaseURL,
            fallbackURL: "",
            path: "/api/v2/servers/\(serverId)/stdin",
            method: "POST",
            headers: headers,
            body: Data(trimmed.utf8)
        )
        try response.requireSuccess()
    }

    func sendAction(serverId: String, action: CraftyAction) async throws {
        let mutationBaseURL = try await reachableMutationBaseURL()
        let response: CraftyStatusResponse = try await engine.request(
            baseURL: mutationBaseURL,
            fallbackURL: "",
            path: "/api/v2/servers/\(serverId)/action/\(action.rawValue)",
            method: "POST",
            headers: authHeaders()
        )
        try response.requireSuccess()
    }

    private func authHeaders() -> [String: String] {
        guard !token.isEmpty else { return [:] }
        return ["Authorization": "Bearer \(token)"]
    }

    private static func cleanURL(_ value: String) -> String {
        value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }
}

struct CraftyEnvelope<T: Codable>: Codable {
    let status: String
    let data: T
}

private struct CraftyStatusResponse: Codable {
    let status: String
    let error: String?
    let errorData: String?

    enum CodingKeys: String, CodingKey {
        case status
        case error
        case errorData = "error_data"
    }

    func requireSuccess() throws {
        guard status.compare("ok", options: .caseInsensitive) == .orderedSame else {
            throw APIError.custom(errorData ?? error ?? "Crafty action failed")
        }
    }
}

struct CraftyLoginRequest: Codable {
    let username: String
    let password: String
}

struct CraftyLoginData: Codable {
    let token: String?
    let userID: String?

    enum CodingKeys: String, CodingKey {
        case token
        case userID = "user_id"
    }
}

struct CraftyServer: Codable, Identifiable, Hashable {
    let serverID: String
    let serverUUID: String?
    let serverName: String
    let type: String?
    let serverPort: Int?

    var id: String { serverID }

    enum CodingKeys: String, CodingKey {
        case serverID = "server_id"
        case serverUUID = "server_uuid"
        case serverName = "server_name"
        case type
        case serverPort = "server_port"
    }
}

struct CraftyServerStats: Codable, Hashable {
    let running: Bool
    let cpu: Double?
    let mem: String?
    let memPercent: Double?
    let online: Int?
    let max: Int?
    let worldName: String?
    let version: String?
    let updating: Bool
    let waitingStart: Bool
    let crashed: Bool
    let downloading: Bool

    enum CodingKeys: String, CodingKey {
        case running
        case cpu
        case mem
        case memPercent = "mem_percent"
        case online
        case max
        case worldName = "world_name"
        case version
        case updating
        case waitingStart = "waiting_start"
        case crashed
        case downloading
    }
}

enum CraftyAction: String, CaseIterable, Equatable, Sendable {
    case start = "start_server"
    case stop = "stop_server"
    case restart = "restart_server"
    case backup = "backup_server"
    case kill = "kill_server"
    case updateExecutable = "update_executable"

    var actionName: String {
        switch self {
        case .start: return "server.start"
        case .stop: return "server.stop"
        case .restart: return "server.restart"
        case .backup: return "server.backup"
        case .kill: return "server.kill"
        case .updateExecutable: return "server.executable.update"
        }
    }

    var risk: ControlledActionRisk {
        switch self {
        case .start: return .low
        case .stop, .restart, .backup: return .medium
        case .kill, .updateExecutable: return .high
        }
    }

    var requiresConfirmation: Bool { risk != .low }

    func request(
        instanceId: UUID,
        serverId: String,
        confirmed: Bool,
        requestId: UUID = UUID(),
        requestedAt: Date = Date(),
        idempotencyKey: UUID = UUID()
    ) -> ControlledActionRequest {
        ControlledActionRequest(
            id: requestId.uuidString,
            providerRef: "crafty-controller:\(instanceId.uuidString.lowercased())",
            action: actionName,
            targetRef: "server/\(serverId.trimmingCharacters(in: .whitespacesAndNewlines).lowercased())",
            risk: risk,
            requestedAt: ISO8601DateFormatter().string(from: requestedAt),
            idempotencyKey: idempotencyKey.uuidString,
            confirmed: confirmed
        )
    }
}

enum CraftyCommandAction: String, CaseIterable, Equatable, Sendable {
    case send = "server.command.send"

    var risk: ControlledActionRisk { .high }
    var requiresConfirmation: Bool { true }

    func request(
        instanceId: UUID,
        serverId: String,
        confirmed: Bool,
        requestId: UUID = UUID(),
        requestedAt: Date = Date(),
        idempotencyKey: UUID = UUID()
    ) -> ControlledActionRequest {
        ControlledActionRequest(
            id: requestId.uuidString,
            providerRef: "crafty-controller:\(instanceId.uuidString.lowercased())",
            action: rawValue,
            targetRef: "server/\(serverId.trimmingCharacters(in: .whitespacesAndNewlines).lowercased())",
            risk: risk,
            requestedAt: ISO8601DateFormatter().string(from: requestedAt),
            idempotencyKey: idempotencyKey.uuidString,
            confirmed: confirmed
        )
    }
}

enum CraftyControlledOperationFailure {
    static func map(_ error: Error) -> ControlledActionOperationError {
        if let controlled = error as? ControlledActionOperationError {
            return controlled
        }
        if error is URLError {
            return indeterminateOutcome()
        }
        guard let apiError = error as? APIError else {
            return providerReportedFailure()
        }

        switch apiError {
        case .networkError, .bothURLsFailed:
            return indeterminateOutcome()
        case .unauthorized:
            return failure("crafty-invalid-credentials")
        case .httpError(let statusCode, _):
            return failure("crafty-http-\(statusCode)")
        case .notConfigured:
            return failure("crafty-not-configured")
        case .invalidURL:
            return failure("crafty-invalid-url")
        case .decodingError:
            return failure("crafty-response-decode-failure")
        case .requestConfigurationRequired:
            return failure("crafty-configuration-required")
        case .custom:
            return providerReportedFailure()
        }
    }

    private static func indeterminateOutcome() -> ControlledActionOperationError {
        failure("crafty-outcome-indeterminate")
    }

    private static func providerReportedFailure() -> ControlledActionOperationError {
        failure("crafty-provider-reported-failure")
    }

    private static func failure(_ reasonCode: String) -> ControlledActionOperationError {
        ControlledActionOperationError(reasonCode: reasonCode, disposition: .nonRetryable)
    }
}
