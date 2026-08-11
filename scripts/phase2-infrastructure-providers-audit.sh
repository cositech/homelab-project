#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() { echo "FAIL: $*" >&2; exit 1; }
require_pattern() { grep -Eq "$1" "$2" || fail "$2 does not match required pattern: $1"; }
reject_pattern() { if grep -Eiq "$1" "$2"; then fail "$2 matches forbidden pattern: $1"; fi; }

ANDROID_CLIENT="HomelabAndroid/app/src/main/java/com/homelab/app/data/repository/InfrastructureOperationsRepository.kt"
ANDROID_OPERATIONS="HomelabAndroid/app/src/main/java/com/homelab/app/ui/operations/OperationsViewModel.kt"
IOS_CLIENT="HomelabSwift/Homelab/Networking/Observability/ObservabilityAPIClients.swift"
IOS_OPERATIONS="HomelabSwift/Homelab/Views/ContentView.swift"
ANDROID_NON_ONEUPTIME="$(mktemp)"
IOS_NON_ONEUPTIME="$(mktemp)"
trap 'rm -f "$ANDROID_NON_ONEUPTIME" "$IOS_NON_ONEUPTIME"' EXIT
awk '/private suspend fun oneUptimeRequest\(/ { skip=1 } skip && /private fun authorization\(/ { skip=0 } !skip { print }' "$ANDROID_CLIENT" > "$ANDROID_NON_ONEUPTIME"
awk '/private func oneUptimeList\(/ { skip=1 } skip && /private func requestObject\(/ { skip=0 } !skip { print }' "$IOS_CLIENT" > "$IOS_NON_ONEUPTIME"

for path in \
  "$ANDROID_CLIENT" "$ANDROID_OPERATIONS" "$IOS_CLIENT" "$IOS_OPERATIONS" \
  docs/integrations/providers/netbox.md \
  docs/integrations/providers/zammad.md \
  docs/integrations/providers/pegaprox.md \
  docs/integrations/providers/opnsense.md \
  docs/integrations/providers/oneuptime.md; do
  test -s "$path" || fail "missing or empty: $path"
done

for path in "$ANDROID_CLIENT" "$IOS_CLIENT"; do
  read_only_path="$ANDROID_NON_ONEUPTIME"
  if [ "$path" = "$IOS_CLIENT" ]; then
    read_only_path="$IOS_NON_ONEUPTIME"
  fi
  require_pattern "/api/status/" "$path"
  require_pattern "/api/dcim/devices/\\?exclude=config_context" "$path"
  require_pattern "/api/virtualization/virtual-machines/\\?exclude=config_context" "$path"
  require_pattern "/api/v1/users/me" "$path"
  require_pattern "/api/v1/tickets\\?expand=true" "$path"
  require_pattern "/api/clusters" "$path"
  require_pattern "/health" "$path"
  require_pattern "/resources" "$path"
  require_pattern "/active-alerts" "$path"
  require_pattern "Token token=" "$path"
  require_pattern "Bearer" "$path"
  require_pattern "MAX_ITEMS|maxItems" "$path"
  require_pattern "MAX_CLUSTERS|maxClusters" "$path"
  require_pattern "piiRedacted" "$path"
  require_pattern "tenantScoped" "$path"
  reject_pattern "\\.(post|put|patch|delete)\\(" "$read_only_path"
  reject_pattern "method:[[:space:]]*\"(POST|PUT|PATCH|DELETE)\"" "$read_only_path"
  reject_pattern "/(console|vnc|shell)(/|\"|$)" "$path"
done

for path in "$ANDROID_CLIENT" "$IOS_CLIENT"; do
  require_pattern "/api/core/firmware/status" "$path"
  require_pattern "/api/interfaces/overview/interfacesInfo" "$path"
  require_pattern "/api/monitor/get-list" "$path"
  require_pattern "/api/alert/get-list" "$path"
  require_pattern "/api/incident/get-list" "$path"
  require_pattern "/api/alert-severity/get-list" "$path"
  require_pattern "/api/incident-severity/get-list" "$path"
  require_pattern "ONEUPTIME_READ_PATHS|oneUptimeReadPaths" "$path"
  require_pattern "contentRedacted" "$path"
  require_pattern "createdAt" "$path"
  require_pattern "alertSeverityId" "$path"
  require_pattern "incidentSeverityId" "$path"
  require_pattern "oneUptimeTimestamp|oneUptimeDate" "$path"
  require_pattern "alertSeverities" "$path"
  require_pattern "OneUptime path is not allowlisted" "$path"
done

require_pattern "WRITE_ACTIONS !in netbox" HomelabAndroid/app/src/test/java/com/homelab/app/domain/provider/ProviderCoreTest.kt
require_pattern "WRITE_ACTIONS !in zammad" HomelabAndroid/app/src/test/java/com/homelab/app/domain/provider/ProviderCoreTest.kt
require_pattern "WRITE_ACTIONS !in pegaprox" HomelabAndroid/app/src/test/java/com/homelab/app/domain/provider/ProviderCoreTest.kt
require_pattern "WRITE_ACTIONS !in opnsense" HomelabAndroid/app/src/test/java/com/homelab/app/domain/provider/ProviderCoreTest.kt
require_pattern "WRITE_ACTIONS !in oneuptime" HomelabAndroid/app/src/test/java/com/homelab/app/domain/provider/ProviderCoreTest.kt
require_pattern "netbox.*writeActions" HomelabSwift/HomelabTests/ModelDecodingTests.swift
require_pattern "zammad.*writeActions" HomelabSwift/HomelabTests/ModelDecodingTests.swift
require_pattern "pegaprox.*writeActions" HomelabSwift/HomelabTests/ModelDecodingTests.swift
require_pattern "opnsense.*writeActions" HomelabSwift/HomelabTests/ModelDecodingTests.swift
require_pattern "oneuptime.*writeActions" HomelabSwift/HomelabTests/ModelDecodingTests.swift
require_pattern "resourceType = .*ticket" "$ANDROID_CLIENT"
require_pattern "resourceType: .*ticket" "$IOS_CLIENT"
require_pattern "resourceType = .*cluster" "$ANDROID_CLIENT"
require_pattern "resourceType: .*cluster" "$IOS_CLIENT"
require_pattern "getSnapshot" "$ANDROID_OPERATIONS"
require_pattern "getSnapshot" "$IOS_OPERATIONS"

echo "Phase 2 infrastructure provider audit passed"
