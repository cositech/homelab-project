# Recommended branch protection

Protect `main` after the Phase-0 pull request proves the final check names.

- Require pull requests and at least one approving review.
- Dismiss stale approvals and require conversation resolution.
- Require CI version consistency, Android test/compile, iOS test/compile, Phase-0 audit, Dependency Review and both CodeQL analyses.
- Block force pushes and branch deletion.
- Restrict direct pushes; retain an audited emergency path for the owner.
- Require signed commits/tags when the release-signing workflow is operational.

Repository settings are deliberately not mutated by the Phase-0 code change because an incorrect required-check name can lock the branch before the first workflow run establishes it.
