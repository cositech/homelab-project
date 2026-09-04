import Foundation

enum PiHoleAuthMode: String, Codable, Equatable {
    case session
    case legacy
}

enum ProxmoxAuthMode: String, Codable, Equatable {
    case credentials
    case apiToken
}

enum UniFiAuthMode: String, Codable, Equatable {
    case siteManager = "site_manager"
    case localNetwork = "local_network"
}

enum TLSMode: String, Codable, Equatable, Hashable, Sendable {
    case system = "SYSTEM"
    case customCA = "CUSTOM_CA"
    case certificatePin = "CERTIFICATE_PIN"
    case insecureCompatibility = "INSECURE_COMPATIBILITY"
}

struct TLSPolicy: Codable, Equatable, Hashable, Sendable {
    var mode: TLSMode
    var customCAPEM: String?
    var certificatePin: String?

    static let system = TLSPolicy(mode: .system)
    static let insecureCompatibility = TLSPolicy(mode: .insecureCompatibility)
}

struct ProxmoxAPITokenParts: Equatable, Hashable {
    let user: String
    let realm: String
    let tokenID: String
    let secret: String

    /// Parses a raw Proxmox API token string in the format `user@realm!tokenID=secret`.
    /// Uses a two-pass approach: first tries positional parsing, then falls back
    /// to regex for robustness against edge cases.
    init?(rawValue: String) {
        let trimmed = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)

        // Primary: positional parsing (fast, handles standard format)
        if let parts = Self.parsePositional(trimmed) {
            self = parts
            return
        }

        // Fallback: regex-based parsing (handles edge cases like special chars in secret)
        if let parts = Self.parseRegex(trimmed) {
            self = parts
            return
        }

        return nil
    }

    /// Parse using positional indices: last `=`, then last `!` before `=`, then last `@` before `!`.
    private static func parsePositional(_ trimmed: String) -> ProxmoxAPITokenParts? {
        guard
            let equalsIndex = trimmed.lastIndex(of: "="),
            let bangIndex = trimmed[..<equalsIndex].lastIndex(of: "!"),
            let atIndex = trimmed[..<bangIndex].lastIndex(of: "@")
        else {
            return nil
        }

        let user = String(trimmed[..<atIndex]).trimmingCharacters(in: .whitespacesAndNewlines)
        let realm = String(trimmed[trimmed.index(after: atIndex)..<bangIndex]).trimmingCharacters(in: .whitespacesAndNewlines)
        let tokenID = String(trimmed[trimmed.index(after: bangIndex)..<equalsIndex]).trimmingCharacters(in: .whitespacesAndNewlines)
        let secret = String(trimmed[trimmed.index(after: equalsIndex)...]).trimmingCharacters(in: .whitespacesAndNewlines)

        guard !user.isEmpty, !realm.isEmpty, !tokenID.isEmpty, !secret.isEmpty else {
            return nil
        }

        return ProxmoxAPITokenParts(user: user, realm: realm, tokenID: tokenID, secret: secret)
    }

    /// Parse using regex as fallback for tokens with unusual characters.
    private static func parseRegex(_ trimmed: String) -> ProxmoxAPITokenParts? {
        // Pattern: everything up to first @ = user, then everything up to first ! = realm,
        // then everything up to first = = tokenID, rest = secret.
        let pattern = #"^(.+?)@(.+?)!(.+?)=(.+)$"#
        guard let regex = try? NSRegularExpression(pattern: pattern, options: []) else {
            return nil
        }
        let nsRange = NSRange(trimmed.startIndex..., in: trimmed)
        guard let match = regex.firstMatch(in: trimmed, options: [], range: nsRange),
              match.numberOfRanges == 5 else {
            return nil
        }

        let user = (Range(match.range(at: 1), in: trimmed).map { String(trimmed[$0]) })?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let realm = (Range(match.range(at: 2), in: trimmed).map { String(trimmed[$0]) })?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let tokenID = (Range(match.range(at: 3), in: trimmed).map { String(trimmed[$0]) })?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let secret = (Range(match.range(at: 4), in: trimmed).map { String(trimmed[$0]) })?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

        guard !user.isEmpty, !realm.isEmpty, !tokenID.isEmpty, !secret.isEmpty else {
            return nil
        }

        return ProxmoxAPITokenParts(user: user, realm: realm, tokenID: tokenID, secret: secret)
    }

    init?(user: String, realm: String, tokenID: String, secret: String) {
        let user = user.trimmingCharacters(in: .whitespacesAndNewlines)
        let realm = realm.trimmingCharacters(in: .whitespacesAndNewlines)
        let tokenID = tokenID.trimmingCharacters(in: .whitespacesAndNewlines)
        let secret = secret.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !user.isEmpty, !realm.isEmpty, !tokenID.isEmpty, !secret.isEmpty else {
            return nil
        }

        self.user = user
        self.realm = realm
        self.tokenID = tokenID
        self.secret = secret
    }

    var rawValue: String {
        "\(user)@\(realm)!\(tokenID)=\(secret)"
    }
}

