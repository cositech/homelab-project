package com.homelab.app.domain.asset

import com.homelab.app.domain.provider.ProviderResource

/**
 * Phase 4 canonical asset model.
 *
 * Resolution is pure, deterministic and read-only: the same observations always produce the same
 * asset keys out, with no clock or network dependency. It never rewrites provider data, never
 * merges across tenants, and a wrong match degrades to "two assets" rather than leaking one
 * tenant's host into another. Assets are recomputed from the current operations snapshot on every
 * refresh; there is no long-lived asset store.
 */

/** Normalized identity signals extracted from a single provider observation. */
data class AssetIdentity(
    /** Lowercased fully-qualified names, trailing dot stripped, kept intact so `host.a` != `host.b`. */
    val fqdns: Set<String> = emptySet(),
    /** First label of each hostname/FQDN, lowercased. Weak on its own. */
    val shortHostnames: Set<String> = emptySet(),
    val ipv4: Set<String> = emptySet(),
    val ipv6: Set<String> = emptySet(),
    /** Normalized `aa:bb:cc:dd:ee:ff`. */
    val macs: Set<String> = emptySet(),
    val serials: Set<String> = emptySet(),
    /** Provider-native stable ids, e.g. `proxmox:node:pve1`. */
    val cloudIds: Set<String> = emptySet()
) {
    val isEmpty: Boolean
        get() = fqdns.isEmpty() && shortHostnames.isEmpty() && ipv4.isEmpty() &&
            ipv6.isEmpty() && macs.isEmpty() && serials.isEmpty() && cloudIds.isEmpty()

    fun merge(other: AssetIdentity) = AssetIdentity(
        fqdns = fqdns + other.fqdns,
        shortHostnames = shortHostnames + other.shortHostnames,
        ipv4 = ipv4 + other.ipv4,
        ipv6 = ipv6 + other.ipv6,
        macs = macs + other.macs,
        serials = serials + other.serials,
        cloudIds = cloudIds + other.cloudIds
    )

    /** Fields that uniquely name a host on their own. */
    fun strongTokens(): Set<String> =
        serials.map { "serial:$it" }.toSet() +
            macs.map { "mac:$it" } +
            fqdns.map { "fqdn:$it" } +
            cloudIds.map { "cloud:$it" }

    /** Fields that only correlate when at least two of them agree. */
    fun weakTokens(): Set<String> =
        shortHostnames.map { "host:$it" }.toSet() +
            ipv4.map { "ipv4:$it" } +
            ipv6.map { "ipv6:$it" }

    companion object {
        private val HOSTNAME_KEYS = listOf(
            "fqdn", "hostname", "hostName", "host", "friendlyName", "nodeName", "dnsName"
        )
        private val IPV4_KEYS = listOf("ipv4", "primaryIp4", "ip", "ipAddress", "address")
        private val IPV6_KEYS = listOf("ipv6", "primaryIp6")
        private val MAC_KEYS = listOf("mac", "macAddress", "hwAddress")
        private val SERIAL_KEYS = listOf("serial", "serialNumber", "serviceTag")
        private val CLOUD_ID_KEYS = listOf("cloudId", "canonicalId")

        private val ipv4Regex = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

        /** Best-effort identity extraction from a normalized resource. Unknown/garbage fields are dropped. */
        fun from(resource: ProviderResource): AssetIdentity {
            val attrs = resource.attributes
            fun values(keys: List<String>): List<String> = keys.mapNotNull { key ->
                attrs.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
            }

            val fqdns = mutableSetOf<String>()
            val shorts = mutableSetOf<String>()
            val hostIpv4 = mutableSetOf<String>()
            (values(HOSTNAME_KEYS) + resource.name).forEach { raw ->
                val host = normalizeHost(raw) ?: return@forEach
                val asIpv4 = normalizeIpv4(host)
                when {
                    asIpv4 != null -> hostIpv4 += asIpv4
                    host.contains('.') -> {
                        fqdns += host
                        host.substringBefore('.').takeIf { it.isNotBlank() }?.let(shorts::add)
                    }
                    !host.contains(':') -> shorts += host
                }
            }

            return AssetIdentity(
                fqdns = fqdns,
                shortHostnames = shorts,
                ipv4 = (values(IPV4_KEYS).mapNotNull(::normalizeIpv4) + hostIpv4).toSet(),
                ipv6 = values(IPV6_KEYS).mapNotNull(::normalizeIpv6).toSet(),
                macs = values(MAC_KEYS).mapNotNull(::normalizeMac).toSet(),
                serials = values(SERIAL_KEYS).mapNotNull { it.trim().lowercase().takeIf(String::isNotBlank) }.toSet(),
                cloudIds = values(CLOUD_ID_KEYS).mapNotNull { it.trim().lowercase().takeIf(String::isNotBlank) }.toSet()
            )
        }

        private fun normalizeHost(raw: String?): String? {
            val clean = raw?.trim()?.trimEnd('.')?.lowercase() ?: return null
            if (clean.isBlank() || clean.contains(' ') || clean.contains('/')) return null
            return clean
        }

        private fun normalizeIpv4(raw: String?): String? {
            val clean = raw?.trim()?.substringBefore('/') ?: return null
            val match = ipv4Regex.matchEntire(clean) ?: return null
            if (match.groupValues.drop(1).any { it.toInt() !in 0..255 }) return null
            return clean
        }

        /** Expand to the canonical, fully zero-padded 8-group form so differing spellings match. */
        private fun normalizeIpv6(raw: String?): String? {
            val clean = raw?.trim()?.substringBefore('/')?.substringBefore('%')?.lowercase() ?: return null
            if (clean.count { it == ':' } < 2) return null
            if (clean.indexOf("::") != clean.lastIndexOf("::")) return null
            val compressed = clean.contains("::")
            val headRaw: String
            val tailRaw: String
            if (compressed) {
                val halves = clean.split("::", limit = 2)
                headRaw = halves[0]
                tailRaw = halves[1]
            } else {
                headRaw = clean
                tailRaw = ""
            }
            val head = if (headRaw.isEmpty()) emptyList() else headRaw.split(":")
            val tail = if (tailRaw.isEmpty()) emptyList() else tailRaw.split(":")
            val present = head.size + tail.size
            val groups = when {
                compressed && present <= 7 -> head + List(8 - present) { "0" } + tail
                !compressed && present == 8 -> head + tail
                else -> return null
            }
            if (groups.any { it.isEmpty() || it.length > 4 || it.any { c -> c !in "0123456789abcdef" } }) return null
            return groups.joinToString(":") { it.padStart(4, '0') }
        }

        /** Accepts colon, hyphen and dotted spellings; emits canonical `aa:bb:cc:dd:ee:ff`. */
        private fun normalizeMac(raw: String?): String? {
            val hex = raw?.trim()?.lowercase()?.replace(Regex("[:.-]"), "") ?: return null
            if (hex.length != 12 || hex.any { it !in "0123456789abcdef" }) return null
            return hex.chunked(2).joinToString(":")
        }
    }
}

