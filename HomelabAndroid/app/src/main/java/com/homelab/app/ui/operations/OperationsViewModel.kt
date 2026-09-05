package com.homelab.app.ui.operations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.local.TenantStore
import com.homelab.app.data.repository.ProxmoxRepository
import com.homelab.app.data.repository.ProxmoxBackupServerRepository
import com.homelab.app.data.repository.ObservabilityRepository
import com.homelab.app.data.repository.InfrastructureOperationsRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.data.repository.UptimeKumaMonitorStatus
import com.homelab.app.data.repository.UptimeKumaRepository
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.domain.model.TenantSelection
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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
    private val tenantStore: TenantStore,
    private val proxmoxRepository: ProxmoxRepository,
    private val proxmoxBackupServerRepository: ProxmoxBackupServerRepository,
    private val uptimeKumaRepository: UptimeKumaRepository,
    private val observabilityRepository: ObservabilityRepository,
    private val infrastructureOperationsRepository: InfrastructureOperationsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(OperationsUiState())
    val uiState: StateFlow<OperationsUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    val tenantSelection: StateFlow<TenantSelection> = tenantStore.selection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TenantSelection.INITIAL)

    init {
        viewModelScope.launch {
            tenantStore.selection.collect { refresh() }
        }
    }

    fun setActiveTenant(id: String) {
        viewModelScope.launch { tenantStore.setActiveTenant(id) }
    }

    fun setAllTenantsMode(enabled: Boolean) {
        viewModelScope.launch { tenantStore.setAllTenantsMode(enabled) }
    }

    /** Cancels any in-flight refresh so a tenant switch never races a stale scope onto the screen. */
    fun refresh() {
        refreshJob?.cancel()
        lateinit var job: Job
        job = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                servicesRepository.checkAllReachability(force = true)
                val selection = tenantStore.current()
                val instances = if (selection.allTenantsMode) {
                    servicesRepository.allInstances.first()
                } else {
                    servicesRepository.instancesForTenant(selection.activeTenantId).first()
                }
                val reachability = servicesRepository.reachability.first()
                _uiState.value = OperationsUiState(
                    snapshot = buildSnapshot(instances, reachability),
                    isRefreshing = false
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(isRefreshing = false, error = error.message ?: "Unable to refresh operations data")
                }
            } finally {
                if (refreshJob === job) refreshJob = null
            }
        }
        refreshJob = job
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
                    ServiceType.PROXMOX_BACKUP_SERVER -> {
                        instanceHealth = proxmoxBackupServerRepository.getNormalizedHealth(instance.id)
                        appendProxmoxBackupServer(instance, assets, alerts, observedAt)
                    }
                    ServiceType.UPTIME_KUMA -> {
                        instanceHealth = uptimeKumaRepository.getNormalizedHealth(instance.id)
                        appendUptimeKuma(instance, assets, alerts, observedAt)
                    }
                    ServiceType.PROMETHEUS -> {
                        val overview = observabilityRepository.getPrometheusOverview(instance.id)
                        instanceHealth = observabilityRepository.normalizePrometheusHealth(instance.id, overview)
                        appendPrometheus(instance, overview, assets, alerts, observedAt)
                    }
                    ServiceType.GRAFANA -> {
                        val overview = observabilityRepository.getGrafanaOverview(instance.id)
                        instanceHealth = observabilityRepository.normalizeGrafanaHealth(instance.id, overview)
                        appendGrafana(instance, overview, assets)
                    }
                    ServiceType.NETBOX,
                    ServiceType.ZAMMAD,
                    ServiceType.PEGAPROX,
                    ServiceType.OPNSENSE,
                    ServiceType.ONEUPTIME -> {
                        val payload = infrastructureOperationsRepository.getSnapshot(instance.id)
                        instanceHealth = payload.health
                        assets += payload.assets
                        alerts += payload.alerts
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

    private suspend fun appendProxmoxBackupServer(
        instance: ServiceInstance,
        assets: MutableList<ProviderResource>,
        alerts: MutableList<ProviderEvent>,
        observedAt: Long
    ) {
        val dashboard = proxmoxBackupServerRepository.getDashboard(instance.id)
        dashboard.datastores.forEach { datastore ->
            val usageRatio = datastore.usageRatio
            val inMaintenance = !datastore.maintenance.isNullOrBlank()
            val state = when {
                inMaintenance -> "maintenance"
                usageRatio != null && usageRatio >= 0.95 -> "critical"
                usageRatio != null && usageRatio >= 0.85 -> "warning"
                else -> "healthy"
            }
            assets += ProviderResource(
                providerId = "proxmox-backup-server",
                instanceId = instance.id,
                resourceType = "datastore",
                resourceId = datastore.store,
                name = datastore.store,
                state = state,
                attributes = buildMap {
                    datastore.totalBytes?.let { put("totalBytes", it.toString()) }
                    datastore.usedBytes?.let { put("usedBytes", it.toString()) }
                    datastore.availableBytes?.let { put("availableBytes", it.toString()) }
                    usageRatio?.let { put("usagePercent", "%.1f".format(it * 100.0)) }
                    datastore.maintenance?.let { put("maintenance", it) }
                    dashboard.version?.let { put("serverVersion", it) }
                }
            )
            when {
                inMaintenance -> alerts += ProviderEvent(
                    "proxmox-backup-server",
                    instance.id,
                    "datastore:${datastore.store}:maintenance",
                    "warning",
                    "PBS datastore ${datastore.store} is in maintenance",
                    observedAt,
                    datastore.store
                )
                usageRatio != null && usageRatio >= 0.95 -> alerts += ProviderEvent(
                    "proxmox-backup-server",
                    instance.id,
                    "datastore:${datastore.store}:capacity-critical",
                    "critical",
                    "PBS datastore ${datastore.store} is ${"%.1f".format(usageRatio * 100.0)}% full",
                    observedAt,
                    datastore.store
                )
                usageRatio != null && usageRatio >= 0.85 -> alerts += ProviderEvent(
                    "proxmox-backup-server",
                    instance.id,
                    "datastore:${datastore.store}:capacity-warning",
                    "warning",
                    "PBS datastore ${datastore.store} is ${"%.1f".format(usageRatio * 100.0)}% full",
                    observedAt,
                    datastore.store
                )
            }
        }
    }

    private fun appendPrometheus(
        instance: ServiceInstance,
        overview: com.homelab.app.data.repository.PrometheusOverview,
        assets: MutableList<ProviderResource>,
        alerts: MutableList<ProviderEvent>,
        observedAt: Long
    ) {
        overview.targets.forEach { target ->
            assets += ProviderResource(
                providerId = "prometheus",
                instanceId = instance.id,
                resourceType = "scrape-target",
                resourceId = target.id,
                name = "${target.job} / ${target.instance}",
                state = target.health.lowercase(),
                attributes = buildMap {
                    put("job", target.job)
                    put("instance", target.instance)
                    target.lastScrape?.let { put("lastScrape", it) }
                }
            )
            if (!target.health.equals("up", ignoreCase = true)) {
                alerts += ProviderEvent(
                    "prometheus",
                    instance.id,
                    "target:${target.id}:down",
                    "critical",
                    "Prometheus target ${target.job} / ${target.instance} is ${target.health}",
                    observedAt,
                    target.id
                )
            }
        }
        overview.alerts.forEachIndexed { index, alert ->
            val severity = if (alert.state.equals("firing", true)) "critical" else "warning"
            alerts += ProviderEvent(
                "prometheus",
                instance.id,
                "alert:${alert.name.hashCode()}:$index:${alert.state.lowercase()}",
                severity,
                alert.summary?.takeIf { it.isNotBlank() } ?: "Prometheus alert ${alert.name} is ${alert.state}",
                observedAt,
                alert.name
            )
        }
    }

    private fun appendGrafana(
        instance: ServiceInstance,
        overview: com.homelab.app.data.repository.GrafanaOverview,
        assets: MutableList<ProviderResource>
    ) {
        overview.dashboards.forEach { dashboard ->
            assets += ProviderResource(
                providerId = "grafana",
                instanceId = instance.id,
                resourceType = "dashboard",
                resourceId = dashboard.uid,
                name = dashboard.title,
                state = "available",
                attributes = buildMap {
                    dashboard.folderTitle?.let { put("folder", it) }
                    if (dashboard.tags.isNotEmpty()) put("tags", dashboard.tags.joinToString(", "))
                }
            )
        }
        overview.dataSources.forEach { dataSource ->
            assets += ProviderResource(
                providerId = "grafana",
                instanceId = instance.id,
                resourceType = "data-source",
                resourceId = dataSource.uid,
                name = dataSource.name,
                state = "configured",
                attributes = mapOf(
                    "type" to dataSource.type,
                    "default" to dataSource.isDefault.toString(),
                    "readOnly" to dataSource.readOnly.toString()
                )
            )
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
