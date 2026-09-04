package com.homelab.app.domain.model

import kotlinx.serialization.Serializable

/**
 * The device-local set of configured tenants plus which one is active.
 *
 * All transforms are pure and return a re-[normalized] value, so the store layer is a thin
 * persistence wrapper and the rules are unit-testable without a DataStore. Invariants held by
 * [normalized]:
 *
 *  - [tenants] is non-empty, contains [Tenant.DEFAULT], lists it first, and has no duplicate ids.
 *  - [activeTenantId] names a tenant that is present.
 *  - [allTenantsMode] is only ever `true` when more than one tenant exists.
 */
@Serializable
data class TenantSelection(
    val tenants: List<Tenant> = listOf(Tenant.DEFAULT),
    val activeTenantId: String = Tenant.DEFAULT_ID,
    val allTenantsMode: Boolean = false
) {
    val activeTenant: Tenant
        get() = tenants.firstOrNull { it.id == activeTenantId } ?: Tenant.DEFAULT

    /** A single-tenant install shows no tenant UI and behaves exactly as before Phase 4. */
    val isSingleTenant: Boolean get() = tenants.size == 1

    /**
     * Local membership set for the Phase-4 [com.homelab.app.domain.action.ControlledActionPolicy]
     * gate: every tenant configured on this device. This is a local convenience check, not a trust
     * boundary — a gateway deployment still enforces tenant authorization server-side.
     */
    val membershipRefs: Set<String> get() = tenants.map { it.id }.toSet()

    fun normalized(): TenantSelection {
        val deduped = LinkedHashMap<String, Tenant>()
        deduped[Tenant.DEFAULT_ID] = Tenant.DEFAULT
        for (tenant in tenants) {
            val id = Tenant.refOrDefault(tenant.id)
            if (id == Tenant.DEFAULT_ID) continue
            deduped[id] = tenant.copy(id = id)
        }
        val ordered = deduped.values.toList()
        val active = ordered.firstOrNull { it.id == activeTenantId }?.id ?: Tenant.DEFAULT_ID
        return TenantSelection(
            tenants = ordered,
            activeTenantId = active,
            allTenantsMode = allTenantsMode && ordered.size > 1
        )
    }

    /** Adds [tenant], or replaces the existing entry with the same id. The default is never replaced. */
    fun adding(tenant: Tenant): TenantSelection {
        val id = Tenant.refOrDefault(tenant.id)
        if (id == Tenant.DEFAULT_ID) return normalized()
        val next = tenants.filterNot { it.id == id } + tenant.copy(id = id)
        return copy(tenants = next).normalized()
    }

    fun renaming(id: String, name: String): TenantSelection {
        val target = Tenant.refOrDefault(id)
        val next = tenants.map { if (it.id == target) it.copy(name = name) else it }
        return copy(tenants = next).normalized()
    }

    /** Removes a tenant. The default cannot be removed; removing the active tenant falls back to the default. */
    fun removing(id: String): TenantSelection {
        val target = Tenant.refOrDefault(id)
        if (target == Tenant.DEFAULT_ID) return normalized()
        val next = tenants.filterNot { it.id == target }
        val active = if (activeTenantId == target) Tenant.DEFAULT_ID else activeTenantId
        return copy(tenants = next, activeTenantId = active).normalized()
    }

    /** Selects a single active tenant, leaving all-tenants mode. A no-op if [id] is not present. */
    fun activating(id: String): TenantSelection {
        val target = Tenant.refOrDefault(id)
        if (tenants.none { it.id == target }) return normalized()
        return copy(activeTenantId = target, allTenantsMode = false).normalized()
    }

    /** Enables the fan-out "All tenants" mode. Ignored unless more than one tenant exists. */
    fun settingAllTenantsMode(enabled: Boolean): TenantSelection =
        copy(allTenantsMode = enabled).normalized()

    companion object {
        val INITIAL: TenantSelection = TenantSelection()
    }
}
