#!/usr/bin/env bash
set -euo pipefail

android_core="HomelabAndroid/app/src/main/java/com/homelab/app/domain/action/ControlledActions.kt"
android_tests="HomelabAndroid/app/src/test/java/com/homelab/app/domain/action/ControlledActionsTest.kt"
swift_core="HomelabSwift/Homelab/Models/ServiceType.swift"
swift_tests="HomelabSwift/HomelabTests/ModelDecodingTests.swift"
architecture="docs/architecture/PHASE3_CONTROLLED_ACTIONS.md"

for required_file in "$android_core" "$android_tests" "$swift_core" "$swift_tests" "$architecture" "schemas/action.schema.json"; do
  test -s "$required_file"
done

for pattern in   'enum class ActionRisk'   'enum class ActionRole'   'data class ControlledActionRequest'   'object ControlledActionPolicy'   'class ControlledActionCoordinator'   'ActionPolicyOutcome.DRY_RUN_APPROVED'   'provider-write-capability-required'   'ProviderCapability.WRITE_ACTIONS'   'CancellationException'   'terminalResults'   'idempotencyKey'
do
  grep -Fq "$pattern" "$android_core"
done

for pattern in   'enum ControlledActionRisk'   'enum ControlledActionRole'   'struct ControlledActionRequest'   'enum ControlledActionPolicy'   'actor ControlledActionCoordinator'   'case dryRunApproved'   'provider-write-capability-required'   'providerCapabilities.contains(.writeActions)'   'ControlledActionExecutionGate'   'terminalResults'   'decodeIfPresent'   'idempotencyKey'
do
  grep -Fq "$pattern" "$swift_core"
done

grep -Fq 'dry run validates without invoking provider mutation' "$android_tests"
grep -Fq 'idempotency returns previous terminal result' "$android_tests"
grep -Fq 'testControlledActionDryRunDoesNotInvokeMutation' "$swift_tests"
grep -Fq 'testControlledActionIdempotencyReturnsTerminalResult' "$swift_tests"

if grep -A20 -F 'data class ActionAuditRecord' "$android_core" | grep -Eq 'parameters|credential|password|token|header|responseBody'; then
  echo "Android audit record contains forbidden sensitive payload fields" >&2
  exit 1
fi

if grep -A20 -F 'struct ActionAuditRecord' "$swift_core" | grep -Eq 'parameters|credential|password|token|header|responseBody'; then
  echo "Swift audit record contains forbidden sensitive payload fields" >&2
  exit 1
fi

echo "Phase 3 controlled actions audit passed"
