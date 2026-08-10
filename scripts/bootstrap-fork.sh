#!/usr/bin/env bash
set -euo pipefail

UPSTREAM="JohnnWi/homelab-project"
OWNER=""
REPO="infrahub-mobile"
OVERLAY=""
EXECUTE=0
WORKDIR="${TMPDIR:-/tmp}/infrahub-mobile-bootstrap"

usage() {
  cat <<'EOF'
Usage:
  bootstrap-fork.sh --owner USER [--repo NAME] --overlay PATH [--execute]

Without --execute the script prints the intended operations.
Requires git and GitHub CLI (gh).
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --owner) OWNER="$2"; shift 2 ;;
    --repo) REPO="$2"; shift 2 ;;
    --overlay) OVERLAY="$2"; shift 2 ;;
    --execute) EXECUTE=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 2 ;;
  esac
done

[[ -n "$OWNER" ]] || { echo "--owner is required" >&2; exit 2; }
[[ -n "$OVERLAY" ]] || { echo "--overlay is required" >&2; exit 2; }
OVERLAY="$(cd "$OVERLAY" && pwd)"

cat <<EOF
Upstream:   $UPSTREAM
Owner:      $OWNER
Target:     $OWNER/$REPO
Overlay:    $OVERLAY
Workdir:    $WORKDIR
Branch:     phase0/foundation
EOF

if (( EXECUTE == 0 )); then
  echo
  echo "Dry run only. Re-run with --execute."
  exit 0
fi

command -v git >/dev/null
command -v gh >/dev/null
gh auth status >/dev/null

rm -rf "$WORKDIR"

# Create the fork under its upstream name first. GitHub may report that it
# already exists; accept an existing fork owned by OWNER.
if ! gh repo view "$OWNER/homelab-project" >/dev/null 2>&1; then
  gh repo fork "$UPSTREAM" --clone=false
fi

# Rename the fork while preserving the GitHub fork relationship.
if [[ "$REPO" != "homelab-project" ]]; then
  if ! gh repo view "$OWNER/$REPO" >/dev/null 2>&1; then
    gh api \
      --method PATCH \
      -H "Accept: application/vnd.github+json" \
      "/repos/$OWNER/homelab-project" \
      -f "name=$REPO" >/dev/null
  fi
fi

gh repo clone "$OWNER/$REPO" "$WORKDIR"
cd "$WORKDIR"

if git remote get-url upstream >/dev/null 2>&1; then
  git remote set-url upstream "https://github.com/$UPSTREAM.git"
else
  git remote add upstream "https://github.com/$UPSTREAM.git"
fi

git fetch upstream main
git checkout -B phase0/foundation upstream/main

# Overlay Phase-0 files without copying this bootstrap package's transient
# checksum/ZIP artifacts.
tar -C "$OVERLAY" \
  --exclude='./SHA256SUMS' \
  --exclude='*.zip' \
  -cf - . | tar -xf -

chmod +x scripts/*.sh
./scripts/phase0-audit.sh

git add .
git status --short
git commit -m "chore: establish Phase 0 fork foundation"
git push -u origin phase0/foundation

gh pr create \
  --repo "$OWNER/$REPO" \
  --base main \
  --head phase0/foundation \
  --draft \
  --title "chore: establish Phase 0 fork foundation" \
  --body-file docs/project/PHASE0_REPORT.md

echo
echo "Fork bootstrap complete."
echo "Repository: https://github.com/$OWNER/$REPO"
