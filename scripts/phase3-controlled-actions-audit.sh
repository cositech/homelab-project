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
android_adguard_models="HomelabAndroid/app/src/main/java/com/homelab/app/data/remote/dto/adguard/AdGuardDto.kt"
android_adguard_view_model="HomelabAndroid/app/src/main/java/com/homelab/app/ui/adguard/AdGuardHomeViewModel.kt"
swift_adguard_dashboard="HomelabSwift/Homelab/Views/AdGuardHome/AdGuardHomeDashboard.swift"
android_pihole_models="HomelabAndroid/app/src/main/java/com/homelab/app/data/remote/dto/pihole/PiholeDomainDto.kt"
android_pihole_view_model="HomelabAndroid/app/src/main/java/com/homelab/app/ui/pihole/PiholeViewModel.kt"
android_pihole_ui="HomelabAndroid/app/src/main/java/com/homelab/app/ui/pihole/PiholeDomainListScreen.kt"
android_pihole_api="HomelabAndroid/app/src/main/java/com/homelab/app/data/remote/api/PiholeApi.kt"
android_pihole_repository="HomelabAndroid/app/src/main/java/com/homelab/app/data/repository/PiholeRepository.kt"
android_fallback_interceptor="HomelabAndroid/app/src/main/java/com/homelab/app/data/remote/SmartFallbackInterceptor.kt"
android_fallback_tests="HomelabAndroid/app/src/test/java/com/homelab/app/data/remote/SmartFallbackInterceptorTest.kt"
swift_pihole_models="HomelabSwift/Homelab/Models/PiholeDomain.swift"
swift_pihole_ui="HomelabSwift/Homelab/Views/PiHole/PiholeDomainListView.swift"
swift_pihole_api="HomelabSwift/Homelab/Networking/PiHole/PiHoleAPIClient.swift"
android_healthchecks_models="HomelabAndroid/app/src/main/java/com/homelab/app/data/remote/dto/healthchecks/HealthchecksDto.kt"
swift_healthchecks_models="HomelabSwift/Homelab/Models/Healthchecks/HealthchecksModels.swift"
android_healthchecks_detail="HomelabAndroid/app/src/main/java/com/homelab/app/ui/healthchecks/HealthchecksDetailViewModel.kt"
android_healthchecks_editor="HomelabAndroid/app/src/main/java/com/homelab/app/ui/healthchecks/HealthchecksEditorViewModel.kt"
android_healthchecks_ui="HomelabAndroid/app/src/main/java/com/homelab/app/ui/healthchecks/HealthchecksScreens.kt"
swift_healthchecks_detail="HomelabSwift/Homelab/Views/Healthchecks/HealthchecksDetail.swift"
swift_healthchecks_editor="HomelabSwift/Homelab/Views/Healthchecks/HealthchecksCheckEditor.swift"
architecture="docs/architecture/PHASE3_CONTROLLED_ACTIONS.md"

for required_file in "$android_core" "$android_tests" "$android_di" "$android_proxmox" "$android_proxmox_ui" "$swift_core" "$swift_tests" "$swift_store" "$swift_proxmox" "$android_portainer_models" "$android_portainer_list" "$android_portainer_detail" "$swift_portainer_models" "$swift_portainer_list" "$swift_portainer_detail" "$android_adguard_models" "$android_adguard_view_model" "$swift_adguard_dashboard" "$android_pihole_models" "$android_pihole_view_model" "$android_pihole_ui" "$android_pihole_api" "$android_pihole_repository" "$android_fallback_interceptor" "$android_fallback_tests" "$swift_pihole_models" "$swift_pihole_ui" "$swift_pihole_api" "$android_healthchecks_models" "$swift_healthchecks_models" "$android_healthchecks_detail" "$android_healthchecks_editor" "$android_healthchecks_ui" "$swift_healthchecks_detail" "$swift_healthchecks_editor" "$architecture" "schemas/action.schema.json"; do
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

for pattern in 'protection.enable' 'protection.disable' 'ActionRisk.LOW' 'ActionRisk.MEDIUM' 'controlledRequest'; do
  grep -Fq "$pattern" "$android_adguard_models"
done
for pattern in 'controlledActionCoordinator.execute' 'ProviderRegistry.capabilities(ServiceType.ADGUARD_HOME)' 'confirmed = !enabled'; do
  grep -Fq "$pattern" "$android_adguard_view_model"
done
for pattern in 'AdGuardControlledProtectionAction' 'case .adguardHome:' 'capabilities = [.health, .writeActions]'; do
  grep -Fq "$pattern" "$swift_core"
