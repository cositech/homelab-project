# Provider specification: Proxmox Backup Server

- Provider ID: `proxmox-backup-server`.
- Domain and owner: backup infrastructure; Proxmox Server Solutions GmbH.
- Supported versions: current PBS 4.x API. PBS 3.x compatibility is plausible for the selected endpoints but remains unverified until fixture and live-appliance coverage is added.
- Delivery mode: direct HTTPS; the optional gateway remains possible for externally unreachable instances.
- Authentication and minimum scopes: API token only, formatted as `user@realm!token-name` plus token secret. Grant `Audit` on `/` and `DatastoreAudit` only on the datastores that the mobile client may observe. Privilege separation should remain enabled.
- TLS modes and local-network requirements: system trust, custom CA, certificate pinning and explicitly enabled insecure compatibility mode. Plain HTTP is rejected by the shared transport security boundary.
- Rate limits, pagination and timeouts: the initial calls are bounded status endpoints without client-side pagination; standard provider timeouts and primary/fallback endpoint selection apply.
- Declared capabilities: health, resources, events and metrics. `writeActions` is deliberately absent.
- Resource mappings: PBS datastore to normalized `datastore` asset; total, used and available bytes plus usage percentage are non-sensitive attributes.
- Event/severity mappings: maintenance mode is warning; capacity at 85 percent is warning; capacity at 95 percent is critical; failed authentication or connectivity produces unavailable provider health.
- Actions and risk classes: none in Phase 2. Backup mutation, prune, garbage collection, verification and restore are deferred to the Phase-3 controlled-action model.
- Tenant/site/customer scoping: one configured provider instance represents one PBS endpoint. Visibility is constrained by the API token ACLs.
- Sensitive fields and redaction: token secret is stored only in the platform secure credential store. Authorization headers and credential references must never enter normalized health, asset, alert, diagnostic or search state.
- Fixtures and negative tests: datastore decoding, capacity normalization, stored-value aliases, capability denial of write actions and static rejection of mutating HTTP methods.
- Operational dashboards and alerts: the global Operations workspace shows server health, version, datastore assets, maintenance and capacity alerts. Home cards show healthy versus visible datastores.
- Known limitations: no password/ticket authentication, task history, namespaces, snapshots, verification history, prune jobs, tape, remote or sync-job views in this first read-only slice.

## Read-only API surface

- `GET /api2/json/version`
- `GET /api2/json/status/datastore-usage`

Requests use `Authorization: PBSAPIToken=TOKENID:TOKENSECRET` and never place credentials in URLs or normalized provider records.
