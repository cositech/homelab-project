# Phase 0 / Phase 1 Epic Structure

## EPIC-00 — Fork and governance

- preserve fork/history
- establish branch protection
- contribution/security policy
- define product naming decision
- retain Apache-2.0 attribution

## EPIC-01 — CI and build reproducibility

- execute Android tests
- execute iOS tests
- deterministic simulator selection
- Gradle wrapper validation
- dependency review
- CodeQL
- lock Ruby tooling

## EPIC-02 — Mobile secret storage

- Android credential extraction from Room
- credentialRef model
- Keystore implementation
- migration fixtures/tests
- iOS config/secret split
- backup/export compatibility

## EPIC-03 — TLS hardening

- trust profile model
- custom CA
- certificate pin option
- explicit HTTP compatibility
- remove silent trust-all behavior
- remove broad iOS ATS exceptions where possible

## EPIC-04 — Provider/capability core

- capability interfaces
- provider registry
- provider metadata/specs
- normalized errors
- health model
- action model

## EPIC-05 — Resource identity and tenancy

- Organization
- Site
- IntegrationInstance
- ResourceIdentity
- aliases/relations/tags
- migration from current multi-instance data

## EPIC-06 — Reference-provider migrations

- Proxmox VE
- Uptime Kuma
- Gitea/Forgejo
- compatibility fixtures
- cross-platform parity

## EPIC-07 — Unified search and correlated asset view

- search index contract
- provider search fan-out
- resource relations
- source-of-truth links

## EPIC-08 — First COStech provider wave

- PBS
- NetBox
- Prometheus
- Grafana
- Zammad
- PegaProx
- OneUptime
- OPNsense
- WireGuard

## EPIC-09 — Action policy and audit

- risk levels
- confirmation
- biometric step-up
- idempotency/correlation IDs
- direct-mode local audit
- gateway policy contract

## EPIC-10 — Gateway/AIOC contracts (design only until later phase)

- REST contract
- event envelope
- gateway auth boundary
- MCP boundary
- evidence model
