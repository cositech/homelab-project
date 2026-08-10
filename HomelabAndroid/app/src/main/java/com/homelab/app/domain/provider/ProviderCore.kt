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
                ServiceType.PROXMOX -> setOf(
                    ProviderCapability.HEALTH,
                    ProviderCapability.RESOURCES,
                    ProviderCapability.EVENTS,
                    ProviderCapability.METRICS,
                    ProviderCapability.READ_ACTIONS,
                    ProviderCapability.WRITE_ACTIONS
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