/// Phase 4 correlation and MSP contracts.
///
/// A `Tenant` is the isolation unit: every `ServiceInstance`, credential, operations record and
/// controlled-action request belongs to exactly one tenant, and no read ever crosses the boundary.
/// A single-tenant install runs entirely inside the implicit `Tenant.defaultId` tenant and shows
/// no new UI.
enum TenantKind: String, Codable, CaseIterable, Equatable, Sendable {
    /// The operator's own estate. The `Tenant.defaultId` tenant is always this kind.
    case personal
    /// An MSP-managed customer estate. Carries `Customer` metadata.
    case customer
}

struct Tenant: Codable, Identifiable, Equatable, Hashable, Sendable {
    let id: String
    var name: String
    var kind: TenantKind

    init(id: String, name: String, kind: TenantKind = .personal) {
        self.id = id
        self.name = name
        self.kind = kind
    }

    var isDefault: Bool { id == Tenant.defaultId }

    /// Id of the implicit tenant every pre-Phase-4 instance is migrated into.
    static let defaultId = "default"

    /// The implicit personal tenant. Cannot be deleted.
    static let `default` = Tenant(id: defaultId, name: "Default", kind: .personal)

    /// Normalizes a nil/blank stored value to a usable tenant id.
    static func refOrDefault(_ value: String?) -> String {
        let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? defaultId : trimmed
    }
}

/// A physical or logical location within a `Tenant` (a rack, a home, a branch office).
struct Site: Codable, Identifiable, Equatable, Hashable, Sendable {
    let id: String
    var tenantRef: String
    var name: String
}

/// MSP-facing metadata on a `.customer` tenant. Never holds a secret; kept out of the audit ledger.
struct Customer: Codable, Equatable, Sendable {
    var tenantRef: String
    var accountName: String
    var contact: String?
    var notes: String?
}

/// The device-local set of configured tenants plus which one is active.
///
/// Every transform is pure and returns a re-`normalized()` value, so the store layer is a thin
/// persistence wrapper and the rules are unit-testable. Invariants held by `normalized()`:
///
///  - `tenants` is non-empty, contains `Tenant.default`, lists it first, and has no duplicate ids.
///  - `activeTenantId` names a tenant that is present.
///  - `allTenantsMode` is only ever `true` when more than one tenant exists.
struct TenantSelection: Codable, Equatable, Sendable {
    var tenants: [Tenant]
    var activeTenantId: String
    var allTenantsMode: Bool

    init(
        tenants: [Tenant] = [Tenant.default],
        activeTenantId: String = Tenant.defaultId,
        allTenantsMode: Bool = false
    ) {
        self.tenants = tenants
        self.activeTenantId = activeTenantId
        self.allTenantsMode = allTenantsMode
    }

    static let initial = TenantSelection()

    var activeTenant: Tenant {
        tenants.first { $0.id == activeTenantId } ?? Tenant.default
    }

    /// A single-tenant install shows no tenant UI and behaves exactly as before Phase 4.
    var isSingleTenant: Bool { tenants.count == 1 }

    /// Local membership set for the Phase-4 `ControlledActionPolicy` gate: every tenant configured
    /// on this device. A local convenience check, not a trust boundary.
    var membershipRefs: Set<String> { Set(tenants.map(\.id)) }

