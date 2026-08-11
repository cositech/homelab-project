package com.homelab.app.ui.operations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.ProxmoxRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.data.repository.UptimeKumaMonitorStatus
import com.homelab.app.data.repository.UptimeKumaRepository
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.domain.provider.OperationsSnapshot
import com.homelab.app.domain.provider.ProviderDiagnostic
import com.homelab.app.domain.provider.ProviderEvent
import com.homelab.app.domain.provider.ProviderHealth
import com.homelab.app.domain.provider.ProviderHealthState
import com.homelab.app.domain.provider.ProviderRegistry
import com.homelab.app.domain.provider.ProviderResource
import com.homelab.app.util.ServiceType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OperationsUiState(
    val snapshot: OperationsSnapshot = OperationsSnapshot(),
    val isRefreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class OperationsViewModel @Inject constructor(
    private val servicesRepository: ServicesRepository,
    private val proxmoxRepository: ProxmoxRepository,
    private val uptimeKumaRepository: UptimeKumaRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(OperationsUiState())
    val uiState: StateFlow<OperationsUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                servicesRepository.checkAllReachability(force = true)
                val instances = servicesRepository.allInstances.first()
                val reachability = servicesRepository.reachability.first()
                _uiState.value = OperationsUiState(
                    snapshot = buildSnapshot(instances, reachability),
                    isRefreshing = false
                )
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(isRefreshing = false, error = error.message ?: "Unable to refresh operations data")
                }
            } finally {
                refreshJob = null
            }
        }
    }

    private suspend fun buildSnapshot(
        instances: List<ServiceInstance>,
        reachability: Map<String, Boolean?>
    ): OperationsSnapshot {
        val health = mutableListOf<ProviderHealth>()
        val alerts = mutableListOf<ProviderEvent>()
        val assets = mutableListOf<ProviderResource>()
        val diagnostics = mutableListOf<ProviderDiagnostic>()
        val observedAt = System.currentTimeMillis()

        for (instance in instances) {
            val descriptor = ProviderRegistry.descriptor(instance.type) ?: continue
            var instanceHealth = reachabilityHealth(instance, descriptor.id, reachability[instance.id], observedAt)

            try {
                when (instance.type) {
                    ServiceType.PROXMOX -> {
                        instanceHealth = proxmoxRepository.getNormalizedHealth(instance.id)
                        appendProxmox(instance, assets, alerts, observedAt)
                    }
                    ServiceType.UPTIME_KUMA -> {
                        instanceHealth = uptimeKumaRepository.getNormalizedHealth(instance.id)
                        appendUptimeKuma(instance, assets, alerts, observedAt)
                    }
                    else -> Unit
                }
            } catch (error: Throwable) {
                instanceHealth = instanceHealth.copy(
                    state = ProviderHealthState.UNAVAILABLE,
                    message = error.message ?: "Provider diagnostics failed"
                )
            }

            health += instanceHealth
            assets += ProviderResource(
                providerId = descriptor.id,
                instanceId = instance.id,
                resourceType = "provider-instance",
                resourceId = instance.id,
                name = instance.label.ifBlank { descriptor.displayName },
                state = instanceHealth.state.name.lowercase(),
                attributes = mapOf("serviceType" to instance.type.name)
            )
            if (instanceHealth.state in setOf(ProviderHealthState.DEGRADED, ProviderHealthState.UNAVAILABLE)) {
                alerts += ProviderEvent(
                    providerId = descriptor.id,
                    instanceId = instance.id,
                    eventId = "health:${instance.id}:${instanceHealth.state.name}",
                    severity = if (instanceHealth.state == ProviderHealthState.UNAVAILABLE) "critical" else "warning",
                    message = instanceHealth.message ?: "${descriptor.displayName} is ${instanceHealth.state.name.lowercase()}",
                    occurredAtEpochMillis = instanceHealth.observedAtEpochMillis,
                    resourceId = instance.id
                )
            }
            diagnostics += ProviderDiagnostic(
                providerId = descriptor.id,
                instanceId = instance.id,
                displayName = instance.label.ifBlank { descriptor.displayName },
                endpoint = safeEndpoint(instance.url),
                tlsMode = instance.effectiveTlsMode.name,
                capabilities = descriptor.capabilities,
                state = instanceHealth.state,
                message = instanceHealth.message,
                observedAtEpochMillis = instanceHealth.observedAtEpochMillis
            )
        }

        return OperationsSnapshot(
            health = health.sortedWith(compareBy<ProviderHealth> { healthRank(it.state) }.thenBy { it.providerId }),
            alerts = alerts.distinctBy { it.eventId }.sortedWith(compareBy<ProviderEvent> { severityRank(it.severity) }.thenByDescending { it.occurredAtEpochMillis }),
            assets = assets.distinctBy { "${it.providerId}:${it.instanceId}:${it.resourceType}:${it.resourceId}" }.sortedWith(compareBy<ProviderResource> { it.resourceType }.thenBy { it.name.lowercase() }),
            diagnostics = diagnostics.sortedWith(compareBy<ProviderDiagnostic> { healthRank(it.state) }.thenBy { it.displayName.lowercase() }),
            refreshedAtEpochMillis = observedAt
        )
    }

    private suspend fun appendProxmox(
        instance: ServiceInstance,
        assets: MutableList<ProviderResource>,
        alerts: MutableList<ProviderEvent>,
        observedAt: Long
    ) {
        val nodes = proxmoxRepository.getNodes(instance.id)
        for (node in nodes) {
            assets += ProviderResource(
                providerId = "proxmox",
                instanceId = instance.id,
                resourceType = "node",
                resourceId = node.node,
                name = node.node,
                state = node.status,
                attributes = mapOf(
                    "cpuPercent" to "%.1f".format(node.cpuPercent),
                    "memoryPercent" to "%.1f".format(node.memPercent),
                    "uptime" to node.formattedUptime
                )
            )
            if (!node.isOnline) {
                alerts += ProviderEvent("proxmox", instance.id, "node:${node.node}:offline", "critical", "Proxmox node ${node.node} is offline", observedAt, node.node)
            }
            proxmoxRepository.getVMs(instance.id, node.node).forEach { vm ->
                assets += ProviderResource("proxmox", instance.id, "virtual-machine", vm.vmid.toString(), vm.displayName, vm.status, mapOf("node" to node.node))
            }
            proxmoxRepository.getLXCs(instance.id, node.node).forEach { lxc ->
                assets += ProviderResource("proxmox", instance.id, "container", lxc.vmid.toString(), lxc.displayName, lxc.status, mapOf("node" to node.node))
            }
        }
    }

    private suspend fun appendUptimeKuma(
        instance: ServiceInstance,
        assets: MutableList<ProviderResource>,
        alerts: MutableList<ProviderEvent>,
        observedAt: Long
    ) {
        uptimeKumaRepository.getDashboard(instance.id).monitors.forEach { monitor ->
            assets += ProviderResource(
                providerId = "uptime-kuma",
                instanceId = instance.id,
                resourceType = "monitor",
                resourceId = monitor.id,
                name = monitor.name,
                state = monitor.status.name.lowercase(),
                attributes = buildMap {
                    monitor.type?.let { put("type", it) }
                    monitor.url?.let { put("target", it) }
                    monitor.responseTimeMs?.let { put("responseTimeMs", "%.0f".format(it)) }
                    monitor.certDaysRemaining?.let { put("certDaysRemaining", "%.0f".format(it)) }
                }
            )
            when (monitor.status) {
                UptimeKumaMonitorStatus.DOWN -> alerts += ProviderEvent("uptime-kuma", instance.id, "monitor:${monitor.id}:down", "critical", "${monitor.name} is down", observedAt, monitor.id)
                UptimeKumaMonitorStatus.PENDING -> alerts += ProviderEvent("uptime-kuma", instance.id, "monitor:${monitor.id}:pending", "warning", "${monitor.name} is pending", observedAt, monitor.id)
                else -> Unit
            }
            monitor.certDaysRemaining?.takeIf { it in 0.0..30.0 }?.let { days ->
                alerts += ProviderEvent("uptime-kuma", instance.id, "monitor:${monitor.id}:certificate", "warning", "${monitor.name} certificate expires in ${days.toInt()} days", observedAt, monitor.id)
            }
        }
    }

    private fun reachabilityHealth(
        instance: ServiceInstance,
        providerId: String,
        reachable: Boolean?,
        observedAt: Long
    ): ProviderHealth = ProviderHealth(
        providerId = providerId,
        instanceId = instance.id,
        state = when (reachable) {
            true -> ProviderHealthState.HEALTHY
            false -> ProviderHealthState.UNAVAILABLE
            null -> ProviderHealthState.UNKNOWN
        },
        message = when (reachable) {
            true -> "${instance.label} reachable"
            false -> "${instance.label} unreachable"
            null -> "${instance.label} has not been checked"
        },
        observedAtEpochMillis = observedAt
    )

    private fun safeEndpoint(raw: String): String = runCatching {
        val uri = URI(raw)
        val port = if (uri.port > 0) ":${uri.port}" else ""
        "${uri.scheme}://${uri.host}$port"
    }.getOrDefault("invalid-endpoint")

    private fun healthRank(state: ProviderHealthState): Int = when (state) {
        ProviderHealthState.UNAVAILABLE -> 0
        ProviderHealthState.DEGRADED -> 1
        ProviderHealthState.UNKNOWN -> 2
        ProviderHealthState.HEALTHY -> 3
    }

    private fun severityRank(severity: String): Int = when (severity.lowercase()) {
        "critical" -> 0
        "warning" -> 1
        else -> 2
    }
}
