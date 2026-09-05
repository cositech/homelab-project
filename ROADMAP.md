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
  - [ ] Proxmox VE non-lifecycle mutations — guest clone/migrate — plus a Proxmox Backup Server
    backup-job trigger (this one doesn't exist as a mutation on either client yet — PBS support is
    read-only today, so it needs new API client work, not just rewiring); these still call
    `proxmoxRepository` / the Proxmox API client directly — _in progress: VM/LXC snapshot
    create/delete/rollback, storage-content delete, firewall enable/disable and backup-job trigger
    are done — all four route through the coordinator on both clients, with fallback-replay
    suppression added on every endpoint touched (9 so far) and a confirm dialog for every
    destructive action that didn't have one before (snapshot delete/rollback, firewall disable —
    Android's firewall disable confirm is new UI added here; storage-content delete and iOS's
    firewall toggle already had a confirm dialog, just not routed through the coordinator; backup
    trigger needs none, see below). Risk: backup-job trigger and firewall enable are the "safe to
    repeat, no confirmation" end (low); snapshot create/delete are medium; snapshot rollback,
    storage-content delete and firewall disable are high/high/medium and require confirmation.
    Backup-job trigger and storage-content delete both wrap transport failures as non-retryable in
    their operation closures (neither is idempotent — a lost-response retry could fire a duplicate
    backup run or hit an already-removed volume); the firewall toggle is deliberately left
    retryable, since flipping a boolean option is naturally idempotent. Rewiring the iOS firewall
    toggle surfaced and fixed a real pre-existing bug: `toggleFirewall` took the already-desired new
    state into a parameter named `currentlyEnabled` and negated it, so confirming "Enable the
    firewall?" actually disabled it (and vice versa) — Android's equivalent was correct, this was
    iOS-only. The already-migrated lifecycle mutations (start/stop/shutdown/reboot) still lack
    fallback-replay suppression — a pre-existing gap, not something these slices introduced, left
    as further follow-up_
  - [x] Every other provider audited — the integrations without a controlled-action surface
    (Uptime Kuma, Gitea, OPNsense, Beszel, Maltrail, Jellystat, Plex, UniFi, TrueNAS, Wakapi and
    the Phase-2 read-only observability providers) expose no mutating endpoints in this app, so
    there is nothing further to migrate

Exit gate: policy and audit contract tests, one Android/iOS reference-provider migration, recovery tests, security invariants, CodeQL and dependency review pass. The framework, tests and audit script are in place and every provider except the remaining Proxmox items above routes its write surface through the coordinator; `scripts/phase3-controlled-actions-audit.sh` now asserts the Proxmox snapshot actions by name (not just one coordinator call per file) — the gate closes once the same is true for the rest of the Proxmox non-lifecycle mutations.

## Phase 4 — Correlation and MSP mode

See `docs/architecture/PHASE4_CORRELATION_MSP.md` for the design.

- [x] Canonical asset model: cross-provider identity resolution (hostname, IP, MAC, serial, cloud id) into stable asset keys, read-only
- [ ] Site and tenant contracts: `Tenant`, `Site`, `Customer` value objects; every provider instance, asset, health record, alert and action request carries a `tenantRef`
- [ ] Tenant-scoped storage and queries: operations snapshots, search, the Phase-3 audit ledger and durable action queue partition by tenant; no cross-tenant reads — _in progress: device-local `TenantSelection` + `TenantStore` (configured tenants, active selection, all-tenants mode) persisted on both clients; tenant-scoped read methods exist for the instance list, the audit ledger and the durable queue's pending-recovery set (`instancesForTenant`/`instances(tenantRef:)`, `auditSnapshot(tenantRef:)`, `pendingRecovery(tenantRef:)`); the Operations workspace and its search now consume the instance-list one — `buildSnapshot`/`performRefresh` scope to the active tenant (or fan out across all tenants) via the tenant switcher — but the audit ledger and durable queue readers still have no UI/workspace consumer_
- [x] Per-tenant credential isolation: the Phase-1 `credentialRef` indirection is kept but tenant-namespaced (migration re-keys existing references into the instance's own tenant); Keystore/Keychain entries — including the custom CA PEM carried in the same envelope — are never shared across tenants. (The certificate pin is plain, non-secret instance metadata and was already row/tenant-scoped.)
- [ ] Cross-provider correlation views: group health, alerts and assets by canonical asset and by site/customer; surface "same host, three providers" rollups — _in progress: a "By Asset" tab landed in the Operations workspace on both platforms — `CanonicalAssetResolver.resolveAcrossTenants` (partitions a mixed-tenant asset list by each instance's own tenant before resolving, so an all-tenants-mode refresh can never merge two tenants' hosts) wired into `buildSnapshot`/`performRefresh`; each `CanonicalAsset` card shows its member observations' live state and alert count (matched by exact provider/instance/resource ref), correlated (multi-provider) hosts sorted first. Still missing: the "by site/customer" rollup — `Site`/`Customer` remain inert value objects with no picker UI on either platform (tracked separately, out of scope for this slice)_
- [x] Tenant switcher and scoping UI on both clients; global workspace defaults to the active tenant, with an explicit all-tenants fan-out mode for multi-tenant installs. A Settings → Tenants screen (add/rename/delete/activate) and a tenant picker on the instance create/edit flow landed first; this closes the loop with a switcher in the global Operations workspace chrome (both platforms) that scopes health/alerts/assets/search to the active tenant, or fans out to every tenant when "All tenants" is selected — hidden entirely on single-tenant installs, same rule as every other Phase-4 tenant affordance
- [x] Policy extension: `ControlledActionPolicy` gains a tenant-membership check; an actor may only execute against instances in tenants they belong to
- [x] Migration and back-compat: existing single-tenant installs map to an implicit `default` tenant with no user-visible change. Verified end-to-end rather than added as a separate pass — every slice landed with the default baked in: the Room `tenantRef` column carries a SQL-level `DEFAULT 'default'` (migration 7→8), `ServiceInstance.init`/both Codable decoders on iOS apply `Tenant.refOrDefault` to an absent field, `TenantStore` starts from `TenantSelection.initial` (default tenant, active) when no selection is persisted, and the #66 credential migration physically re-keys and re-saves every instance on first launch, stamping the default tenant into the persisted record on both platforms

Exit gate: canonical-asset and tenant-isolation contract tests, a cross-provider correlation reference (one host seen by Proxmox + a monitor + a patch provider), Android/iOS compilation and unit tests, Phase-1 credential-isolation invariants, Phase-3 policy/audit invariants, CodeQL and dependency review pass. No cross-tenant data path may exist in storage, query, credential or action code.

## Phase 5 — Gateway and push

Optional self-hosted gateway for OAuth/OIDC, push fan-out, webhooks, rate limiting, audit export, MCP/AIOC and APIs not suitable for direct mobile access.

## Phase 6 — Distribution and operations

Signed release automation, SBOM and provenance, migration telemetry without sensitive data, backup/restore compatibility, runbooks and release channels.
