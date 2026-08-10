# Prometheus provider

- ID: `prometheus`; delivery: direct or gateway; priority: P0.
- Capabilities: metrics, health, alerts and search; exporters are aggregated here.
- Resources: target, series/query result, rule and alert state.
- Security: read-only credentials, query allow-list/limits in gateway mode, no arbitrary unbounded range queries.
- Tests: partial responses, NaN/Inf, cardinality/size limits, timeouts, label collision, tenancy headers and error redaction.
