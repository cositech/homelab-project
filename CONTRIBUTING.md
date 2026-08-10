# Contributing

## Workflow

1. Base work on current `main`; keep `upstream` pointed at `JohnnWi/homelab-project`.
2. Use a focused branch (`feat/`, `fix/`, `docs/`, `ci/`, `security/`).
3. Preserve existing integrations and both native clients unless an approved ADR says otherwise.
4. Add tests for storage, networking, parsing, models, ViewModels, migrations, or security behavior.
5. Run the checks appropriate to the touched paths and complete the pull request checklist.

## Compatibility

- Android and iOS versions and build numbers remain aligned.
- Existing encrypted backups require explicit migration tests before their format changes.
- Provider contracts are additive by default. Breaking schema changes require a new schema version and ADR.
- Never silently weaken TLS or credential handling to support a legacy endpoint.

## Commit style

Use `feat:`, `fix:`, `docs:`, `ci:`, `security:`, `test:`, or `chore:` followed by a concise imperative description.
