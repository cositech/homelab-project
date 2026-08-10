# ADR 0010 — Normalized health and incident model

**Status:** Accepted

## Decision

Provider alerts remain source observations. Incidents can correlate several alerts
and resources without overwriting source data.

## Consequences

- implementation must follow this decision unless superseded by a later ADR;
- deviations require an explicit ADR or documented compatibility exception.
