package com.homelab.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.homelab.app.domain.action.ControlledActionCoordinator
import com.homelab.app.domain.action.DurableActionQueueEntry
import com.homelab.app.domain.action.DurableActionQueueStore
import com.homelab.app.security.AndroidKeystoreCredentialStore
import com.homelab.app.security.SecureCredentialStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class DataStoreDurableActionQueueStore(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
    private val maximumEntries: Int = 500
) : DurableActionQueueStore {
    init { require(maximumEntries > 0) }

    private val mutex = Mutex()
    private val key = stringPreferencesKey("controlled_action_queue_v1")
    private val serializer = ListSerializer(DurableActionQueueEntry.serializer())

    override suspend fun snapshot(): List<DurableActionQueueEntry> = mutex.withLock {
        decode(dataStore.data.first()[key])
    }

    override suspend fun upsert(entry: DurableActionQueueEntry) {
        mutex.withLock {
            dataStore.edit { preferences ->
                val entries = LinkedHashMap<String, DurableActionQueueEntry>()
                decode(preferences[key]).forEach {
                    entries[it.request.idempotencyKey] = it
                }
                entries.remove(entry.request.idempotencyKey)
                entries[entry.request.idempotencyKey] = entry
                while (entries.size > maximumEntries) entries.remove(entries.keys.first())
                preferences[key] = json.encodeToString(serializer, entries.values.toList())
            }
        }
    }

    private fun decode(raw: String?): List<DurableActionQueueEntry> =
        raw?.let { json.decodeFromString(serializer, it) } ?: emptyList()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds
    @Singleton
    abstract fun bindSecureCredentialStore(
        implementation: AndroidKeystoreCredentialStore
    ): SecureCredentialStore

    companion object {
        @Provides
        @Singleton
        fun provideDurableActionQueueStore(
            dataStore: DataStore<Preferences>,
            json: Json
        ): DurableActionQueueStore = DataStoreDurableActionQueueStore(dataStore, json)

        @Provides
        @Singleton
        fun provideControlledActionCoordinator(
            durableStore: DurableActionQueueStore
        ): ControlledActionCoordinator = ControlledActionCoordinator(durableStore = durableStore)
    }
}
