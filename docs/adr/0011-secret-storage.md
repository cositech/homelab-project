# ADR 0011 — Secrets are references, not domain fields

**Status:** Accepted

## Decision

Domain/config persistence stores credential references. Secret material uses
platform secure storage or gateway-side secret storage.

## Consequences

- implementation must follow this decision unless superseded by a later ADR;
- deviations require an explicit ADR or documented compatibility exception.
