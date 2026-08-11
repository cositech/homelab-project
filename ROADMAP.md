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
- [ ] Prometheus and Grafana providers
- [ ] NetBox, Zammad and PegaProx providers
- [ ] OPNsense and OneUptime providers

Exit gate: operations contract tests, Android/iOS compilation and unit tests, security invariants, CodeQL and dependency review pass for every vertical Phase-2 delivery.

## Phase 3 — Controlled actions

Typed actions, risk classification, confirmation, RBAC, idempotency, immutable audit records, dry-run support, action queue and failure recovery.

## Phase 4 — Correlation and MSP mode

Sites, tenants, customers, canonical assets, cross-provider correlation, per-tenant credentials and strict tenant isolation.

## Phase 5 — Gateway and push

Optional self-hosted gateway for OAuth/OIDC, push fan-out, webhooks, rate limiting, audit export, MCP/AIOC and APIs not suitable for direct mobile access.

## Phase 6 — Distribution and operations

Signed release automation, SBOM and provenance, migration telemetry without sensitive data, backup/restore compatibility, runbooks and release channels.