/** One provider's view of an asset within a single refresh. */
data class AssetObservation(
    val providerId: String,
    val instanceId: String,
    val resourceType: String,
    val resourceId: String,
    val name: String,
    val identity: AssetIdentity
) {
    val ref: String get() = "$providerId/$instanceId/$resourceId"

    companion object {
        fun from(resource: ProviderResource): AssetObservation = AssetObservation(
            providerId = resource.providerId,
            instanceId = resource.instanceId,
            resourceType = resource.resourceType,
            resourceId = resource.resourceId,
            name = resource.name,
            identity = AssetIdentity.from(resource)
        )
    }
}

/** A host correlated across one or more providers, always within a single tenant. */
data class CanonicalAsset(
    val key: String,
    val tenantRef: String,
    val displayName: String,
    val identity: AssetIdentity,
    val observations: List<AssetObservation>
) {
    val providerIds: Set<String> get() = observations.map { it.providerId }.toSet()
    val isCorrelated: Boolean get() = providerIds.size > 1
}

object CanonicalAssetResolver {

    /**
     * Groups [observations] into canonical assets for one tenant. Deterministic and order-independent:
     * two observations join when they share any strong token (serial, MAC, FQDN, cloud id) or at
     * least two weak tokens (short hostname, IPv4, IPv6). Observations with no identity signal each
     * stay their own asset.
     */
    fun resolve(tenantRef: String, observations: List<AssetObservation>): List<CanonicalAsset> {
        val ordered = observations.sortedBy { it.ref }
        val parent = IntArray(ordered.size) { it }

        fun find(node: Int): Int {
            var n = node
            while (parent[n] != n) {
                parent[n] = parent[parent[n]]
                n = parent[n]
            }
            return n
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra == rb) return
            if (ra < rb) parent[rb] = ra else parent[ra] = rb
        }

        for (i in ordered.indices) {
            val si = ordered[i].identity.strongTokens()
            val wi = ordered[i].identity.weakTokens()
            for (j in i + 1 until ordered.size) {
                val sj = ordered[j].identity.strongTokens()
                val strong = si.any { it in sj }
                val weak = if (strong) 0 else wi.count { it in ordered[j].identity.weakTokens() }
                if (strong || weak >= 2) union(i, j)
            }
        }

        val groups = LinkedHashMap<Int, MutableList<AssetObservation>>()
        for (i in ordered.indices) {
            groups.getOrPut(find(i)) { mutableListOf() }.add(ordered[i])
        }

        return groups.values.map { members ->
            val identity = members.fold(AssetIdentity()) { acc, obs -> acc.merge(obs.identity) }
            CanonicalAsset(
                key = keyFor(identity, members),
                tenantRef = tenantRef,
                displayName = displayNameFor(identity, members),
                identity = identity,
                observations = members.sortedBy { it.ref }
            )
        }.sortedBy { it.key }
    }

    private fun keyFor(identity: AssetIdentity, members: List<AssetObservation>): String = when {
        identity.serials.isNotEmpty() -> "serial:${identity.serials.min()}"
        identity.cloudIds.isNotEmpty() -> "cloud:${identity.cloudIds.min()}"
        identity.fqdns.isNotEmpty() -> "fqdn:${identity.fqdns.min()}"
        identity.macs.isNotEmpty() -> "mac:${identity.macs.min()}"
        else -> "obs:${members.map { it.ref }.sorted().joinToString("|")}"
    }

    private fun displayNameFor(identity: AssetIdentity, members: List<AssetObservation>): String =
        identity.fqdns.minOrNull()
            ?: identity.shortHostnames.minOrNull()
            ?: members.firstOrNull { it.name.isNotBlank() }?.name
            ?: members.first().ref
}
