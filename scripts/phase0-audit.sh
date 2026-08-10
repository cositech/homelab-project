#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

required=(
  ARCHITECTURE.md ROADMAP.md INTEGRATIONS.md SECURITY.md CONTRIBUTING.md
  docs/security/SECURITY_AUDIT.md docs/security/THREAT_MODEL.md
  docs/project/UPSTREAM_BASELINE.md docs/project/DEPENDENCY_AUDIT.md
  docs/project/TEST_BASELINE.md docs/project/TECH_DEBT.md docs/project/EPICS.md
  schemas/provider.schema.json schemas/capability.schema.json
  schemas/resource.schema.json schemas/event.schema.json schemas/action.schema.json
  .github/workflows/ci.yml .github/workflows/codeql.yml
  .github/workflows/dependency-review.yml .github/dependabot.yml
)

for path in "${required[@]}"; do
  test -s "$path" || { echo "FAIL missing or empty: $path"; exit 1; }
  echo "OK   $path"
done

python3 - <<'PY'
import json
from pathlib import Path
for path in sorted(Path("schemas").glob("*.json")):
    data = json.loads(path.read_text(encoding="utf-8"))
    assert data.get("$schema") == "https://json-schema.org/draft/2020-12/schema"
    assert data.get("$id")
    print(f"OK   valid JSON {path}")
PY

adr_count="$(find docs/adr -maxdepth 1 -name '[0-9][0-9][0-9][0-9]-*.md' | wc -l | tr -d ' ')"
test "$adr_count" -ge 16 || { echo "FAIL expected at least 16 ADRs, got $adr_count"; exit 1; }
echo "OK   ADR count: $adr_count"

if git grep -nEI '(ghp|github_pat|AKIA)[A-Za-z0-9_=-]{16,}' -- ':!scripts/phase0-audit.sh'; then
  echo "FAIL potential committed secret"
  exit 1
fi
echo "OK   basic committed-secret pattern scan"

grep -q 'android-test:' .github/workflows/ci.yml
grep -q 'ios-test:' .github/workflows/ci.yml
grep -q 'phase0-static-audit:' .github/workflows/ci.yml
echo "OK   test and audit CI jobs"
echo "Phase 0 static audit: PASS"
