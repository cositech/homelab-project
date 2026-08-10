# NetBox provider

- ID: `netbox`; delivery: direct or gateway; priority: P0; preferred canonical asset source.
- Auth: scoped API token; read-only initially.
- Capabilities: assets, inventory and search.
- Resources: tenant, site, location, rack, device, VM, interface, IP/prefix, circuit and tag.
- Correlation keys: stable NetBox IDs plus provider namespace; names alone are insufficient.
- Tests: pagination, custom fields, tenancy, permissions, deleted/stale objects and API-version compatibility.
