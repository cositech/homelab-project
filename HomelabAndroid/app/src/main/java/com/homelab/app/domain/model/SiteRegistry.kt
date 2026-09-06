package com.homelab.app.domain.model

import kotlinx.serialization.Serializable

/**
 * The device-local set of configured [Site]s, each scoped to exactly one [Tenant] via
 * [Site.tenantRef]. Unlike [TenantSelection] there is no "active site" concept - a site is an
 * optional attribute an instance is assigned to, not something the whole app switches into.
 *
 * All transforms are pure and return a re-[normalized] value, so the store layer is a thin
 * persistence wrapper. Invariants held by [normalized]:
 *
 *  - [sites] has no duplicate ids and no blank id/name.
 *  - A tenant deleting its sites is not this type's job: a site whose tenant no longer exists is
 *    simply unreachable through the UI (the Sites screen is reached from a tenant row), the same
 *    lazy-orphan handling already used for a deleted tenant's instances.
 */
@Serializable
data class SiteRegistry(
    val sites: List<Site> = emptyList()
) {
    fun sitesForTenant(tenantRef: String): List<Site> {
        val target = Tenant.refOrDefault(tenantRef)
        return sites.filter { it.tenantRef == target }
    }

    fun normalized(): SiteRegistry {
        val deduped = LinkedHashMap<String, Site>()
        for (site in sites) {
            val id = site.id.trim()
            val name = site.name.trim()
            if (id.isEmpty() || name.isEmpty()) continue
            deduped[id] = site.copy(id = id, tenantRef = Tenant.refOrDefault(site.tenantRef), name = name)
        }
        return SiteRegistry(sites = deduped.values.toList())
    }

    /** Adds [site], or replaces the existing entry with the same id. */
    fun adding(site: Site): SiteRegistry {
        val id = site.id.trim()
        if (id.isEmpty()) return normalized()
        val next = sites.filterNot { it.id == id } + site.copy(id = id)
        return copy(sites = next).normalized()
    }

    fun renaming(id: String, name: String): SiteRegistry {
        val next = sites.map { if (it.id == id) it.copy(name = name) else it }
        return copy(sites = next).normalized()
    }

    fun removing(id: String): SiteRegistry =
        copy(sites = sites.filterNot { it.id == id }).normalized()

    companion object {
        val INITIAL: SiteRegistry = SiteRegistry()
    }
}
