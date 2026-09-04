package com.homelab.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Phase 4 correlation and MSP contracts.
 *
 * A [Tenant] is the isolation unit: every [ServiceInstance], credential, operations record and
 * controlled-action request belongs to exactly one tenant, and no read ever crosses the boundary.
 * A single-tenant install runs entirely inside the implicit [Tenant.DEFAULT_ID] tenant and shows
 * no new UI.
 */
@Serializable
enum class TenantKind {
    /** The operator's own estate. The [Tenant.DEFAULT_ID] tenant is always this kind. */
    @SerialName("personal") PERSONAL,

    /** An MSP-managed customer estate. Carries [Customer] metadata. */
    @SerialName("customer") CUSTOMER
}

@Serializable
data class Tenant(
    val id: String,
    val name: String,
    val kind: TenantKind = TenantKind.PERSONAL
) {
    val isDefault: Boolean get() = id == DEFAULT_ID

    companion object {
        /** Id of the implicit tenant every pre-Phase-4 instance is migrated into. */
        const val DEFAULT_ID: String = "default"

        /** The implicit personal tenant. Cannot be deleted. */
        val DEFAULT: Tenant = Tenant(id = DEFAULT_ID, name = "Default", kind = TenantKind.PERSONAL)

        /** Normalizes a nullable/blank stored value to a usable tenant id. */
        fun refOrDefault(value: String?): String = value?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_ID
    }
}

/** A physical or logical location within a [Tenant] (a rack, a home, a branch office). */
@Serializable
data class Site(
    val id: String,
    val tenantRef: String,
    val name: String
)

/** MSP-facing metadata on a [TenantKind.CUSTOMER] tenant. Never holds a secret; kept out of the audit ledger. */
@Serializable
data class Customer(
    val tenantRef: String,
    val accountName: String,
    val contact: String? = null,
    val notes: String? = null
)
