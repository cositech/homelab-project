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
- [x] Remaining provider actions migrated by risk class
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
  - [x] Every remaining provider audited — the integrations without a controlled-action surface
    (Uptime Kuma, Gitea, OPNsense, Beszel, Maltrail, Jellystat, Plex, UniFi, TrueNAS, Wakapi and
    the Phase-2 read-only observability providers) expose no mutating endpoints in this app, so
    there is nothing further to migrate

Exit gate: policy and audit contract tests, one Android/iOS reference-provider migration, recovery tests, security invariants, CodeQL and dependency review pass. **Met** — every provider with a write surface routes through the coordinator, and both clients ship contract/failure-mapping tests plus `scripts/phase3-controlled-actions-audit.sh`.

## Phase 4 — Correlation and MSP mode

See `docs/architecture/PHASE4_CORRELATION_MSP.md` for the design.

- [ ] Canonical asset model: cross-provider identity resolution (hostname, IP, MAC, serial, cloud id) into stable asset keys, read-only
- [ ] Site and tenant contracts: `Tenant`, `Site`, `Customer` value objects; every provider instance, asset, health record, alert and action request carries a `tenantRef`
- [ ] Tenant-scoped storage and queries: operations snapshots, search, the Phase-3 audit ledger and durable action queue partition by tenant; no cross-tenant reads
- [ ] Per-tenant credential isolation: credential store keyed by `(tenantRef, instanceId)`; Keystore/Keychain entries and TLS trust never shared across tenants
- [ ] Cross-provider correlation views: group health, alerts and assets by canonical asset and by site/customer; surface "same host, three providers" rollups
- [ ] Tenant switcher and scoping UI on both clients; global workspace defaults to the active tenant, with an explicit all-tenants mode for single-tenant installs
- [ ] Policy extension: `ControlledActionPolicy` gains a tenant-membership check; an actor may only execute against instances in tenants they belong to
- [ ] Migration and back-compat: existing single-tenant installs map to an implicit `default` tenant with no user-visible change

Exit gate: canonical-asset and tenant-isolation contract tests, a cross-provider correlation reference (one host seen by Proxmox + a monitor + a patch provider), Android/iOS compilation and unit tests, Phase-1 credential-isolation invariants, Phase-3 policy/audit invariants, CodeQL and dependency review pass. No cross-tenant data path may exist in storage, query, credential or action code.

## Phase 5 — Gateway and push

Optional self-hosted gateway for OAuth/OIDC, push fan-out, webhooks, rate limiting, audit export, MCP/AIOC and APIs not suitable for direct mobile access.

## Phase 6 — Distribution and operations

Signed release automation, SBOM and provenance, migration telemetry without sensitive data, backup/restore compatibility, runbooks and release channels.
