# Integration Catalogue

This catalogue contains the **final 34 integrations advertised by the archived
upstream README** plus additional source-observed/legacy and target-environment
systems that must be considered by the fork.

`P0/P1/P2` expresses implementation order, **not** whether every listed system
is currently deployed.

## Design rule

Exporters/telemetry endpoints normally **do not** receive a first-class mobile
screen. They feed an aggregator such as Prometheus unless direct control is
required.

Likewise, a system marked `gateway` should generally not place high-privilege
customer credentials on a phone.

## Upstream reconciliation

The final upstream README contains exactly **34 integrated service dashboards**.
The matrix marks those entries with `upstream=yes`.

Other source-observed or previously relevant components remain in the catalogue
with `upstream=no`; this avoids incorrectly calling them one of the final 34
while still keeping them visible for migration/compatibility review.

## Matrix

| Integration | Category | Final upstream 34 | Priority | Preferred strategy | Capabilities |
|---|---|---:|---:|---|---|
| Proxmox VE | Compute | yes | P0 | migrate | health,inventory,metrics,search |
| Portainer | Containers | yes | P2 | migrate | provider-specific |
| TrueNAS | Storage | yes | P2 | migrate | provider-specific |
| Uptime Kuma | Monitoring | yes | P0 | migrate | health,inventory,metrics,search |
| Dockhand | Containers | yes | P2 | migrate | provider-specific |
| DockMon | Containers | yes | P2 | migrate | provider-specific |
| Beszel | Monitoring | yes | P2 | migrate | provider-specific |
| Komodo | Containers | yes | P2 | migrate | provider-specific |
| Linux Update | Operations | yes | P2 | migrate | provider-specific |
| Gitea / Forgejo | DevOps | yes | P0 | migrate | health,inventory,metrics,search |
| Healthchecks | Monitoring | yes | P2 | migrate | provider-specific |
| PatchMon | Operations | yes | P2 | migrate | provider-specific |
| Pi-hole | Network | yes | P2 | migrate | provider-specific |
| AdGuard Home | Network | yes | P2 | migrate | provider-specific |
| Ubiquiti / UniFi | Network | yes | P2 | migrate | provider-specific |
| Technitium DNS | Network | yes | P2 | migrate | provider-specific |
| Maltrail | Security | yes | P2 | migrate | provider-specific |
| Nginx Proxy Manager / NPMplus | Network | yes | P2 | migrate | provider-specific |
| Plex | Media | yes | P2 | migrate | provider-specific |
| Jellyfin | Media | no | P2 | migrate | provider-specific |
| Sonarr | Media | yes | P2 | migrate | provider-specific |
| Radarr | Media | yes | P2 | migrate | provider-specific |
| Lidarr | Media | yes | P2 | migrate | provider-specific |
| Prowlarr | Media | yes | P2 | migrate | provider-specific |
| qBittorrent | Media | yes | P2 | migrate | provider-specific |
| Jellyseerr | Media | yes | P2 | migrate | provider-specific |
| Bazarr | Media | yes | P2 | migrate | provider-specific |
| Gluetun | Network | yes | P2 | migrate | provider-specific |
| FlareSolverr | Utility | yes | P2 | migrate | provider-specific |
| Crafty Controller | Gaming | yes | P2 | migrate | provider-specific |
| RomM | Gaming | no | P2 | migrate | provider-specific |
| MineOS | Gaming | no | P2 | migrate | provider-specific |
| Steam | Gaming | no | P2 | migrate | provider-specific |
| Proxmox Backup Server | Backup | no | P0 | direct+gateway | health,backup,metrics,alerts,actions |
| Proxmox Mail Gateway | Messaging/Security | no | P1 | direct+gateway | health,metrics,alerts,search |
| Ceph | Storage | no | P1 | prometheus+direct | health,inventory,metrics,alerts |
| ZFS | Storage | no | P1 | prometheus+host | health,inventory,metrics |
| Linux / systemd / OpenSSH | Compute | no | P0 | host/gateway | health,inventory,metrics,actions |
| Docker / Docker Compose | Containers | no | P0 | direct+gateway | health,inventory,metrics,actions |
| Kubernetes / RKE2 | Containers | no | P1 | direct+gateway | health,inventory,metrics,alerts,actions |
| Rancher | Containers | no | P1 | direct+gateway | health,inventory,actions |
| Longhorn | Storage | no | P1 | direct+prometheus | health,inventory,metrics,alerts |
| NVIDIA DCGM | AI/Compute | no | P1 | prometheus | health,metrics |
| OPNsense | Network | no | P0 | direct+gateway | health,inventory,metrics,alerts,actions |
| WireGuard | Network | no | P0 | host+gateway | health,inventory,metrics |
| OSPF | Network | no | P1 | provider+telemetry | health,inventory,metrics |
| BGP | Network | no | P2 | provider+telemetry | health,inventory,metrics |
| Palo Alto PAN-OS | Security/Network | no | P1 | direct+gateway | health,inventory,metrics,alerts,security,actions |
| GlobalProtect | Security/Network | no | P1 | panos | health,inventory,alerts |
| Arista EOS | Network | no | P2 | direct+gateway | health,inventory,metrics,actions |
| SNMP | Network | no | P1 | gateway | health,inventory,metrics |
| Cloudflare | Network/Security | no | P1 | gateway | health,inventory,security,actions |
| NetBox | Source of Truth | no | P0 | direct+gateway | asset,inventory,search,documentation |
| Oxidized | Network/Backup | no | P1 | direct+gateway | backup,inventory,search |
| Prometheus | Observability | no | P0 | direct+gateway | metrics,health,search |
| Grafana | Observability | no | P0 | direct+gateway | metrics,documentation,search |
| Alertmanager | Observability | no | P0 | direct+gateway | alerts,actions |
| OneUptime | Observability | no | P0 | direct+gateway | health,alerts,incidents,metrics |
| Centreon | Observability | no | P1 | direct+gateway | health,alerts,metrics |
| Graylog | Logging | no | P1 | gateway | logs,search,alerts |
| Loki | Logging | no | P1 | gateway | logs,search |
| OpenSearch | Logging/Search | no | P1 | gateway | logs,search,alerts |
| OpenObserve | Observability | no | P2 | gateway | logs,metrics,search |
| OpenTelemetry | Observability | no | P2 | collector | metrics,logs |
| Tempo / Jaeger | Tracing | no | P2 | gateway | search,documentation |
| cAdvisor | Observability | no | P2 | prometheus | metrics |
| Blackbox Exporter | Observability | no | P2 | prometheus | metrics,health |
| Node Exporter | Observability | no | P2 | prometheus | metrics |
| SNMP Exporter | Observability | no | P2 | prometheus | metrics |
| kube-state-metrics | Observability | no | P2 | prometheus | metrics |
| OPNsense Exporter | Observability | no | P2 | prometheus | metrics |
| Proxmox Exporter | Observability | no | P2 | prometheus | metrics |
| Zammad | Support | no | P0 | direct+gateway | tickets,incidents,search,actions |
| PegaProx | RMM | no | P0 | direct+gateway | health,asset,inventory,metrics,alerts,actions |
| FileWave UEM | Endpoint | no | P1 | gateway | asset,inventory,security,actions |
| Cynet AiO 360 | Security | no | P1 | gateway | security,alerts,incidents |
| Authentik | Identity | no | P1 | direct+gateway | health,inventory,security |
| Microsoft Entra ID | Identity | no | P1 | gateway | inventory,security,alerts,actions |
| Microsoft Intune | Endpoint | no | P1 | gateway | asset,inventory,security,actions |
| Microsoft Defender | Security | no | P1 | gateway | security,alerts,incidents |
| Microsoft 365 | Business | no | P1 | gateway | health,inventory,alerts |
| Exchange Online | Messaging | no | P1 | gateway | health,inventory,alerts |
| Microsoft Teams | Collaboration | no | P2 | gateway | search,notifications |
| Teleport | Security/Access | no | P2 | gateway | security,inventory,search |
| Vault | Security/Secrets | no | P2 | gateway-only | health,security |
| Wazuh | Security | no | P2 | gateway | security,alerts,incidents |
| TheHive | Security | no | P2 | gateway | incidents,tasks,search |
| Suricata | Security | no | P2 | via-siem | security,alerts |
| Fail2Ban | Security | no | P2 | host+gateway | security,alerts,actions |
| OpenProject | Project | no | P1 | direct+gateway | tasks,search,documentation,actions |
| Outline | Knowledge | no | P1 | direct+gateway | documentation,search |
| Paperless-ngx | Knowledge | no | P1 | direct+gateway | documentation,search |
| SharePoint | Knowledge | no | P2 | gateway | documentation,search |
| OneDrive for Business | Knowledge | no | P2 | gateway | documentation,search |
| sevDesk | Business | no | P2 | gateway | search |
| GitHub | DevOps | no | P1 | direct+gateway | inventory,tasks,search,alerts |
| GitHub Actions | DevOps | no | P1 | github | health,tasks,alerts,actions |
| Dokploy | Deployment | no | P1 | direct+gateway | deployment,health,inventory,actions |
| Renovate | DevOps | no | P2 | github/gitea | tasks,alerts |
| COStech Platform API | AI | no | P1 | direct+gateway | health,ai,metrics |
| AIOC | AI | no | P1 | gateway+mcp | ai,search,actions |
| OpenWebUI | AI | no | P2 | direct | health,inventory |
| LiteLLM | AI | no | P1 | direct+gateway | health,metrics,inventory |
| Ollama | AI | no | P1 | direct+gateway | health,inventory,metrics |
| Qdrant | AI/Data | no | P1 | direct+gateway | health,inventory,metrics |
| PostgreSQL | Data | no | P1 | metrics+gateway | health,metrics,backup |
| Redis | Data | no | P1 | metrics+gateway | health,metrics |
| MCP Gateway | AI | no | P1 | gateway | health,ai,search,actions |
| Hetzner StorageBox | Backup | no | P1 | gateway | backup,health |
| S3-compatible object storage | Backup | no | P1 | gateway | backup,health |
| Ceph RBD / CSI | Storage | no | P1 | kubernetes+ceph | health,inventory,metrics |
| Calagopus | Gaming | yes | P2 | migrate | provider-specific |
| Jellystat | Media | yes | P2 | migrate | provider-specific |
| Pangolin / Newt | Network | yes | P2 | migrate | provider-specific |
| Pterodactyl | Gaming | yes | P2 | migrate | provider-specific |
| Wakapi | DevOps | yes | P2 | migrate | provider-specific |

Machine-readable CSV:
`docs/integrations/integration-matrix.csv`

## P0 new-provider sequence

1. Proxmox Backup Server
2. NetBox
3. Prometheus
4. Grafana
5. Zammad
6. PegaProx
7. OneUptime
8. OPNsense
9. WireGuard

Existing Proxmox VE, Uptime Kuma and Gitea/Forgejo act as migration reference
providers.

## Correlation example

```text
NetBox Asset
├── Proxmox resource
├── Prometheus target
├── Uptime Kuma monitor
├── OneUptime monitor
├── PegaProx endpoint
├── Zammad tickets
├── OpenProject work packages
├── Outline/Paperless documentation
└── AIOC context
```
