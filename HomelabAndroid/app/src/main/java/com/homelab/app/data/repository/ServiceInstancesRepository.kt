package com.homelab.app.data.repository

import com.homelab.app.data.local.SettingsManager
import com.homelab.app.data.local.dao.ServiceInstanceDao
import com.homelab.app.data.local.entity.ServiceInstanceEntity
import com.homelab.app.domain.model.PiHoleAuthMode
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.domain.model.Tenant
import com.homelab.app.domain.model.TlsMode
import com.homelab.app.security.CredentialEnvelope
import com.homelab.app.security.SecureCredentialStore
import com.homelab.app.util.ServiceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceInstancesRepository @Inject constructor(
    private val dao: ServiceInstanceDao,
    private val settingsManager: SettingsManager,
    private val credentialStore: SecureCredentialStore
) {
    val allInstances: Flow<List<ServiceInstance>> = dao.observeAll().map { entities ->
        entities.map { it.toDomain(credentialStore) }
    }

    val instancesByType: Flow<Map<ServiceType, List<ServiceInstance>>> = allInstances.map { instances ->
        ServiceType.entries
            .filter { it != ServiceType.UNKNOWN }
            .associateWith { type -> instances.filter { it.type == type } }
    }

    val preferredInstanceIdByType: Flow<Map<ServiceType, String?>> = settingsManager.preferredInstanceIds

    val preferredInstancesByType: Flow<Map<ServiceType, ServiceInstance?>> = combine(
        instancesByType,
        preferredInstanceIdByType
    ) { grouped, preferredIds ->
        ServiceType.entries
            .filter { it != ServiceType.UNKNOWN }
            .associateWith { type ->
                val instances = grouped[type].orEmpty()
                val preferredId = preferredIds[type]
                instances.firstOrNull { it.id == preferredId } ?: instances.firstOrNull()
            }
    }

    suspend fun initialize() {
        migrateLegacyDataIfNeeded()
        normalizeStoredInstancesIfNeeded()
        repairAllPreferredInstances()
    }

    suspend fun getInstance(id: String): ServiceInstance? = dao.getById(id)?.toDomain(credentialStore)

    suspend fun getAllInstances(): List<ServiceInstance> {
        return dao.getAll().map { it.toDomain(credentialStore) }
    }

    suspend fun getInstances(type: ServiceType): List<ServiceInstance> {
        val entities = dao.getByType(type.name)
        return entities.map { it.toDomain(credentialStore) }
    }

    suspend fun getPreferredInstance(type: ServiceType): ServiceInstance? {
        val instances = getInstances(type)
        val preferredId = settingsManager.preferredInstanceId(type).first()
        val preferred = instances.firstOrNull { it.id == preferredId } ?: instances.firstOrNull()
        if (preferred?.id != preferredId) {
            settingsManager.setPreferredInstanceId(type, preferred?.id)
        }
        return preferred
    }

    suspend fun saveInstance(instance: ServiceInstance) {
        val previousEntity = dao.getById(instance.id)
        val previous = previousEntity?.toDomain(credentialStore)
        val candidate = normalizeInstance(instance)
        val normalized = if (
            previous?.effectiveTlsMode in setOf(TlsMode.CUSTOM_CA, TlsMode.CERTIFICATE_PIN) &&
            candidate.effectiveTlsMode == TlsMode.SYSTEM
        ) {
            candidate.copy(
                tlsMode = previous!!.tlsMode,
                customCaPem = previous.customCaPem,
                certificatePin = previous.certificatePin
            )
        } else {
            candidate
        }
        val previousCredentialRef = previousEntity?.credentialRef
        val envelope = CredentialEnvelope.from(normalized)
        val newCredentialRef = if (envelope.isEmpty) {
            null
        } else {
            credentialStore.newReference(normalized.id).also { reference ->
                check(credentialStore.put(reference, envelope)) {
                    "Unable to persist credentials for service instance ${normalized.id}"
                }
                check(credentialStore.get(reference) == envelope) {
                    credentialStore.delete(reference)
                    "Unable to verify credentials for service instance ${normalized.id}"
                }
            }
        }

        try {
            dao.upsert(normalized.toEntity(newCredentialRef))
        } catch (error: Throwable) {
            newCredentialRef?.let(credentialStore::delete)
            throw error
        }
        if (previousCredentialRef != null && previousCredentialRef != newCredentialRef) {
            credentialStore.delete(previousCredentialRef)
        }
        val currentPreferred = settingsManager.preferredInstanceId(normalized.type).first()
        if (currentPreferred.isNullOrBlank()) {
            settingsManager.setPreferredInstanceId(normalized.type, normalized.id)
        }
    }

    suspend fun deleteInstance(id: String) {
        val entity = dao.getById(id) ?: return
        val instance = entity.toDomain(credentialStore)
        dao.deleteById(id)
        entity.credentialRef?.let(credentialStore::delete)
        repairPreferredInstance(instance.type)
    }

    suspend fun setPreferredInstance(type: ServiceType, instanceId: String?) {
        val validId = instanceId?.takeIf { candidate ->
            dao.getById(candidate)?.let { entity ->
                ServiceType.fromStoredName(entity.type) == type
            } == true
        }
        settingsManager.setPreferredInstanceId(type, validId)
        if (validId == null) {
            repairPreferredInstance(type)
        }
    }

    suspend fun migrateLegacyDataIfNeeded() {
        if (settingsManager.serviceInstancesMigrated.first()) {
            return
        }

        ServiceType.entries
            .filter { it != ServiceType.UNKNOWN }
            .forEach { type ->
                val existing = getInstances(type)
                val legacy = settingsManager.getLegacyConnection(type)

                if (legacy != null && existing.isEmpty()) {
                    val migrated = normalizeInstance(legacy.migratedInstance(UUID.randomUUID().toString()))
                    saveInstance(migrated)
                    settingsManager.setPreferredInstanceId(type, migrated.id)
                } else if (existing.isNotEmpty()) {
                    val currentPreferred = settingsManager.preferredInstanceId(type).first()
                    if (currentPreferred.isNullOrBlank()) {
                        settingsManager.setPreferredInstanceId(type, existing.first().id)
                    }
                }

                settingsManager.removeLegacyConnection(type)
            }

        settingsManager.setServiceInstancesMigrated(true)
    }

    private suspend fun normalizeStoredInstancesIfNeeded() {
        val entities = dao.getAll()
        if (entities.isEmpty()) return

        val normalized = entities.map { entity ->
            val serviceType = ServiceType.fromStoredName(entity.type)
            val normalizedType = serviceType.name
            val normalizedUrl = normalizeUrl(entity.url, serviceType)
            val normalizedFallback = normalizeOptionalUrl(entity.fallbackUrl, serviceType)
            if (
                normalizedType == entity.type &&
                normalizedUrl == entity.url &&
                normalizedFallback == entity.fallbackUrl
            ) {
                entity
            } else {
                entity.copy(
                    type = normalizedType,
                    url = normalizedUrl,
                    fallbackUrl = normalizedFallback
                )
            }
        }

        if (normalized != entities) {
            dao.upsertAll(normalized)
        }
    }

    suspend fun repairAllPreferredInstances() {
        ServiceType.entries
            .filter { it != ServiceType.UNKNOWN }
            .forEach { repairPreferredInstance(it) }
    }

    suspend fun repairPreferredInstance(type: ServiceType) {
        val instances = getInstances(type)
        val currentPreferred = settingsManager.preferredInstanceId(type).first()
        val repaired = instances.firstOrNull { it.id == currentPreferred } ?: instances.firstOrNull()
        settingsManager.setPreferredInstanceId(type, repaired?.id)
    }
}

