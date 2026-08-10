# PegaProx provider

- ID: `pegaprox`; delivery: gateway preferred; priority: P0.
- Initial capabilities: customer-scoped assets, health, metrics, alerts and remote-session deep links.
- Resources: customer, site, device, operating system, patch state, alert and session reference.
- Future actions: restart service/device, patch and approved script jobs only after action policy/audit.
- Security: customer scope is mandatory server-side; remote links are short-lived and never logged.
- Tests: foreign-customer negative queries/actions, device ownership, expiry, permissions and redaction.
