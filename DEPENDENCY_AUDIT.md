# Dependency and Build-Chain Audit

## Goal

Establish an updateable and reproducible baseline without combining dependency churn with the Phase-0 fork foundation.

## Android baseline

The current fork `main` uses:

- Android Gradle Plugin `9.0.1`;
- Kotlin `2.1.10`;
- KSP `2.1.10-1.0.29`;
- Gradle wrapper `9.4.0`;
- compileSdk / targetSdk `36`;
- minSdk `26`;
- Java source/target compatibility `17`;
- JDK `21` in the upstream GitHub Actions build environment.

Phase-0 policy:

1. make existing builds/tests deterministic first;
2. use automated dependency PRs;
3. update one ecosystem group at a time;
4. require CI for dependency changes;
5. avoid framework migration in the same change set.

## iOS baseline

The repository contains both `HomelabSwift/Homelab.xcodeproj` and `HomelabSwift/project.yml`. The current upstream CI builds the committed Xcode project directly on macOS 26/Xcode 26 and creates only the CI signing xcconfig dynamically.

Phase 0 therefore does not introduce a Ruby/Xcode-project generator dependency. Structural project changes must keep `project.yml` and the committed Xcode project consistent.

## Automation

The fork adds Dependabot for Gradle and GitHub Actions.

Dependency Review is configured, but the newly created fork currently reports that GitHub Dependency Graph is disabled. Until that repository setting is enabled, the workflow records a visible warning instead of blocking all pull requests. Once Dependency Graph is enabled, remove the temporary `continue-on-error` behavior so high-severity dependency changes become a hard gate.

If Renovate becomes the standard later, replace Dependabot rather than operating two overlapping update bots.

## Supply-chain policy

- validate the Gradle wrapper;
- enable GitHub Dependency Graph and make Dependency Review blocking;
- retain license attribution;
- do not commit release binaries;
- use CI before accepting automated dependency changes;
- add release SBOM/signing work in a later release-engineering phase.
