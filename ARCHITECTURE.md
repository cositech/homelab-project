# InfraHub Mobile architecture

This fork evolves Homelab Dashboard into a self-hosted mobile infrastructure operations platform while preserving both native clients and all upstream integrations.

## Principles

- Android and iOS remain native and versioned together.
- Providers expose normalized capabilities instead of leaking service-specific models into global views.
- NetBox-compatible asset identity is the preferred correlation layer; providers remain usable without NetBox.
- Credentials are referenced by opaque `credentialRef` values and stored in platform secure storage.
- TLS is secure by default. Custom CAs and certificate pins are explicit; insecure compatibility is visible, scoped, and auditable.
- Mutating operations pass through policy, confirmation, audit, and result reporting.
- Direct REST APIs remain first class; an optional self-hosted gateway can provide tenant isolation, push, correlation, and MCP/AIOC access.

## Logical model

```text
Android / iOS
  -> Provider Registry
     -> Health | Asset | Metrics | Alert | Incident | Ticket | Backup | Action | Search
        -> Direct API providers
        -> Optional Integration Gateway
           -> RBAC | tenant isolation | audit | secrets | push | MCP
```

## Core contracts

- `Provider`: identity, connection metadata, declared capabilities, health.
- `Resource`: normalized asset or operational object with provider provenance.
- `Event`: normalized alert, incident, audit, task, or state change.
- `Action`: typed request with risk class, confirmation policy, authorization, idempotency, and audit result.

JSON schemas under `schemas/` are the language-neutral source of truth. Platform implementations may add fields but must preserve schema compatibility.

## Delivery boundaries

Phase 0 changes governance, documentation, schemas, validation, and CI only. Runtime migrations start in Phase 1 and must be incremental so existing service dashboards remain functional.
