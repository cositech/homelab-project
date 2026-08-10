# Provider priorities

## P0 reference set

Proxmox VE, Uptime Kuma, NetBox, Zammad, PegaProx, Prometheus, OneUptime and OPNsense. These exercise infrastructure, monitoring, inventory, ticketing, RMM and firewall domains.

## P1 operational set

PBS, Ceph, Grafana, Alertmanager, Centreon, Graylog, Authentik, WireGuard, Palo Alto, Cynet, OpenProject, Outline, Paperless-ngx, Gitea/Forgejo and the COStech AI platform.

## Sequencing

1. Secure storage/TLS and provider contracts.
2. Proxmox/Uptime Kuma migrations as cross-platform reference implementations.
3. Read-only P0 providers and asset correlation.
4. Action framework and selected low-risk operations.
5. P1 providers, gateway and MSP isolation.

No new integration may copy credentials into an ordinary database or introduce a new TLS bypass.
