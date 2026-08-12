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
android_portainer_models="HomelabAndroid/app/src/main/java/com/homelab/app/data/remote/dto/portainer/PortainerDto.kt"
android_portainer_list="HomelabAndroid/app/src/main/java/com/homelab/app/ui/portainer/ContainerListViewModel.kt"
android_portainer_detail="HomelabAndroid/app/src/main/java/com/homelab/app/ui/portainer/ContainerDetailViewModel.kt"
swift_portainer_models="HomelabSwift/Homelab/Models/Portainer/PortainerModels.swift"
swift_portainer_list="HomelabSwift/Homelab/Views/Portainer/ContainerListView.swift"
swift_portainer_detail="HomelabSwift/Homelab/Views/Portainer/ContainerDetailView.swift"
android_healthchecks_models="HomelabAndroid/app/src/main/java/com/homelab/app/data/remote/dto/healthchecks/HealthchecksDto.kt"
swift_healthchecks_models="HomelabSwift/Homelab/Models/Healthchecks/HealthchecksModels.swift"
android_healthchecks_detail="HomelabAndroid/app/src/main/java/com/homelab/app/ui/healthchecks/HealthchecksDetailViewModel.kt"
android_healthchecks_editor="HomelabAndroid/app/src/main/java/com/homelab/app/ui/healthchecks/HealthchecksEditorViewModel.kt"
android_healthchecks_ui="HomelabAndroid/app/src/main/java/com/homelab/app/ui/healthchecks/HealthchecksScreens.kt"
swift_healthchecks_detail="HomelabSwift/Homelab/Views/Healthchecks/HealthchecksDetail.swift"
swift_healthchecks_editor="HomelabSwift/Homelab/Views/Healthchecks/HealthchecksCheckEditor.swift"
architecture="docs/architecture/PHASE3_CONTROLLED_ACTIONS.md"

for required_file in "$android_core" "$android_tests" "$android_di" "$android_proxmox" "$android_proxmox_ui" "$swift_core" "$swift_tests" "$swift_store" "$swift_proxmox" "$android_portainer_models" "$android_portainer_list" "$android_portainer_detail" "$swift_portainer_models" "$swift_portainer_list" "$swift_portainer_detail" "$android_healthchecks_models" "$swift_healthchecks_models" "$android_healthchecks_detail" "$android_healthchecks_editor" "$android_healthchecks_ui" "$swift_healthchecks_detail" "$swift_healthchecks_editor" "$architecture" "schemas/action.schema.json"; do
  test -s "$required_file"
done

for pattern in   'enum class ActionRisk' 'enum class ActionRole' 'data class ControlledActionRequest'   'object ControlledActionPolicy' 'class ControlledActionCoordinator'   'ActionPolicyOutcome.DRY_RUN_APPROVED' 'provider-write-capability-required'   'ProviderCapability.WRITE_ACTIONS' 'CancellationException' 'terminalResults'   'DurableActionQueueEntry' 'DurableActionQueueStore' 'ActionRetryPolicy'   'ActionExecutionState.MANUAL_REVIEW' 'interrupted-execution'   'automatic-retry-forbidden-' 'terminal-persistence-failed' 'sanitizedForPersistence'
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

for pattern in 'container.start' 'container.stop' 'container.kill' 'ActionRisk.HIGH' 'requiresConfirmation' 'controlledRequest'; do
  grep -Fq "$pattern" "$android_portainer_models"
done
for file in "$android_portainer_list" "$android_portainer_detail"; do
  grep -Fq 'controlledActionCoordinator.execute' "$file"
  grep -Fq 'ProviderRegistry.capabilities(ServiceType.PORTAINER)' "$file"
done
for pattern in 'PortainerControlledContainerAction' 'case .kill, .remove: return .high' 'requiresConfirmation'; do
  grep -Fq "$pattern" "$swift_core"
done
grep -Fq 'controlledAction' "$swift_portainer_models"
for file in "$swift_portainer_list" "$swift_portainer_detail"; do
  grep -Fq 'controlledActionCoordinator.execute' "$file"
  grep -Fq 'ProviderRegistry.descriptor(for: .portainer).capabilities' "$file"
done
grep -Fq 'testPortainerContainerActionsHaveStableRiskClassificationAndIdentity' "$swift_tests"
grep -Fq 'portainer container actions have stable risk classification and identity' "$android_tests"

for pattern in 'check.create' 'check.update' 'check.channels.update' 'check.pause' 'check.resume' 'check.delete' 'ActionRisk.HIGH' 'controlledRequest'; do
  grep -Fq "$pattern" "$android_healthchecks_models"
done
for pattern in 'controlledActionCoordinator.execute' 'ProviderRegistry.capabilities(ServiceType.HEALTHCHECKS)' 'confirmed: Boolean'; do
  grep -Fq "$pattern" "$android_healthchecks_detail"
done
for pattern in 'controlledActionCoordinator.execute' 'HealthchecksControlledCheckAction.CREATE' 'HealthchecksControlledCheckAction.UPDATE' 'confirmed: Boolean'; do
  grep -Fq "$pattern" "$android_healthchecks_editor"
done
for pattern in 'showToggleDialog' 'togglePause(confirmed = true)' 'deleteCheck(confirmed = true'; do
  grep -Fq "$pattern" "$android_healthchecks_ui"
done
grep -Fq 'struct HealthchecksCheckPayload: Encodable, Sendable' "$swift_healthchecks_models"
for pattern in 'HealthchecksControlledCheckAction' 'case .create, .update, .updateChannels, .pause, .resume: return .medium' 'case .delete: return .high'; do
  grep -Fq "$pattern" "$swift_core"
done
for pattern in 'controlledActionCoordinator.execute' 'ProviderRegistry.descriptor(for: .healthchecks).capabilities' 'confirmed: true' 'HealthchecksControlledCheckAction.updateChannels'; do
  grep -Fq "$pattern" "$swift_healthchecks_detail"
done
for pattern in 'controlledActionCoordinator.execute' 'HealthchecksControlledCheckAction' 'confirmed: true' 'existingUUID == nil ? .create : .update'; do
  grep -Fq "$pattern" "$swift_healthchecks_editor"
done
grep -Fq 'testHealthchecksCheckActionsHaveStableRiskClassificationAndIdentity' "$swift_tests"
grep -Fq 'healthchecks check actions have stable risk classification and identity' "$android_tests"

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
