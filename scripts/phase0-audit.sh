#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail=0
check_file() {
  if [[ ! -f "$1" ]]; then
    echo "FAIL missing: $1" >&2
    fail=1
  else
    echo "OK   $1"
  fi
}

for f in \
  ARCHITECTURE.md ROADMAP.md INTEGRATIONS.md SECURITY_AUDIT.md \
  DEPENDENCY_AUDIT.md TEST_BASELINE.md TECH_DEBT.md AGENTS.md \
  schemas/provider.schema.json schemas/resource.schema.json schemas/event.schema.json \
  docs/project/EPICS.md docs/project/FORK_RUNBOOK.md \
  HomelabAndroid/gradlew HomelabSwift/project.yml HomelabSwift/Homelab.xcodeproj/project.pbxproj; do
  check_file "$f"
done

python3 - <<'PY'
import json, pathlib
for p in pathlib.Path("schemas").glob("*.json"):
    json.loads(p.read_text())
    print("OK   valid JSON", p)
PY

count="$(find docs/adr -maxdepth 1 -name '*.md' | wc -l | tr -d ' ')"
if (( count < 15 )); then
  echo "FAIL expected >=15 ADRs, found $count" >&2
  fail=1
else
  echo "OK   ADR count: $count"
fi

upstream_count="$(python3 - <<'PY'
import csv
with open('docs/integrations/integration-matrix.csv', newline='', encoding='utf-8') as f:
    print(sum(1 for row in csv.DictReader(f) if row['upstream'] == 'yes'))
PY
)"
if [[ "$upstream_count" != "34" ]]; then
  echo "FAIL expected exactly 34 final upstream integrations, found $upstream_count" >&2
  fail=1
else
  echo "OK   final upstream integration count: 34"
fi

if grep -RIE --exclude='phase0-audit.sh' \
  '(BEGIN (RSA|OPENSSH|EC) PRIVATE KEY|ghp_[A-Za-z0-9]{20,}|sk-[A-Za-z0-9]{20,})' \
  . >/tmp/infrahub-secret-scan.txt 2>/dev/null; then
  echo "FAIL potential secret-like material found:" >&2
  cat /tmp/infrahub-secret-scan.txt >&2
  fail=1
else
  echo "OK   basic secret-pattern scan"
fi

if (( fail != 0 )); then
  exit 1
fi

echo "Phase 0 static audit: PASS"
