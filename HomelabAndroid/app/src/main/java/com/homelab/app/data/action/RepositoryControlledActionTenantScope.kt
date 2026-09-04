package com.homelab.app.data.action

import com.homelab.app.data.local.TenantStore
import com.homelab.app.data.repository.ServiceInstancesRepository
import com.homelab.app.domain.action.ControlledActionTenantScope
import com.homelab.app.domain.model.Tenant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps a controlled action back to its tenant by parsing the instance id out of the
 * `provider:instanceId` [providerRef] and reading that instance's `tenantRef`, and reports the
 * device-local membership set from the [TenantStore]. A single-tenant install resolves everything
 * to [Tenant.DEFAULT_ID], so the Phase-4 gate stays a no-op until a second tenant is configured.
 */
@Singleton
class RepositoryControlledActionTenantScope @Inject constructor(
    private val serviceInstances: ServiceInstancesRepository,
    private val tenantStore: TenantStore
) : ControlledActionTenantScope {

    override suspend fun tenantRefFor(providerRef: String): String? {
        val instanceId = providerRef.substringAfter(':', "").trim()
        if (instanceId.isEmpty()) return null
        val instance = serviceInstances.getAllInstances()
            .firstOrNull { it.id.equals(instanceId, ignoreCase = true) }
            ?: return null
        return Tenant.refOrDefault(instance.tenantRef)
    }

    override suspend fun membershipRefs(): Set<String> = tenantStore.current().membershipRefs
}
