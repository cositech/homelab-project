# ADR 0006 — REST/WebSocket for UI, MCP for AI

**Status:** Accepted

## Decision

Deterministic UI operations use normal APIs. MCP is an AI/tool plane and cannot be
the sole transport for critical operator workflows.

## Consequences

- implementation must follow this decision unless superseded by a later ADR;
- deviations require an explicit ADR or documented compatibility exception.
