import XCTest
@testable import Homelab

final class KeychainServiceTests: XCTestCase {
    private var backend: KeychainBackendDouble!

    override func setUp() {
        super.setUp()
        backend = KeychainBackendDouble()
        KeychainService.backend = backend
    }

    override func tearDown() {
        KeychainService.backend = SecurityKeychainBackend()
        backend = nil
        super.tearDown()
    }

    func testLegacyConnectionsMigrateToServiceStateV2Idempotently() throws {
        let legacy = [
            ServiceType.portainer: ServiceConnection(type: .portainer, url: "https://portainer.local", token: "jwt", apiKey: "api-key"),
            ServiceType.pihole: ServiceConnection(type: .pihole, url: "https://pihole.local", token: "sid", piholePassword: "secret")
        ]
        let payload = try JSONEncoder().encode(legacy)
        backend.save(data: payload, service: "com.homelab.homelab.services", account: "homelab_user")

        let migrated = KeychainService.loadServiceState()
        let reloaded = KeychainService.loadServiceState()

        XCTAssertEqual(migrated.instances.count, 2)
        XCTAssertEqual(reloaded, migrated)
        XCTAssertEqual(Set(migrated.instances.map(\.label)), ["Portainer", "Pi-hole"])
        XCTAssertEqual(migrated.preferredInstanceIdByType[.portainer], migrated.instances.first(where: { $0.type == .portainer })?.id)
        XCTAssertEqual(migrated.preferredInstanceIdByType[.pihole], migrated.instances.first(where: { $0.type == .pihole })?.id)
        XCTAssertNil(backend.load(service: "com.homelab.homelab.services", account: "homelab_user"))
        XCTAssertNil(backend.load(service: "com.homelab.homelab.services", account: "homelab_service_state_v2"))

        let metadataData = try XCTUnwrap(
            backend.load(
                service: "com.homelab.homelab.services",
                account: "homelab_service_state_v3_metadata"
            )
        )
        let metadataJSON = try XCTUnwrap(String(data: metadataData, encoding: .utf8))
        XCTAssertFalse(metadataJSON.contains("jwt"))
        XCTAssertFalse(metadataJSON.contains("api-key"))
        XCTAssertFalse(metadataJSON.contains("secret"))

        let metadata = try JSONDecoder().decode(ServiceStateV3.self, from: metadataData)
        XCTAssertEqual(metadata.instances.count, 2)
        for instance in metadata.instances {
            XCTAssertNotNil(
                backend.load(
                    service: "com.homelab.homelab.services",
                    account: instance.credentialRef
                )
            )
        }
    }

    func testServiceStateV2MigratesOnlyAfterCredentialVerification() throws {
        let instance = ServiceInstance(
            id: UUID(uuidString: "30000000-0000-0000-0000-000000000001")!,
            type: .proxmox,
            label: "Lab",
            url: "https://pve.local:8006",
            token: "ticket",
            apiKey: "csrf",
            password: "password"
        )
        let legacyState = ServiceStateV2(instances: [instance], preferredInstanceIdByType: [.proxmox: instance.id])
        backend.save(
            data: try JSONEncoder().encode(legacyState),
            service: "com.homelab.homelab.services",
            account: "homelab_service_state_v2"
        )

        let migrated = KeychainService.loadServiceState()

        XCTAssertEqual(migrated.instances.single?.token, "ticket")
        XCTAssertEqual(migrated.instances.single?.apiKey, "csrf")
        XCTAssertEqual(migrated.instances.single?.password, "password")
        XCTAssertNil(backend.load(service: "com.homelab.homelab.services", account: "homelab_service_state_v2"))
    }
}

private extension Array {
    var single: Element? { count == 1 ? first : nil }
}

private final class KeychainBackendDouble: KeychainBackend, @unchecked Sendable {
    private var storage: [String: Data] = [:]

    @discardableResult
    func save(data: Data, service: String, account: String) -> Bool {
        storage[key(service: service, account: account)] = data
        return true
    }

    func load(service: String, account: String) -> Data? {
        storage[key(service: service, account: account)]
    }

    func delete(service: String, account: String) {
        storage.removeValue(forKey: key(service: service, account: account))
    }

    private func key(service: String, account: String) -> String {
        "\(service)::\(account)"
    }
}
