# OPNsense provider

- ID: `opnsense`; delivery: direct or gateway; priority: P0.
- Capabilities: health, assets, metrics, alerts, VPN/routing and controlled actions.
- Resources: firewall, interface, gateway, VPN tunnel/peer, DHCP lease, route and service.
- Actions: service restart/peer toggle (high); rule and routing changes excluded until transaction/rollback design exists.
- Tests: HA nodes, API permissions, IPv4/IPv6, WireGuard, gateway states, secret redaction and compatibility across supported releases.
