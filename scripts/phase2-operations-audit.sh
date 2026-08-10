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
  if grep -Eq "$pattern" "$file"; then
    fail "$file matches forbidden pattern: $pattern"
  fi
}

ANDROID_CORE="HomelabAndroid/app/src/main/java/com/homelab/app/domain/provider/ProviderCore.kt"
ANDROID_VM="HomelabAndroid/app/src/main/java/com/homelab/app/ui/operations/OperationsViewModel.kt"
ANDROID_VIEW="HomelabAndroid/app/src/main/java/com/homelab/app/ui/operations/OperationsScreen.kt"
ANDROID_NAV="HomelabAndroid/app/src/main/java/com/homelab/app/ui/navigation/AppNavigation.kt"
IOS_CORE="HomelabSwift/Homelab/Models/ServiceType.swift"
IOS_VIEW="HomelabSwift/Homelab/Views/ContentView.swift"

for contract in OperationsSnapshot ProviderDiagnostic OperationsSearchResults; do
  require_pattern "$contract" "$ANDROID_CORE"
  require_pattern "$contract" "$IOS_CORE"
done

for section in HEALTH ALERTS ASSETS SEARCH DIAGNOSTICS; do
  require_pattern "$section" "$ANDROID_VIEW"
done
for section in health alerts assets search diagnostics; do
  require_pattern "case $section" "$IOS_VIEW"
done

require_pattern "Screen\\.Operations" "$ANDROID_NAV"
require_pattern "OperationsScreen" "$ANDROID_NAV"
require_pattern "Tab\\(\"Operations\"" "$IOS_VIEW"
require_pattern "appendProxmox" "$ANDROID_VM"
require_pattern "appendUptimeKuma" "$ANDROID_VM"
require_pattern "loadProxmox" "$IOS_VIEW"
require_pattern "loadUptimeKuma" "$IOS_VIEW"
require_pattern "safeEndpoint" "$ANDROID_VM"
require_pattern "safeEndpoint" "$IOS_VIEW"

reject_pattern "val (token|password|apiKey|secret|credentialRef):" "$ANDROID_CORE"
reject_pattern "let (token|password|apiKey|secret|credentialRef):" "$IOS_CORE"

echo "Phase 2 operations audit passed"
