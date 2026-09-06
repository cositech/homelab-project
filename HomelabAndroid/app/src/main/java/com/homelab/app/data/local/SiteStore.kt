package com.homelab.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.homelab.app.domain.model.Site
import com.homelab.app.domain.model.SiteRegistry
import com.homelab.app.domain.model.Tenant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Persists the device-local [SiteRegistry] in the same Preferences DataStore [TenantStore] uses,
 * under its own JSON key. All mutation rules live in [SiteRegistry]; this class only stores the
 * result and generates ids for new sites.
 *
 * A device that never creates a site keeps the default empty [SiteRegistry.INITIAL] and never
 * writes the key.
 */
@Singleton
class SiteStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    private val key = stringPreferencesKey(STORE_KEY)

    val registry: Flow<SiteRegistry> = dataStore.data.map { decode(it[key]) }

    suspend fun current(): SiteRegistry = decode(dataStore.data.first()[key])

    /** Creates a site for [tenantRef] with a fresh id and returns the stored registry. */
    suspend fun addSite(tenantRef: String, name: String): SiteRegistry {
        val site = Site(
            id = "site-${UUID.randomUUID()}",
            tenantRef = Tenant.refOrDefault(tenantRef),
            name = name.trim()
        )
        return update { it.adding(site) }
    }

    suspend fun renameSite(id: String, name: String): SiteRegistry =
        update { it.renaming(id, name.trim()) }

    suspend fun removeSite(id: String): SiteRegistry = update { it.removing(id) }

    private suspend fun update(transform: (SiteRegistry) -> SiteRegistry): SiteRegistry {
        var result = SiteRegistry.INITIAL
        dataStore.edit { preferences ->
            result = transform(decode(preferences[key])).normalized()
            preferences[key] = json.encodeToString(SiteRegistry.serializer(), result)
        }
        return result
    }

    private fun decode(raw: String?): SiteRegistry {
        if (raw.isNullOrBlank()) return SiteRegistry.INITIAL
        return runCatching {
            json.decodeFromString(SiteRegistry.serializer(), raw).normalized()
        }.getOrDefault(SiteRegistry.INITIAL)
    }

    private companion object {
        const val STORE_KEY = "site_registry_v1"
    }
}
