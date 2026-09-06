import Foundation
import Observation

/// Persists the device-local `SiteRegistry` as a single JSON blob in `UserDefaults`, under its own
/// key alongside `TenantStore`. All mutation rules live in `SiteRegistry`; this type only stores
/// the result and generates ids for new sites.
///
/// A device that never creates a site keeps `SiteRegistry.initial` and never writes the key.
@Observable
@MainActor
final class SiteStore {

    private(set) var registry: SiteRegistry

    @ObservationIgnored private let defaults: UserDefaults
    private static let storageKey = "site_registry_v1"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.registry = Self.snapshot(defaults: defaults)
    }

    nonisolated static func snapshot(defaults: UserDefaults = .standard) -> SiteRegistry {
        guard
            let data = defaults.data(forKey: storageKey),
            let decoded = try? JSONDecoder().decode(SiteRegistry.self, from: data)
        else {
            return .initial
        }
        return decoded.normalized()
    }

    /// Creates a site for `tenantRef` with a fresh id and returns the stored registry.
    @discardableResult
    func addSite(tenantRef: String, name: String) -> SiteRegistry {
        let site = Site(
            id: "site-\(UUID().uuidString.lowercased())",
            tenantRef: Tenant.refOrDefault(tenantRef),
            name: name.trimmingCharacters(in: .whitespacesAndNewlines)
        )
        return apply { $0.adding(site) }
    }

    @discardableResult
    func renameSite(id: String, name: String) -> SiteRegistry {
        apply { $0.renaming(id: id, to: name.trimmingCharacters(in: .whitespacesAndNewlines)) }
    }

    @discardableResult
    func removeSite(id: String) -> SiteRegistry {
        apply { $0.removing(id: id) }
    }

    private func apply(_ transform: (SiteRegistry) -> SiteRegistry) -> SiteRegistry {
        let next = transform(registry).normalized()
        registry = next
        persist(next)
        return next
    }

    private func persist(_ registry: SiteRegistry) {
        guard let data = try? JSONEncoder().encode(registry) else { return }
        defaults.set(data, forKey: Self.storageKey)
    }
}
