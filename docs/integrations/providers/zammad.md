# Zammad provider

- ID: `zammad`; delivery: direct or gateway; priority: P0.
- Auth: HTTP access token using `Authorization: Token token=...`; OAuth2 bearer support can follow when the optional gateway exists.
- Phase-2 capabilities: health, visible-ticket inventory, escalation alerts and normalized search. All write actions remain deferred to Phase 3.
- Fixed endpoints: `GET /api/v1/users/me` and paginated `GET /api/v1/tickets?expand=true` with 100 objects per page and a 500-ticket cap.
- Privacy boundary: normalized assets use ticket ID/number, state, priority, group and timestamps only. Titles, customers, organizations, article bodies, attachments, email addresses and free-form text are excluded.
- Authorization is entirely based on the token's Zammad groups and permissions; ticket text is never authorization context.
- Tests: pagination, group permissions, escalation mapping, customer isolation and PII-redaction invariants.
