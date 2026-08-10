# Test Baseline and Quality Gates

## Static inventory

### Android

At least ten unit-test source files are present upstream, covering selected:

- authentication interceptor behavior;
- URL fallback;
- compatibility parsing;
- NPMplus;
- Pi-hole;
- Proxmox;
- Radarr;
- Sonarr;
- Uptime Kuma.

### iOS

At least two test files are present upstream:

- Keychain service;
- model decoding.

## Gap

Tests exist but are not run by the upstream `ci.yml`.

There is no established coverage threshold in the reviewed baseline.

## Phase 0 quality gates

Every pull request must:

1. pass source/header checks;
2. validate Gradle wrapper;
3. execute Android unit tests;
4. assemble Android debug;
5. regenerate the iOS Xcode project;
6. execute iOS tests;
7. build iOS simulator target;
8. pass dependency review when dependency metadata changed;
9. pass CodeQL on scheduled/default-branch analysis.

## Coverage strategy

Do not introduce an arbitrary percentage target immediately.

### Stage A

- tests run reliably;
- publish test results;
- record baseline coverage.

### Stage B

- changed-code coverage gate;
- provider parsers/repositories require fixture tests;
- credential/security components require high branch coverage.

### Stage C

Suggested target after measurement:

- normalized domain/provider core: >= 80% line coverage;
- security/credential migration/action policy: >= 90% branch coverage where
  tooling provides meaningful measurement;
- UI snapshot/instrumentation coverage based on critical journeys rather than
  a global line metric.

## Provider contract test suite

Every provider must pass a reusable contract suite:

- authentication success/failure;
- version detection;
- timeout;
- invalid JSON/malformed payload;
- 401/403;
- 404 endpoint/version fallback;
- 429/backoff;
- 5xx;
- TLS failure;
- empty data;
- partial data;
- resource identity stability;
- health mapping;
- secret redaction;
- capability declaration consistency.
