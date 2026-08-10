# Integration Gateway Boundary

The gateway is optional for homelab deployments and recommended for
professional multi-customer deployments.

Responsibilities:

- authenticate mobile users;
- authorize tenant/site/provider access;
- resolve provider credentials;
- normalize provider APIs;
- provide audit trail;
- stream normalized events;
- push notifications;
- enforce action policies;
- host provider-side integrations that cannot safely expose credentials to a
  phone;
- expose MCP tools to AIOC separately from deterministic mobile endpoints.

The gateway is **not** part of Phase 0 implementation.
