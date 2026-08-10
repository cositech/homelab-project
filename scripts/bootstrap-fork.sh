#!/usr/bin/env bash
set -euo pipefail

usage() { echo "Usage: $0 OWNER [REPOSITORY]"; }
owner="${1:-}"
repo="${2:-homelab-project}"
test -n "$owner" || { usage; exit 2; }
command -v gh >/dev/null || { echo "GitHub CLI is required"; exit 2; }
gh auth status >/dev/null

if ! gh repo view "$owner/$repo" >/dev/null 2>&1; then
  gh repo fork JohnnWi/homelab-project --org "$owner" --clone=false
fi

echo "Fork ready: https://github.com/$owner/$repo"
echo "Clone with: git clone https://github.com/$owner/$repo.git"
