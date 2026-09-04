package com.homelab.app.domain.asset

import com.homelab.app.domain.model.Tenant
import com.homelab.app.domain.provider.ProviderResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalAssetResolverTest {

    private fun obs(
        provider: String,
        resourceId: String,
        name: String = resourceId,
        attributes: Map<String, String> = emptyMap()
    ) = AssetObservation.from(
        ProviderResource(
            providerId = provider,
            instanceId = "inst-$provider",
            resourceType = "host",
            resourceId = resourceId,
            name = name,
            attributes = attributes
        )
    )

    @Test
    fun `a shared serial correlates two providers into one asset`() {
        val assets = CanonicalAssetResolver.resolve(
            Tenant.DEFAULT_ID,
            listOf(
                obs("proxmox", "101", "web01", mapOf("serialNumber" to "ABC-123")),
                obs("netbox", "42", "web01.example.com", mapOf("serial" to "abc-123"))
            )
        )

        assertEquals(1, assets.size)
        assertTrue(assets.single().isCorrelated)
        assertEquals(setOf("proxmox", "netbox"), assets.single().providerIds)
        assertEquals("serial:abc-123", assets.single().key)
    }

    @Test
    fun `a shared MAC or exact FQDN is a strong match`() {
        val byMac = CanonicalAssetResolver.resolve(
            Tenant.DEFAULT_ID,
            listOf(
                obs("unifi", "d1", "AP", mapOf("mac" to "AA-BB-CC-DD-EE-FF")),
                obs("prometheus", "n1", "ap", mapOf("macAddress" to "aa:bb:cc:dd:ee:ff"))
            )
        )
        assertEquals(1, byMac.size)

        val byFqdn = CanonicalAssetResolver.resolve(
            Tenant.DEFAULT_ID,
            listOf(
                obs("proxmox", "1", "pve1.lab.internal."),
                obs("healthchecks", "c", "check", mapOf("hostname" to "PVE1.lab.internal"))
            )
        )
        assertEquals(1, byFqdn.size)
        assertEquals("fqdn:pve1.lab.internal", byFqdn.single().key)
    }

    @Test
    fun `same short hostname in different domains stays two assets`() {
        val assets = CanonicalAssetResolver.resolve(
            Tenant.DEFAULT_ID,
            listOf(
                obs("proxmox", "a", "host.site-a"),
                obs("netbox", "b", "host.site-b")
            )
        )
        assertEquals(2, assets.size)
        assertTrue(assets.all { !it.isCorrelated })
    }

    @Test
    fun `a lone short hostname or lone IP is too weak to correlate`() {
        val loneHost = CanonicalAssetResolver.resolve(
            Tenant.DEFAULT_ID,
            listOf(obs("a", "1", "nas"), obs("b", "2", "nas"))
        )
        assertEquals(2, loneHost.size)

        val loneIp = CanonicalAssetResolver.resolve(
            Tenant.DEFAULT_ID,
            listOf(
                obs("a", "1", "x", mapOf("ipv4" to "10.0.0.5")),
                obs("b", "2", "y", mapOf("ip" to "10.0.0.5"))
            )
        )
        assertEquals(2, loneIp.size)
    }

    @Test
    fun `two agreeing weak signals correlate`() {
        val assets = CanonicalAssetResolver.resolve(
            Tenant.DEFAULT_ID,
            listOf(
                obs("a", "1", "nas", mapOf("ipv4" to "10.0.0.5")),
                obs("b", "2", "NAS", mapOf("ipAddress" to "10.0.0.5/24"))
            )
        )
        assertEquals(1, assets.size)
        assertTrue(assets.single().isCorrelated)
    }

    @Test
    fun `resolution is order independent`() {
        val a = obs("proxmox", "101", "web01", mapOf("serial" to "S1"))
        val b = obs("netbox", "42", "web01", mapOf("serial" to "S1"))
        val c = obs("uptime_kuma", "9", "db01", mapOf("mac" to "11:22:33:44:55:66"))

        val forward = CanonicalAssetResolver.resolve(Tenant.DEFAULT_ID, listOf(a, b, c))
        val reversed = CanonicalAssetResolver.resolve(Tenant.DEFAULT_ID, listOf(c, b, a))

        assertEquals(forward.map { it.key }, reversed.map { it.key })
        assertEquals(2, forward.size)
    }

    @Test
    fun `observations with no identity signal each stay their own asset`() {
        val assets = CanonicalAssetResolver.resolve(
            Tenant.DEFAULT_ID,
            listOf(
                obs("prometheus", "job-1", "  "),
                obs("prometheus", "job-2", "  ")
            )
        )
        assertEquals(2, assets.size)
        assertTrue(assets.all { it.key.startsWith("obs:") })
    }

    @Test
    fun `garbage identity fields are ignored`() {
        val identity = AssetIdentity.from(
            ProviderResource(
                providerId = "x", instanceId = "i", resourceType = "host", resourceId = "r",
                name = "my host with spaces",
                attributes = mapOf(
                    "ipv4" to "999.1.1.1",
                    "mac" to "not-a-mac",
                    "ipv6" to "https://nope",
                    "hostname" to "real-host.example.com"
                )
            )
        )
        assertTrue(identity.ipv4.isEmpty())
        assertTrue(identity.macs.isEmpty())
        assertTrue(identity.ipv6.isEmpty())
        assertEquals(setOf("real-host.example.com"), identity.fqdns)
        assertEquals(setOf("real-host"), identity.shortHostnames)
    }

    @Test
    fun `dotted and hyphenated MAC spellings still correlate`() {
        val assets = CanonicalAssetResolver.resolve(
            Tenant.DEFAULT_ID,
            listOf(
                obs("unifi", "d1", "AP", mapOf("mac" to "aabb.ccdd.eeff")),
                obs("prometheus", "n1", "ap", mapOf("macAddress" to "AA-BB-CC-DD-EE-FF"))
            )
        )
        assertEquals(1, assets.size)
        assertEquals("mac:aa:bb:cc:dd:ee:ff", assets.single().key)
    }

    @Test
    fun `differently spelled IPv6 addresses are one weak signal`() {
        val identity = AssetIdentity.from(
            ProviderResource(
                providerId = "x", instanceId = "i", resourceType = "host", resourceId = "r",
                name = "n", attributes = mapOf("ipv6" to "2001:db8::1")
            )
        )
        val other = AssetIdentity.from(
            ProviderResource(
                providerId = "y", instanceId = "i", resourceType = "host", resourceId = "r",
                name = "n", attributes = mapOf("primaryIp6" to "2001:0db8:0000:0000:0000:0000:0000:0001")
            )
        )
        assertEquals(identity.ipv6, other.ipv6)
        assertEquals(setOf("2001:0db8:0000:0000:0000:0000:0000:0001"), identity.ipv6)

        val malformed = AssetIdentity.from(
            ProviderResource(
                providerId = "z", instanceId = "i", resourceType = "host", resourceId = "r",
                name = "n", attributes = mapOf("ipv6" to ":")
            )
        )
        assertTrue(malformed.ipv6.isEmpty())
    }

    @Test
    fun `an IPv4 literal in a hostname field is not a second weak signal`() {
        val assets = CanonicalAssetResolver.resolve(
            Tenant.DEFAULT_ID,
            listOf(
                obs("a", "1", "10.0.0.5"),
                obs("b", "2", "10.0.0.5", mapOf("ip" to "10.0.0.5"))
            )
        )
        assertEquals(2, assets.size)
        assertTrue(assets.all { it.identity.shortHostnames.isEmpty() })
        assertTrue(assets.all { it.identity.ipv4 == setOf("10.0.0.5") })
    }

    @Test
    fun `carries the tenant ref through unchanged`() {
        val assets = CanonicalAssetResolver.resolve(
            "acme",
            listOf(obs("proxmox", "1", "pve1.acme.internal"))
        )
        assertEquals("acme", assets.single().tenantRef)
        assertFalse(assets.single().isCorrelated)
    }
}
