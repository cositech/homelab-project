import Foundation
import Observation

/// Persists the device-local `TenantSelection` (configured tenants + active selection) as a single
/// JSON blob in `UserDefaults`. All mutation rules live in `TenantSelection`; this type only stores
/// the result and generates ids for new tenants.
///
/// A device that never adds a second tenant keeps `TenantSelection.initial` and never writes the
/// key, so a single-tenant install is byte-identical to pre-Phase-4.
@Observable
@MainActor
final class TenantStore {

    private(set) var selection: TenantSelection

    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private static let storageKey = "tenant_selection_v1"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.selection = Self.load(from: defaults)
    }

    var activeTenant: Tenant { selection.activeTenant }
    var activeTenantRef: String { selection.activeTenantId }
    var isSingleTenant: Bool { selection.isSingleTenant }
    var membershipRefs: Set<String> { selection.membershipRefs }

    /// Creates a tenant with a fresh id and returns the stored selection.
    @discardableResult
    func addTenant(name: String, kind: TenantKind = .customer) -> TenantSelection {
        let tenant = Tenant(
            id: "tenant-\(UUID().uuidString.lowercased())",
            name: name.trimmingCharacters(in: .whitespacesAndNewlines),
            kind: kind
        )
        return apply { $0.adding(tenant) }
    }

    @discardableResult
    func renameTenant(id: String, name: String) -> TenantSelection {
        apply { $0.renaming(id: id, to: name.trimmingCharacters(in: .whitespacesAndNewlines)) }
    }

    @discardableResult
    func removeTenant(id: String) -> TenantSelection {
        apply { $0.removing(id: id) }
    }

    @discardableResult
    func setActiveTenant(id: String) -> TenantSelection {
        apply { $0.activating(id: id) }
    }

    @discardableResult
    func setAllTenantsMode(_ enabled: Bool) -> TenantSelection {
        apply { $0.settingAllTenantsMode(enabled) }
    }

    private func apply(_ transform: (TenantSelection) -> TenantSelection) -> TenantSelection {
        let next = transform(selection).normalized()
        selection = next
        persist(next)
        return next
    }

    private func persist(_ selection: TenantSelection) {
        guard let data = try? JSONEncoder().encode(selection) else { return }
        defaults.set(data, forKey: Self.storageKey)
    }

    private static func load(from defaults: UserDefaults) -> TenantSelection {
        guard
            let data = defaults.data(forKey: storageKey),
            let decoded = try? JSONDecoder().decode(TenantSelection.self, from: data)
        else {
            return .initial
        }
        return decoded.normalized()
    }
}
