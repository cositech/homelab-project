package com.homelab.app.domain.provider

import com.homelab.app.util.ServiceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCoreTest {
    @Test
    fun `registry covers all supported service types`() {
        assertEquals(
            ServiceType.entries.count { it != ServiceType.UNKNOWN },
            ProviderRegistry.registeredProviders().size
        )
    }

    @Test
    fun `reference providers expose normalized capabilities`() {
        val proxmox = ProviderRegistry.capabilities(ServiceType.PROXMOX)
        val kuma = ProviderRegistry.capabilities(ServiceType.UPTIME_KUMA)

        assertTrue(ProviderCapability.RESOURCES in proxmox)
        assertTrue(ProviderCapability.WRITE_ACTIONS in proxmox)
        assertTrue(ProviderCapability.METRICS in kuma)
        assertTrue(ProviderCapability.WRITE_ACTIONS !in kuma)
    }
}
