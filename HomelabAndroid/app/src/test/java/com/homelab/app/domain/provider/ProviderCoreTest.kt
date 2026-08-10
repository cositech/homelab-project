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

    @Test
    fun `operations search returns matching assets without exposing unrelated records`() {
        val snapshot = OperationsSnapshot(
            assets = listOf(
                ProviderResource("proxmox", "pve-1", "virtual-machine", "101", "zammad", "running"),
                ProviderResource("uptime-kuma", "kuma-1", "monitor", "22", "grafana", "up")
            ),
            alerts = listOf(
                ProviderEvent("uptime-kuma", "kuma-1", "alert-1", "critical", "customer portal is down", 1L, "22")
            )
        )

        val result = snapshot.search("zammad")

        assertEquals(listOf("101"), result.assets.map { it.resourceId })
        assertTrue(result.alerts.isEmpty())
        assertTrue(result.health.isEmpty())
    }

    @Test
    fun `blank operations search does not return the full potentially sensitive snapshot`() {
        val snapshot = OperationsSnapshot(
            diagnostics = listOf(
                ProviderDiagnostic("proxmox", "pve-1", "PVE", "https://pve.internal", "SYSTEM", emptySet(), ProviderHealthState.HEALTHY)
            )
        )

        assertTrue(snapshot.search("   ").isEmpty)
    }
}
