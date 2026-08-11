#!/usr/bin/env bash
set -euo pipefail

android_core="HomelabAndroid/app/src/main/java/com/homelab/app/domain/action/ControlledActions.kt"
android_tests="HomelabAndroid/app/src/test/java/com/homelab/app/domain/action/ControlledActionsTest.kt"
android_di="HomelabAndroid/app/src/main/java/com/homelab/app/di/SecurityModule.kt"
android_proxmox="HomelabAndroid/app/src/main/java/com/homelab/app/ui/proxmox/ProxmoxViewModel.kt"
android_proxmox_ui="HomelabAndroid/app/src/main/java/com/homelab/app/ui/proxmox/ProxmoxGuestDetailScreen.kt"
swift_core="HomelabSwift/Homelab/Models/ServiceType.swift"
swift_tests="HomelabSwift/HomelabTests/ModelDecodingTests.swift"
swift_store="HomelabSwift/Homelab/Stores/ServicesStore.swift"
swift_proxmox="HomelabSwift/Homelab/Views/Proxmox/ProxmoxGuestDetailView.swift"
architecture="docs/architecture/PHASE3_CONTROLLED_ACTIONS.md"

for required_file in "$android_core" "$android_tests" "$android_di" "$android_proxmox" "$android_proxmox_ui" "$swift_core" "$swift_tests" "$swift_store" "$swift_proxmox" "$architecture" "schemas/action.schema.json"; do
  test -s "$required_file"
done

for pattern in   'enum class ActionRisk' 'enum class ActionRole' 'data class ControlledActionRequest'   'object ControlledActionPolicy' 'class ControlledActionCoordinator'   'ActionPolicyOutcome.DRY_RUN_APPROVED' 'provider-write-capability-required'   'ProviderCapability.WRITE_ACTIONS' 'CancellationException' 'terminalResults'   'DurableActionQueueEntry' 'DurableActionQueueStore' 'ActionRetryPolicy'   'ActionExecutionState.MANUAL_REVIEW' 'interrupted-execution'   'automatic-retry-forbidden-' 'sanitizedForPersistence'
do
  grep -Fq "$pattern" "$android_core"
done

for pattern in   'DataStoreDurableActionQueueStore' 'controlled_action_queue_v1'   'provideDurableActionQueueStore' 'provideControlledActionCoordinator'
do
  grep -Fq "$pattern" "$android_di"
done

for pattern in   'enum ControlledActionRisk' 'enum ControlledActionRole' 'struct ControlledActionRequest'   'enum ControlledActionPolicy' 'actor ControlledActionCoordinator'   'case dryRunApproved' 'provider-write-capability-required'   'providerCapabilities.contains(.writeActions)' 'ControlledActionExecutionGate'   'terminalResults' 'DurableActionQueueEntry' 'DurableActionQueueStore'   'UserDefaultsDurableActionQueueStore' 'ActionRetryPolicy'   'case manualReview' 'interrupted-execution' 'automatic-retry-forbidden-'   'sanitizedForPersistence'
do
  grep -Fq "$pattern" "$swift_core"
done

grep -Fq 'UserDefaultsDurableActionQueueStore()' "$swift_store"

for pattern in   'dry run validates without invoking provider mutation'   'terminal idempotency result survives coordinator reconstruction'   'interrupted execution recovers as manual review'   'high risk transport failure requires manual review without retry'   'durable queue omits action parameters'
do
  grep -Fq "$pattern" "$android_tests"
done

for pattern in   'testControlledActionDryRunDoesNotInvokeMutation'   'testControlledActionTerminalResultSurvivesCoordinatorReconstruction'   'testControlledActionInterruptedExecutionRecoversAsManualReview'   'testControlledActionHighRiskTransportFailureRequiresManualReview'   'testControlledActionDurableQueueOmitsParameters'
do
  grep -Fq "$pattern" "$swift_tests"
done

for pattern in 'controlledActionCoordinator.execute' 'ActionRole.ADMIN' 'controlledRequest' 'providerDescriptor.capabilities'; do
  grep -Fq "$pattern" "$android_proxmox"
done
grep -Fq 'proxmox_confirm_action' "$android_proxmox_ui"

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
grep -Fq 'copy(parameters = emptyMap())' "$android_core"
grep -Fq 'copy.parameters = [:]' "$swift_core"

echo "Phase 3 controlled actions audit passed"
