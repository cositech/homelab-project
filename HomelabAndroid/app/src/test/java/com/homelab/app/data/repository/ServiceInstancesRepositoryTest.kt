package com.homelab.app.data.repository

import com.homelab.app.data.local.SettingsManager
import com.homelab.app.data.local.dao.ServiceInstanceDao
import com.homelab.app.data.local.entity.ServiceInstanceEntity
import com.homelab.app.domain.model.ServiceConnection
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.security.CredentialEnvelope
import com.homelab.app.security.InMemorySecureCredentialStore
import com.homelab.app.util.ServiceType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceInstancesRepositoryTest {

    @Test
    fun `migrates legacy single-instance data only once`() = runTest {
        val dao = FakeServiceInstanceDao()
        val state = SettingsState(
            legacy = mutableMapOf(
                ServiceType.PIHOLE to ServiceConnection(
                    type = ServiceType.PIHOLE,
                    url = "https://pihole.local",
                    token = "sid123",
                    piholePassword = "secret"
                )
            )
        )
        val credentials = InMemorySecureCredentialStore()
        val repository = ServiceInstancesRepository(dao, settingsManager(state), credentials)

        repository.migrateLegacyDataIfNeeded()
        repository.migrateLegacyDataIfNeeded()

        val migrated = dao.getByType(ServiceType.PIHOLE.name)
        assertEquals(1, migrated.size)
        assertEquals(ServiceType.PIHOLE.displayName, migrated.single().label)
        assertEquals(
            CredentialEnvelope(token = "sid123", piholePassword = "secret"),
            credentials.get(migrated.single().credentialRef!!)
        )
        assertEquals("sid123", repository.getInstance(migrated.single().id)?.token)
        assertEquals(migrated.single().id, state.preferred.value[ServiceType.PIHOLE])
        assertNull(state.legacy[ServiceType.PIHOLE])
        assertTrue(state.migrated.value)
    }

    @Test
    fun `two instances of same type coexist and preferred repairs after delete`() = runTest {
        val dao = FakeServiceInstanceDao()
        val state = SettingsState()
        val credentials = InMemorySecureCredentialStore()
        val repository = ServiceInstancesRepository(dao, settingsManager(state), credentials)
        val first = ServiceInstance(
            id = "instance-1",
            type = ServiceType.GITEA,
            label = "Main",
            url = "https://gitea-main.local",
            token = "token-1"
        )
        val second = ServiceInstance(
            id = "instance-2",
            type = ServiceType.GITEA,
            label = "Backup",
            url = "https://gitea-backup.local",
            token = "token-2"
        )

        repository.saveInstance(first)
        repository.saveInstance(second)
        val secondCredentialRef = dao.getById(second.id)?.credentialRef!!
        assertTrue(credentials.contains(secondCredentialRef))
        assertEquals(2, dao.getByType(ServiceType.GITEA.name).size)
        repository.setPreferredInstance(ServiceType.GITEA, second.id)
        repository.deleteInstance(second.id)

        assertEquals(1, dao.getByType(ServiceType.GITEA.name).size)
        assertEquals(first.id, state.preferred.value[ServiceType.GITEA])
        assertEquals(first.id, repository.getPreferredInstance(ServiceType.GITEA)?.id)
        assertNull(repository.getInstance(second.id))
        assertTrue(!credentials.contains(secondCredentialRef))
    }

    @Test
    fun `saveInstance mints a tenant-namespaced v2 credential reference`() = runTest {
        val dao = FakeServiceInstanceDao()
        val credentials = InMemorySecureCredentialStore()
        val repository = ServiceInstancesRepository(dao, settingsManager(SettingsState()), credentials)
        val instance = ServiceInstance(
            id = "instance-1",
            type = ServiceType.GITEA,
            label = "Main",
            url = "https://gitea.local",
            token = "token-1",
            tenantRef = "acme"
        )

        repository.saveInstance(instance)

        val ref = dao.getById(instance.id)?.credentialRef!!
        assertTrue(ref.startsWith("credential:v2:acme:"))
    }

    @Test
    fun `migration re-keys a legacy credential reference into the instance tenant`() = runTest {
        val dao = FakeServiceInstanceDao()
        val credentials = InMemorySecureCredentialStore()
        val repository = ServiceInstancesRepository(dao, settingsManager(SettingsState()), credentials)
        val envelope = CredentialEnvelope(token = "legacy-token")
        credentials.put("credential:v1:instance-1:old", envelope)
        dao.upsert(legacyEntity(id = "instance-1", tenantRef = "acme", credentialRef = "credential:v1:instance-1:old"))

        repository.migrateCredentialReferencesIfNeeded()

        val migratedRef = dao.getById("instance-1")?.credentialRef!!
        assertTrue(migratedRef.startsWith("credential:v2:acme:"))
        assertEquals(envelope, credentials.get(migratedRef))
        assertTrue(!credentials.contains("credential:v1:instance-1:old"))
    }

    @Test
    fun `migration is idempotent for already-migrated references`() = runTest {
        val dao = FakeServiceInstanceDao()
        val credentials = InMemorySecureCredentialStore()
        val repository = ServiceInstancesRepository(dao, settingsManager(SettingsState()), credentials)
        credentials.put("credential:v1:instance-1:old", CredentialEnvelope(token = "legacy-token"))
        dao.upsert(legacyEntity(id = "instance-1", credentialRef = "credential:v1:instance-1:old"))
        repository.migrateCredentialReferencesIfNeeded()
        val firstRef = dao.getById("instance-1")?.credentialRef

        repository.migrateCredentialReferencesIfNeeded()

        assertEquals(firstRef, dao.getById("instance-1")?.credentialRef)
    }

    @Test
    fun `migration fails closed when the legacy envelope is missing`() = runTest {
        val dao = FakeServiceInstanceDao()
        val credentials = InMemorySecureCredentialStore()
        val repository = ServiceInstancesRepository(dao, settingsManager(SettingsState()), credentials)
        dao.upsert(legacyEntity(id = "instance-1", credentialRef = "credential:v1:instance-1:gone"))

        repository.migrateCredentialReferencesIfNeeded()

        assertNull(dao.getById("instance-1")?.credentialRef)
    }

    @Test
    fun `migration fails closed when the new reference cannot be verified`() = runTest {
        val dao = FakeServiceInstanceDao()
        val credentials = InMemorySecureCredentialStore()
        val repository = ServiceInstancesRepository(dao, settingsManager(SettingsState()), credentials)
        credentials.put("credential:v1:instance-1:old", CredentialEnvelope(token = "legacy-token"))
        dao.upsert(legacyEntity(id = "instance-1", credentialRef = "credential:v1:instance-1:old"))
        credentials.failNextPut = true

        repository.migrateCredentialReferencesIfNeeded()

        assertNull(dao.getById("instance-1")?.credentialRef)
        assertTrue(!credentials.contains("credential:v1:instance-1:old"))
    }

    @Test
    fun `migration leaves the legacy reference intact when the database write fails`() = runTest {
        val dao = FakeServiceInstanceDao()
        val credentials = InMemorySecureCredentialStore()
        val repository = ServiceInstancesRepository(dao, settingsManager(SettingsState()), credentials)
        val envelope = CredentialEnvelope(token = "legacy-token")
        credentials.put("credential:v1:instance-1:old", envelope)
        dao.upsert(legacyEntity(id = "instance-1", credentialRef = "credential:v1:instance-1:old"))
        dao.failNextUpsert = true

        try {
            repository.migrateCredentialReferencesIfNeeded()
            org.junit.Assert.fail("expected the simulated database failure to propagate")
        } catch (_: IllegalStateException) {
            // expected
        }

        // The row still depends on the legacy reference, so it must still be readable, and the
        // newly minted (now-unreferenced) v2 reference must not have been left dangling.
        assertEquals("credential:v1:instance-1:old", dao.getById("instance-1")?.credentialRef)
        assertEquals(envelope, credentials.get("credential:v1:instance-1:old"))
        assertTrue(!credentials.contains("credential:v2:default:0"))
    }

    private fun legacyEntity(
        id: String,
        tenantRef: String = "default",
        credentialRef: String?
    ) = ServiceInstanceEntity(
        id = id,
        type = ServiceType.GITEA.name,
        label = "Legacy",
        url = "https://legacy.local",
        tenantRef = tenantRef,
        credentialRef = credentialRef,
        username = null,
        piholeAuthMode = null,
        fallbackUrl = null,
        tlsMode = "SYSTEM"
    )

    private fun settingsManager(state: SettingsState): SettingsManager {
        return mockk(relaxed = true) {
            every { serviceInstancesMigrated } returns state.migrated
            every { preferredInstanceIds } returns state.preferred
            every { preferredInstanceId(any()) } answers {
                val type = invocation.args[0] as ServiceType
                state.preferred.map { it[type] }
            }

            coEvery { getLegacyConnection(any()) } coAnswers {
                state.legacy[invocation.args[0] as ServiceType]
            }
            coEvery { removeLegacyConnection(any()) } coAnswers {
                state.legacy.remove(invocation.args[0] as ServiceType)
            }
            coEvery { setPreferredInstanceId(any(), any()) } coAnswers {
                val type = invocation.args[0] as ServiceType
                val id = invocation.args[1] as String?
                state.preferred.value = state.preferred.value.toMutableMap().apply { put(type, id) }
            }
            coEvery { setServiceInstancesMigrated(any()) } coAnswers {
                state.migrated.value = invocation.args[0] as Boolean
            }
        }
    }
}

