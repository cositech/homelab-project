# Roadmap

## Phase 0 — Fork foundation

- [x] Upstream baseline, architecture, integration catalogue, security and dependency audits
- [x] Cross-platform contracts and initial provider specifications
- [x] CI with tests, builds, schema validation, dependency review, CodeQL, and Dependabot
- [x] Contribution, security, issue, PR, ADR, and validation workflows
- [x] GitHub branch protection and required checks enabled by repository owner

Exit gate: static audit passes and GitHub Actions prove Android/iOS tests and builds on the fork.

## Phase 1 — Secure capability core

- [x] Android Keystore-backed credential store and Room migration to `credentialRef`
- [x] iOS credential envelopes in Keychain with metadata separated from secrets
- [x] Explicit TLS modes: `SYSTEM`, `CUSTOM_CA`, `CERTIFICATE_PIN`, `INSECURE_COMPATIBILITY`
- [x] Provider registry, capability discovery, normalized health/resource/event models
- [x] Proxmox VE and Uptime Kuma reference migrations without feature regression

Exit gate: Phase 1 security invariants, Android/iOS compilation and unit tests, CodeQL, and dependency review pass on the delivery pull request.

## Phase 2 — Operations views

- [x] Cross-platform normalized operations snapshots for health, alerts, assets, search and diagnostics
- [x] Global Android and iOS operations workspaces with refresh and empty/error states
- [x] Read-only Proxmox VE and Uptime Kuma resource/alert aggregation
- [x] Proxmox Backup Server provider
- [x] Prometheus and Grafana providers
- [x] NetBox, Zammad and PegaProx providers
- [x] OPNsense and OneUptime providers

Exit gate: operations contract tests, Android/iOS compilation and unit tests, security invariants, CodeQL and dependency review pass for every vertical Phase-2 delivery.

## Phase 3 — Controlled actions

