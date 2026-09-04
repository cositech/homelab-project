# Phase 4 — Correlation and MSP mode

Phase 4 makes the app usable for managing more than one estate from one install: a
home lab plus a friend's, or an MSP operator running several customers. It adds a
tenant boundary that every existing subsystem — instances, credentials, operations
snapshots, search, the Phase‑3 audit ledger and durable action queue — must
respect, and a canonical asset model that lets the same physical host seen through
several providers collapse into one row.

Nothing in Phase 4 adds new provider integrations or new mutations. It is a
horizontal concern layered under Phases 1–3.

## Invariants

1. Every provider instance belongs to exactly one tenant. An instance with no
   explicit tenant belongs to the implicit `default` tenant.
2. Every derived record — `ProviderHealth`, `ProviderEvent`, `ProviderResource`,
   `ProviderDiagnostic`, operations snapshots, search results, `ActionAuditRecord`
   and `DurableActionQueueEntry` — carries the `tenantRef` of its originating
   instance and is only ever read back within that tenant.
3. Credential references are tenant‑scoped. The Phase‑1 indirection is kept — the
   Keystore/Keychain entry is still addressed by the instance's random
   `credentialRef` — but that reference is namespaced by `tenantRef`, so a
   Keystore/Keychain entry, TLS trust anchor or pinned certificate is never shared
   across tenants even for byte‑identical secrets.
4. There is no query, storage key, cache key or code path that returns data from
   more than one tenant unless the caller is explicitly in all‑tenants mode and the
   result is labeled per tenant.
5. Canonical asset resolution is read‑only and advisory. It never rewrites provider
   data, never merges records across tenants, and a wrong match degrades to
   "two assets" rather than leaking one tenant's host into another.
6. `ControlledActionPolicy` gains a membership gate: an actor may only execute an
   action whose target instance is in a tenant the actor belongs to. This is a
   local convenience check, not a trust boundary — a gateway deployment still
   enforces tenant authorization server‑side.
7. A single‑tenant install (one implicit `default` tenant) shows no new UI surface
   and behaves exactly as before Phase 4.
8. Switching the active tenant never carries state — in‑flight fetches, search
   text, selected filters and per‑instance view state are scoped to the tenant and
   reset on switch.

## Canonical asset model

A `CanonicalAsset` is a stable key plus the set of `(providerId, instanceId,
resourceId)` observations that resolved to it. Resolution runs over the identity
fields the providers already expose. Each observed hostname is kept in two forms:
a normalized FQDN (lowercased, trailing dot stripped, left intact) and a derived
short hostname (the first label). Together with IPv4 and IPv6 addresses, MAC
addresses, hardware serial, and provider‑native cloud or cluster ids, they carry a
fixed precedence: serial and MAC are strong; an exact normalized‑FQDN match or a
stable cloud id is strong; the short hostname alone or a private IP alone is weak
and only matches when a second field corroborates it. `host.site-a` and
`host.site-b` therefore stay distinct on the FQDN even though their short form
collides.

Resolution is deterministic and pure: same observations in, same asset keys out,
no clock or network dependency, so it is unit‑testable the way the Phase‑3 policy
is. Assets are recomputed from the current operations snapshot on each refresh;
there is no long‑lived asset store to drift or require migration.

The canonical asset never crosses a tenant boundary — observations are grouped by
tenant first, then resolved within each tenant.

## Tenant and site model

Three value objects, all `tenantRef`‑anchored:

- `Tenant` — the isolation unit. `id`, display name, and a `kind` of `personal`
  or `customer`. The `default` tenant is `personal` and cannot be deleted.
- `Site` — a physical or logical location within a tenant (a rack, a home, a
  branch office). Optional; instances may reference a `siteRef`.
- `Customer` — MSP‑facing metadata on a `customer` tenant (account name, contact,
  notes). Never contains a secret; PII stays out of the audit ledger and durable
  queue exactly as in Phase 3.

`ServiceInstance` gains `tenantRef` (required, defaults to `default` on migration)
and optional `siteRef`. Existing instance fields are unchanged.

## Storage and isolation

Android partitions by tenant with a key prefix on the Preferences DataStore
entries and a `tenant_ref` column on the Room `service_instance` table plus a
non‑null index; the Phase‑3 `controlled_action_queue_v1` payload and the audit
ledger snapshot are stored per tenant. iOS applies the same partitioning to its
`UserDefaults` Codable payloads and Keychain query attributes
(`kSecAttrService` gains the tenant id).

The credential store keeps its Phase‑1 shape — lookups are by the instance's
random `credentialRef`, not by `instanceId` — but that reference is now
tenant‑namespaced (`credential:v2:<tenantRef>:<random>` on Android;
`kSecAttrService` gains the tenant id on iOS). Migration re‑keys every existing
`credentialRef` into the `default` tenant, updating the persisted
`ServiceInstance.credentialRef` in the same transaction; a failure to re‑key an
entry fails closed (the instance is shown as unconfigured) rather than falling
back to an untenanted read.

Every repository query that today takes an `instanceId` or a `ServiceType` gains a
`tenantRef` (or reads the active tenant from a scope holder). The operations
workspace, global search and the audit/queue readers filter on it. All‑tenants
mode is an explicit flag that fans out per tenant and tags each result group.

## Correlation views

The operations workspace gains a "by asset" grouping alongside the existing "by
provider" view: each `CanonicalAsset` renders its worst rolled‑up health, the
providers that observe it, and its open alerts de‑duplicated across those
providers. An MSP install also gets a "by customer" rollup — per‑tenant health
and alert counts on one screen — reachable only in all‑tenants mode.

Correlation is presentation only. It reads the same snapshot the provider views
read; it issues no extra requests and holds no extra state.

## UI

Both clients get a tenant switcher in the global chrome (a menu on the operations
workspace and settings). The switcher is hidden when only the `default` tenant
exists. Selecting a tenant scopes every subsequent screen; an "All tenants" entry
enables the fan‑out mode. Instance create/edit gains a tenant (and optional site)
picker, defaulting to the active tenant. A `customer` tenant's settings screen
edits the `Customer` metadata.

## Policy

`ControlledActionPolicy.evaluate` takes the actor's tenant membership set and the
target instance's `tenantRef`. A non‑member actor gets `DENIED` with reason
`tenant-membership-required`, evaluated before the existing role and capability
checks. The `ControlledActionRequest` already has a `tenantRef` field (unused
since Phase 3) — Phase 4 populates it from the target instance and the coordinator
persists it in the audit record.

## Migration and back‑compat

On first launch after the Phase‑4 update: create the `default` tenant, stamp every
existing `ServiceInstance`, credential entry, queued action and audit record with
`tenantRef = "default"`, and set it active. No screen changes for a user who never
adds a second tenant. The migration is idempotent and reversible by data export
from Phase 6; it never deletes or rewrites secret material, only re‑keys the
lookup.

## Non‑goals for Phase 4

Server‑side authorization, cross‑device tenant sync, billing, and per‑tenant
push routing are Phase 5 (gateway) concerns. Phase 4 is entirely local to the
device.