done
for pattern in 'controlledActionCoordinator.execute' 'ProviderRegistry.descriptor(for: .adguardHome).capabilities' 'confirmed: !enabled'; do
  grep -Fq "$pattern" "$swift_adguard_dashboard"
done
grep -Fq 'adguard protection actions have stable risk classification and identity' "$android_tests"
grep -Fq 'testAdGuardProtectionActionsHaveStableRiskClassificationAndIdentity' "$swift_tests"

for pattern in 'domain.add' 'domain.remove' 'ActionRisk.HIGH' 'ActionRisk.MEDIUM' 'controlledRequest'; do
  grep -Fq "$pattern" "$android_pihole_models"
done
for pattern in 'controlledActionCoordinator.execute' 'ProviderRegistry.capabilities(ServiceType.PIHOLE)'; do
  grep -Fq "$pattern" "$android_pihole_view_model"
done
for pattern in 'pendingRemoval' 'confirmed = true'; do
  grep -Fq "$pattern" "$android_pihole_ui"
done
for pattern in 'PiholeControlledDomainAction' 'case add, remove' '"domain." + rawValue'; do
  grep -Fq "$pattern" "$swift_pihole_models"
done
for pattern in 'controlledActionCoordinator.execute' 'ProviderRegistry.descriptor(for: .pihole).capabilities' 'allowsFullSwipe: false' 'confirmed: true'; do
  grep -Fq "$pattern" "$swift_pihole_ui"
done
grep -Fq 'pihole domain actions require confirmation and have stable identity' "$android_tests"
grep -Fq 'testPiholeDomainActionsRequireConfirmationAndHaveStableIdentity' "$swift_tests"
for pattern in 'X-Homelab-No-Fallback' '!noFallback'; do
  grep -Fq "$pattern" "$android_fallback_interceptor"
done
grep -Fq 'no fallback header prevents mutation replay and is not sent upstream' "$android_fallback_tests"
grep -Fq '@Header("X-Homelab-No-Fallback")' "$android_pihole_api"
for pattern in 'shouldUseLegacyDomainMutation' 'setOf(404, 405, 501)' 'if (!shouldUseLegacyDomainMutation(e)) throw e'; do
  grep -Fq "$pattern" "$android_pihole_repository"
done
grep -Fq 'lowercase(Locale.ROOT)' "$android_pihole_models"
for pattern in 'shouldUseLegacyDomainMutation' '[404, 405, 501]' 'fallbackURL: ""'; do
  grep -Fq "$pattern" "$swift_pihole_api"
done
for pattern in 'let selectedList = selectedTab' 'client.addDomain(domain: normalizedDomain, to: selectedList)' '.whitespacesAndNewlines'; do
  grep -Fq "$pattern" "$swift_pihole_ui"
done
grep -Fq 'testPiholeLegacyMutationFallbackRequiresUnsupportedEndpointStatus' "$swift_tests"
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
for pattern in 'HealthchecksControlledCheckAction' 'case .update, .updateChannels, .pause, .resume: return .medium' 'case .create, .delete: return .high'; do
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

android_dockhand_models="HomelabAndroid/app/src/main/java/com/homelab/app/data/repository/DockhandRepository.kt"
android_dockhand_view_model="HomelabAndroid/app/src/main/java/com/homelab/app/ui/dockhand/DockhandViewModel.kt"
android_dockhand_ui="HomelabAndroid/app/src/main/java/com/homelab/app/ui/dockhand/DockhandDashboardScreen.kt"
android_dockhand_api="HomelabAndroid/app/src/main/java/com/homelab/app/data/remote/api/DockhandApi.kt"
swift_dockhand_ui="HomelabSwift/Homelab/Views/Dockhand/DockhandDashboard.swift"
swift_dockhand_api="HomelabSwift/Homelab/Networking/LinuxUpdate/LinuxUpdateAPIClient.swift"

for required_file in "$android_dockhand_models" "$android_dockhand_view_model" "$android_dockhand_ui" "$android_dockhand_api" "$swift_dockhand_ui" "$swift_dockhand_api"; do
  test -s "$required_file"
done
for pattern in 'container.start' 'container.stop' 'container.restart' 'stack.start' 'stack.stop' 'stack.restart' 'ActionRisk.MEDIUM' 'requiresConfirmation' 'controlledRequest' 'lowercase(Locale.ROOT)'; do
  grep -Fq "$pattern" "$android_dockhand_models"
done
for pattern in 'controlledActionCoordinator.execute' 'ProviderRegistry.capabilities(ServiceType.DOCKHAND)' 'confirmed: Boolean' 'ActionFailureDisposition.NON_RETRYABLE' 'dockhand-provider-reported-failure' 'dockhand-outcome-indeterminate'; do
  grep -Fq "$pattern" "$android_dockhand_view_model"
