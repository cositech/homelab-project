# Fork Runbook

## Current repository

The maintained fork is:

```text
cositech/homelab-project
```

Upstream remains:

```text
JohnnWi/homelab-project
```

Phase 0 is developed on:

```text
phase0/foundation
```

The fork relationship and complete upstream Git history are preserved.

## Recommended local checkout

```bash
git clone https://github.com/cositech/homelab-project.git
cd homelab-project

git remote add upstream https://github.com/JohnnWi/homelab-project.git
git fetch --all --prune

git remote -v
```

Expected remotes:

```text
origin   https://github.com/cositech/homelab-project.git
upstream https://github.com/JohnnWi/homelab-project.git
```

## Phase-0 validation

On Linux/macOS:

```bash
chmod +x scripts/*.sh
./scripts/phase0-audit.sh
./scripts/validate-android.sh
```

On macOS additionally:

```bash
./scripts/validate-ios.sh
```

GitHub Actions on the Phase-0 pull request is the authoritative cross-platform build/test evidence.

## Synchronizing upstream

The upstream repository is archived, but retain the remote so history and any future administrative changes remain traceable.

```bash
git fetch upstream
```

Do not merge unrelated upstream/history changes into feature branches without reviewing the diff.

## Branch protection to configure after Phase 0 is green

Require on `main`:

- pull requests for changes;
- Android test/build;
- iOS test/build;
- Phase-0/static audit;
- dependency review where supported;
- resolved review conversations;
- no force pushes.

Add CODEOWNERS once the long-term maintainer/reviewer set is finalized.
