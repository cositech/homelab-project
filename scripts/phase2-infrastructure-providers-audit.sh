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
  reject_pattern "\\.(put|patch|delete)\\(" "$path"
  reject_pattern "method:[[:space:]]*\\\"(PUT|PATCH|DELETE)\\\"" "$path"
  reject_pattern "/(console|vnc|shell)(/|\\\"|$)" "$path"
done

for path in "$ANDROID_CLIENT" "$IOS_CLIENT"; do
  require_pattern "/api/core/firmware/status" "$path"
  require_pattern "/api/interfaces/overview/interfacesInfo" "$path"
  require_pattern "/api/monitor/get-list" "$path"
  require_pattern "/api/alert/get-list" "$path"
  require_pattern "/api/incident/get-list" "$path"
  require_pattern "ONEUPTIME_READ_PATHS|oneUptimeReadPaths" "$path"
  require_pattern "contentRedacted" "$path"
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