done
for pattern in 'pendingContainerAction' 'pendingStackAction' 'confirmed = true'; do
  grep -Fq "$pattern" "$android_dockhand_ui"
done
test "$(grep -Fc '@Header("X-Homelab-No-Fallback")' "$android_dockhand_api")" -eq 6
grep -Fq 'dockhand lifecycle actions have stable risk classification and identity' "$android_tests"
grep -Fq 'dockhand indeterminate mutation is non retryable' "$android_tests"
for pattern in 'DockhandControlledAction' 'case containerStart = "container.start"' 'case stackRestart = "stack.restart"' 'case .healthchecks, .dockhand, .dockmon:'; do
  grep -Fq "$pattern" "$swift_core"
done
for pattern in 'PendingDockhandAction' 'controlledActionCoordinator.execute' 'ProviderRegistry.descriptor(for: .dockhand).capabilities' 'confirmed: true' '.nonRetryable' 'dockhand-provider-reported-failure' 'dockhand-outcome-indeterminate'; do
  grep -Fq "$pattern" "$swift_dockhand_ui"
done
test "$(grep -Fc 'allowFallback: false' "$swift_dockhand_api")" -eq 2
grep -Fq 'testDockhandLifecycleActionsHaveStableRiskClassificationAndIdentity' "$swift_tests"
grep -Fq 'testDockhandIndeterminateMutationIsNonRetryable' "$swift_tests"

android_dockmon_models="HomelabAndroid/app/src/main/java/com/homelab/app/data/repository/DockmonRepository.kt"
android_dockmon_view_model="HomelabAndroid/app/src/main/java/com/homelab/app/ui/dockmon/DockmonViewModel.kt"
android_dockmon_ui="HomelabAndroid/app/src/main/java/com/homelab/app/ui/dockmon/DockmonDashboardScreen.kt"
android_dockmon_api="HomelabAndroid/app/src/main/java/com/homelab/app/data/remote/api/DockmonApi.kt"
swift_dockmon_ui="HomelabSwift/Homelab/Views/Dockmon/DockmonDashboard.swift"
swift_dockmon_api="HomelabSwift/Homelab/Networking/Dockmon/DockmonAPIClient.swift"

for required_file in "$android_dockmon_models" "$android_dockmon_view_model" "$android_dockmon_ui" "$android_dockmon_api" "$swift_dockmon_ui" "$swift_dockmon_api"; do
  test -s "$required_file"
done
for pattern in 'container.restart' 'container.update' 'ActionRisk.MEDIUM' 'ActionRisk.HIGH' 'requiresConfirmation' 'controlledRequest' 'lowercase(Locale.ROOT)'; do
  grep -Fq "$pattern" "$android_dockmon_models"
done
for pattern in 'controlledActionCoordinator.execute' 'ProviderRegistry.capabilities(ServiceType.DOCKMON)' 'confirmed: Boolean' 'ActionFailureDisposition.NON_RETRYABLE' 'dockmon-provider-reported-failure' 'dockmon-outcome-indeterminate'; do
  grep -Fq "$pattern" "$android_dockmon_view_model"
done
for pattern in 'pendingAction' 'DockmonControlledAction.RESTART' 'confirmed = true'; do
  grep -Fq "$pattern" "$android_dockmon_ui"
done
test "$(grep -Fc '@Header("X-Homelab-No-Fallback")' "$android_dockmon_api")" -eq 2
grep -Fq 'dockmon actions have stable risk classification and identity' "$android_tests"
grep -Fq 'dockmon indeterminate mutation is non retryable' "$android_tests"
for pattern in 'DockmonControlledAction' 'case restart = "container.restart"' 'case update = "container.update"' 'case .healthchecks, .dockhand, .dockmon:'; do
  grep -Fq "$pattern" "$swift_core"
done
for pattern in 'pendingAction' 'controlledActionCoordinator.execute' 'ProviderRegistry.descriptor(for: .dockmon).capabilities' 'confirmed: true' '.nonRetryable' 'dockmon-provider-reported-failure' 'dockmon-outcome-indeterminate'; do
  grep -Fq "$pattern" "$swift_dockmon_ui"
done
test "$(grep -Fc 'fallbackURL: ""' "$swift_dockmon_api")" -eq 1
grep -Fq 'testDockmonActionsHaveStableRiskClassificationAndIdentity' "$swift_tests"
grep -Fq 'testDockmonIndeterminateMutationIsNonRetryable' "$swift_tests"

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
