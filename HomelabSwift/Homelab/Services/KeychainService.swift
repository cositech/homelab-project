import Foundation
import Security

enum KeychainService {
    /// Not `private` so tests can assert against the raw backend using the same service string.
    static let service = "com.homelab.homelab.services"
    private static let legacyConnectionsAccount = "homelab_user"
    private static let serviceStateV2Account = "homelab_service_state_v2"
    private static let serviceStateV3Account = "homelab_service_state_v3_metadata"
    private static let pinAccount = "homelab_pin"
    nonisolated(unsafe) static var backend: any KeychainBackend = SecurityKeychainBackend()

    @discardableResult
    static func saveServiceState(_ state: ServiceStateV2) -> Bool {
        let previousReferences = Set(loadV3Metadata()?.instances.map(\.credentialRef) ?? [])
        let metadata = ServiceStateV3(
            instances: state.instances.map(ServiceInstanceMetadata.init),
            preferredInstanceIdByType: state.preferredInstanceIdByType
        )

        for instance in state.instances {
            let envelope = ServiceCredentialEnvelope(instance: instance)
            guard
                let data = try? JSONEncoder().encode(envelope),
                backend.save(data: data, service: service, account: instance.credentialRef),
                let verifiedData = backend.load(service: service, account: instance.credentialRef),
                (try? JSONDecoder().decode(ServiceCredentialEnvelope.self, from: verifiedData)) == envelope
            else {
                return false
            }
        }

        guard
            let metadataData = try? JSONEncoder().encode(metadata),
            backend.save(data: metadataData, service: service, account: serviceStateV3Account),
            let verifiedMetadataData = backend.load(service: service, account: serviceStateV3Account),
            (try? JSONDecoder().decode(ServiceStateV3.self, from: verifiedMetadataData)) == metadata
        else {
            return false
        }

        let activeReferences = Set(metadata.instances.map(\.credentialRef))
        for removedReference in previousReferences.subtracting(activeReferences) {
            backend.delete(service: service, account: removedReference)
        }
        backend.delete(service: service, account: serviceStateV2Account)
        backend.delete(service: service, account: legacyConnectionsAccount)
        return true
    }

    static func loadServiceState() -> ServiceStateV2 {
        if let metadata = loadV3Metadata() {
            let instances = metadata.instances.map { instanceMetadata in
                let credentials = backend.load(service: service, account: instanceMetadata.credentialRef)
                    .flatMap { try? JSONDecoder().decode(ServiceCredentialEnvelope.self, from: $0) }
                return instanceMetadata.hydrated(with: credentials)
            }
            return ServiceStateV2(
                instances: instances,
                preferredInstanceIdByType: metadata.preferredInstanceIdByType
            )
        }

        if let data = backend.load(service: service, account: serviceStateV2Account),
           let state = try? JSONDecoder().decode(ServiceStateV2.self, from: data) {
            _ = saveServiceState(state)
            return state
        }

        let legacyConnections = loadLegacyConnections()
        guard !legacyConnections.isEmpty else { return .empty }

        var preferredByType: [ServiceType: UUID] = [:]
        let instances = legacyConnections
            .sorted { $0.key.rawValue < $1.key.rawValue }
            .map { type, connection -> ServiceInstance in
                let migrated = connection.migratedInstance()
                preferredByType[type] = migrated.id
                return migrated
            }

        let migratedState = ServiceStateV2(instances: instances, preferredInstanceIdByType: preferredByType)
        _ = saveServiceState(migratedState)
        return migratedState
    }

    static func deleteAll() {
        loadV3Metadata()?.instances.forEach { metadata in
            backend.delete(service: service, account: metadata.credentialRef)
        }
        backend.delete(service: service, account: serviceStateV3Account)
        backend.delete(service: service, account: serviceStateV2Account)
        backend.delete(service: service, account: legacyConnectionsAccount)
    }

    // MARK: - PIN Storage

    static func savePin(_ pin: String) {
        guard let data = pin.data(using: .utf8) else { return }
        _ = backend.save(data: data, service: service, account: pinAccount)
    }

    static func loadPin() -> String? {
        guard let data = backend.load(service: service, account: pinAccount),
              let pin = String(data: data, encoding: .utf8)
        else { return nil }

        return pin
    }

    static func deletePin() {
        backend.delete(service: service, account: pinAccount)
    }

    private static func loadLegacyConnections() -> [ServiceType: ServiceConnection] {
        guard let data = backend.load(service: service, account: legacyConnectionsAccount),
              let connections = try? JSONDecoder().decode([ServiceType: ServiceConnection].self, from: data)
        else { return [:] }

        return connections
    }

    private static func loadV3Metadata() -> ServiceStateV3? {
        guard let data = backend.load(service: service, account: serviceStateV3Account) else {
            return nil
        }
        return try? JSONDecoder().decode(ServiceStateV3.self, from: data)
    }

    /// Instance id (lowercased UUID string) → `tenantRef`, read from the V3 metadata blob only —
    /// no per-instance credential hydration. Empty when there is no V3 metadata at all
    /// (a pre-tenant install), which callers treat as "everything is the default tenant".
    nonisolated static func instanceTenantRefs() -> [String: String] {
        guard let metadata = loadV3Metadata() else { return [:] }
        return Dictionary(
            metadata.instances.map { ($0.id.uuidString.lowercased(), Tenant.refOrDefault($0.tenantRef)) },
            uniquingKeysWith: { first, _ in first }
        )
    }
}

protocol KeychainBackend: Sendable {
    @discardableResult func save(data: Data, service: String, account: String) -> Bool
    func load(service: String, account: String) -> Data?
    func delete(service: String, account: String)
}

struct SecurityKeychainBackend: KeychainBackend {
    @discardableResult
    func save(data: Data, service: String, account: String) -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        let updateStatus = SecItemUpdate(
            query as CFDictionary,
            [kSecValueData as String: data] as CFDictionary
        )
        if updateStatus == errSecSuccess {
            return true
        }
        guard updateStatus == errSecItemNotFound else {
            return false
        }

        var addQuery = query
        addQuery[kSecValueData as String] = data
        addQuery[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        return SecItemAdd(addQuery as CFDictionary, nil) == errSecSuccess
    }

    func load(service: String, account: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess else { return nil }
        return result as? Data
    }

    func delete(service: String, account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}
