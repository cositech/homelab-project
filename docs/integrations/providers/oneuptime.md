# OneUptime provider

- ID: `oneuptime`; delivery: direct or gateway; priority: P0.
- Phase-2 capabilities: health, monitor assets, recent alert/incident events, safe diagnostics and read actions.
- Authentication: project-scoped, read-only `ApiKey` header stored through the platform secure credential envelope. The token also needs read access to alert and incident severities so IDs can be normalized without hard-coded assumptions.
- Read allow-list: only `POST /api/monitor/get-list`, `POST /api/alert/get-list`, `POST /api/incident/get-list`, `POST /api/alert-severity/get-list` and `POST /api/incident-severity/get-list`, each capped at 100 results with an application-owned `select`, empty `query` and descending creation sort.
- Data minimization: monitor endpoint configuration is not selected. Alert titles, incident slugs/titles, descriptions, notes and feeds are not selected; normalized events contain only record IDs and generated numbers.
- Event fidelity: alert creation times and incident declaration times are preserved. Severity IDs are resolved through the two read-only severity lists and normalized to the platform severity model.
- The POST verb is semantically read-only in OneUptime's list API. The client rejects every path outside the exact allow-list and contains no create, update, delete, acknowledge or resolve request.
- Future actions: acknowledge/resolve incident (medium) only after the Phase-3 policy and audit framework.
- Tests: project scope, caps, selected-field contract, content redaction, rate limiting and rejection of non-allowlisted paths.
