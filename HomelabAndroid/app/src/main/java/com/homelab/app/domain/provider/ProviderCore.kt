package com.homelab.app.domain.provider

import com.homelab.app.util.ServiceType
import kotlinx.serialization.Serializable

@Serializable
enum class ProviderCapability {
    HEALTH,
    RESOURCES,
    EVENTS,
    METRICS,
    READ_ACTIONS,
    WRITE_ACTIONS
}

@Serializable
enum class ProviderHealthState {
    HEALTHY,
    DEGRADED,
    UNAVAILABLE,
    UNKNOWN
}

@Serializable
data class ProviderHealth(
    val providerId: String,
    val instanceId: String,
    val state: ProviderHealthState,
    val message: String? = null,
    val observedAtEpochMillis: Long = System.currentTimeMillis(),
    val attributes: Map<String, String> = emptyMap()
)

@Serializable
data class ProviderResource(
    val providerId: String,
    val instanceId: String,
    val resourceType: String,
    val resourceId: String,
    val name: String,
    val state: String? = null,
    val attributes: Map<String, String> = emptyMap()
)

@Serializable
data class ProviderEvent(
    val providerId: String,
    val instanceId: String,
    val eventId: String,
    val severity: String,
    val message: String,
    val occurredAtEpochMillis: Long,
    val resourceId: String? = null
)

@Serializable
data class ProviderDiagnostic(
    val providerId: String,
    val instanceId: String,
    val displayName: String,
    val endpoint: String,
    val tlsMode: String,
    val capabilities: Set<ProviderCapability>,
    val state: ProviderHealthState,
    val message: String? = null,
    val observedAtEpochMillis: Long = System.currentTimeMillis()
)

@Serializable
data class OperationsSnapshot(
    val health: List<ProviderHealth> = emptyList(),
    val alerts: List<ProviderEvent> = emptyList(),
    val assets: List<ProviderResource> = emptyList(),
    val diagnostics: List<ProviderDiagnostic> = emptyList(),
    val refreshedAtEpochMillis: Long = System.currentTimeMillis()
) {
    fun search(query: String): OperationsSearchResults {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return OperationsSearchResults()
        fun String?.matches(): Boolean = this?.lowercase()?.contains(needle) == true
        return OperationsSearchResults(
            health = health.filter { it.providerId.matches() || it.instanceId.matches() || it.message.matches() },
            alerts = alerts.filter { it.providerId.matches() || it.message.matches() || it.resourceId.matches() },
            assets = assets.filter {
                it.providerId.matches() || it.resourceType.matches() || it.resourceId.matches() ||
                    it.name.matches() || it.state.matches() || it.attributes.any { entry ->
                        entry.key.matches() || entry.value.matches()
                    }
            },
            diagnostics = diagnostics.filter {
                it.providerId.matches() || it.displayName.matches() || it.endpoint.matches() || it.message.matches()
            }
        )
    }
}

@Serializable
data class OperationsSearchResults(
    val health: List<ProviderHealth> = emptyList(),
    val alerts: List<ProviderEvent> = emptyList(),
    val assets: List<ProviderResource> = emptyList(),
    val diagnostics: List<ProviderDiagnostic> = emptyList()
) {
    val isEmpty: Boolean get() = health.isEmpty() && alerts.isEmpty() && assets.isEmpty() && diagnostics.isEmpty()
}

data class ProviderDescriptor(
    val id: String,
    val serviceType: ServiceType,
    val displayName: String,
    val capabilities: Set<ProviderCapability>
)

object ProviderRegistry {
    private val descriptors: Map<ServiceType, ProviderDescriptor> = ServiceType.entries
        .filter { it != ServiceType.UNKNOWN }
        .associateWith { type ->
            val capabilities = when (type) {
                ServiceType.PROXMOX, ServiceType.PORTAINER -> setOf(
                    ProviderCapability.HEALTH,
                    ProviderCapability.RESOURCES,
                    ProviderCapability.EVENTS,
                    ProviderCapability.METRICS,
                    ProviderCapability.READ_ACTIONS,
                    ProviderCapability.WRITE_ACTIONS
                )
                ServiceType.ADGUARD_HOME -> setOf(
                    ProviderCapability.HEALTH,
                    ProviderCapability.WRITE_ACTIONS
                )
                ServiceType.HEALTHCHECKS -> setOf(
                    ProviderCapability.HEALTH,
                    ProviderCapability.WRITE_ACTIONS
                )
                ServiceType.PROXMOX_BACKUP_SERVER -> setOf(
                    ProviderCapability.HEALTH,
                    ProviderCapability.RESOURCES,
                    ProviderCapability.EVENTS,
                    ProviderCapability.METRICS
                )
                ServiceType.PROMETHEUS -> setOf(
                    ProviderCapability.HEALTH,
                    ProviderCapability.RESOURCES,
                    ProviderCapability.EVENTS,
                    ProviderCapability.METRICS
                )
                ServiceType.GRAFANA -> setOf(
                    ProviderCapability.HEALTH,
                    ProviderCapability.RESOURCES,
                    ProviderCapability.METRICS
                )
                ServiceType.NETBOX -> setOf(
                    ProviderCapability.HEALTH,
                    ProviderCapability.RESOURCES
                )
                ServiceType.ZAMMAD -> setOf(
                    ProviderCapability.HEALTH,
                    ProviderCapability.RESOURCES,
                    ProviderCapability.EVENTS,
                    ProviderCapability.READ_ACTIONS
                )
                ServiceType.PEGAPROX -> setOf(
                    ProviderCapability.HEALTH,
                    ProviderCapability.RESOURCES,
                    ProviderCapability.EVENTS,
                    ProviderCapability.METRICS,
                    ProviderCapability.READ_ACTIONS
                )
                ServiceType.OPNSENSE -> setOf(
                    ProviderCapability.HEALTH,
                    ProviderCapability.RESOURCES,
                    ProviderCapability.METRICS,
                    ProviderCapability.READ_ACTIONS
                )
                ServiceType.ONEUPTIME -> setOf(
                    ProviderCapability.HEALTH,
                    ProviderCapability.RESOURCES,
                    ProviderCapability.EVENTS,
                    ProviderCapability.READ_ACTIONS
                )
                ServiceType.UPTIME_KUMA -> setOf(
                    ProviderCapability.HEALTH,
                    ProviderCapability.RESOURCES,
                    ProviderCapability.EVENTS,
                    ProviderCapability.METRICS
                )
                else -> setOf(ProviderCapability.HEALTH)
            }
            ProviderDescriptor(
                id = type.name.lowercase().replace('_', '-'),
                serviceType = type,
                displayName = type.displayName,
                capabilities = capabilities
            )
        }

    fun descriptor(type: ServiceType): ProviderDescriptor? = descriptors[type]

    fun capabilities(type: ServiceType): Set<ProviderCapability> =
        descriptor(type)?.capabilities.orEmpty()

    fun registeredProviders(): List<ProviderDescriptor> = descriptors.values.sortedBy { it.id }
}
