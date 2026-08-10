#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/HomelabSwift"

mkdir -p Config
cat > Config/Signing.xcconfig <<'EOF'
DEVELOPMENT_TEAM =
PRODUCT_BUNDLE_IDENTIFIER = homelab.foreverhomelab
EOF

UDID="$(python3 - <<'PY'
import json, subprocess
data=json.loads(subprocess.check_output(["xcrun","simctl","list","devices","available","-j"]))
items=[]
for runtime, devices in data.get("devices", {}).items():
    if "iOS" not in runtime:
        continue
    for d in devices:
        if d.get("isAvailable") and d.get("name", "").startswith("iPhone"):
            items.append((runtime, d["name"], d["udid"]))
if not items:
    raise SystemExit("No available iPhone simulator")
items.sort(reverse=True)
print(items[0][2])
PY
)"

xcodebuild test \
  -project Homelab.xcodeproj \
  -scheme Homelab \
  -destination "platform=iOS Simulator,id=$UDID" \
  CODE_SIGNING_ALLOWED=NO

xcodebuild build \
  -project Homelab.xcodeproj \
  -scheme Homelab \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination "platform=iOS Simulator,id=$UDID" \
  CODE_SIGNING_ALLOWED=NO
