package com.homelab.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.homelab.app.domain.model.Customer
import com.homelab.app.domain.model.CustomerRegistry
import com.homelab.app.domain.model.Tenant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Persists the device-local [CustomerRegistry] in the same Preferences DataStore [TenantStore] and
 * [SiteStore] use, under its own JSON key. All mutation rules live in [CustomerRegistry]; this
 * class only stores the result.
 *
 * A device with no customer-kind tenant (or one that never fills in its metadata) keeps the
 * default empty [CustomerRegistry.INITIAL] and never writes the key.
 */
@Singleton
class CustomerStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    private val key = stringPreferencesKey(STORE_KEY)

    val registry: Flow<CustomerRegistry> = dataStore.data.map { decode(it[key]) }

    suspend fun current(): CustomerRegistry = decode(dataStore.data.first()[key])

    suspend fun setCustomer(
        tenantRef: String,
        accountName: String,
        contact: String?,
        notes: String?
    ): CustomerRegistry = update {
        it.setting(
            Customer(
                tenantRef = Tenant.refOrDefault(tenantRef),
                accountName = accountName.trim(),
                contact = contact?.trim(),
                notes = notes?.trim()
            )
        )
    }

    suspend fun removeCustomer(tenantRef: String): CustomerRegistry = update { it.removing(tenantRef) }

    private suspend fun update(transform: (CustomerRegistry) -> CustomerRegistry): CustomerRegistry {
        var result = CustomerRegistry.INITIAL
        dataStore.edit { preferences ->
            result = transform(decode(preferences[key])).normalized()
            preferences[key] = json.encodeToString(CustomerRegistry.serializer(), result)
        }
        return result
    }

    private fun decode(raw: String?): CustomerRegistry {
        if (raw.isNullOrBlank()) return CustomerRegistry.INITIAL
        return runCatching {
            json.decodeFromString(CustomerRegistry.serializer(), raw).normalized()
        }.getOrDefault(CustomerRegistry.INITIAL)
    }

    private companion object {
        const val STORE_KEY = "customer_registry_v1"
    }
}
