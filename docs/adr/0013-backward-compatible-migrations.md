# ADR-0013: Backward-compatible migrations

Status: Accepted

Credential, database, schema and backup migrations are versioned, transactional where possible, tested with real legacy fixtures and never delete source data before verified success.