private fun ServiceInstanceEntity.toDomain(credentialStore: SecureCredentialStore): ServiceInstance {
    val credentials = credentialRef?.let(credentialStore::get) ?: CredentialEnvelope()
    val storedTlsMode = runCatching { TlsMode.valueOf(tlsMode) }.getOrDefault(TlsMode.SYSTEM)
    return ServiceInstance(
        id = id,
        type = ServiceType.fromStoredName(type),
        label = label,
        url = url,
        tenantRef = Tenant.refOrDefault(tenantRef),
        siteRef = siteRef,
        token = credentials.token.orEmpty(),
        proxmoxCsrfToken = credentials.proxmoxCsrfToken,
        proxmoxOtp = credentials.proxmoxOtp,
        username = username,
        apiKey = credentials.apiKey,
        piholePassword = credentials.piholePassword,
        piholeAuthMode = piholeAuthMode?.let(PiHoleAuthMode::valueOf),
        fallbackUrl = fallbackUrl,
        allowSelfSigned = storedTlsMode == TlsMode.INSECURE_COMPATIBILITY,
        password = credentials.password,
        credentialRef = credentialRef,
        tlsMode = storedTlsMode,
        customCaPem = credentials.customCaPem,
        certificatePin = certificatePin
    )
}

private fun ServiceInstance.toEntity(credentialRef: String?): ServiceInstanceEntity {
    return ServiceInstanceEntity(
        id = id,
        type = type.name,
        label = label.ifBlank { type.displayName },
        url = url,
        tenantRef = Tenant.refOrDefault(tenantRef),
        siteRef = siteRef,
        credentialRef = credentialRef,
        username = username,
        piholeAuthMode = piholeAuthMode?.name,
        fallbackUrl = fallbackUrl,
        tlsMode = effectiveTlsMode.name,
        certificatePin = certificatePin
    )
}

private fun normalizeUrl(raw: String, type: ServiceType? = null): String {
    var clean = raw.trim()
    clean = clean.trimEnd { it == ')' || it == ']' || it == '}' || it == ',' || it == ';' }
    if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
        clean = "https://$clean"
    }
    clean = clean.replace(Regex("/+$"), "")
    return if (type == ServiceType.UNIFI_NETWORK) stripKnownUnifiApiPath(clean) else clean
}

private fun normalizeOptionalUrl(raw: String?, type: ServiceType? = null): String? {
    if (raw.isNullOrBlank()) return null
    val normalized = normalizeUrl(raw, type)
    return normalized.ifBlank { null }
}

private fun normalizeInstance(instance: ServiceInstance): ServiceInstance {
    val normalizedUrl = normalizeUrl(instance.url, instance.type)
    val normalizedFallback = normalizeOptionalUrl(instance.fallbackUrl, instance.type)
    if (normalizedUrl == instance.url && normalizedFallback == instance.fallbackUrl) {
        return instance
    }
    return instance.copy(url = normalizedUrl, fallbackUrl = normalizedFallback)
}

private fun stripKnownUnifiApiPath(raw: String): String {
    return runCatching {
        val uri = URI(raw)
        val path = uri.rawPath.orEmpty()
        if (!isKnownUnifiApiPath(path)) return@runCatching raw
        URI(uri.scheme, uri.userInfo, uri.host, uri.port, null, null, null).toString()
    }.getOrDefault(raw)
}

private fun isKnownUnifiApiPath(path: String): Boolean {
    val normalized = path.trimEnd('/')
    return normalized == "/proxy/network/integration/v1" ||
        normalized.startsWith("/proxy/network/integration/v1/") ||
        normalized == "/v1" ||
        normalized.startsWith("/v1/")
}
