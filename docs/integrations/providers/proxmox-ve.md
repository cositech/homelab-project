# Proxmox VE provider

- ID: `proxmox-ve`; delivery: direct or gateway; priority: P0.
- Auth: API token preferred, classic ticket only where console requires it; least-privilege roles documented per capability.
- Capabilities: health, assets, metrics, alerts, backups, search and controlled actions.
- Resources: cluster, node, VM, LXC, storage, task, HA resource, Ceph object.
- Actions: start/stop/reboot (medium), migrate/restore/config mutation (high), delete (critical).
- Tests: token/ticket auth, 2FA limitation, pagination, task polling, permission errors, redaction and TLS modes.
