# Phase 0 Completion Report

## Repository state

**Fork established and Phase-0 foundation published for validation.**

- Fork: `cositech/homelab-project`
- Upstream: `JohnnWi/homelab-project`
- Base branch: `main`
- Phase-0 branch: `phase0/foundation`
- Upstream/base commit: `75e7f3d53e2ec73ff55ce95927ddc173c724c9fd`

The fork relationship and upstream history are preserved. Phase 0 deliberately changes no Android/iOS production source files; it introduces governance, architecture, audit, CI, schemas and integration specifications around the existing application.

## Completed

- upstream repository metadata captured;
- source layout and provider/API organization inspected;
- build configuration reviewed;
- existing CI and CI history reviewed;
- Android and iOS tests inventoried;
- Android networking/TLS/credential persistence reviewed;
- iOS Keychain and ATS configuration reviewed;
- technical-debt register created;
- architecture decisions recorded in 16 ADRs;
- final 34 upstream integrations reconciled;
- extended target integration catalogue created;
- replacement CI/security workflows authored;
- deterministic validation scripts authored;
- normalized resource/provider/event schemas authored;
- initial provider specifications authored;
- issue and pull-request governance templates authored.

## Important findings

1. Existing unit tests are present but were not executed by upstream CI.
2. Android persists provider credential material in Room DB fields.
3. Android globally permits cleartext HTTP.
4. Android supports an insecure trust-all TLS compatibility client.
5. iOS stores service state in Keychain, which is the stronger credential-storage baseline.
6. iOS broadly relaxes ATS through arbitrary-load settings.
7. Android debug HTTP logging can expose textual response bodies.
8. Provider authentication/networking is centrally coupled and should be split into provider strategies.
9. Dependency/security automation was minimal upstream.
10. Cross-platform provider parity is not enforced mechanically.

## Repository-setting reconciliation

GitHub Dependency Review is present, but the new fork currently has Dependency Graph disabled. The workflow therefore warns rather than blocks until that repository setting is enabled. This is an administrative repository setting, not a source-code failure.

## Dynamic exit gate

Static Phase-0 validation and upstream-invariant checks are required to pass. Phase 0 is fully closed when GitHub Actions also prove:

- Gradle wrapper validation green;
- Android unit tests green;
- Android debug build green;
- iOS unit tests green;
- iOS simulator build green;
- CodeQL jobs green or a documented repository-feature limitation is reconciled.

After enabling GitHub Dependency Graph, Dependency Review should be converted from warning mode to a hard gate.

Do not begin broad provider expansion before the security and capability foundation is split into Phase-1 workstreams.
