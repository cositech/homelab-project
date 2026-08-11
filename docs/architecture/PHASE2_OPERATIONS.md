# Phase 2 operations workspace

The first Phase-2 delivery adds a read-only cross-provider operations workspace to both native clients.

## Data boundary

The workspace consumes the Phase-1 provider contracts and stores one immutable snapshot containing provider health, normalized alerts, normalized assets and safe diagnostics. It never copies provider credentials, credential references, authorization headers or full endpoint query strings into operations state.

Diagnostics expose only scheme, host and explicit port. Search returns an empty result for blank queries instead of returning the complete snapshot implicitly.

## Current adapters

- Every configured provider contributes reachability health, a provider-instance asset and safe TLS/capability diagnostics.
- Proxmox VE additionally contributes nodes, virtual machines, containers and offline-node alerts.
- Uptime Kuma additionally contributes monitors, down/pending alerts and certificate-expiry warnings.
- Proxmox Backup Server additionally contributes read-only datastore capacity and maintenance state, with warning and critical capacity alerts.
- Prometheus additionally contributes active scrape-target health and active alerts from a fixed endpoint allow-list; arbitrary PromQL is excluded.
- Grafana additionally contributes a read-only dashboard and data-source inventory without copying data-source URLs or secure configuration.

The workspace is intentionally read-only. Mutating operations remain in the existing service dashboards until Phase 3 introduces policy, confirmation, RBAC and immutable audit records.

## Follow-up deliveries

NetBox, Zammad, PegaProx, OPNsense and OneUptime attach through the same snapshot boundary in subsequent Phase-2 pull requests. The Grafana legacy `/api` inventory endpoints require a planned migration path to `/apis` as Grafana 13 deprecation work progresses.
