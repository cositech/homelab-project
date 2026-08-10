# Contributing

## Branches

Use short-lived branches:

```text
feature/<area>-<subject>
fix/<area>-<subject>
security/<subject>
refactor/<area>-<subject>
```

## Pull requests

All PRs must remain reviewable and should not combine:

- dependency upgrades + architecture rewrite;
- branding + security migration;
- multiple unrelated providers;
- framework migration + feature work.

## Provider additions

A provider is incomplete until it has:

- integration spec;
- authentication documentation;
- minimum permission/scopes;
- version detection;
- timeout/error mapping;
- sanitized fixture tests;
- Android implementation;
- iOS implementation or tracked parity gap;
- security considerations;
- documentation.

## Compatibility

Existing upstream configurations are migration inputs. Migrations must be
repeatable, testable and fail-safe. Never destroy old state before validating
the new representation.
