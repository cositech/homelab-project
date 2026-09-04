package com.homelab.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.homelab.app.domain.model.Tenant
import com.homelab.app.domain.model.TenantKind
import com.homelab.app.domain.model.TenantSelection
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Persists the device-local [TenantSelection] (configured tenants + active selection) in the
 * Preferences DataStore under a single JSON key. All mutation rules live in [TenantSelection];
 * this class only stores the result and generates ids for new tenants.
 *
 * A device that never adds a second tenant keeps the default [TenantSelection.INITIAL] and never
 * writes the key, so a single-tenant install is byte-identical to pre-Phase-4.
 */
@Singleton
class TenantStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    private val key = stringPreferencesKey(STORE_KEY)

    val selection: Flow<TenantSelection> = dataStore.data.map { decode(it[key]) }

    suspend fun current(): TenantSelection = decode(dataStore.data.first()[key])

    /** Creates a tenant with a fresh id and returns the stored selection. */
    suspend fun addTenant(name: String, kind: TenantKind = TenantKind.CUSTOMER): TenantSelection {
        val tenant = Tenant(id = "tenant-${UUID.randomUUID()}", name = name.trim(), kind = kind)
        return update { it.adding(tenant) }
    }

    suspend fun renameTenant(id: String, name: String): TenantSelection =
        update { it.renaming(id, name.trim()) }

    suspend fun removeTenant(id: String): TenantSelection = update { it.removing(id) }

    suspend fun setActiveTenant(id: String): TenantSelection = update { it.activating(id) }

    suspend fun setAllTenantsMode(enabled: Boolean): TenantSelection =
        update { it.settingAllTenantsMode(enabled) }

    private suspend fun update(transform: (TenantSelection) -> TenantSelection): TenantSelection {
        var result = TenantSelection.INITIAL
        dataStore.edit { preferences ->
            result = transform(decode(preferences[key])).normalized()
            preferences[key] = json.encodeToString(TenantSelection.serializer(), result)
        }
        return result
    }

    private fun decode(raw: String?): TenantSelection {
        if (raw.isNullOrBlank()) return TenantSelection.INITIAL
        return runCatching {
            json.decodeFromString(TenantSelection.serializer(), raw).normalized()
        }.getOrDefault(TenantSelection.INITIAL)
    }

    private companion object {
        const val STORE_KEY = "tenant_selection_v1"
    }
}
