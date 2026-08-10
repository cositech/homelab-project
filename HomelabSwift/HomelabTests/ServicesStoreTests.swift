import XCTest
@testable import Homelab

@MainActor
final class ServicesStoreTests: XCTestCase {
    private var backend: InMemoryKeychainBackend!

    override func setUp() {
        super.setUp()
        backend = InMemoryKeychainBackend()
        KeychainService.backend = backend
    }

    override func tearDown() {
        KeychainService.backend = SecurityKeychainBackend()
        backend = nil
        super.tearDown()
    }

    func testInitialState() {
        let store = ServicesStore()
        XCTAssertEqual(store.connectedCount, 0)
        XCTAssertFalse(store.isReady)
        XCTAssertFalse(store.isConnected(.portainer))
        XCTAssertFalse(store.isConnected(.pihole))
        XCTAssertFalse(store.isConnected(.beszel))
        XCTAssertFalse(store.isConnected(.gitea))
        XCTAssertNil(store.isReachable(.portainer))
    }

    func testTwoInstancesOfSameTypeCoexist() async {
        let store = ServicesStore()
        let home = ServiceInstance(
            id: UUID(uuidString: "00000000-0000-0000-0000-000000000001")!,
            type: .portainer,
            label: "Portainer Home",
            url: "https://portainer-home.local",
            apiKey: "home-key"
        )
        let office = ServiceInstance(
            id: UUID(uuidString: "00000000-0000-0000-0000-000000000002")!,
            type: .portainer,
            label: "Portainer Office",
            url: "https://portainer-office.local",
            apiKey: "office-key"
        )

        await store.saveInstance(home)
        await store.saveInstance(office)

        XCTAssertEqual(store.instances(for: .portainer).count, 2)
        XCTAssertEqual(store.connectedCount, 2)
        XCTAssertEqual(
            Set(store.instances(for: .portainer).map(\.displayLabel)),
            ["Portainer Home", "Portainer Office"]
        )
        XCTAssertEqual(store.preferredInstance(for: .portainer)?.id, home.id)
    }

    func testSetPreferredInstanceUpdatesPreferredPointer() async {
        let store = ServicesStore()
        let first = ServiceInstance(type: .gitea, label: "Main", url: "https://gitea-main.local", token: "token-1")
        let second = ServiceInstance(type: .gitea, label: "Backup", url: "https://gitea-backup.local", token: "token-2")

        await store.saveInstance(first)
        await store.saveInstance(second)
        store.setPreferredInstance(id: second.id, for: .gitea)

        XCTAssertEqual(store.preferredInstance(for: .gitea)?.id, second.id)
    }

    func testDeletingPreferredRepairsPreferredPointer() async {
        let store = ServicesStore()
        let first = ServiceInstance(type: .pihole, label: "Home", url: "https://pihole-home.local", token: "sid-home")
        let second = ServiceInstance(type: .pihole, label: "Office", url: "https://pihole-office.local", token: "sid-office")

        await store.saveInstance(first)
        await store.saveInstance(second)
        store.setPreferredInstance(id: second.id, for: .pihole)

        store.deleteInstance(id: second.id)

        XCTAssertEqual(store.instances(for: .pihole).count, 1)
        XCTAssertEqual(store.preferredInstance(for: .pihole)?.id, first.id)
    }

    func testInitializeRepairsInvalidPreferredInstance() async {
        let first = ServiceInstance(
            id: UUID(uuidString: "10000000-0000-0000-0000-000000000001")!,
            type: .beszel,
            label: "Alpha",
            url: "https://alpha.local",
            token: "token-1"
        )
        let second = ServiceInstance(
            id: UUID(uuidString: "10000000-0000-0000-0000-000000000002")!,
            type: .beszel,
            label: "Beta",
            url: "https://beta.local",
            token: "token-2"
        )

        KeychainService.saveServiceState(
            ServiceStateV2(
                instances: [first, second],
                preferredInstanceIdByType: [.beszel: UUID()]
            )
        )

        let store = ServicesStore()
        await store.initialize()
        store.stopPeriodicHealthChecks()

        XCTAssertEqual(store.preferredInstance(for: .beszel)?.id, first.id)
    }

    func testUnauthorizedNotificationMarksOnlyAffectedInstanceUnreachable() async {
        let store = ServicesStore()
        let first = ServiceInstance(
            id: UUID(uuidString: "20000000-0000-0000-0000-000000000001")!,
            type: .gitea,
            label: "Primary",
            url: "https://gitea-primary.local",
            token: "token-primary"
        )
        let second = ServiceInstance(
            id: UUID(uuidString: "20000000-0000-0000-0000-000000000002")!,
            type: .gitea,
            label: "Secondary",
            url: "https://gitea-secondary.local",
            token: "token-secondary"
        )

        await store.saveInstance(first)
        await store.saveInstance(second)

        NotificationCenter.default.post(
            name: .serviceUnauthorized,
            object: nil,
            userInfo: ["instanceId": first.id]
        )

        try? await Task.sleep(for: .milliseconds(50))

        XCTAssertNotNil(store.instance(id: first.id))
        XCTAssertNotNil(store.instance(id: second.id))
        XCTAssertEqual(store.instances(for: .gitea).count, 2)
        XCTAssertEqual(store.reachability(for: first.id), false)
        XCTAssertNil(store.reachability(for: second.id))
    }

    func testServiceConnectionURLCleaning() {
        let conn = ServiceConnection(type: .portainer, url: "  https://portainer.local///  ", token: "t")
        XCTAssertEqual(conn.url, "https://portainer.local")
    }

    func testServiceConnectionEmptyFallback() {
        let conn1 = ServiceConnection(type: .pihole, url: "https://pi.local", token: "t", fallbackUrl: "")
        XCTAssertNil(conn1.fallbackUrl)

        let conn2 = ServiceConnection(type: .pihole, url: "https://pi.local", token: "t", fallbackUrl: nil)
        XCTAssertNil(conn2.fallbackUrl)

        let conn3 = ServiceConnection(type: .pihole, url: "https://pi.local", token: "t", fallbackUrl: "https://backup")
        XCTAssertEqual(conn3.fallbackUrl, "https://backup")
    }
}

private final class InMemoryKeychainBackend: KeychainBackend, @unchecked Sendable {
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
