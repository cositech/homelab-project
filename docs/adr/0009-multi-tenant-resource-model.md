# ADR 0009 — Organization/Site resource hierarchy

**Status:** Accepted

## Decision

Multi-instance support is expanded into Organization, Site, IntegrationInstance and
Resource. Provider code may not assume a single tenant.

## Consequences

- implementation must follow this decision unless superseded by a later ADR;
- deviations require an explicit ADR or documented compatibility exception.
