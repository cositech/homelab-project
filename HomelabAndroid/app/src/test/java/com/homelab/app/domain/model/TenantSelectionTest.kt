package com.homelab.app.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TenantSelectionTest {

    private fun tenant(id: String, name: String = id, kind: TenantKind = TenantKind.CUSTOMER) =
        Tenant(id = id, name = name, kind = kind)

    @Test
    fun `initial selection is the single default tenant`() {
        val selection = TenantSelection.INITIAL

        assertEquals(listOf(Tenant.DEFAULT), selection.tenants)
        assertEquals(Tenant.DEFAULT_ID, selection.activeTenantId)
        assertTrue(selection.isSingleTenant)
        assertFalse(selection.allTenantsMode)
        assertEquals(setOf(Tenant.DEFAULT_ID), selection.membershipRefs)
    }

    @Test
    fun `normalize forces the default tenant to be present and first`() {
        val selection = TenantSelection(
            tenants = listOf(tenant("acme"), tenant("globex")),
            activeTenantId = "acme"
        ).normalized()

        assertEquals(listOf(Tenant.DEFAULT_ID, "acme", "globex"), selection.tenants.map { it.id })
        assertEquals("acme", selection.activeTenantId)
    }

    @Test
    fun `normalize drops duplicate ids and keeps the last`() {
        val selection = TenantSelection(
            tenants = listOf(tenant("acme", name = "Old"), tenant("acme", name = "New"))
        ).normalized()

        assertEquals(listOf(Tenant.DEFAULT_ID, "acme"), selection.tenants.map { it.id })
        assertEquals("New", selection.tenants.first { it.id == "acme" }.name)
    }

    @Test
    fun `normalize pins an unknown active tenant back to the default`() {
        val selection = TenantSelection(
            tenants = listOf(tenant("acme")),
            activeTenantId = "ghost"
        ).normalized()

        assertEquals(Tenant.DEFAULT_ID, selection.activeTenantId)
    }

    @Test
    fun `normalize trims the stored active tenant id before matching`() {
        val selection = TenantSelection(
            tenants = listOf(tenant("acme")),
            activeTenantId = "  acme  "
        ).normalized()

        assertEquals("acme", selection.activeTenantId)
    }

    @Test
    fun `all-tenants mode collapses to false for a single-tenant install`() {
        assertFalse(TenantSelection(allTenantsMode = true).normalized().allTenantsMode)
    }

    @Test
    fun `adding a tenant appends it and leaves the active selection untouched`() {
        val selection = TenantSelection.INITIAL.adding(tenant("acme", name = "Acme"))

        assertEquals(listOf(Tenant.DEFAULT_ID, "acme"), selection.tenants.map { it.id })
        assertEquals(Tenant.DEFAULT_ID, selection.activeTenantId)
        assertFalse(selection.isSingleTenant)
        assertEquals(setOf(Tenant.DEFAULT_ID, "acme"), selection.membershipRefs)
    }

    @Test
    fun `adding with an existing id replaces that entry`() {
        val selection = TenantSelection.INITIAL
            .adding(tenant("acme", name = "Acme"))
            .adding(tenant("acme", name = "Acme Corp"))

        assertEquals(2, selection.tenants.size)
        assertEquals("Acme Corp", selection.tenants.first { it.id == "acme" }.name)
    }

    @Test
    fun `the default tenant can never be replaced or removed`() {
        val replaced = TenantSelection.INITIAL.adding(tenant(Tenant.DEFAULT_ID, name = "Hacked"))
        assertEquals("Default", replaced.tenants.single().name)

        val removed = TenantSelection.INITIAL
            .adding(tenant("acme"))
            .removing(Tenant.DEFAULT_ID)
        assertTrue(removed.tenants.any { it.isDefault })
    }

    @Test
    fun `removing the active tenant falls back to the default`() {
        val selection = TenantSelection.INITIAL
            .adding(tenant("acme"))
            .activating("acme")
            .removing("acme")

        assertEquals(Tenant.DEFAULT_ID, selection.activeTenantId)
        assertEquals(listOf(Tenant.DEFAULT_ID), selection.tenants.map { it.id })
    }

    @Test
    fun `activating an unknown tenant is a no-op`() {
        val selection = TenantSelection.INITIAL.adding(tenant("acme")).activating("ghost")
        assertEquals(Tenant.DEFAULT_ID, selection.activeTenantId)
    }

    @Test
    fun `activating a tenant leaves all-tenants mode`() {
        val selection = TenantSelection.INITIAL
            .adding(tenant("acme"))
            .settingAllTenantsMode(true)
            .activating("acme")

        assertEquals("acme", selection.activeTenantId)
        assertFalse(selection.allTenantsMode)
    }

    @Test
    fun `all-tenants mode is only enabled with more than one tenant`() {
        assertFalse(TenantSelection.INITIAL.settingAllTenantsMode(true).allTenantsMode)
        assertTrue(
            TenantSelection.INITIAL.adding(tenant("acme")).settingAllTenantsMode(true).allTenantsMode
        )
    }

    @Test
    fun `renaming updates only the matching tenant`() {
        val selection = TenantSelection.INITIAL
            .adding(tenant("acme", name = "Acme"))
            .renaming("acme", "Acme Corp")

        assertEquals("Acme Corp", selection.tenants.first { it.id == "acme" }.name)
        assertEquals("Default", selection.tenants.first { it.isDefault }.name)
    }

    @Test
    fun `selection survives a json round trip`() {
        val original = TenantSelection.INITIAL
            .adding(tenant("acme", name = "Acme"))
            .adding(tenant("globex", name = "Globex"))
            .activating("acme")
            .settingAllTenantsMode(false)

        val json = Json.encodeToString(TenantSelection.serializer(), original)
        val restored = Json.decodeFromString(TenantSelection.serializer(), json).normalized()

        assertEquals(original, restored)
    }
}
