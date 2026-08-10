# Logging and diagnostics standard

- Never log authorization headers, cookies, passwords, tokens, API keys, private keys, backup passphrases, request bodies containing secrets, or URLs with sensitive query parameters.
- Provider, instance, tenant and resource identifiers use pseudonymous stable IDs in exported diagnostics.
- Security and action logs record actor, time, provider, normalized action, target reference, policy decision, correlation/idempotency ID and result—not secret material.
- Debug logging is time-limited, opt-in, clearly visible and still redacted.
- Diagnostic bundles show an exact preview and require explicit user confirmation before export.
- Gateway logs support retention limits, access control, integrity protection and export to self-hosted observability systems.