    func normalized() -> TenantSelection {
        var ordered: [Tenant] = [Tenant.default]
        for tenant in tenants {
            let id = Tenant.refOrDefault(tenant.id)
            if id == Tenant.defaultId { continue }
            let normalizedTenant = Tenant(id: id, name: tenant.name, kind: tenant.kind)
            if let existing = ordered.firstIndex(where: { $0.id == id }) {
                ordered[existing] = normalizedTenant
            } else {
                ordered.append(normalizedTenant)
            }
        }
        let active = ordered.contains { $0.id == activeTenantId } ? activeTenantId : Tenant.defaultId
        return TenantSelection(
            tenants: ordered,
            activeTenantId: active,
            allTenantsMode: allTenantsMode && ordered.count > 1
        )
    }

    /// Adds `tenant`, or replaces the existing entry with the same id. The default is never replaced.
    func adding(_ tenant: Tenant) -> TenantSelection {
        let id = Tenant.refOrDefault(tenant.id)
        guard id != Tenant.defaultId else { return normalized() }
        var next = tenants.filter { $0.id != id }
        next.append(Tenant(id: id, name: tenant.name, kind: tenant.kind))
        var copy = self
        copy.tenants = next
        return copy.normalized()
    }

    func renaming(id: String, to name: String) -> TenantSelection {
        let target = Tenant.refOrDefault(id)
        var copy = self
        copy.tenants = tenants.map { tenant in
            guard tenant.id == target else { return tenant }
            return Tenant(id: tenant.id, name: name, kind: tenant.kind)
        }
        return copy.normalized()
    }

    /// Removes a tenant. The default cannot be removed; removing the active tenant falls back to it.
    func removing(id: String) -> TenantSelection {
        let target = Tenant.refOrDefault(id)
        guard target != Tenant.defaultId else { return normalized() }
        var copy = self
        copy.tenants = tenants.filter { $0.id != target }
        if activeTenantId == target { copy.activeTenantId = Tenant.defaultId }
        return copy.normalized()
    }

    /// Selects a single active tenant, leaving all-tenants mode. A no-op if `id` is not present.
    func activating(id: String) -> TenantSelection {
        let target = Tenant.refOrDefault(id)
        guard tenants.contains(where: { $0.id == target }) else { return normalized() }
        var copy = self
        copy.activeTenantId = target
        copy.allTenantsMode = false
        return copy.normalized()
    }

    /// Enables the fan-out "All tenants" mode. Ignored unless more than one tenant exists.
    func settingAllTenantsMode(_ enabled: Bool) -> TenantSelection {
        var copy = self
        copy.allTenantsMode = enabled
        return copy.normalized()
    }
}

struct ServiceInstance: Codable, Identifiable, Equatable, Hashable {
    let id: UUID
    let type: ServiceType
    var label: String
    var url: String
    var tenantRef: String
    var siteRef: String?
    var token: String
    var username: String?
    var apiKey: String?
    var piholePassword: String?
    var piholeAuthMode: PiHoleAuthMode?
    var proxmoxAuthMode: ProxmoxAuthMode?
    var proxmoxRealm: String?
    var proxmoxOTP: String?
    var unifiAuthMode: UniFiAuthMode?
    var fallbackUrl: String?
    var allowSelfSigned: Bool
    var password: String?
    var credentialRef: String
    var tlsPolicy: TLSPolicy

