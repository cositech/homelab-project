# Dependency audit

## Baseline controls

- Gradle wrapper and Swift project files remain committed for reproducibility.
- Dependabot monitors Gradle and GitHub Actions weekly.
- Dependency Review blocks newly introduced high/critical vulnerable dependencies on pull requests.
- CodeQL analyzes Kotlin/Java and Swift on pull requests, pushes to `main`, and weekly.
- GitHub Actions are pinned to explicit major versions today; pinning to immutable commit SHAs is a Phase-1 supply-chain hardening task.

## Required follow-up

1. Generate CycloneDX SBOMs for Android and the final iOS archive.
2. Add artifact signing/provenance to releases.
3. Document minimum supported toolchains and a controlled dependency-update window.
4. Review transitive networking, crypto, database, and serialization dependencies first.
5. Reject dependencies that require telemetry or SaaS control planes without an approved ADR.
