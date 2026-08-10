# Zammad provider

- ID: `zammad`; delivery: direct or gateway; priority: P0.
- Capabilities: tickets, alerts, search and controlled actions.
- Resources: ticket, article, user, organization, group and SLA state.
- Actions: comment/change owner/state (medium); destructive or bulk changes excluded initially.
- Correlation: organization/customer plus explicit asset references; ticket text is never used as authorization context.
- Tests: pagination, permissions, attachment limits, HTML sanitization, customer isolation and PII redaction.
