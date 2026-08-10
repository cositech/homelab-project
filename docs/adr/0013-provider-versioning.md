# ADR 0013 — Versioned provider compatibility

**Status:** Accepted

## Decision

Providers detect service/API versions and document compatibility. Broad catch-all
fallback behavior is not a substitute for version handling.

## Consequences

- implementation must follow this decision unless superseded by a later ADR;
- deviations require an explicit ADR or documented compatibility exception.
