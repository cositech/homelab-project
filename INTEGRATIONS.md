# Integration catalogue

The catalogue preserves all upstream dashboards and adds the infrastructure, operations and AI systems relevant to COStech. Priorities express delivery order, not product exclusion.

## Upstream baseline (preserve)

Portainer, Proxmox VE, TrueNAS Scale/Core, Uptime Kuma, Dockhand, DockMon, Komodo, Beszel, Linux Update Dashboard, Crafty Controller, Pterodactyl, Calagopus, Gitea/Forgejo, Pangolin/Newt, Healthchecks, PatchMon, Wakapi, Pi-hole, AdGuard Home, UniFi, Technitium DNS, Maltrail, Nginx Proxy Manager/NPMplus, Plex, Jellystat, Sonarr, Radarr, Lidarr, Prowlarr, qBittorrent, Jellyseerr, Bazarr, Gluetun and FlareSolverr.

## COStech expansion catalogue

| Domain | P0/P1 providers | Later/direct or aggregated candidates |
|---|---|---|
| Compute | Proxmox VE, PBS, Ceph, Linux, Docker | PMG, ZFS, LXC, VMware, Hyper-V, Incus |
| Kubernetes | Kubernetes/RKE2, Rancher, Longhorn | MicroK8s, K3s, Argo CD, Flux, Helm |
| Network | OPNsense, WireGuard, NetBox, Palo Alto, GlobalProtect | FRR/OSPF/BGP, Oxidized, SNMP, Arista, MikroTik, Tailscale, Cloudflare |
| Monitoring | Prometheus, Alertmanager, Grafana, Uptime Kuma, OneUptime, Centreon, Graylog | Loki, OpenSearch, OpenObserve, Tempo, Jaeger, OpenTelemetry, Blackbox/SNMP/Node/DCGM exporters |
| Support/RMM | Zammad, PegaProx | FileWave, Cynet, MeshCentral, Tactical RMM |
| Identity/security | Authentik, Entra ID, Defender, OPNsense, Palo Alto | Keycloak, CrowdSec, Wazuh, Vault, step-ca |
| Inventory/docs | NetBox, Outline, Paperless-ngx | Snipe-IT, Nautobot, BookStack, Wiki.js |
| Work/dev | OpenProject, Gitea/Forgejo, GitHub, Dokploy | GitLab, Woodpecker, Jenkins, Harbor, Renovate |
| Backup/storage | PBS, S3, Hetzner Storage Box | Restic, Borg, Kopia, MinIO, Ceph RGW, Veeam |
| Microsoft | M365 service health, Entra, Intune, Exchange, Teams, Defender | Graph-backed provider via gateway |
| AI platform | COStech Platform API, AIOC, MCP Gateway, LiteLLM, Ollama, Qdrant | OpenWebUI, PostgreSQL, Redis, RAG pipelines, model/GPU telemetry |
| Facilities | UPS/PDU, temperature/environment | NUT, APC, Eaton, Modbus, Redfish, IPMI |

Exporters are normally consumed through Prometheus rather than implemented as separate mobile dashboards. Microsoft and other broad OAuth APIs should normally be brokered through the optional gateway.

## Provider delivery rules

Every provider defines auth type, TLS support, capabilities, normalized resource/event mappings, pagination/rate limits, read/write scopes, minimum tested versions, error/redaction behavior and fixtures. Read-only delivery precedes actions. Remote shell and arbitrary script execution are out of scope until the action policy and audit pipeline exist.
