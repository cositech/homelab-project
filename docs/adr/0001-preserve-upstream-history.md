# ADR 0001 — Preserve upstream history

**Status:** Accepted

## Decision

Use a GitHub fork or history-preserving import. Do not start from a source snapshot.
This preserves attribution, bisectability and license history.

## Consequences

- implementation must follow this decision unless superseded by a later ADR;
- deviations require an explicit ADR or documented compatibility exception.
