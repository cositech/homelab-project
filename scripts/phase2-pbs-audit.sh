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
require_pattern 'WRITE_ACTIONS !in pbs' 'HomelabAndroid/app/src/test/java/com/homelab/app/domain/provider/ProviderCoreTest.kt'
require_pattern 'proxmoxBackupServer.*writeActions' 'HomelabSwift/HomelabTests/ModelDecodingTests.swift'

reject_pattern '\.(post|put|patch|delete)\(' "$ANDROID_CLIENT"
reject_pattern 'method:[[:space:]]*"(POST|PUT|PATCH|DELETE)"' "$IOS_CLIENT"
reject_pattern '(tokenSecret|password|authorization).*attributes' "$ANDROID_OPERATIONS"
reject_pattern '(tokenSecret|password|authorization).*attributes' "$IOS_OPERATIONS"

echo "Phase 2 PBS provider audit passed"
