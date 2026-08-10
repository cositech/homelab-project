# Contract schemas

Schemas use JSON Schema 2020-12 and are versioned through their `$id` and `schemaVersion`. Additive optional fields are compatible within v1. Required-field, semantic or enum removals require a new version and ADR.

Clients validate provider/gateway fixtures in tests. Gateway inputs are validated before authorization and execution; schema validation never replaces tenant or action authorization.
