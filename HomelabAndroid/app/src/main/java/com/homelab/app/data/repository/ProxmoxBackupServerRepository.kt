package com.homelab.app.data.repository

import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.domain.provider.ProviderHealth
import com.homelab.app.domain.provider.ProviderHealthState
import com.homelab.app.domain.provider.ProviderRegistry
import com.homelab.app.util.ServiceType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class ProxmoxBackupDatastore(
    val store: String,
    val totalBytes: Long?,
    val usedBytes: Long?,
    val availableBytes: Long?,
    val maintenance: String?
) {
    val usageRatio: Double?
        get() = if (totalBytes != null && totalBytes > 0 && usedBytes != null) {
            usedBytes.toDouble() / totalBytes.toDouble()
        } else {
            null
        }
}

data class ProxmoxBackupServerDashboard(
    val version: String?,
    val datastores: List<ProxmoxBackupDatastore>
)

@Singleton
class ProxmoxBackupServerRepository @Inject constructor(
    private val serviceInstancesRepository: ServiceInstancesRepository,
    private val tlsClientSelector: TlsClientSelector
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val descriptor = requireNotNull(ProviderRegistry.descriptor(ServiceType.PROXMOX_BACKUP_SERVER))

    suspend fun authenticate(
        url: String,
        tokenId: String,
        tokenSecret: String,
        fallbackUrl: String? = null,
        allowSelfSigned: Boolean = false
    ) {
        validateToken(tokenId, tokenSecret)
        val client = tlsClientSelector.forAllowSelfSigned(allowSelfSigned)
        requestWithFallback(url, fallbackUrl, "/api2/json/version", tokenId, tokenSecret, client)
    }

    suspend fun getDashboard(instanceId: String): ProxmoxBackupServerDashboard {
        val instance = requireInstance(instanceId)
        val client = tlsClientSelector.forInstance(instanceId)
        val versionRoot = requestWithFallback(
            instance.url,
            instance.fallbackUrl,
            "/api2/json/version",
            instance.username.orEmpty(),
            instance.password.orEmpty(),
            client
        )
        val datastoresRoot = requestWithFallback(
            instance.url,
            instance.fallbackUrl,
            "/api2/json/status/datastore-usage",
            instance.username.orEmpty(),
            instance.password.orEmpty(),
            client
        )
        return ProxmoxBackupServerDashboard(
            version = versionRoot.dataObject()?.string("version"),
            datastores = datastoresRoot.dataArray().mapNotNull(::parseDatastore).sortedBy { it.store.lowercase() }
        )
    }

    suspend fun getNormalizedHealth(instanceId: String): ProviderHealth = runCatching {
        val dashboard = getDashboard(instanceId)
        val maintenanceCount = dashboard.datastores.count { !it.maintenance.isNullOrBlank() }
        val highestUsage = dashboard.datastores.mapNotNull { it.usageRatio }.maxOrNull()
        val state = when {
            dashboard.datastores.isEmpty() -> ProviderHealthState.UNKNOWN
            maintenanceCount > 0 -> ProviderHealthState.DEGRADED
            highestUsage != null && highestUsage >= 0.85 -> ProviderHealthState.DEGRADED
            else -> ProviderHealthState.HEALTHY
        }
        ProviderHealth(
            providerId = descriptor.id,
            instanceId = instanceId,
            state = state,
            message = when {
                dashboard.datastores.isEmpty() -> "PBS reachable; no visible datastores"
                maintenanceCount > 0 -> "$maintenanceCount datastore(s) in maintenance"
                highestUsage != null -> "${dashboard.datastores.size} datastore(s); highest usage ${formatPercent(highestUsage)}"
                else -> "${dashboard.datastores.size} datastore(s) available"
            },
            attributes = buildMap {
                put("datastores", dashboard.datastores.size.toString())
                dashboard.version?.let { put("version", it) }
                highestUsage?.let { put("highestUsagePercent", formatPercent(it)) }
            }
        )
    }.getOrElse { error ->
        ProviderHealth(
            providerId = descriptor.id,
            instanceId = instanceId,
            state = ProviderHealthState.UNAVAILABLE,
            message = error.message ?: "Proxmox Backup Server unavailable"
        )
    }

    private suspend fun requireInstance(instanceId: String): ServiceInstance {
        val instance = serviceInstancesRepository.getInstance(instanceId)
            ?: throw IllegalStateException("PBS instance not found")
        require(instance.type == ServiceType.PROXMOX_BACKUP_SERVER) { "Instance is not a PBS provider" }
        validateToken(instance.username.orEmpty(), instance.password.orEmpty())
        return instance
    }

    private fun validateToken(tokenId: String, tokenSecret: String) {
        require(tokenId.contains("@") && tokenId.contains("!")) {
            "PBS token ID must use user@realm!token-name"
        }
        require(tokenSecret.isNotBlank()) { "PBS token secret is required" }
    }

    private suspend fun requestWithFallback(
        primaryUrl: String,
        fallbackUrl: String?,
        path: String,
        tokenId: String,
        tokenSecret: String,
        client: OkHttpClient
    ): JsonObject {
        validateToken(tokenId, tokenSecret)
        var lastError: Exception? = null
        listOf(primaryUrl, fallbackUrl)
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
            .forEach { baseUrl ->
                try {
                    return fetch(baseUrl, path, tokenId, tokenSecret, client)
                } catch (error: Exception) {
                    lastError = error
                }
            }
        throw lastError ?: IllegalStateException("PBS request failed")
    }

    private suspend fun fetch(
        baseUrl: String,
        path: String,
        tokenId: String,
        tokenSecret: String,
        client: OkHttpClient
    ): JsonObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .get()
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "PBSAPIToken=$tokenId:$tokenSecret")
            .addHeader("X-Homelab-Bypass", "true")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            when (response.code) {
                in 200..299 -> json.parseToJsonElement(body).jsonObject
                401, 403 -> throw IllegalStateException("PBS API token rejected or missing audit permissions")
                else -> throw IllegalStateException("PBS returned HTTP ${response.code}")
            }
        }
    }

    private fun parseDatastore(element: JsonElement): ProxmoxBackupDatastore? {
        val value = element as? JsonObject ?: return null
        val store = value.string("store") ?: return null
        return ProxmoxBackupDatastore(
            store = store,
            totalBytes = value.long("total"),
            usedBytes = value.long("used"),
            availableBytes = value.long("avail"),
            maintenance = value["maintenance-mode"]?.displayValue()
                ?: value["maintenance"]?.displayValue()
        )
    }

    private fun JsonObject.dataObject(): JsonObject? = this["data"] as? JsonObject
    private fun JsonObject.dataArray(): JsonArray = this["data"] as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
    private fun JsonElement.displayValue(): String? = when (this) {
        is JsonPrimitive -> contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
        else -> toString().takeIf { it != "null" && it != "{}" }
    }

    private fun formatPercent(ratio: Double): String = "%.1f".format(ratio * 100.0)
}
