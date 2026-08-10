# Agent Instructions — InfraHub Mobile Fork

These instructions apply repository-wide unless a more specific `AGENTS.md` exists deeper in the tree.

## Mission

Maintain a secure, backwards-compatible native Android/iOS infrastructure operations client. Reuse normalized capability contracts rather than creating isolated one-off architectures for each provider.

## Non-negotiable rules

1. Preserve Apache-2.0 upstream attribution and notices.
2. Do not delete an existing upstream integration without an explicit issue/ADR.
3. Do not perform a framework rewrite as part of a provider feature.
4. Keep Android and iOS behavior aligned unless the PR documents a deliberate temporary parity gap.
5. Never log credentials, tokens, cookies, session data or unsanitized API response bodies.
6. Never add a trust-all TLS path as a silent/default behavior.
7. New mutable operations require `ActionCapability`, risk classification, confirmation semantics and audit metadata.
8. Do not store new secrets in ordinary Room/CoreData/UserDefaults/domain models.
9. Fixture files must be sanitized and contain no production identifiers or credentials.
10. Provider code must handle supported API-version differences explicitly; broad exception fallback is not version handling.

## Architecture

```text
UI -> UseCase -> Domain/Capabilities <- Provider Adapter
```

Provider DTOs do not escape the provider/data boundary. Every capability-migrated provider declares metadata in `integration-spec/`.

## Required provider workflow

1. update/create integration spec;
2. add/modify sanitized fixtures;
3. add parser/repository/contract tests;
4. implement Android change;
5. implement iOS change or document parity issue;
6. run platform validation;
7. run `scripts/phase0-audit.sh`;
8. update `docs/integrations/matrix.md` if maturity changes.

## Android

```bash
cd HomelabAndroid
./gradlew --no-daemon --console=plain :app:testDebugUnitTest
./gradlew --no-daemon --console=plain :app:assembleDebug
```

For release-affecting changes additionally:

```bash
./gradlew --no-daemon --console=plain :app:assembleRelease
```

The project targets Java 17 bytecode; CI currently provisions JDK 21 to match the upstream build environment.

## iOS

Run from repository root on macOS:

```bash
./scripts/validate-ios.sh
```

`HomelabSwift/Homelab.xcodeproj` is committed and built directly by CI. `HomelabSwift/project.yml` is also retained; changes that touch project structure must keep both representations consistent and review the generated/project-file diff explicitly.

## Security-sensitive files

Changes to credential storage, Keychain/Keystore, TLS/trust handling, auth interceptors, OAuth/token refresh, backup crypto, remote actions, gateway authorization or audit logging require explicit security review in the PR.

## Code quality

- prefer provider-specific auth strategies over continuing to grow one central interceptor;
- normalize errors at the provider boundary;
- use typed domain models and stable identifiers;
- propagate cancellation and timeouts;
- distinguish `unsupported`, `unauthorized`, `unavailable`, `degraded`, `malformed`, and `unknown`.

## Commit/PR policy

Use focused conventional commits where practical. PRs must document scope, behavior before/after, platform parity, security impact, compatibility impact, tests and rollback notes for migrations.
