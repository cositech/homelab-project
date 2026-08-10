# Capabilities

Capabilities are small domain contracts that can be composed.

## HealthCapability

Returns normalized resource/service health.

States:

```text
healthy | degraded | unavailable | maintenance | unknown
```

## MetricsCapability

Exposes named time-series/current metrics with units, timestamps and source.

## AlertCapability

Exposes source alerts without assuming they are already incidents.

## IncidentCapability

Represents correlated operational impact.

## AssetCapability / InventoryCapability

Exposes resources and their provider-local attributes.

## TicketCapability

Support/work-management objects, e.g. Zammad.

## BackupCapability

Backups, snapshots, verification, prune/GC and restore-test evidence.

## SearchCapability

Returns typed resource hits; global search combines all enabled providers.

## ActionCapability

Declares operator actions with risk, input schema and confirmation semantics.

## DocumentationCapability

Links documents/knowledge entries to resources.

## AICapability

Provides structured, evidence-bearing AI operations. It never bypasses action
policy.