    init(
        id: UUID = UUID(),
        type: ServiceType,
        label: String,
        url: String,
        tenantRef: String = Tenant.defaultId,
        siteRef: String? = nil,
        token: String = "",
        username: String? = nil,
        apiKey: String? = nil,
        piholePassword: String? = nil,
        piholeAuthMode: PiHoleAuthMode? = nil,
        proxmoxAuthMode: ProxmoxAuthMode? = nil,
        proxmoxRealm: String? = nil,
        proxmoxOTP: String? = nil,
        unifiAuthMode: UniFiAuthMode? = nil,
        fallbackUrl: String? = nil,
        allowSelfSigned: Bool = false,
        password: String? = nil,
        credentialRef: String? = nil,
        tlsPolicy: TLSPolicy? = nil
    ) {
        self.id = id
        self.type = type
        self.label = label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? type.displayName : label.trimmingCharacters(in: .whitespacesAndNewlines)
        self.url = type == .unifiNetwork ? Self.cleanUniFiURL(url) : Self.cleanURL(url)
        self.tenantRef = Tenant.refOrDefault(tenantRef)
        self.siteRef = siteRef?.trimmedNilIfEmpty
        self.token = token
        self.username = username?.trimmedNilIfEmpty
        self.apiKey = apiKey?.trimmedNilIfEmpty
        self.piholePassword = piholePassword?.trimmedNilIfEmpty
        self.piholeAuthMode = piholeAuthMode
        self.proxmoxAuthMode = proxmoxAuthMode
        self.proxmoxRealm = proxmoxRealm?.trimmedNilIfEmpty
        self.proxmoxOTP = proxmoxOTP?.trimmedNilIfEmpty
        self.unifiAuthMode = unifiAuthMode
        self.fallbackUrl = type == .unifiNetwork ? Self.cleanOptionalUniFiURL(fallbackUrl) : Self.cleanOptionalURL(fallbackUrl)
        let resolvedTLSPolicy = tlsPolicy ?? (allowSelfSigned ? .insecureCompatibility : .system)
        self.allowSelfSigned = resolvedTLSPolicy.mode == .insecureCompatibility
        self.password = password?.trimmedNilIfEmpty
        self.credentialRef = credentialRef ?? "credential:v1:\(id.uuidString.lowercased())"
        self.tlsPolicy = resolvedTLSPolicy
    }

    var displayLabel: String {
        label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? type.displayName : label
    }

    var piHoleStoredSecret: String? {
        if let piholePassword, !piholePassword.isEmpty {
            return piholePassword
        }
        if type == .pihole, let apiKey, !apiKey.isEmpty {
            return apiKey
        }
        return nil
    }

    func updatingToken(_ token: String, piholeAuthMode: PiHoleAuthMode? = nil) -> ServiceInstance {
        let migratedPiHolePassword = type == .pihole ? piHoleStoredSecret : piholePassword
        return ServiceInstance(
            id: id,
            type: type,
            label: displayLabel,
            url: url,
            tenantRef: tenantRef,
            siteRef: siteRef,
            token: token,
            username: username,
            apiKey: apiKey,
            piholePassword: migratedPiHolePassword,
            piholeAuthMode: piholeAuthMode ?? self.piholeAuthMode,
            proxmoxAuthMode: proxmoxAuthMode,
            proxmoxRealm: proxmoxRealm,
            proxmoxOTP: proxmoxOTP,
            unifiAuthMode: unifiAuthMode,
            fallbackUrl: fallbackUrl,
            allowSelfSigned: allowSelfSigned,
            password: password,
            credentialRef: credentialRef,
            tlsPolicy: tlsPolicy
        )
    }

    func updating(
        label: String? = nil,
        url: String? = nil,
        tenantRef: String? = nil,
        siteRef: String? = nil,
        token: String? = nil,
        username: String? = nil,
        apiKey: String? = nil,
        piholePassword: String? = nil,
        piholeAuthMode: PiHoleAuthMode? = nil,
        proxmoxAuthMode: ProxmoxAuthMode? = nil,
        proxmoxRealm: String? = nil,
        proxmoxOTP: String? = nil,
        unifiAuthMode: UniFiAuthMode? = nil,
        fallbackUrl: String? = nil,
        allowSelfSigned: Bool? = nil,
        password: String? = nil,
        tlsPolicy: TLSPolicy? = nil
    ) -> ServiceInstance {
        ServiceInstance(
            id: id,
            type: type,
            label: label ?? displayLabel,
            url: url ?? self.url,
            tenantRef: tenantRef ?? self.tenantRef,
            siteRef: siteRef ?? self.siteRef,
            token: token ?? self.token,
            username: username ?? self.username,
            apiKey: apiKey ?? self.apiKey,
            piholePassword: piholePassword ?? self.piholePassword,
            piholeAuthMode: piholeAuthMode ?? self.piholeAuthMode,
            proxmoxAuthMode: proxmoxAuthMode ?? self.proxmoxAuthMode,
            proxmoxRealm: proxmoxRealm ?? self.proxmoxRealm,
            proxmoxOTP: proxmoxOTP ?? self.proxmoxOTP,
            unifiAuthMode: unifiAuthMode ?? self.unifiAuthMode,
            fallbackUrl: fallbackUrl ?? self.fallbackUrl,
            allowSelfSigned: allowSelfSigned ?? self.allowSelfSigned,
            password: password ?? self.password,
            credentialRef: credentialRef,
            tlsPolicy: tlsPolicy ?? self.tlsPolicy
        )
    }

