package com.homelab.app.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomerRegistryTest {

    private fun customer(tenantRef: String, accountName: String = tenantRef, contact: String? = null, notes: String? = null) =
        Customer(tenantRef = tenantRef, accountName = accountName, contact = contact, notes = notes)

    @Test
    fun `initial registry is empty`() {
        assertEquals(emptyList<Customer>(), CustomerRegistry.INITIAL.customers)
    }

    @Test
    fun `setting adds a customer record`() {
        val registry = CustomerRegistry.INITIAL.setting(customer("acme", "Acme Corp"))

        assertEquals("Acme Corp", registry.customerFor("acme")?.accountName)
    }

    @Test
    fun `setting with an existing tenant ref replaces that entry`() {
        val registry = CustomerRegistry.INITIAL
            .setting(customer("acme", "Acme"))
            .setting(customer("acme", "Acme Corp", contact = "ops@acme.test"))

        assertEquals(1, registry.customers.size)
        assertEquals("Acme Corp", registry.customerFor("acme")?.accountName)
        assertEquals("ops@acme.test", registry.customerFor("acme")?.contact)
    }

    @Test
    fun `normalize drops a blank account name`() {
        val registry = CustomerRegistry(
            customers = listOf(Customer(tenantRef = "acme", accountName = "  "))
        ).normalized()

        assertEquals(emptyList<Customer>(), registry.customers)
    }

    @Test
    fun `normalize trims fields and blanks optional ones to null`() {
        val registry = CustomerRegistry(
            customers = listOf(
                Customer(tenantRef = "  acme  ", accountName = "  Acme  ", contact = "   ", notes = "  Some notes  ")
            )
        ).normalized()

        val stored = registry.customerFor("acme")
        assertEquals("acme", stored?.tenantRef)
        assertEquals("Acme", stored?.accountName)
        assertNull(stored?.contact)
        assertEquals("Some notes", stored?.notes)
    }

    @Test
    fun `customerFor returns null when no record exists`() {
        assertNull(CustomerRegistry.INITIAL.customerFor("ghost"))
    }

    @Test
    fun `setting a blank account name effectively removes the record`() {
        val registry = CustomerRegistry.INITIAL
            .setting(customer("acme", "Acme"))
            .setting(customer("acme", "  "))

        assertNull(registry.customerFor("acme"))
    }

    @Test
    fun `removing drops only the matching tenant's record`() {
        val registry = CustomerRegistry.INITIAL
            .setting(customer("acme", "Acme"))
            .setting(customer("globex", "Globex"))
            .removing("acme")

        assertNull(registry.customerFor("acme"))
        assertEquals("Globex", registry.customerFor("globex")?.accountName)
    }

    @Test
    fun `removing an unknown tenant ref is a no-op`() {
        val registry = CustomerRegistry.INITIAL.setting(customer("acme", "Acme")).removing("ghost")
        assertEquals("Acme", registry.customerFor("acme")?.accountName)
    }

    @Test
    fun `registry survives a json round trip`() {
        val original = CustomerRegistry.INITIAL
            .setting(customer("acme", "Acme", contact = "ops@acme.test", notes = "VIP"))
            .setting(customer("globex", "Globex"))

        val json = Json.encodeToString(CustomerRegistry.serializer(), original)
        val restored = Json.decodeFromString(CustomerRegistry.serializer(), json).normalized()

        assertEquals(original, restored)
    }
}