- [x] Cross-platform typed request, risk, role and policy contracts
- [x] Explicit confirmation, dry-run and provider write-capability gates
- [x] Serialized execution, idempotency and bounded append-only mobile audit history
- [x] Proxmox VE guest lifecycle reference migration
- [x] Durable queue recovery and retry policy
- [ ] Remaining provider actions migrated by risk class
  - [x] Portainer container lifecycle and removal
  - [x] Portainer container rename and stack Compose updates
  - [x] Healthchecks check lifecycle, creation, editing and integration channels
  - [x] AdGuard Home protection enable, disable and timed pause
  - [x] AdGuard Home filter lists, user rules, blocked services and DNS rewrites
  - [x] Pi-hole allow and deny domain list mutations
  - [x] Technitium DNS blocking, blocklist refresh and blocked-domain mutations
  - [x] Linux Update checks, cache refresh, package/system upgrades and reboot actions
  - [x] Dockhand container and stack lifecycle actions
  - [x] DockMon container restart and image update actions
  - [x] Komodo stack deploy, start, stop and restart actions
  - [x] Pterodactyl and Calagopus game-server power actions
  - [x] Nginx Proxy Manager proxy-host lifecycle actions
  - [x] Nginx Proxy Manager redirection, stream, dead-host, certificate, access-list and user actions
  - [x] Crafty Controller lifecycle, executable update, backup and console-command actions
  - [x] Pangolin public/private resource and target configuration actions
  - [x] qBittorrent torrent and transfer lifecycle actions
  - [x] PatchMon monitored-host removal
  - [x] Radarr, Sonarr, Lidarr, Jellyseerr, Prowlarr, Gluetun and FlareSolverr media-service actions
  - [ ] Proxmox VE non-lifecycle mutations — every one now routes through the coordinator on both
    clients (snapshot create/delete/rollback, storage-content delete, firewall enable/disable,
    backup-job trigger, guest clone/migrate — the last one across both its call sites, including
    the easy-to-miss "deploy from template" flow in iOS's `ProxmoxDashboard.swift`), each with its
    own risk tier (low: backup-job trigger, firewall enable; medium: snapshot create/delete, clone;
    high: snapshot rollback, storage-content delete, firewall disable, migrate) and fallback-replay
    suppression added to every endpoint touched (13 total). Only a Proxmox Backup Server
    backup-job-trigger mutation remains, and it doesn't exist as a mutation on either client yet —
    PBS support is read-only today, so closing this needs new API client work, not just rewiring.
    The already-migrated lifecycle mutations (start/stop/shutdown/reboot) still lack fallback-replay
    suppression, a separate pre-existing gap not introduced by these slices. Full history of what
    landed in each slice (including two real pre-existing bugs these migrations surfaced and fixed
    along the way) is in the `phase3-proxmox-gap` memory, not repeated here.
  - [x] Every other provider audited — the integrations without a controlled-action surface
    (Uptime Kuma, Gitea, OPNsense, Beszel, Maltrail, Jellystat, Plex, UniFi, TrueNAS, Wakapi and
    the Phase-2 read-only observability providers) expose no mutating endpoints in this app, so
    there is nothing further to migrate

Exit gate: policy and audit contract tests, one Android/iOS reference-provider migration, recovery tests, security invariants, CodeQL and dependency review pass. The framework, tests and audit script are in place, and every provider's write surface now routes through the coordinator except the one Proxmox Backup Server backup-job-trigger mutation, which doesn't exist as a mutation on either client yet; `scripts/phase3-controlled-actions-audit.sh` asserts every migrated Proxmox action group by name (not just one coordinator call per file) — the gate closes once the PBS mutation is built and routed the same way.

## Phase 4 — Correlation and MSP mode

See `docs/architecture/PHASE4_CORRELATION_MSP.md` for the design.

- [x] Canonical asset model: cross-provider identity resolution (hostname, IP, MAC, serial, cloud id) into stable asset keys, read-only
- [x] Site and tenant contracts: `Tenant`, `Site`, `Customer` value objects; every provider instance, asset, health record, alert and action request carries a `tenantRef`. `ServiceInstance.tenantRef`, `ControlledActionRequest.tenantRef` and `ActionAuditRecord.tenantRef` are literal fields; `ProviderHealth`/`ProviderEvent`/`ProviderResource`/`ProviderDiagnostic` (the health-record/alert/asset models) are not, and were audited to confirm that's sound rather than an oversight — every all-tenants fan-out point (`buildSnapshot`/`performRefresh`, the By Asset/By Site/By Customer tabs, search, Action History) either reads a self-tagged record or resolves tenant identity from a same-refresh `instanceId → tenantRef` map built from the same instance list already in memory (the same mechanism `CanonicalAsset.tenantRef` already used since #72), and `resolveAcrossTenants` partitions by tenant *before* correlating so two tenants' hosts can never merge. No path was found, on either platform, where a health/alert/asset record ends up detached from which tenant it came from — tenant identity carries through by construction, just not as a redundant stored field on every record
- [x] Tenant-scoped storage and queries: operations snapshots, search, the Phase-3 audit ledger and durable action queue partition by tenant; no cross-tenant reads. Device-local `TenantSelection` + `TenantStore` (configured tenants, active selection, all-tenants mode) persisted on both clients; tenant-scoped read methods exist for the instance list, the audit ledger and the durable queue's pending-recovery set (`instancesForTenant`/`instances(tenantRef:)`, `auditSnapshot(tenantRef:)`, `pendingRecovery(tenantRef:)`). The Operations workspace and its search consume the instance-list one — `buildSnapshot`/`performRefresh` scope to the active tenant (or fan out across all tenants) via the tenant switcher. The audit ledger and durable queue readers now have a UI consumer too: a read-only "Action History" screen under Settings on both clients (History / Pending tabs), scoped the same way — active tenant, or every tenant's records in all-tenants mode
- [x] Per-tenant credential isolation: the Phase-1 `credentialRef` indirection is kept but tenant-namespaced (migration re-keys existing references into the instance's own tenant); Keystore/Keychain entries — including the custom CA PEM carried in the same envelope — are never shared across tenants. (The certificate pin is plain, non-secret instance metadata and was already row/tenant-scoped.)
- [x] Cross-provider correlation views: group health, alerts and assets by canonical asset and by site/customer; surface "same host, three providers" rollups. A "By Asset" tab in the Operations workspace on both platforms — `CanonicalAssetResolver.resolveAcrossTenants` (partitions a mixed-tenant asset list by each instance's own tenant before resolving, so an all-tenants-mode refresh can never merge two tenants' hosts) wired into `buildSnapshot`/`performRefresh`; each `CanonicalAsset` card shows its member observations' live state and alert count (matched by exact provider/instance/resource ref), correlated (multi-provider) hosts sorted first. A "By Site" tab sits alongside it: the same correlated assets regrouped by the `Site` assigned to any of a canonical asset's member instances (an asset merging observations across providers is assumed to sit at one physical site), keyed by tenant plus site (not site alone) so two tenants naming a site the same thing never render as one indistinguishable group, with an "unassigned" group for anything with no site set. `Customer` metadata (account name, contact, notes) is editable from a "Customer Info" button on any customer-kind tenant's row in Settings → Tenants, backed by its own `CustomerRegistry`/`CustomerStore` (a 1:1-per-tenant registry, unlike `Site`'s 1:many one) mirroring the same device-local persistence pattern as `TenantStore`/`SiteStore`; saving with a blank account name clears the record. Per `docs/architecture/PHASE4_CORRELATION_MSP.md`'s "by customer" spec, a "By Customer" tab — reachable only in all-tenants mode, hidden otherwise — rounds this out: one summary card per configured tenant with its per-tenant health-state counts (healthy/degraded/unavailable) and open-alert count, the Customer account name shown as a subtitle when set, reading the exact same snapshot every other correlation tab reads (no extra requests, no extra state, per the design doc's "presentation only" rule).
- [x] Tenant switcher and scoping UI on both clients; global workspace defaults to the active tenant, with an explicit all-tenants fan-out mode for multi-tenant installs. A Settings → Tenants screen (add/rename/delete/activate) and a tenant picker on the instance create/edit flow landed first; this closes the loop with a switcher in the global Operations workspace chrome (both platforms) that scopes health/alerts/assets/search to the active tenant, or fans out to every tenant when "All tenants" is selected — hidden entirely on single-tenant installs, same rule as every other Phase-4 tenant affordance
- [x] Policy extension: `ControlledActionPolicy` gains a tenant-membership check; an actor may only execute against instances in tenants they belong to
- [x] Migration and back-compat: existing single-tenant installs map to an implicit `default` tenant with no user-visible change. Verified end-to-end rather than added as a separate pass — every slice landed with the default baked in: the Room `tenantRef` column carries a SQL-level `DEFAULT 'default'` (migration 7→8), `ServiceInstance.init`/both Codable decoders on iOS apply `Tenant.refOrDefault` to an absent field, `TenantStore` starts from `TenantSelection.initial` (default tenant, active) when no selection is persisted, and the #66 credential migration physically re-keys and re-saves every instance on first launch, stamping the default tenant into the persisted record on both platforms

Exit gate: canonical-asset and tenant-isolation contract tests, a cross-provider correlation reference (one host seen by Proxmox + a monitor + a patch provider), Android/iOS compilation and unit tests, Phase-1 credential-isolation invariants, Phase-3 policy/audit invariants, CodeQL and dependency review pass. No cross-tenant data path may exist in storage, query, credential or action code.

## Phase 5 — Gateway and push

Optional self-hosted gateway for OAuth/OIDC, push fan-out, webhooks, rate limiting, audit export, MCP/AIOC and APIs not suitable for direct mobile access.

## Phase 6 — Distribution and operations

Signed release automation, SBOM and provenance, migration telemetry without sensitive data, backup/restore compatibility, runbooks and release channels.
