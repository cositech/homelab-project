package com.homelab.app.data.repository

import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.domain.provider.ProviderEvent
import com.homelab.app.domain.provider.ProviderHealth
import com.homelab.app.domain.provider.ProviderHealthState
import com.homelab.app.domain.provider.ProviderResource
import com.homelab.app.util.ServiceType
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class InfrastructureOperationsPayload(
    val health: ProviderHealth,
    val assets: List<ProviderResource>,
    val alerts: List<ProviderEvent>
)

@Singleton
class InfrastructureOperationsRepository @Inject constructor(
    private val serviceInstancesRepository: ServiceInstancesRepository,
    private val tlsClientSelector: TlsClientSelector
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun authenticate(
        type: ServiceType,
        url: String,
        apiToken: String,
        fallbackUrl: String?,
        allowSelfSigned: Boolean
    ) {
        require(type in supportedTypes) { "Unsupported infrastructure provider" }
        require(apiToken.isNotBlank()) { "A read-only API token is required" }
        val path = when (type) {
            ServiceType.NETBOX -> "/api/status/"
            ServiceType.ZAMMAD -> "/api/v1/users/me"
            ServiceType.PEGAPROX -> "/api/clusters"
            else -> error("Unsupported infrastructure provider")
        }
        request(url, fallbackUrl, path, type, apiToken, tlsClientSelector.forAllowSelfSigned(allowSelfSigned))
    }

    suspend fun getSnapshot(instanceId: String): InfrastructureOperationsPayload {
        val instance = serviceInstancesRepository.getInstance(instanceId)
            ?: throw IllegalStateException("Infrastructure provider instance not found")
        require(instance.type in supportedTypes) { "Unexpected infrastructure provider type" }
        val token = instance.apiKey?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Read-only API token is required")
        val client = tlsClientSelector.forInstance(instanceId)
        return when (instance.type) {
            ServiceType.NETBOX -> netBoxSnapshot(instance, token, client)
            ServiceType.ZAMMAD -> zammadSnapshot(instance, token, client)
            ServiceType.PEGAPROX -> pegaProxSnapshot(instance, token, client)
            else -> error("Unsupported infrastructure provider")
        }
    }

    private suspend fun netBoxSnapshot(
        instance: ServiceInstance,
        token: String,
        client: OkHttpClient
    ): InfrastructureOperationsPayload {
        val status = requestObject(instance, "/api/status/", token, client)
        val devices = paginatedNetBox(instance, "/api/dcim/devices/?exclude=config_context", token, client)
        val virtualMachines = paginatedNetBox(instance, "/api/virtualization/virtual-machines/?exclude=config_context", token, client)
        val assets = buildList {
            devices.forEach { item -> parseNetBoxAsset(instance.id, "device", item)?.let(::add) }
            virtualMachines.forEach { item -> parseNetBoxAsset(instance.id, "virtual-machine", item)?.let(::add) }
        }
        val version = status.string("netbox-version") ?: status.string("netbox_version") ?: status.string("version")
        return InfrastructureOperationsPayload(
            health = ProviderHealth(
                providerId = "netbox",
                instanceId = instance.id,
                state = ProviderHealthState.HEALTHY,
                message = "${devices.size} device(s), ${virtualMachines.size} virtual machine(s)",
                attributes = buildMap {
                    version?.let { put("version", it) }
                    put("devices", devices.size.toString())
                    put("virtualMachines", virtualMachines.size.toString())
                    put("resultLimit", MAX_ITEMS.toString())
                }
            ),
            assets = assets,
            alerts = emptyList()
        )
    }

    private suspend fun zammadSnapshot(
        instance: ServiceInstance,
        token: String,
        client: OkHttpClient
    ): InfrastructureOperationsPayload {
        val me = requestObject(instance, "/api/v1/users/me", token, client)
        val tickets = paginatedArray(instance, "/api/v1/tickets?expand=true", token, client, ServiceType.ZAMMAD)
        val now = System.currentTimeMillis()
        val assets = tickets.mapNotNull { ticket ->
            val id = ticket.string("id") ?: return@mapNotNull null
            val number = ticket.string("number") ?: id
            ProviderResource(
                providerId = "zammad",
                instanceId = instance.id,
                resourceType = "ticket",
                resourceId = id,
                name = "Ticket #$number",
                state = ticket.string("state") ?: ticket.string("state_id") ?: "unknown",
                attributes = buildMap {
                    ticket.string("priority")?.let { put("priority", it) }
                    ticket.string("group")?.let { put("group", it) }
                    ticket.string("created_at")?.let { put("createdAt", it) }
                    ticket.string("updated_at")?.let { put("updatedAt", it) }
                    put("contentRedacted", "true")
                }
            )
        }
        val alerts = tickets.mapNotNull { ticket ->
            val escalation = ticket.string("escalation_at") ?: return@mapNotNull null
            val id = ticket.string("id") ?: return@mapNotNull null
            val number = ticket.string("number") ?: id
            ProviderEvent(
                providerId = "zammad",
                instanceId = instance.id,
                eventId = "ticket:$id:escalated",
                severity = "warning",
                message = "Ticket #$number is escalated",
                occurredAtEpochMillis = now,
                resourceId = id
            ).takeIf { escalation.isNotBlank() }
        }
        return InfrastructureOperationsPayload(
            health = ProviderHealth(
                providerId = "zammad",
                instanceId = instance.id,
                state = if (alerts.isEmpty()) ProviderHealthState.HEALTHY else ProviderHealthState.DEGRADED,
                message = "${tickets.size} visible ticket(s), ${alerts.size} escalated",
                attributes = buildMap {
                    me.string("login")?.let { put("authenticatedUser", it) }
                    put("tickets", tickets.size.toString())
                    put("escalated", alerts.size.toString())
                    put("piiRedacted", "true")
                    put("resultLimit", MAX_ITEMS.toString())
                }
            ),
            assets = assets,
            alerts = alerts
        )
    }

    private suspend fun pegaProxSnapshot(
        instance: ServiceInstance,
        token: String,
        client: OkHttpClient
    ): InfrastructureOperationsPayload {
        val clusters = requestArray(instance, "/api/clusters", token, client)
            .mapNotNull { it as? JsonObject }
            .take(MAX_CLUSTERS)
        val assets = mutableListOf<ProviderResource>()
        val alerts = mutableListOf<ProviderEvent>()
        var disconnected = 0
        clusters.forEach { cluster ->
            val id = cluster.string("id")?.takeIf(::safePathSegment) ?: return@forEach
            val name = cluster.string("display_name")?.takeIf { it.isNotBlank() }
                ?: cluster.string("name") ?: id
            val connected = cluster.boolean("connected")
            if (!connected) disconnected++
            val health = runCatching {
                requestObject(instance, "/api/clusters/${encodePath(id)}/health", token, client)
            }.getOrNull()
            assets += ProviderResource(
                providerId = "pegaprox",
                instanceId = instance.id,
                resourceType = "cluster",
                resourceId = id,
                name = name,
                state = health?.string("band") ?: if (connected) "connected" else "disconnected",
                attributes = buildMap {
                    cluster.string("cluster_type")?.let { put("clusterType", it) }
                    health?.string("score")?.let { put("healthScore", it) }
                    put("tenantScoped", "true")
                }
            )
            val resources = runCatching {
                requestArray(instance, "/api/clusters/${encodePath(id)}/resources", token, client)
            }.getOrDefault(JsonArray(emptyList())).take(MAX_RESOURCES_PER_CLUSTER)
            resources.forEach { resource -> parsePegaProxResource(instance.id, id, resource)?.let(assets::add) }
            val active = runCatching {
                requestObject(instance, "/api/clusters/${encodePath(id)}/active-alerts", token, client)
            }.getOrNull()?.get("active_alerts") as? JsonArray ?: JsonArray(emptyList())
            active.take(MAX_ALERTS_PER_CLUSTER).forEach alertLoop@ { alert ->
                val value = alert as? JsonObject ?: return@alertLoop
                val alertId = value.string("id") ?: value.string("alert_id") ?: return@alertLoop
                alerts += ProviderEvent(
                    providerId = "pegaprox",
                    instanceId = instance.id,
                    eventId = "cluster:$id:alert:$alertId",
                    severity = normalizeSeverity(value.string("severity")),
                    message = value.string("message")?.take(240) ?: "Active PegaProx alert",
                    occurredAtEpochMillis = System.currentTimeMillis(),
                    resourceId = value.string("target_name") ?: id
                )
            }
        }
        val state = when {
            clusters.isEmpty() -> ProviderHealthState.UNKNOWN
            disconnected > 0 || alerts.isNotEmpty() -> ProviderHealthState.DEGRADED
            else -> ProviderHealthState.HEALTHY
        }
        return InfrastructureOperationsPayload(
            health = ProviderHealth(
                providerId = "pegaprox",
                instanceId = instance.id,
                state = state,
                message = "${clusters.size} scoped cluster(s), $disconnected disconnected, ${alerts.size} active alert(s)",
                attributes = mapOf(
                    "clusters" to clusters.size.toString(),
                    "disconnected" to disconnected.toString(),
                    "activeAlerts" to alerts.size.toString(),
                    "serverSideScope" to "required"
                )
            ),
            assets = assets,
            alerts = alerts
        )
    }

    private suspend fun paginatedNetBox(
        instance: ServiceInstance,
        basePath: String,
        token: String,
        client: OkHttpClient
    ): List<JsonObject> {
        val results = mutableListOf<JsonObject>()
        var offset = 0
        while (results.size < MAX_ITEMS) {
            val separator = if ('?' in basePath) '&' else '?'
            val root = requestObject(instance, "$basePath${separator}limit=$PAGE_SIZE&offset=$offset", token, client)
            val page = (root["results"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            results += page.take(MAX_ITEMS - results.size)
            if (page.size < PAGE_SIZE || root["next"] == null || root["next"] is JsonNull) break
            offset += PAGE_SIZE
        }
        return results
    }

    private suspend fun paginatedArray(
        instance: ServiceInstance,
        basePath: String,
        token: String,
        client: OkHttpClient,
        type: ServiceType
    ): List<JsonObject> {
        val results = mutableListOf<JsonObject>()
        var pageNumber = 1
        while (results.size < MAX_ITEMS) {
            val separator = if ('?' in basePath) '&' else '?'
            val page = requestArray(instance, "$basePath${separator}page=$pageNumber&per_page=$PAGE_SIZE", token, client, type)
                .mapNotNull { it as? JsonObject }
            results += page.take(MAX_ITEMS - results.size)
            if (page.size < PAGE_SIZE) break
            pageNumber++
        }
        return results
    }

    private fun parseNetBoxAsset(instanceId: String, resourceType: String, value: JsonObject): ProviderResource? {
        val id = value.string("id") ?: return null
        val name = value.string("name") ?: value.string("display") ?: id
        return ProviderResource(
            providerId = "netbox",
            instanceId = instanceId,
            resourceType = resourceType,
            resourceId = id,
            name = name,
            state = value.objectString("status") ?: "unknown",
            attributes = buildMap {
                value.objectString("site")?.let { put("site", it) }
                value.objectString("role")?.let { put("role", it) }
                value.objectString("tenant")?.let { put("tenant", it) }
                value.objectString("cluster")?.let { put("cluster", it) }
                value.string("primary_ip4")?.let { put("primaryIp4", it) }
                value.string("primary_ip6")?.let { put("primaryIp6", it) }
            }
        )
    }

    private fun parsePegaProxResource(instanceId: String, clusterId: String, value: JsonElement): ProviderResource? {
        val resource = value as? JsonObject ?: return null
        val vmid = resource.string("vmid") ?: resource.string("id") ?: return null
        val type = resource.string("type") ?: "guest"
        return ProviderResource(
            providerId = "pegaprox",
            instanceId = instanceId,
            resourceType = type,
            resourceId = "$clusterId:$vmid",
            name = resource.string("name") ?: "$type $vmid",
            state = resource.string("status") ?: "unknown",
            attributes = buildMap {
                put("clusterId", clusterId)
                resource.string("node")?.let { put("node", it) }
                resource.string("template")?.let { put("template", it) }
                put("tenantScoped", "true")
            }
        )
    }

    private suspend fun requestObject(instance: ServiceInstance, path: String, token: String, client: OkHttpClient): JsonObject =
        request(instance.url, instance.fallbackUrl, path, instance.type, token, client) as? JsonObject
            ?: throw IllegalStateException("Expected JSON object")

    private suspend fun requestArray(
        instance: ServiceInstance,
        path: String,
        token: String,
        client: OkHttpClient,
        type: ServiceType = instance.type
    ): JsonArray = request(instance.url, instance.fallbackUrl, path, type, token, client) as? JsonArray
        ?: throw IllegalStateException("Expected JSON array")

    private suspend fun request(
        primaryUrl: String,
        fallbackUrl: String?,
        path: String,
        type: ServiceType,
        token: String,
        client: OkHttpClient
    ): JsonElement {
        var lastError: Exception? = null
        listOfNotNull(primaryUrl.trim().takeIf { it.isNotBlank() }, fallbackUrl?.trim()?.takeIf { it.isNotBlank() })
            .distinct()
            .forEach { baseUrl ->
                try {
                    return fetch(baseUrl, path, type, token, client)
                } catch (error: Exception) {
                    lastError = error
                }
            }
        throw lastError ?: IllegalStateException("Infrastructure provider request failed")
    }

    private suspend fun fetch(
        baseUrl: String,
        path: String,
        type: ServiceType,
        token: String,
        client: OkHttpClient
    ): JsonElement = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .get()
            .addHeader("Accept", "application/json")
            .addHeader("X-Homelab-Bypass", "true")
            .addHeader("Authorization", authorization(type, token))
        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            when (response.code) {
                in 200..299 -> json.parseToJsonElement(body)
                401, 403 -> throw IllegalStateException("Read-only provider token rejected or insufficiently scoped")
                else -> throw IllegalStateException("Infrastructure provider returned HTTP ${response.code}")
            }
        }
    }

    private fun authorization(type: ServiceType, token: String): String = when (type) {
        ServiceType.NETBOX -> if (token.startsWith("nbt_")) "Bearer $token" else "Token $token"
        ServiceType.ZAMMAD -> "Token token=$token"
        ServiceType.PEGAPROX -> "Bearer $token"
        else -> error("Unsupported infrastructure provider")
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.boolean(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
    private fun JsonObject.objectString(key: String): String? {
        val value = this[key]
        return when (value) {
            is JsonPrimitive -> value.contentOrNull
            is JsonObject -> value.string("display") ?: value.string("name") ?: value.string("label") ?: value.string("value")
            else -> null
        }
    }

    private fun safePathSegment(value: String): Boolean = value.matches(Regex("[A-Za-z0-9._-]{1,128}"))
    private fun encodePath(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
    private fun normalizeSeverity(raw: String?): String = when (raw?.lowercase()) {
        "critical", "error", "high" -> "critical"
        "warning", "warn", "medium" -> "warning"
        else -> "info"
    }

    companion object {
        private const val PAGE_SIZE = 100
        private const val MAX_ITEMS = 500
        private const val MAX_CLUSTERS = 50
        private const val MAX_RESOURCES_PER_CLUSTER = 1_000
        private const val MAX_ALERTS_PER_CLUSTER = 200
        private val supportedTypes = setOf(ServiceType.NETBOX, ServiceType.ZAMMAD, ServiceType.PEGAPROX)
    }
}
