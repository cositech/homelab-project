package com.homelab.app.domain.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TenantTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `default tenant has stable identity and personal kind`() {
        assertEquals("default", Tenant.DEFAULT_ID)
        assertEquals(Tenant.DEFAULT_ID, Tenant.DEFAULT.id)
        assertEquals(TenantKind.PERSONAL, Tenant.DEFAULT.kind)
        assertTrue(Tenant.DEFAULT.isDefault)
        assertFalse(Tenant(id = "acme", name = "Acme", kind = TenantKind.CUSTOMER).isDefault)
    }

    @Test
    fun `refOrDefault normalizes null and blank to the default tenant`() {
        assertEquals("default", Tenant.refOrDefault(null))
        assertEquals("default", Tenant.refOrDefault(""))
        assertEquals("default", Tenant.refOrDefault("   "))
        assertEquals("acme", Tenant.refOrDefault(" acme "))
    }

    @Test
    fun `service instance decoded without a tenant belongs to the default tenant`() {
        val legacy = """
            { "id": "i-1", "type": "PIHOLE", "label": "Pi-hole", "url": "https://pihole.local" }
        """.trimIndent()

        val decoded = json.decodeFromString<ServiceInstance>(legacy)

        assertEquals(Tenant.DEFAULT_ID, decoded.tenantRef)
        assertEquals(null, decoded.siteRef)
    }

    @Test
    fun `service instance keeps an explicit tenant and site`() {
        val scoped = ServiceInstance(
            id = "i-2",
            type = com.homelab.app.util.ServiceType.PROXMOX,
            label = "PVE",
            url = "https://pve.acme.internal",
            tenantRef = "acme",
            siteRef = "acme-rack-1"
        )

        val round = json.decodeFromString<ServiceInstance>(json.encodeToString(ServiceInstance.serializer(), scoped))

        assertEquals("acme", round.tenantRef)
        assertEquals("acme-rack-1", round.siteRef)
    }

    @Test
    fun `legacy connection migrates into the default tenant`() {
        val migrated = ServiceConnection(
            type = com.homelab.app.util.ServiceType.BESZEL,
            url = "https://beszel.local"
        ).migratedInstance("i-3")

        assertEquals(Tenant.DEFAULT_ID, migrated.tenantRef)
    }
}
