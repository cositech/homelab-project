# Provider specification: Prometheus

- Provider ID: `prometheus`.
- Domain and owner: metrics and alerting; Prometheus project under the CNCF.
- Supported versions: Prometheus releases exposing the stable HTTP API below. Compatibility is validated by fixtures and CI; live-appliance coverage remains a release gate for version claims.
- Delivery mode: direct HTTP(S), typically on a trusted local network or behind an authenticating reverse proxy. Gateway delivery remains possible later.
- Authentication and minimum scopes: no credential for a trusted unauthenticated endpoint, or an optional read-only Bearer token supplied by a reverse proxy. Native Prometheus does not define API authorization scopes.
- TLS modes: system trust, custom CA, certificate pinning and explicitly enabled insecure compatibility mode. Plain HTTP remains subject to the shared transport policy.
- Declared capabilities: health, resources, events and metrics. Read and write actions are absent.
- Resource mappings: every active scrape target becomes a normalized `scrape-target` asset containing only job, instance, health and last-scrape metadata. The scrape URL and last error are deliberately excluded from operations attributes.
- Event/severity mappings: unhealthy targets are critical; firing alerts are critical; non-firing active alerts and API warnings are degraded/warning conditions.
- Actions and risk classes: none in Phase 2. Arbitrary PromQL, rule changes, target mutation and lifecycle endpoints are not exposed.
- Sensitive fields and redaction: Bearer tokens remain in the platform secure credential store. Authorization headers, scrape URLs, full label sets and credential references never enter normalized operations state.
- Operational dashboards: Operations shows provider health, active targets and active alerts. Home shows healthy versus visible targets.
- Known limitations: no arbitrary instant/range queries, series discovery, metadata, rules inventory, remote-write status or Alertmanager API in this first slice.

## Read-only API surface

- `GET /api/v1/status/buildinfo`
- `GET /api/v1/targets?state=active`
- `GET /api/v1/alerts`

The fixed endpoint allow-list bounds response shape and prevents user-controlled high-cardinality or expensive PromQL execution.
