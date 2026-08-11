#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() { echo "FAIL: $*" >&2; exit 1; }
require_pattern() { grep -Eq "$1" "$2" || fail "$2 does not match required pattern: $1"; }
reject_pattern() { if grep -Eiq "$1" "$2"; then fail "$2 matches forbidden pattern: $1"; fi; }

ANDROID_CLIENT="HomelabAndroid/app/src/main/java/com/homelab/app/data/repository/ObservabilityRepository.kt"
ANDROID_OPERATIONS="HomelabAndroid/app/src/main/java/com/homelab/app/ui/operations/OperationsViewModel.kt"
IOS_CLIENT="HomelabSwift/Homelab/Networking/Observability/ObservabilityAPIClients.swift"
IOS_OPERATIONS="HomelabSwift/Homelab/Views/ContentView.swift"
PROM_SPEC="docs/integrations/providers/prometheus.md"
GRAFANA_SPEC="docs/integrations/providers/grafana.md"

for path in "$ANDROID_CLIENT" "$ANDROID_OPERATIONS" "$IOS_CLIENT" "$IOS_OPERATIONS" "$PROM_SPEC" "$GRAFANA_SPEC"; do
  test -s "$path" || fail "missing or empty: $path"
done

for path in "$ANDROID_CLIENT" "$IOS_CLIENT"; do
  require_pattern "/api/v1/status/buildinfo" "$path"
  require_pattern "/api/v1/targets\\?state=active" "$path"
  require_pattern "/api/v1/alerts" "$path"
  require_pattern "/api/health" "$path"
  require_pattern "/api/search\\?type=dash-db" "$path"
  require_pattern "/api/datasources" "$path"
  require_pattern "Authorization.*Bearer" "$path"
  reject_pattern "/api/v1/query(_range)?" "$path"
  reject_pattern "\\.(post|put|patch|delete)\\(" "$path"
  reject_pattern "method:[[:space:]]*\\\"(POST|PUT|PATCH|DELETE)\\\"" "$path"
done

require_pattern "WRITE_ACTIONS !in prometheus" HomelabAndroid/app/src/test/java/com/homelab/app/domain/provider/ProviderCoreTest.kt
require_pattern "EVENTS !in grafana" HomelabAndroid/app/src/test/java/com/homelab/app/domain/provider/ProviderCoreTest.kt
require_pattern "prometheus.*writeActions" HomelabSwift/HomelabTests/ModelDecodingTests.swift
require_pattern "grafana.*events" HomelabSwift/HomelabTests/ModelDecodingTests.swift
require_pattern "resourceType = .*scrape-target" "$ANDROID_OPERATIONS"
require_pattern "resourceType: .*scrape-target" "$IOS_OPERATIONS"
require_pattern "resourceType = .*data-source" "$ANDROID_OPERATIONS"
require_pattern "resourceType: .*data-source" "$IOS_OPERATIONS"
reject_pattern "(scrapeUrl|dataSource\\.url).*put\\(" "$ANDROID_OPERATIONS"
reject_pattern "(scrapeUrl|dataSource\\.url).*attributes" "$IOS_OPERATIONS"

echo "Phase 2 observability provider audit passed"
