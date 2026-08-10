# Integration Maturity Matrix

The full inventory is in `../../INTEGRATIONS.md` and
`integration-matrix.csv`.

Maturity states:

- **legacy** — existing upstream implementation
- **planned** — architecture/integration is catalogued
- **experimental**
- **beta**
- **stable**

Initial reference targets:

| Provider | Initial state | Phase-1 role |
|---|---|---|
| Proxmox VE | legacy | reference compute migration |
| Uptime Kuma | legacy | reference health migration |
| Gitea/Forgejo | legacy | reference devops migration |
| NetBox | planned | resource correlation / SOT |
| Zammad | planned | ticket capability |
| PegaProx | planned | RMM capability |
| Prometheus | planned | metrics aggregator |
| OneUptime | planned | incident/monitoring |
| OPNsense | planned | network provider |
