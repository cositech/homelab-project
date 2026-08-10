# Uptime Kuma provider

- ID: `uptime-kuma`; delivery: direct or gateway; priority: P0.
- Capabilities: health, metrics, alerts and incidents; read-only first.
- Resources: monitor, status page, heartbeat, incident and maintenance window.
- Correlation: monitor tags/URL/hostname map to canonical assets without treating display names as unique identifiers.
- Tests: supported API/socket versions, reconnect/backoff, missing heartbeats, maintenance suppression and secret redaction.
