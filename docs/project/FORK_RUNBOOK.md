# Fork runbook

## Remotes

```bash
git remote set-url origin https://github.com/cositech/homelab-project.git
git remote add upstream https://github.com/JohnnWi/homelab-project.git
git fetch --all --prune
```

## Update from upstream

```bash
git switch main
git pull --ff-only origin main
git fetch upstream main
git switch -c chore/upstream-sync-YYYYMMDD
git merge --no-ff upstream/main
```

Resolve conflicts deliberately, run both platform checks, and merge through a reviewed pull request. Never force-push `main` and never overwrite fork security changes with upstream versions.

## Phase-0 verification

```bash
./scripts/phase0-audit.sh | tee phase0-audit.log
./scripts/validate-android.sh
./scripts/validate-ios.sh   # macOS/Xcode only
```

After merge, protect `main` with required review, conversation resolution, linear history where practical, and all Phase-0 checks required.
