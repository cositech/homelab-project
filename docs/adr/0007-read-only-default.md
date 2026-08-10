# ADR 0007 — Read-only by default

**Status:** Accepted

## Decision

Provider adoption starts read-only. Mutations are explicitly declared actions with
risk, confirmation and audit semantics.

## Consequences

- implementation must follow this decision unless superseded by a later ADR;
- deviations require an explicit ADR or documented compatibility exception.
