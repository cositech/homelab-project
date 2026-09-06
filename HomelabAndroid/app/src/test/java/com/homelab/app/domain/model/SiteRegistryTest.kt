package com.homelab.app.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteRegistryTest {

    private fun site(id: String, tenantRef: String, name: String = id) =
        Site(id = id, tenantRef = tenantRef, name = name)

    @Test
    fun `initial registry is empty`() {
        assertEquals(emptyList<Site>(), SiteRegistry.INITIAL.sites)
    }

    @Test
    fun `adding a site appends it`() {
        val registry = SiteRegistry.INITIAL.adding(site("rack-1", "acme", "Rack 1"))

        assertEquals(listOf("rack-1"), registry.sites.map { it.id })
        assertEquals("Rack 1", registry.sites.single().name)
    }

    @Test
    fun `adding with an existing id replaces that entry`() {
        val registry = SiteRegistry.INITIAL
            .adding(site("rack-1", "acme", "Rack 1"))
            .adding(site("rack-1", "acme", "Server Room"))

        assertEquals(1, registry.sites.size)
        assertEquals("Server Room", registry.sites.single().name)
    }

    @Test
    fun `normalize drops blank ids and names`() {
        val registry = SiteRegistry(
            sites = listOf(
                Site(id = "", tenantRef = "acme", name = "No id"),
                Site(id = "rack-2", tenantRef = "acme", name = "  "),
                Site(id = "rack-3", tenantRef = "acme", name = "Valid")
            )
        ).normalized()

        assertEquals(listOf("rack-3"), registry.sites.map { it.id })
    }

    @Test
    fun `normalize trims ids, tenant refs and names`() {
        val registry = SiteRegistry(
            sites = listOf(Site(id = "  rack-1  ", tenantRef = "  acme  ", name = "  Rack 1  "))
        ).normalized()

        val stored = registry.sites.single()
        assertEquals("rack-1", stored.id)
        assertEquals("acme", stored.tenantRef)
        assertEquals("Rack 1", stored.name)
    }

    @Test
    fun `sitesForTenant only returns that tenant's sites`() {
        val registry = SiteRegistry.INITIAL
            .adding(site("rack-1", "acme"))
            .adding(site("rack-2", "globex"))
            .adding(site("rack-3", "acme"))

        assertEquals(setOf("rack-1", "rack-3"), registry.sitesForTenant("acme").map { it.id }.toSet())
        assertEquals(listOf("rack-2"), registry.sitesForTenant("globex").map { it.id })
        assertTrue(registry.sitesForTenant("ghost").isEmpty())
    }

    @Test
    fun `renaming updates only the matching site`() {
        val registry = SiteRegistry.INITIAL
            .adding(site("rack-1", "acme", "Rack 1"))
            .adding(site("rack-2", "acme", "Rack 2"))
            .renaming("rack-1", "Server Room")

        assertEquals("Server Room", registry.sites.first { it.id == "rack-1" }.name)
        assertEquals("Rack 2", registry.sites.first { it.id == "rack-2" }.name)
    }

    @Test
    fun `removing drops only the matching site`() {
        val registry = SiteRegistry.INITIAL
            .adding(site("rack-1", "acme"))
            .adding(site("rack-2", "acme"))
            .removing("rack-1")

        assertEquals(listOf("rack-2"), registry.sites.map { it.id })
    }

    @Test
    fun `removing an unknown id is a no-op`() {
        val registry = SiteRegistry.INITIAL.adding(site("rack-1", "acme")).removing("ghost")
        assertEquals(listOf("rack-1"), registry.sites.map { it.id })
    }

    @Test
    fun `registry survives a json round trip`() {
        val original = SiteRegistry.INITIAL
            .adding(site("rack-1", "acme", "Rack 1"))
            .adding(site("rack-2", "globex", "Rack 2"))

        val json = Json.encodeToString(SiteRegistry.serializer(), original)
        val restored = Json.decodeFromString(SiteRegistry.serializer(), json).normalized()

        assertEquals(original, restored)
    }
}