    private static func cleanURL(_ value: String) -> String {
        value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }

    private static func cleanUniFiURL(_ value: String) -> String {
        stripKnownUniFiAPIPath(from: cleanURL(value))
    }

    private static func cleanOptionalURL(_ value: String?) -> String? {
        guard let value else { return nil }
        let cleaned = cleanURL(value)
        return cleaned.isEmpty ? nil : cleaned
    }

    private static func cleanOptionalUniFiURL(_ value: String?) -> String? {
        guard let value else { return nil }
        let cleaned = cleanUniFiURL(value)
        return cleaned.isEmpty ? nil : cleaned
    }

    private static func stripKnownUniFiAPIPath(from raw: String) -> String {
        guard var components = URLComponents(string: raw) else {
            return raw
        }
        let path = components.percentEncodedPath
        guard !path.isEmpty, isKnownUniFiAPIPath(path)
        else {
            return raw
        }
        components.percentEncodedPath = ""
        components.percentEncodedQuery = nil
        components.fragment = nil
        return components.string ?? raw
    }

    private static func isKnownUniFiAPIPath(_ path: String) -> Bool {
        let normalized = path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        return normalized == "proxy/network/integration/v1" ||
            normalized.hasPrefix("proxy/network/integration/v1/") ||
            normalized == "v1" ||
            normalized.hasPrefix("v1/")
    }

    enum CodingKeys: String, CodingKey {
        case id
        case type
        case label
        case url
        case tenantRef
        case siteRef
        case token
        case username
        case apiKey
        case piholePassword
        case piholeAuthMode
        case proxmoxAuthMode
        case proxmoxRealm
        case proxmoxOTP
        case unifiAuthMode
        case fallbackUrl
        case allowSelfSigned
        case password
        case credentialRef
        case tlsPolicy
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let id = try container.decode(UUID.self, forKey: .id)
        let legacyAllowSelfSigned = try container.decodeIfPresent(Bool.self, forKey: .allowSelfSigned) ?? false
        self.init(
            id: id,
            type: try container.decode(ServiceType.self, forKey: .type),
            label: try container.decode(String.self, forKey: .label),
            url: try container.decode(String.self, forKey: .url),
            tenantRef: try container.decodeIfPresent(String.self, forKey: .tenantRef) ?? Tenant.defaultId,
            siteRef: try container.decodeIfPresent(String.self, forKey: .siteRef),
            token: try container.decodeIfPresent(String.self, forKey: .token) ?? "",
            username: try container.decodeIfPresent(String.self, forKey: .username),
            apiKey: try container.decodeIfPresent(String.self, forKey: .apiKey),
            piholePassword: try container.decodeIfPresent(String.self, forKey: .piholePassword),
            piholeAuthMode: try container.decodeIfPresent(PiHoleAuthMode.self, forKey: .piholeAuthMode),
            proxmoxAuthMode: try container.decodeIfPresent(ProxmoxAuthMode.self, forKey: .proxmoxAuthMode),
            proxmoxRealm: try container.decodeIfPresent(String.self, forKey: .proxmoxRealm),
            proxmoxOTP: try container.decodeIfPresent(String.self, forKey: .proxmoxOTP),
            unifiAuthMode: try container.decodeIfPresent(UniFiAuthMode.self, forKey: .unifiAuthMode),
            fallbackUrl: try container.decodeIfPresent(String.self, forKey: .fallbackUrl),
            allowSelfSigned: legacyAllowSelfSigned,
            password: try container.decodeIfPresent(String.self, forKey: .password),
            credentialRef: try container.decodeIfPresent(String.self, forKey: .credentialRef),
            tlsPolicy: try container.decodeIfPresent(TLSPolicy.self, forKey: .tlsPolicy)
        )
    }
}

struct ServiceStateV2: Codable, Equatable {
    var instances: [ServiceInstance]
    var preferredInstanceIdByType: [ServiceType: UUID]

    static let empty = ServiceStateV2(instances: [], preferredInstanceIdByType: [:])
}

struct ServiceCredentialEnvelope: Codable, Equatable {
    var token: String?
    var apiKey: String?
    var piholePassword: String?
    var proxmoxOTP: String?
    var password: String?
    var customCAPEM: String?

