# NetBox provider

- ID: `netbox`; delivery: direct or gateway; priority: P0; preferred canonical asset source.
- Auth: read-only v2 bearer token preferred; legacy v1 `Token` authentication remains compatible until NetBox 5 removes v1 tokens.
- Phase-2 capabilities: health, device/VM inventory and normalized search. No mutations are exposed.
- Fixed endpoints: `GET /api/status/`, `GET /api/dcim/devices/`, and `GET /api/virtualization/virtual-machines/`.
- Pagination is client-controlled with fixed offsets, 100 objects per page and a 500-object cap per resource class. Server-provided `next` URLs are never followed, preventing a compromised response from redirecting credentials.
- Device and VM requests add `exclude=config_context`; normalized assets retain stable NetBox IDs and selected site/role/tenant/cluster state but never configuration contexts or custom-field payloads.
- Future resources: tenant, site, location, rack, interface, IP/prefix, circuit and tag after dedicated fixture coverage.
- Tests: pagination boundaries, permissions, deleted/stale objects, v1/v2 token headers and API-version compatibility.
