# PegaProx provider

- ID: `pegaprox`; delivery: direct or gateway; priority: P0.
- Product boundary: PegaProx is a multi-cluster management platform for Proxmox VE and XCP-ng, not an RMM inventory system. The previous customer/device/patch model is retired.
- Auth: restricted `pgx_` API token using bearer authentication. The token role is evaluated server-side and cluster access is tenant/RBAC filtered by PegaProx.
- Phase-2 capabilities: health, tenant-visible clusters, VM/container assets, metrics summary and active alerts. No write endpoint is called.
- Fixed endpoints: `GET /api/clusters`, then only validated cluster IDs are used with `GET /api/clusters/{id}/health`, `/resources`, and `/active-alerts`.
- Security: the clients never broaden the cluster list, accept only conservative cluster-ID path segments, cap cluster/resources/alerts, and exclude console, SSH, VNC, shell and remote-session URLs.
- Future actions: VM power, migration, patching, scripts and alert acknowledgement only after Phase-3 risk policy, confirmation and immutable audit records.
- Tests: foreign-tenant negative queries, token-role downgrades, cluster/VM ACLs, path validation, response caps and redaction.
