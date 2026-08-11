# OPNsense provider

- ID: `opnsense`; delivery: direct or gateway; priority: P0.
- Phase-2 capabilities: health, interface assets, safe diagnostics and read actions.
- Authentication: dedicated API key and API secret using HTTP Basic authentication. Both values are stored through the platform secure credential envelope; the API user must receive only the firmware-status and interface-overview privileges.
- Read allow-list: `GET /api/core/firmware/status` and `GET /api/interfaces/overview/interfacesInfo`.
- Normalization: interfaces become stable `interface` resources; firmware version and interface/down counts become health attributes. API keys, secrets and authorization headers never enter snapshots or diagnostics.
- Excluded in Phase 2: firewall rules, aliases, DHCP leases, VPN peers, routes, service controls, configuration export and every POST mutation.
- Future actions: service restart/peer toggle (high); rule and routing changes remain excluded until the Phase-3 policy, confirmation, audit and rollback design exists.
- Tests: API permissions, IPv4/IPv6 interface shapes, secret redaction, self-signed compatibility and supported-release response variants.
