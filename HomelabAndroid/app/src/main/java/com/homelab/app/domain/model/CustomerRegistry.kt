package com.homelab.app.domain.model

import kotlinx.serialization.Serializable

/**
 * The device-local set of [Customer] records, at most one per tenant (keyed by [Customer.tenantRef]).
 * Unlike [SiteRegistry] this is a 1:1 relationship, not a 1:many one - a tenant either has customer
 * metadata or doesn't, so there is no "customers for tenant" list, only a single lookup.
 *
 * All transforms are pure and return a re-[normalized] value. Invariants held by [normalized]:
 * [customers] has no duplicate tenant refs and no blank account name - clearing the account name
 * and saving is how a customer record is removed.
 */
@Serializable
data class CustomerRegistry(
    val customers: List<Customer> = emptyList()
) {
    fun customerFor(tenantRef: String): Customer? {
        val target = Tenant.refOrDefault(tenantRef)
        return customers.firstOrNull { it.tenantRef == target }
    }

    fun normalized(): CustomerRegistry {
        val deduped = LinkedHashMap<String, Customer>()
        for (customer in customers) {
            val tenantRef = Tenant.refOrDefault(customer.tenantRef)
            val accountName = customer.accountName.trim()
            if (accountName.isEmpty()) continue
            deduped[tenantRef] = customer.copy(
                tenantRef = tenantRef,
                accountName = accountName,
                contact = customer.contact?.trim()?.takeIf { it.isNotEmpty() },
                notes = customer.notes?.trim()?.takeIf { it.isNotEmpty() }
            )
        }
        return CustomerRegistry(customers = deduped.values.toList())
    }

    /** Sets (or replaces) the customer record for [customer]'s tenant. A blank account name removes it. */
    fun setting(customer: Customer): CustomerRegistry {
        val tenantRef = Tenant.refOrDefault(customer.tenantRef)
        val next = customers.filterNot { it.tenantRef == tenantRef } + customer.copy(tenantRef = tenantRef)
        return copy(customers = next).normalized()
    }

    fun removing(tenantRef: String): CustomerRegistry {
        val target = Tenant.refOrDefault(tenantRef)
        return copy(customers = customers.filterNot { it.tenantRef == target }).normalized()
    }

    companion object {
        val INITIAL: CustomerRegistry = CustomerRegistry()
    }
}
