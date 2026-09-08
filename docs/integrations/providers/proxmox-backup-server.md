# Provider specification: Proxmox Backup Server

- Provider ID: `proxmox-backup-server`.
- Domain and owner: backup infrastructure; Proxmox Server Solutions GmbH.
- Supported versions: current PBS 4.x API. PBS 3.x compatibility is plausible for the selected endpoints but remains unverified until fixture and live-appliance coverage is added.
- Delivery mode: direct HTTPS; the optional gateway remains possible for externally unreachable instances.
- Authentication and minimum scopes: API token only, formatted as `user@realm!token-name` plus token secret. Grant `Audit` on `/` and `DatastoreAudit` on the datastores the mobile client may observe. Triggering a sync job (the one Phase-3 controlled action below) additionally requires `DatastoreBackup` or `Datastore.Modify` on that job's datastore; a token with `Audit`-only privileges can still be used for a read-only install and simply has the trigger rejected by PBS itself with an HTTP 403. Privilege separation should remain enabled.
- TLS modes and local-network requirements: system trust, custom CA, certificate pinning and explicitly enabled insecure compatibility mode. Plain HTTP is rejected by the shared transport security boundary.
- Rate limits, pagination and timeouts: the initial calls are bounded status endpoints without client-side pagination; standard provider timeouts and primary/fallback endpoint selection apply.
- Declared capabilities: health, resources, events, metrics and `writeActions` (as of the Phase-3 sync-job-trigger controlled action below; every other mutation - prune, garbage collection, verification, restore - remains deferred).
- Resource mappings: PBS datastore to normalized `datastore` asset; total, used and available bytes plus usage percentage are non-sensitive attributes.
- Event/severity mappings: maintenance mode is warning; capacity at 85 percent is warning; capacity at 95 percent is critical; failed authentication or connectivity produces unavailable provider health.
- Actions and risk classes: sync-job trigger (`sync-job.trigger`), a low-risk "run now" for a configured sync job, routed through the Phase-3 controlled-action coordinator with no confirmation prompt (matching the equivalent Proxmox VE backup-job-trigger precedent). Prune, garbage collection, verification and restore mutations remain deferred to a future slice.
- Tenant/site/customer scoping: one configured provider instance represents one PBS endpoint. Visibility is constrained by the API token ACLs.
- Sensitive fields and redaction: token secret is stored only in the platform secure credential store. Authorization headers and credential references must never enter normalized health, asset, alert, diagnostic or search state.
- Fixtures and negative tests: datastore and sync-job decoding, capacity normalization, stored-value aliases, and an audit assertion that the sync-job trigger is the *only* mutating call either client makes to PBS (`scripts/phase2-pbs-audit.sh`).
- Operational dashboards and alerts: the global Operations workspace shows server health, version, datastore assets, maintenance and capacity alerts. Home cards show healthy versus visible datastores. Tapping a PBS instance opens a dedicated Sync Jobs screen listing configured sync jobs with a "run now" trigger per job.
- Known limitations: no password/ticket authentication, task history, namespaces, snapshots, verification history, prune jobs, tape or remote-management views.

## API surface

Read-only:

- `GET /api2/json/version`
- `GET /api2/json/status/datastore-usage`
- `GET /api2/json/config/sync`

Mutating (Phase-3 controlled action, `sync-job.trigger`):

- `POST /api2/json/admin/sync/{id}/run` — a single attempt against the primary URL only (no fallback retry), since retrying an ambiguous failure could fire a second, overlapping sync run for the same job.

Requests use `Authorization: PBSAPIToken=TOKENID:TOKENSECRET` and never place credentials in URLs or normalized provider records.
