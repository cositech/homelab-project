import Foundation
import Observation

/// Persists the device-local `CustomerRegistry` as a single JSON blob in `UserDefaults`, under its
/// own key alongside `TenantStore` and `SiteStore`. All mutation rules live in `CustomerRegistry`;
/// this type only stores the result.
///
/// A device with no customer-kind tenant (or one that never fills in its metadata) keeps
/// `CustomerRegistry.initial` and never writes the key.
@Observable
@MainActor
final class CustomerStore {

    private(set) var registry: CustomerRegistry

    @ObservationIgnored private let defaults: UserDefaults
    nonisolated static let storageKey = "customer_registry_v1"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.registry = Self.snapshot(defaults: defaults)
    }

    nonisolated static func snapshot(defaults: UserDefaults = .standard) -> CustomerRegistry {
        guard
            let data = defaults.data(forKey: storageKey),
            let decoded = try? JSONDecoder().decode(CustomerRegistry.self, from: data)
        else {
            return .initial
        }
        return decoded.normalized()
    }

    @discardableResult
    func setCustomer(tenantRef: String, accountName: String, contact: String?, notes: String?) -> CustomerRegistry {
        let customer = Customer(
            tenantRef: Tenant.refOrDefault(tenantRef),
            accountName: accountName.trimmingCharacters(in: .whitespacesAndNewlines),
            contact: contact?.trimmingCharacters(in: .whitespacesAndNewlines),
            notes: notes?.trimmingCharacters(in: .whitespacesAndNewlines)
        )
        return apply { $0.setting(customer) }
    }

    @discardableResult
    func removeCustomer(tenantRef: String) -> CustomerRegistry {
        apply { $0.removing(tenantRef: tenantRef) }
    }

    private func apply(_ transform: (CustomerRegistry) -> CustomerRegistry) -> CustomerRegistry {
        let next = transform(registry).normalized()
        registry = next
        persist(next)
        return next
    }

    private func persist(_ registry: CustomerRegistry) {
        guard let data = try? JSONEncoder().encode(registry) else { return }
        defaults.set(data, forKey: Self.storageKey)
    }
}