    init(instance: ServiceInstance) {
        token = instance.token.nilIfEmpty
        apiKey = instance.apiKey?.nilIfEmpty
        piholePassword = instance.piholePassword?.nilIfEmpty
        proxmoxOTP = instance.proxmoxOTP?.nilIfEmpty
        password = instance.password?.nilIfEmpty
        customCAPEM = instance.tlsPolicy.customCAPEM?.nilIfEmpty
    }
}

struct ServiceInstanceMetadata: Codable, Equatable {
    let id: UUID
    let type: ServiceType
    var label: String
    var url: String
    var tenantRef: String
    var siteRef: String?
    var username: String?
    var piholeAuthMode: PiHoleAuthMode?
    var proxmoxAuthMode: ProxmoxAuthMode?
    var proxmoxRealm: String?
    var unifiAuthMode: UniFiAuthMode?
    var fallbackUrl: String?
    var credentialRef: String
    var tlsMode: TLSMode
    var certificatePin: String?

    private enum CodingKeys: String, CodingKey {
        case id, type, label, url, tenantRef, siteRef, username, piholeAuthMode
        case proxmoxAuthMode, proxmoxRealm, unifiAuthMode, fallbackUrl, credentialRef, tlsMode, certificatePin
    }

    init(instance: ServiceInstance) {
        id = instance.id
        type = instance.type
        label = instance.displayLabel
        url = instance.url
        tenantRef = instance.tenantRef
        siteRef = instance.siteRef
        username = instance.username
        piholeAuthMode = instance.piholeAuthMode
        proxmoxAuthMode = instance.proxmoxAuthMode
        proxmoxRealm = instance.proxmoxRealm
        unifiAuthMode = instance.unifiAuthMode
        fallbackUrl = instance.fallbackUrl
        credentialRef = instance.credentialRef
        tlsMode = instance.tlsPolicy.mode
        certificatePin = instance.tlsPolicy.certificatePin
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        type = try container.decode(ServiceType.self, forKey: .type)
        label = try container.decode(String.self, forKey: .label)
        url = try container.decode(String.self, forKey: .url)
        // Metadata persisted before Phase 4 has no tenant scope; it belongs to the default tenant.
        tenantRef = Tenant.refOrDefault(try container.decodeIfPresent(String.self, forKey: .tenantRef))
        siteRef = try container.decodeIfPresent(String.self, forKey: .siteRef)
        username = try container.decodeIfPresent(String.self, forKey: .username)
        piholeAuthMode = try container.decodeIfPresent(PiHoleAuthMode.self, forKey: .piholeAuthMode)
        proxmoxAuthMode = try container.decodeIfPresent(ProxmoxAuthMode.self, forKey: .proxmoxAuthMode)
        proxmoxRealm = try container.decodeIfPresent(String.self, forKey: .proxmoxRealm)
        unifiAuthMode = try container.decodeIfPresent(UniFiAuthMode.self, forKey: .unifiAuthMode)
        fallbackUrl = try container.decodeIfPresent(String.self, forKey: .fallbackUrl)
        credentialRef = try container.decode(String.self, forKey: .credentialRef)
        tlsMode = try container.decode(TLSMode.self, forKey: .tlsMode)
        certificatePin = try container.decodeIfPresent(String.self, forKey: .certificatePin)
    }

    func hydrated(with credentials: ServiceCredentialEnvelope?) -> ServiceInstance {
        let credentials = credentials ?? ServiceCredentialEnvelope.empty
        return ServiceInstance(
            id: id,
            type: type,
            label: label,
            url: url,
            tenantRef: tenantRef,
            siteRef: siteRef,
            token: credentials.token ?? "",
            username: username,
            apiKey: credentials.apiKey,
            piholePassword: credentials.piholePassword,
            piholeAuthMode: piholeAuthMode,
            proxmoxAuthMode: proxmoxAuthMode,
            proxmoxRealm: proxmoxRealm,
            proxmoxOTP: credentials.proxmoxOTP,
            unifiAuthMode: unifiAuthMode,
            fallbackUrl: fallbackUrl,
            allowSelfSigned: tlsMode == .insecureCompatibility,
            password: credentials.password,
            credentialRef: credentialRef,
            tlsPolicy: TLSPolicy(
                mode: tlsMode,
                customCAPEM: credentials.customCAPEM,
                certificatePin: certificatePin
            )
        )
    }
}

