# Architecture

## Objective

Evolve the archived native mobile application into a maintainable
infrastructure operations client that can support:

- homelab direct-to-service operation;
- professional multi-site/multi-customer operation;
- heterogeneous infrastructure providers;
- a central integration gateway without making it mandatory;
- deterministic REST/API workflows;
- MCP/AIOC-based AI and automation as an additional intelligence plane.

## Architectural principles

1. **Preserve native clients initially.**
   Jetpack Compose and SwiftUI remain the UI technologies through Phase 1.
2. **Provider logic is capability-driven.**
   UI features consume normalized contracts, not provider-specific DTOs.
3. **Read-only first.**
   Mutating actions require explicit capability declarations and policy.
4. **Secrets are not domain data.**
   Credentials must not be persisted in ordinary application databases.
5. **Direct and gateway mode share the same provider contracts.**
6. **NetBox-like source-of-truth correlation is separate from monitoring.**
7. **REST/WebSocket/SSE are deterministic UI transports.**
   MCP is the AI/tooling plane, not the primary mobile UI protocol.
8. **Every action is auditable.**
9. **Existing integrations are migrated incrementally; no big-bang rewrite.**
10. **Provider compatibility is testable with sanitized fixtures.**

## Logical architecture

```text
┌──────────────────────── Mobile UI ────────────────────────┐
│ Home │ Assets │ Incidents │ Operations │ AI │ More       │
└────────────────────────────┬───────────────────────────────┘
                             │
                    Feature / Use Cases
                             │
                   Normalized Domain Model
                             │
                Provider Capability Contracts
                             │
          ┌──────────────────┴───────────────────┐
          │                                      │
     Direct providers                      Gateway provider
          │                                      │
          ▼                                      ▼
  Service REST/API                         Integration Gateway
                                                  │
                                   ┌──────────────┼─────────────┐
                                   ▼              ▼             ▼
                               Providers         Events       MCP/AIOC
```

## Target domain model

```text
Organization
└── Site
    └── IntegrationInstance
        └── Resource
            ├── ResourceIdentity
            ├── Health
            ├── Metrics
            ├── Alerts
            ├── Relations
            ├── Documentation
            └── Actions
```

### Stable identity

Provider-local IDs are not sufficient. A resource receives:

- internal UUID;
- provider + instance + provider-local key;
- optional source-of-truth identity;
- aliases;
- tags;
- relations.

Example:

```text
Resource: fw01
├── netbox.device.id = 441
├── opnsense.host = fw01
├── prometheus.instance = 10.20.0.1:9100
├── uptime-kuma.monitor = 83
├── zammad.object-link = customer/site/fw01
└── tags = [customer:example, site:ffm, role:firewall]
```

## Capability contracts

Initial contract set:

- `HealthCapability`
- `MetricsCapability`
- `AlertCapability`
- `AssetCapability`
- `InventoryCapability`
- `IncidentCapability`
- `TicketCapability`
- `LogCapability`
- `BackupCapability`
- `TaskCapability`
- `DeploymentCapability`
- `SecurityCapability`
- `SearchCapability`
- `ActionCapability`
- `DocumentationCapability`
- `AICapability`

Capabilities are independent of UI route structure.

## Direct mode

Best for a home lab or small trusted environment.

```text
Mobile
  │
  ├── Proxmox API
  ├── Uptime Kuma API
  ├── NetBox API
  └── ...
```

Requirements:

- per-instance credential protection;
- TLS policy;
- local-only/VPN recommendations;
- no hidden insecure fallback;
- no silent certificate bypass.

## Gateway mode

Best for MSP, customer, multi-site and enterprise use.

```text
Mobile
   │ OIDC + short-lived token
   ▼
Integration Gateway
   ├── AuthN/AuthZ
   ├── RBAC/ABAC
   ├── provider registry
   ├── secret references
   ├── event normalization
   ├── audit
   ├── rate limits
   ├── cache
   ├── push
   └── action policy
```

Customer credentials remain server-side.

## MCP boundary

MCP is appropriate for:

- AI discovery;
- context acquisition;
- tool invocation;
- RAG/AIOC reasoning;
- controlled automation.

The mobile application must not depend on probabilistic tool selection for
normal navigation, status display or critical operator actions.

## Action model

Action risk levels:

| Level | Example | Required control |
|---|---|---|
| READ | metrics, inventory | RBAC |
| SAFE | restart service, pause monitor | confirmation + audit |
| ELEVATED | reboot VM, assign ticket | step-up auth + audit |
| CRITICAL | firewall mutation, host reboot, destructive backup action | step-up + explicit impact + optional approval |

Every action is represented as an idempotency-aware request with:

- actor;
- target;
- provider;
- action identifier;
- arguments;
- policy decision;
- confirmation state;
- result;
- correlation ID;
- audit timestamp.

## Event model

Normalized events decouple providers from mobile notifications and AI.

```text
Provider -> Normalizer -> Event
                       ├-> Push
                       ├-> Incident correlation
                       ├-> AIOC
                       ├-> Audit
                       └-> Automation
```

See `schemas/event.schema.json`.

## Migration approach

### Strangler migration

Do not refactor all existing services simultaneously.

1. introduce interfaces alongside current repositories;
2. adapt one representative provider (`Proxmox VE`);
3. adapt one health provider (`Uptime Kuma`);
4. adapt one support provider (`Zammad`, new);
5. validate domain contracts;
6. migrate remaining upstream integrations in batches.

This keeps the application usable throughout the transition.
