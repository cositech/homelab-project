#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

require_pattern() {
  local pattern="$1"
  local file="$2"
  grep -Eq "$pattern" "$file" || fail "$file does not match required pattern: $pattern"
}

reject_pattern() {
  local pattern="$1"
  local file="$2"
  if grep -Eiq "$pattern" "$file"; then
    fail "$file matches forbidden pattern: $pattern"
  fi
}

ANDROID_TYPE="HomelabAndroid/app/src/main/java/com/homelab/app/util/ServiceType.kt"
ANDROID_CLIENT="HomelabAndroid/app/src/main/java/com/homelab/app/data/repository/ProxmoxBackupServerRepository.kt"
ANDROID_OPERATIONS="HomelabAndroid/app/src/main/java/com/homelab/app/ui/operations/OperationsViewModel.kt"
IOS_TYPE="HomelabSwift/Homelab/Models/ServiceType.swift"
IOS_CLIENT="HomelabSwift/Homelab/Networking/ProxmoxBackupServer/ProxmoxBackupServerAPIClient.swift"
IOS_OPERATIONS="HomelabSwift/Homelab/Views/ContentView.swift"
SPEC="docs/integrations/providers/proxmox-backup-server.md"

for path in "$ANDROID_TYPE" "$ANDROID_CLIENT" "$ANDROID_OPERATIONS" "$IOS_TYPE" "$IOS_CLIENT" "$IOS_OPERATIONS" "$SPEC"; do
  test -s "$path" || fail "missing or empty: $path"
done

require_pattern 'PROXMOX_BACKUP_SERVER' "$ANDROID_TYPE"
require_pattern 'proxmoxBackupServer' "$IOS_TYPE"
require_pattern 'PBSAPIToken=' "$ANDROID_CLIENT"
require_pattern 'PBSAPIToken=' "$IOS_CLIENT"
require_pattern '/api2/json/version' "$ANDROID_CLIENT"
require_pattern '/api2/json/status/datastore-usage' "$ANDROID_CLIENT"
require_pattern '/api2/json/version' "$IOS_CLIENT"
require_pattern '/api2/json/status/datastore-usage' "$IOS_CLIENT"
require_pattern '0\.85' "$ANDROID_OPERATIONS"
require_pattern '0\.95' "$ANDROID_OPERATIONS"
require_pattern '0\.85' "$IOS_OPERATIONS"
require_pattern '0\.95' "$IOS_OPERATIONS"
require_pattern 'WRITE_ACTIONS in pbs' 'HomelabAndroid/app/src/test/java/com/homelab/app/domain/provider/ProviderCoreTest.kt'
require_pattern 'XCTAssertTrue\(ProviderRegistry\.descriptor\(for: \.proxmoxBackupServer\)\.capabilities\.contains\(\.writeActions\)\)' 'HomelabSwift/HomelabTests/ModelDecodingTests.swift'

# PBS gained exactly one mutation (Phase 3's PBS sync-job-trigger slice): triggering a sync job to
# run now, routed through the controlled-action coordinator like every other Phase 3 mutation.
# Assert the endpoint exists and that it's the ONLY mutating call in each client - any other
# POST/PUT/PATCH/DELETE would be scope creep past what's been reviewed and gated behind
# WRITE_ACTIONS, defeating the point of this audit.
require_pattern '/api2/json/admin/sync/' "$ANDROID_CLIENT"
require_pattern '/api2/json/admin/sync/' "$IOS_CLIENT"

# Every Android call site shares one low-level `fetch(...)` helper, so counting the helper's own
# `.post(` line would stay at 1 forever even if a second call site started passing
# `method = "POST"` into it - count call sites (the `method = "..."` argument), not the builder
# verb, so a future mutation actually trips this.
android_mutations="$(grep -Ec 'method = "(POST|PUT|PATCH|DELETE)"' "$ANDROID_CLIENT")"
test "$android_mutations" -eq 1 || fail "$ANDROID_CLIENT has $android_mutations mutating call site(s), expected exactly 1 (the sync-job trigger)"
ios_mutations="$(grep -Ec 'method:[[:space:]]*"(POST|PUT|PATCH|DELETE)"' "$IOS_CLIENT")"
test "$ios_mutations" -eq 1 || fail "$IOS_CLIENT has $ios_mutations mutating call site(s), expected exactly 1 (the sync-job trigger)"

reject_pattern '(tokenSecret|password|authorization).*attributes' "$ANDROID_OPERATIONS"
reject_pattern '(tokenSecret|password|authorization).*attributes' "$IOS_OPERATIONS"

echo "Phase 2 PBS provider audit passed"