struct ServiceStateV3: Codable, Equatable {
    var instances: [ServiceInstanceMetadata]
    var preferredInstanceIdByType: [ServiceType: UUID]
}

struct ServiceConnection: Codable, Identifiable, Equatable {
    var id: String { type.rawValue }
    let type: ServiceType
    var url: String
    var token: String
    var username: String?
    var apiKey: String?
    var piholePassword: String?
    var piholeAuthMode: PiHoleAuthMode?
    var proxmoxAuthMode: ProxmoxAuthMode?
    var proxmoxRealm: String?
    var fallbackUrl: String?
    var allowSelfSigned: Bool

    init(
        type: ServiceType,
        url: String,
        token: String = "",
        username: String? = nil,
        apiKey: String? = nil,
        piholePassword: String? = nil,
        piholeAuthMode: PiHoleAuthMode? = nil,
        proxmoxAuthMode: ProxmoxAuthMode? = nil,
        proxmoxRealm: String? = nil,
        fallbackUrl: String? = nil,
        allowSelfSigned: Bool = false
    ) {
        self.type = type
        self.url = url.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
        self.token = token
        self.username = username
        self.apiKey = apiKey
        self.piholePassword = piholePassword
        self.piholeAuthMode = piholeAuthMode
        self.proxmoxAuthMode = proxmoxAuthMode
        self.proxmoxRealm = proxmoxRealm?.trimmedNilIfEmpty
        self.fallbackUrl = fallbackUrl?.isEmpty == true ? nil : fallbackUrl
        self.allowSelfSigned = allowSelfSigned
    }

    var piHoleStoredSecret: String? {
        if let piholePassword, !piholePassword.isEmpty {
            return piholePassword
        }
        if type == .pihole, let apiKey, !apiKey.isEmpty {
            return apiKey
        }
        return nil
    }

    func updatingToken(_ token: String, piholeAuthMode: PiHoleAuthMode? = nil) -> ServiceConnection {
        let migratedPiHolePassword = type == .pihole ? piHoleStoredSecret : piholePassword
        return ServiceConnection(
            type: type,
            url: url,
            token: token,
            username: username,
            apiKey: apiKey,
            piholePassword: migratedPiHolePassword,
            piholeAuthMode: piholeAuthMode ?? self.piholeAuthMode,
            proxmoxAuthMode: proxmoxAuthMode,
            proxmoxRealm: proxmoxRealm,
            fallbackUrl: fallbackUrl,
            allowSelfSigned: allowSelfSigned
        )
    }

    func migratedInstance(id: UUID = UUID()) -> ServiceInstance {
        ServiceInstance(
            id: id,
            type: type,
            label: type.displayName,
            url: url,
            token: token,
            username: username,
            apiKey: apiKey,
            piholePassword: type == .pihole ? piHoleStoredSecret : piholePassword,
            piholeAuthMode: piholeAuthMode,
            proxmoxAuthMode: proxmoxAuthMode,
            proxmoxRealm: proxmoxRealm,
            fallbackUrl: fallbackUrl,
            allowSelfSigned: allowSelfSigned
        )
    }

    enum CodingKeys: String, CodingKey {
        case type
        case url
        case token
        case username
        case apiKey
        case piholePassword
        case piholeAuthMode
        case proxmoxAuthMode
        case proxmoxRealm
        case fallbackUrl
        case allowSelfSigned
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.init(
            type: try container.decode(ServiceType.self, forKey: .type),
            url: try container.decode(String.self, forKey: .url),
            token: try container.decodeIfPresent(String.self, forKey: .token) ?? "",
            username: try container.decodeIfPresent(String.self, forKey: .username),
            apiKey: try container.decodeIfPresent(String.self, forKey: .apiKey),
            piholePassword: try container.decodeIfPresent(String.self, forKey: .piholePassword),
            piholeAuthMode: try container.decodeIfPresent(PiHoleAuthMode.self, forKey: .piholeAuthMode),
            proxmoxAuthMode: try container.decodeIfPresent(ProxmoxAuthMode.self, forKey: .proxmoxAuthMode),
            proxmoxRealm: try container.decodeIfPresent(String.self, forKey: .proxmoxRealm),
            fallbackUrl: try container.decodeIfPresent(String.self, forKey: .fallbackUrl),
            allowSelfSigned: try container.decodeIfPresent(Bool.self, forKey: .allowSelfSigned) ?? false
        )
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }

