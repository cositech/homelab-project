#!/usr/bin/env bash
set -euo pipefail

android_core="HomelabAndroid/app/src/main/java/com/homelab/app/domain/action/ControlledActions.kt"
android_tests="HomelabAndroid/app/src/test/java/com/homelab/app/domain/action/ControlledActionsTest.kt"
swift_core="HomelabSwift/Homelab/Models/ServiceType.swift"
swift_tests="HomelabSwift/HomelabTests/ModelDecodingTests.swift"
architecture="docs/architecture/PHASE3_CONTROLLED_ACTIONS.md"
android_proxmox="HomelabAndroid/app/src/main/java/com/homelab/app/ui/proxmox/ProxmoxViewModel.kt"
android_proxmox_ui="HomelabAndroid/app/src/main/java/com/homelab/app/ui/proxmox/ProxmoxGuestDetailScreen.kt"
android_di="HomelabAndroid/app/src/main/java/com/homelab/app/di/SecurityModule.kt"
swift_proxmox="HomelabSwift/Homelab/Views/Proxmox/ProxmoxGuestDetailView.swift"

for required_file in "$android_core" "$android_tests" "$swift_core" "$swift_tests" "$architecture" "$android_proxmox" "$android_proxmox_ui" "$android_di" "$swift_proxmox" "schemas/action.schema.json"; do
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
grep -Fq 'proxmox lifecycle request uses normalized provider and target references' "$android_tests"
grep -Fq 'testProxmoxControlledLifecycleRequestUsesNormalizedReferences' "$swift_tests"

for pattern in 'controlledActionCoordinator.execute' 'ActionRole.ADMIN' 'controlledRequest' 'providerDescriptor.capabilities'; do
  grep -Fq "$pattern" "$android_proxmox"
done

grep -Fq 'proxmox_confirm_action' "$android_proxmox_ui"
grep -Fq 'controlledActionCoordinator: ControlledActionCoordinator' "$android_proxmox"
grep -Fq 'provideControlledActionCoordinator' "$android_di"

for pattern in 'controlledActionCoordinator.execute' 'actorRole: .admin' 'confirmed: true' 'ProxmoxActionReferenceBox'; do
  grep -Fq "$pattern" "$swift_proxmox"
done

if grep -A20 -F 'data class ActionAuditRecord' "$android_core" | grep -Eq 'parameters|credential|password|token|header|responseBody'; then
  echo "Android audit record contains forbidden sensitive payload fields" >&2
  exit 1
fi

if grep -A20 -F 'struct ActionAuditRecord' "$swift_core" | grep -Eq 'parameters|credential|password|token|header|responseBody'; then
  echo "Swift audit record contains forbidden sensitive payload fields" >&2
  exit 1
fi

echo "Phase 3 controlled actions audit passed"
