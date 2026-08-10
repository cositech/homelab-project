# ADR 0014 — Cache is non-authoritative

**Status:** Accepted

## Decision

Offline/cache state is tagged with timestamp/source and must not be mistaken for
fresh operational truth. Destructive actions require online validation.

## Consequences

- implementation must follow this decision unless superseded by a later ADR;
- deviations require an explicit ADR or documented compatibility exception.