    var trimmedNilIfEmpty: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

private extension ServiceCredentialEnvelope {
    static let empty = ServiceCredentialEnvelope(
        token: nil,
        apiKey: nil,
        piholePassword: nil,
        proxmoxOTP: nil,
        password: nil,
        customCAPEM: nil
    )

    init(
        token: String?,
        apiKey: String?,
        piholePassword: String?,
        proxmoxOTP: String?,
        password: String?,
        customCAPEM: String?
    ) {
        self.token = token
        self.apiKey = apiKey
        self.piholePassword = piholePassword
        self.proxmoxOTP = proxmoxOTP
        self.password = password
        self.customCAPEM = customCAPEM
    }
}

func resolvedServiceArtworkURL(_ raw: String?, instance: ServiceInstance?) -> String? {
    guard let instance else {
        return normalizedArtworkURLString(raw)
    }
    return resolvedServiceArtworkURL(
        raw,
        baseURL: instance.url,
        fallbackURL: instance.fallbackUrl,
        apiKey: instance.apiKey
    )
}

func serviceArtworkHeaders(for resolvedURL: String?, instance: ServiceInstance?) -> [String: String] {
    guard
        let resolvedURL,
        let instance,
        let apiKey = instance.apiKey?.trimmingCharacters(in: .whitespacesAndNewlines),
        !apiKey.isEmpty,
        isServiceHostedArtworkURL(resolvedURL, baseURL: instance.url) || isServiceHostedArtworkURL(resolvedURL, baseURL: instance.fallbackUrl)
    else {
        return [:]
    }
    return ["X-Api-Key": apiKey]
}

func resolvedServiceArtworkURL(
    _ raw: String?,
    baseURL: String,
    fallbackURL: String? = nil,
    apiKey: String? = nil
) -> String? {
    guard let value = normalizedArtworkURLString(raw) else { return nil }
    if value.hasPrefix("http://") || value.hasPrefix("https://") {
        let isHostedByService = isServiceHostedArtworkURL(value, baseURL: baseURL)
            || isServiceHostedArtworkURL(value, baseURL: fallbackURL)
        return isHostedByService ? appendingArtworkAPIKey(apiKey, to: value) : value
    }

    let cleanBase = baseURL
        .trimmingCharacters(in: .whitespacesAndNewlines)
        .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    guard !cleanBase.isEmpty else { return value }
    let absolute = cleanBase + (value.hasPrefix("/") ? value : "/\(value)")
    return appendingArtworkAPIKey(apiKey, to: absolute)
}

private func normalizedArtworkURLString(_ raw: String?) -> String? {
    guard let raw else { return nil }
    let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    return value.isEmpty ? nil : value
}

private func isServiceHostedArtworkURL(_ raw: String, baseURL: String?) -> Bool {
    guard
        let baseURL,
        !baseURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
        let artworkURL = URL(string: raw),
        let serviceURL = URL(string: baseURL)
    else {
        return false
    }

    guard artworkURL.host?.lowercased() == serviceURL.host?.lowercased() else {
        return false
    }
    return (artworkURL.port ?? artworkURL.defaultPort) == (serviceURL.port ?? serviceURL.defaultPort)
}

private func appendingArtworkAPIKey(_ apiKey: String?, to raw: String) -> String {
    guard let apiKey = apiKey?.trimmingCharacters(in: .whitespacesAndNewlines), !apiKey.isEmpty else {
        return raw
    }
    guard var components = URLComponents(string: raw) else { return raw }
    let existingItems = components.queryItems ?? []
    if existingItems.contains(where: { $0.name.caseInsensitiveCompare("apikey") == .orderedSame }) {
        return raw
    }
    components.queryItems = existingItems + [URLQueryItem(name: "apikey", value: apiKey)]
    return components.string ?? raw
}

private extension URL {
    var defaultPort: Int? {
        switch scheme?.lowercased() {
        case "http":
            return 80
        case "https":
            return 443
        default:
            return nil
        }
    }
}