private data class SettingsState(
    val migrated: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val preferred: MutableStateFlow<Map<ServiceType, String?>> = MutableStateFlow(emptyMap()),
    val legacy: MutableMap<ServiceType, ServiceConnection?> = mutableMapOf()
)

private class FakeServiceInstanceDao : ServiceInstanceDao {
    private val state = MutableStateFlow<List<ServiceInstanceEntity>>(emptyList())

    override fun observeAll(): Flow<List<ServiceInstanceEntity>> = state

    override suspend fun getAll(): List<ServiceInstanceEntity> = state.value

    override suspend fun getById(id: String): ServiceInstanceEntity? = state.value.firstOrNull { it.id == id }

    override suspend fun getTenantRefById(id: String): String? =
        state.value.firstOrNull { it.id.equals(id, ignoreCase = true) }?.tenantRef

    override suspend fun getByType(type: String): List<ServiceInstanceEntity> =
        state.value.filter { it.type == type }.sortedWith(compareBy<ServiceInstanceEntity> { it.label }.thenBy { it.id })

    /** Simulates a Room I/O failure on the next [upsert] call only. */
    var failNextUpsert: Boolean = false

    override suspend fun upsert(entity: ServiceInstanceEntity) {
        if (failNextUpsert) {
            failNextUpsert = false
            throw IllegalStateException("simulated database failure")
        }
        state.value = state.value.filterNot { it.id == entity.id } + entity
    }

    override suspend fun upsertAll(entities: List<ServiceInstanceEntity>) {
        val ids = entities.map { it.id }.toSet()
        state.value = state.value.filterNot { it.id in ids } + entities
    }

    override suspend fun deleteById(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun deleteAll() {
        state.value = emptyList()
    }
}
