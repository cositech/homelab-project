package com.homelab.app.data.repository

import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.domain.provider.ProviderHealth
import com.homelab.app.domain.provider.ProviderHealthState
import com.homelab.app.util.ServiceType
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class PrometheusTarget(
    val id: String,
    val job: String,
    val instance: String,
    val scrapeUrl: String?,
    val health: String,
    val lastError: String?,
    val lastScrape: String?
)

data class PrometheusAlert(
    val name: String,
    val state: String,
    val summary: String?,
    val activeAt: String?,
    val labels: Map<String, String>
)

data class PrometheusOverview(
    val version: String?,
    val targets: List<PrometheusTarget>,
    val alerts: List<PrometheusAlert>,
    val warnings: List<String>
)

data class GrafanaDashboardAsset(
    val uid: String,
    val title: String,
    val folderTitle: String?,
    val tags: List<String>
)

data class GrafanaDataSourceAsset(
    val uid: String,
    val name: String,
    val type: String,
    val isDefault: Boolean,
    val readOnly: Boolean
)

data class GrafanaOverview(
    val version: String?,
    val database: String?,
    val dashboards: List<GrafanaDashboardAsset>,
    val dataSources: List<GrafanaDataSourceAsset>
)

@Singleton
class ObservabilityRepository @Inject constructor(
    private val serviceInstancesRepository: ServiceInstancesRepository,
    private val tlsClientSelector: TlsClientSelector
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun authenticatePrometheus(
        url: String,
        bearerToken: String?,
        fallbackUrl: String?,
        allowSelfSigned: Boolean
    ) {
        requestJson(url, fallbackUrl, "/api/v1/status/buildinfo", bearerToken, tlsClientSelector.forAllowSelfSigned(allowSelfSigned))
    }

    suspend fun authenticateGrafana(
        url: String,
        serviceAccountToken: String,
        fallbackUrl: String?,
        allowSelfSigned: Boolean
    ) {
        require(serviceAccountToken.isNotBlank()) { "Grafana service account token is required" }
        val client = tlsClientSelector.forAllowSelfSigned(allowSelfSigned)
        requestJson(url, fallbackUrl, "/api/health", serviceAccountToken, client)
        requestJson(url, fallbackUrl, "/api/search?type=dash-db&limit=1&page=1", serviceAccountToken, client)
    }

    suspend fun getPrometheusOverview(instanceId: String): PrometheusOverview {
        val instance = requireInstance(instanceId, ServiceType.PROMETHEUS)
        val client = tlsClientSelector.forInstance(instanceId)
        val build = requestJson(instance.url, instance.fallbackUrl, "/api/v1/status/buildinfo", instance.apiKey, client)
        val targets = requestJson(instance.url, instance.fallbackUrl, "/api/v1/targets?state=active", instance.apiKey, client)
        val alerts = requestJson(instance.url, instance.fallbackUrl, "/api/v1/alerts", instance.apiKey, client)
        return PrometheusOverview(
            version = build.dataObject()?.string("version"),
            targets = parsePrometheusTargets(targets),
            alerts = parsePrometheusAlerts(alerts),
            warnings = (targets["warnings"] as? JsonArray).orEmpty().mapNotNull { it.primitiveText() } +
                (alerts["warnings"] as? JsonArray).orEmpty().mapNotNull { it.primitiveText() }
        )
    }

    suspend fun getGrafanaOverview(instanceId: String): GrafanaOverview {
        val instance = requireInstance(instanceId, ServiceType.GRAFANA)
        val client = tlsClientSelector.forInstance(instanceId)
        val health = requestJson(instance.url, instance.fallbackUrl, "/api/health", instance.apiKey, client)
        val dashboards = requestJsonArray(instance.url, instance.fallbackUrl, "/api/search?type=dash-db&limit=1000&page=1", instance.apiKey, client)
        val dataSources = requestJsonArray(instance.url, instance.fallbackUrl, "/api/datasources", instance.apiKey, client)
        return GrafanaOverview(
            version = health.string("version"),
            database = health.string("database"),
            dashboards = dashboards.mapNotNull(::parseGrafanaDashboard).sortedBy { it.title.lowercase() },
            dataSources = dataSources.mapNotNull(::parseGrafanaDataSource).sortedBy { it.name.lowercase() }
        )
    }

    suspend fun getPrometheusHealth(instanceId: String): ProviderHealth = runCatching {
        normalizePrometheusHealth(instanceId, getPrometheusOverview(instanceId))
    }.getOrElse { unavailable("prometheus", instanceId, it) }

    fun normalizePrometheusHealth(instanceId: String, overview: PrometheusOverview): ProviderHealth {
        val unhealthy = overview.targets.count { !it.health.equals("up", true) }
        val firing = overview.alerts.count { it.state.equals("firing", true) }
        return ProviderHealth(
            providerId = "prometheus",
            instanceId = instanceId,
            state = when {
                overview.targets.isEmpty() -> ProviderHealthState.UNKNOWN
                unhealthy > 0 || firing > 0 || overview.warnings.isNotEmpty() -> ProviderHealthState.DEGRADED
                else -> ProviderHealthState.HEALTHY
            },
            message = "${overview.targets.size} target(s), $unhealthy unhealthy, $firing firing alert(s)",
            attributes = buildMap {
                overview.version?.let { put("version", it) }
                put("targets", overview.targets.size.toString())
                put("firingAlerts", firing.toString())
            }
        )
    }

    suspend fun getGrafanaHealth(instanceId: String): ProviderHealth = runCatching {
        normalizeGrafanaHealth(instanceId, getGrafanaOverview(instanceId))
    }.getOrElse { unavailable("grafana", instanceId, it) }

    fun normalizeGrafanaHealth(instanceId: String, overview: GrafanaOverview): ProviderHealth =
        ProviderHealth(
            providerId = "grafana",
            instanceId = instanceId,
            state = ProviderHealthState.HEALTHY,
            message = "${overview.dashboards.size} dashboard(s), ${overview.dataSources.size} data source(s)",
            attributes = buildMap {
                overview.version?.let { put("version", it) }
                overview.database?.let { put("database", it) }
                put("dashboards", overview.dashboards.size.toString())
                put("dataSources", overview.dataSources.size.toString())
            }
        )

    private suspend fun requireInstance(instanceId: String, type: ServiceType): ServiceInstance {
        val instance = serviceInstancesRepository.getInstance(instanceId)
            ?: throw IllegalStateException("Observability provider instance not found")
        require(instance.type == type) { "Unexpected observability provider type" }
        if (type == ServiceType.GRAFANA) require(!instance.apiKey.isNullOrBlank()) { "Grafana service account token is required" }
        return instance
    }

    private suspend fun requestJson(
        primaryUrl: String,
        fallbackUrl: String?,
        path: String,
        bearerToken: String?,
        client: OkHttpClient
    ): JsonObject = request(primaryUrl, fallbackUrl, path, bearerToken, client).let { body ->
        json.parseToJsonElement(body) as? JsonObject ?: throw IllegalStateException("Expected JSON object")
    }

    private suspend fun requestJsonArray(
        primaryUrl: String,
        fallbackUrl: String?,
        path: String,
        bearerToken: String?,
        client: OkHttpClient
    ): JsonArray = request(primaryUrl, fallbackUrl, path, bearerToken, client).let { body ->
        json.parseToJsonElement(body) as? JsonArray ?: throw IllegalStateException("Expected JSON array")
    }

    private suspend fun request(
        primaryUrl: String,
        fallbackUrl: String?,
        path: String,
        bearerToken: String?,
        client: OkHttpClient
    ): String {
        var lastError: Exception? = null
        listOfNotNull(primaryUrl.trim().takeIf { it.isNotBlank() }, fallbackUrl?.trim()?.takeIf { it.isNotBlank() })
            .distinct()
            .forEach { baseUrl ->
                try {
                    return fetch(baseUrl, path, bearerToken, client)
                } catch (error: Exception) {
                    lastError = error
                }
            }
        throw lastError ?: IllegalStateException("Observability request failed")
    }

    private suspend fun fetch(baseUrl: String, path: String, bearerToken: String?, client: OkHttpClient): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .get()
            .addHeader("Accept", "application/json")
            .addHeader("X-Homelab-Bypass", "true")
        bearerToken?.trim()?.takeIf { it.isNotBlank() }?.let { builder.addHeader("Authorization", "Bearer $it") }
        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            when (response.code) {
                in 200..299 -> body
                401, 403 -> throw IllegalStateException("Read-only observability token rejected or missing permissions")
                else -> throw IllegalStateException("Observability endpoint returned HTTP ${response.code}")
            }
        }
    }

    private fun parsePrometheusTargets(root: JsonObject): List<PrometheusTarget> {
        val targets = root.dataObject()?.get("activeTargets") as? JsonArray ?: return emptyList()
        return targets.mapNotNull { element ->
            val value = element as? JsonObject ?: return@mapNotNull null
            val labels = value["labels"] as? JsonObject
            val discovered = value["discoveredLabels"] as? JsonObject
            val scrapeUrl = value.string("scrapeUrl")
            val job = labels?.string("job") ?: discovered?.string("job") ?: "unknown"
            val instance = labels?.string("instance") ?: discovered?.string("__address__") ?: scrapeUrl ?: "unknown"
            PrometheusTarget(
                id = stableId("$job|$instance|${scrapeUrl.orEmpty()}"),
                job = job,
                instance = instance,
                scrapeUrl = scrapeUrl,
                health = value.string("health") ?: "unknown",
                lastError = value.string("lastError")?.takeIf { it.isNotBlank() },
                lastScrape = value.string("lastScrape")
            )
        }.sortedWith(compareBy<PrometheusTarget> { it.job.lowercase() }.thenBy { it.instance.lowercase() })
    }

    private fun parsePrometheusAlerts(root: JsonObject): List<PrometheusAlert> {
        val alerts = root.dataObject()?.get("alerts") as? JsonArray ?: return emptyList()
        return alerts.mapNotNull { element ->
            val value = element as? JsonObject ?: return@mapNotNull null
            val labels = (value["labels"] as? JsonObject).orEmpty().mapNotNull { (key, item) -> item.primitiveText()?.let { key to it } }.toMap()
            val annotations = value["annotations"] as? JsonObject
            PrometheusAlert(
                name = labels["alertname"] ?: "Unnamed alert",
                state = value.string("state") ?: "unknown",
                summary = annotations?.string("summary") ?: annotations?.string("description"),
                activeAt = value.string("activeAt"),
                labels = labels
            )
        }
    }

    private fun parseGrafanaDashboard(element: JsonElement): GrafanaDashboardAsset? {
        val value = element as? JsonObject ?: return null
        val uid = value.string("uid") ?: return null
        val title = value.string("title") ?: uid
        val tags = (value["tags"] as? JsonArray).orEmpty().mapNotNull { it.primitiveText() }
        return GrafanaDashboardAsset(uid, title, value.string("folderTitle"), tags)
    }

    private fun parseGrafanaDataSource(element: JsonElement): GrafanaDataSourceAsset? {
        val value = element as? JsonObject ?: return null
        val uid = value.string("uid") ?: value.string("id") ?: return null
        return GrafanaDataSourceAsset(
            uid = uid,
            name = value.string("name") ?: uid,
            type = value.string("type") ?: "unknown",
            isDefault = value.boolean("isDefault"),
            readOnly = value.boolean("readOnly")
        )
    }

    private fun unavailable(providerId: String, instanceId: String, error: Throwable) = ProviderHealth(
        providerId = providerId,
        instanceId = instanceId,
        state = ProviderHealthState.UNAVAILABLE,
        message = error.message ?: "$providerId unavailable"
    )

    private fun stableId(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .take(12)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun JsonObject.dataObject(): JsonObject? = this["data"] as? JsonObject
    private fun JsonObject.string(key: String): String? = this[key]?.primitiveText()
    private fun JsonObject.boolean(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
    private fun JsonElement.primitiveText(): String? = (this as? JsonPrimitive)?.contentOrNull
}
