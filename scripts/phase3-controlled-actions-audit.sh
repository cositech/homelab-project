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
android_portainer_api="HomelabAndroid/app/src/main/java/com/homelab/app/data/remote/api/PortainerApi.kt"
swift_portainer_api="HomelabSwift/Homelab/Networking/Portainer/PortainerAPIClient.swift"
swift_portainer_models="HomelabSwift/Homelab/Models/Portainer/PortainerModels.swift"
swift_portainer_list="HomelabSwift/Homelab/Views/Portainer/ContainerListView.swift"
swift_portainer_detail="HomelabSwift/Homelab/Views/Portainer/ContainerDetailView.swift"
android_adguard_models="HomelabAndroid/app/src/main/java/com/homelab/app/data/remote/dto/adguard/AdGuardDto.kt"
android_adguard_view_model="HomelabAndroid/app/src/main/java/com/homelab/app/ui/adguard/AdGuardHomeViewModel.kt"
android_adguard_api="HomelabAndroid/app/src/main/java/com/homelab/app/data/remote/api/AdGuardHomeApi.kt"
swift_adguard_api="HomelabSwift/Homelab/Networking/AdGuardHome/AdGuardHomeAPIClient.swift"
swift_adguard_dashboard="HomelabSwift/Homelab/Views/AdGuardHome/AdGuardHomeDashboard.swift"
swift_adguard_filters="HomelabSwift/Homelab/Views/AdGuardHome/AdGuardHomeFiltersView.swift"
swift_adguard_rewrites="HomelabSwift/Homelab/Views/AdGuardHome/AdGuardHomeRewritesView.swift"
swift_adguard_blocked_services="HomelabSwift/Homelab/Views/AdGuardHome/AdGuardHomeBlockedServicesView.swift"
swift_adguard_user_rules="HomelabSwift/Homelab/Views/AdGuardHome/AdGuardHomeUserRulesView.swift"
swift_adguard_query_log="HomelabSwift/Homelab/Views/AdGuardHome/AdGuardHomeQueryLogView.swift"
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
android_technitium_models="HomelabAndroid/app/src/main/java/com/homelab/app/data/repository/TechnitiumRepository.kt"
android_technitium_view_model="HomelabAndroid/app/src/main/java/com/homelab/app/ui/technitium/TechnitiumViewModel.kt"
android_technitium_ui="HomelabAndroid/app/src/main/java/com/homelab/app/ui/technitium/TechnitiumDashboardScreen.kt"
android_technitium_api="HomelabAndroid/app/src/main/java/com/homelab/app/data/remote/api/TechnitiumApi.kt"
swift_technitium_ui="HomelabSwift/Homelab/Views/Technitium/TechnitiumDashboard.swift"
swift_technitium_api="HomelabSwift/Homelab/Networking/Technitium/TechnitiumAPIClient.swift"
architecture="docs/architecture/PHASE3_CONTROLLED_ACTIONS.md"

for required_file in "$android_core" "$android_tests" "$android_di" "$android_proxmox" "$android_proxmox_ui" "$swift_core" "$swift_tests" "$swift_store" "$swift_proxmox" "$android_portainer_models" "$android_portainer_list" "$android_portainer_detail" "$android_portainer_api" "$swift_portainer_api" "$swift_portainer_models" "$swift_portainer_list" "$swift_portainer_detail" "$android_adguard_models" "$android_adguard_view_model" "$swift_adguard_dashboard" "$android_pihole_models" "$android_pihole_view_model" "$android_pihole_ui" "$android_pihole_api" "$android_pihole_repository" "$android_fallback_interceptor" "$android_fallback_tests" "$swift_pihole_models" "$swift_pihole_ui" "$swift_pihole_api" "$android_healthchecks_models" "$swift_healthchecks_models" "$android_healthchecks_detail" "$android_healthchecks_editor" "$android_healthchecks_ui" "$swift_healthchecks_detail" "$swift_healthchecks_editor" "$android_technitium_models" "$android_technitium_view_model" "$android_technitium_ui" "$android_technitium_api" "$swift_technitium_ui" "$swift_technitium_api" "$architecture" "schemas/action.schema.json"; do
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
for pattern in 'PortainerControlledConfigurationAction' 'container.rename' 'stack.update' 'ActionRisk.MEDIUM' 'ActionRisk.HIGH'; do
  grep -Fq "$pattern" "$android_portainer_models"
done
test "$(grep -Fc '@Header("X-Homelab-No-Fallback")' "$android_portainer_api")" -eq 2
for pattern in 'PortainerControlledConfigurationAction' 'case renameContainer = "container.rename"' 'case updateStack = "stack.update"' 'case .updateStack: return .high'; do
  grep -Fq "$pattern" "$swift_core"
done
for pattern in 'pendingConfigurationAction' 'executeControlledConfigurationAction' 'confirmed: true' 'ProviderRegistry.descriptor(for: .portainer).capabilities' 'PortainerControlledOperationFailure.map'; do
  grep -Fq "$pattern" "$swift_portainer_detail"
done
for pattern in 'reachableMutationBaseURL' 'pingURL("\(baseURL)/api/status"' 'pingURL("\(fallbackURL)/api/status"' 'baseURL: mutationBaseURL, fallbackURL: ""'; do
  grep -Fq "$pattern" "$swift_portainer_api"
done
for pattern in 'portainer-outcome-indeterminate' 'portainer-unauthorized' 'portainer-http-' 'portainer-provider-reported-failure' '.nonRetryable'; do
  grep -Fq "$pattern" "$swift_core"
done
test "$(grep -Fc 'fallbackURL: ""' "$swift_portainer_api")" -eq 3
grep -Fq 'portainer configuration actions require confirmation and have stable identity' "$android_tests"
grep -Fq 'testPortainerConfigurationActionsRequireConfirmationAndHaveStableIdentity' "$swift_tests"

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
for pattern in 'AdGuardControlledConfigurationAction' 'filter-list.update' 'blocked-services.update' 'rewrite.create' 'ActionRisk.HIGH'; do
  grep -Fq "$pattern" "$android_adguard_models"
done
for pattern in 'UPDATE_USER_RULES' 'UPDATE_FILTER' 'UPDATE_BLOCKED_SERVICES' 'CREATE_REWRITE' 'executeControlledConfigurationAction'; do
  grep -Fq "$pattern" "$android_adguard_view_model"
done
for file in "$swift_adguard_filters" "$swift_adguard_rewrites" "$swift_adguard_blocked_services" "$swift_adguard_user_rules" "$swift_adguard_query_log"; do
  grep -Fq 'controlledActionCoordinator.execute' "$file"
  grep -Fq 'ProviderRegistry.descriptor(for: .adguardHome).capabilities' "$file"
done
grep -Fq 'adguard configuration actions are high risk and have stable identity' "$android_tests"
grep -Fq 'testAdGuardConfigurationActionsAreHighRiskAndHaveStableIdentity' "$swift_tests"
test "$(grep -Fc 'X-Homelab-No-Fallback' "$android_adguard_api")" -eq 10
test "$(grep -Fc 'fallbackURL: ""' "$swift_adguard_api")" -eq 9

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
for pattern in 'DockhandControlledAction' 'case containerStart = "container.start"' 'case stackRestart = "stack.restart"' 'case .healthchecks, .dockhand, .dockmon, .linuxUpdate, .komodo, .nginxProxyManager:'; do
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
for pattern in 'pendingAction' 'DockmonControlledAction.RESTART' 'confirmed = true' 'LaunchedEffect(selectedContainerId)' 'pendingAction = null'; do
  grep -Fq "$pattern" "$android_dockmon_ui"
done
test "$(grep -Fc '@Header("X-Homelab-No-Fallback")' "$android_dockmon_api")" -eq 2
grep -Fq 'dockmon actions have stable risk classification and identity' "$android_tests"
grep -Fq 'dockmon indeterminate mutation is non retryable' "$android_tests"
for pattern in 'DockmonControlledAction' 'case restart = "container.restart"' 'case update = "container.update"' 'case .healthchecks, .dockhand, .dockmon, .linuxUpdate, .komodo, .nginxProxyManager:'; do
  grep -Fq "$pattern" "$swift_core"
done
for pattern in 'pendingAction' 'actionConfirmMessage' 'controlledActionCoordinator.execute' 'ProviderRegistry.descriptor(for: .dockmon).capabilities' 'confirmed: true' '.nonRetryable' 'dockmon-provider-reported-failure' 'dockmon-outcome-indeterminate'; do
  grep -Fq "$pattern" "$swift_dockmon_ui"
done
test "$(grep -Fc 'fallbackURL: ""' "$swift_dockmon_api")" -eq 1
grep -Fq 'testDockmonActionsHaveStableRiskClassificationAndIdentity' "$swift_tests"
grep -Fq 'testDockmonIndeterminateMutationIsNonRetryable' "$swift_tests"

android_komodo_models="HomelabAndroid/app/src/main/java/com/homelab/app/data/repository/KomodoRepository.kt"
android_komodo_view_model="HomelabAndroid/app/src/main/java/com/homelab/app/ui/komodo/KomodoViewModel.kt"
android_komodo_ui="HomelabAndroid/app/src/main/java/com/homelab/app/ui/komodo/KomodoDashboardScreen.kt"
android_komodo_api="HomelabAndroid/app/src/main/java/com/homelab/app/data/remote/api/KomodoApi.kt"
swift_komodo_models="HomelabSwift/Homelab/Models/Komodo/KomodoModels.swift"
swift_komodo_ui="HomelabSwift/Homelab/Views/Komodo/KomodoDashboard.swift"
swift_komodo_api="HomelabSwift/Homelab/Networking/Komodo/KomodoAPIClient.swift"

for required_file in "$android_komodo_models" "$android_komodo_view_model" "$android_komodo_ui" "$android_komodo_api" "$swift_komodo_models" "$swift_komodo_ui" "$swift_komodo_api"; do
  test -s "$required_file"
done
for pattern in 'stack.deploy' 'stack.start' 'stack.stop' 'stack.restart' 'ActionRisk.HIGH' 'ActionRisk.MEDIUM' 'requiresConfirmation' 'controlledRequest' 'lowercase(Locale.ROOT)'; do
  grep -Fq "$pattern" "$android_komodo_models"
done
for pattern in 'controlledActionCoordinator.execute' 'ProviderRegistry.capabilities(ServiceType.KOMODO)' 'confirmed: Boolean' 'ActionFailureDisposition.NON_RETRYABLE' 'komodo-outcome-indeterminate'; do
  grep -Fq "$pattern" "$android_komodo_view_model"
done
for pattern in 'PendingKomodoStackAction' 'komodo_confirm_action_message' 'confirmed = true'; do
  grep -Fq "$pattern" "$android_komodo_ui"
done
test "$(grep -Fc '@Header("X-Homelab-No-Fallback")' "$android_komodo_api")" -eq 4
grep -Fq 'komodo stack actions have stable risk classification and identity' "$android_tests"
grep -Fq 'komodo indeterminate mutation is non retryable' "$android_tests"
for pattern in 'enum KomodoStackAction' 'case deploy = "stack.deploy"' 'case restart = "stack.restart"' 'var requiresConfirmation: Bool'; do
  grep -Fq "$pattern" "$swift_komodo_models"
done
for pattern in 'PendingKomodoStackAction' 'actionConfirmMessage' 'controlledActionCoordinator.execute' 'ProviderRegistry.descriptor(for: .komodo).capabilities' 'confirmed: true' '.nonRetryable' 'komodo-outcome-indeterminate'; do
  grep -Fq "$pattern" "$swift_komodo_ui"
done
test "$(grep -Fc 'fallbackURL: ""' "$swift_komodo_api")" -eq 1
grep -Fq 'testKomodoStackActionsHaveStableRiskClassificationAndIdentity' "$swift_tests"
grep -Fq 'testKomodoIndeterminateMutationIsNonRetryable' "$swift_tests"

android_pterodactyl_models="HomelabAndroid/app/src/main/java/com/homelab/app/data/repository/PterodactylRepository.kt"
android_pterodactyl_view_model="HomelabAndroid/app/src/main/java/com/homelab/app/ui/pterodactyl/PterodactylViewModel.kt"
android_pterodactyl_ui="HomelabAndroid/app/src/main/java/com/homelab/app/ui/pterodactyl/PterodactylDashboardScreen.kt"
android_pterodactyl_api="HomelabAndroid/app/src/main/java/com/homelab/app/data/remote/api/PterodactylApi.kt"
android_calagopus_models="HomelabAndroid/app/src/main/java/com/homelab/app/data/repository/CalagopusRepository.kt"
android_calagopus_view_model="HomelabAndroid/app/src/main/java/com/homelab/app/ui/calagopus/CalagopusViewModel.kt"
android_calagopus_ui="HomelabAndroid/app/src/main/java/com/homelab/app/ui/calagopus/CalagopusDashboardScreen.kt"
android_calagopus_api="HomelabAndroid/app/src/main/java/com/homelab/app/data/remote/api/CalagopusApi.kt"
swift_pterodactyl_api="HomelabSwift/Homelab/Networking/Pterodactyl/PterodactylAPIClient.swift"
swift_pterodactyl_ui="HomelabSwift/Homelab/Views/Pterodactyl/PterodactylDashboard.swift"
swift_calagopus_api="HomelabSwift/Homelab/Networking/Calagopus/CalagopusAPIClient.swift"
swift_calagopus_ui="HomelabSwift/Homelab/Views/Calagopus/CalagopusDashboard.swift"

for required_file in "$android_pterodactyl_models" "$android_pterodactyl_view_model" "$android_pterodactyl_ui" "$android_pterodactyl_api" "$android_calagopus_models" "$android_calagopus_view_model" "$android_calagopus_ui" "$android_calagopus_api" "$swift_pterodactyl_api" "$swift_pterodactyl_ui" "$swift_calagopus_api" "$swift_calagopus_ui"; do
  test -s "$required_file"
done
for pattern in 'server.power.$signal' 'ActionRisk.LOW' 'ActionRisk.MEDIUM' 'ActionRisk.HIGH' 'requiresConfirmation' 'controlledRequest' 'lowercase(Locale.ROOT)'; do
  grep -Fq "$pattern" "$android_pterodactyl_models"
  grep -Fq "$pattern" "$android_calagopus_models"
done
for provider in pterodactyl calagopus; do
  eval view_model="\$android_${provider}_view_model"
  eval ui="\$android_${provider}_ui"
  eval api="\$android_${provider}_api"
  grep -Fq 'controlledActionCoordinator.execute' "$view_model"
  grep -Fq 'ActionFailureDisposition.NON_RETRYABLE' "$view_model"
  grep -Fq "${provider}-outcome-indeterminate" "$view_model"
  grep -Fq 'PendingPowerAction' "$ui"
  grep -Fq 'game_server_confirm_action_message' "$ui"
  grep -Fq 'confirmed = true' "$ui"
  test "$(grep -Fc '@Header("X-Homelab-No-Fallback")' "$api")" -eq 1
done
grep -Fq 'ProviderRegistry.capabilities(ServiceType.PTERODACTYL)' "$android_pterodactyl_view_model"
grep -Fq 'ProviderRegistry.capabilities(ServiceType.CALAGOPUS)' "$android_calagopus_view_model"
grep -Fq 'game server power actions have stable risk classification and identity' "$android_tests"
grep -Fq 'game server indeterminate mutations are non retryable' "$android_tests"
for pattern in 'ControlledActionRisk' 'requiresConfirmation' 'server.power.' 'fallbackURL: ""'; do
  grep -Fq "$pattern" "$swift_pterodactyl_api"
  grep -Fq "$pattern" "$swift_calagopus_api"
done
for provider in pterodactyl calagopus; do
  eval ui="\$swift_${provider}_ui"
  grep -Fq 'actionConfirmMessage' "$ui"
  grep -Fq 'controlledActionCoordinator.execute' "$ui"
  grep -Fq "ProviderRegistry.descriptor(for: .${provider}).capabilities" "$ui"
  grep -Fq 'confirmed: true' "$ui"
  grep -Fq '.nonRetryable' "$ui"
  grep -Fq "${provider}-outcome-indeterminate" "$ui"
done
grep -Fq 'testGameServerPowerActionsHaveStableRiskClassificationAndIdentity' "$swift_tests"
grep -Fq 'testPterodactylIndeterminateMutationIsNonRetryable' "$swift_tests"
grep -Fq 'testCalagopusIndeterminateMutationIsNonRetryable' "$swift_tests"

for pattern in 'blocking.enable' 'blocking.disable' 'blocking.disable-temporary' 'blocklist.refresh' 'blocked-domain.add' 'blocked-domain.remove' 'ActionRisk.HIGH' 'requiresConfirmation' 'controlledRequest' 'lowercase(Locale.ROOT)'; do
  grep -Fq "$pattern" "$android_technitium_models"
done
for pattern in 'controlledActionCoordinator.execute' 'ProviderRegistry.capabilities(ServiceType.TECHNITIUM)' 'confirmed: Boolean' 'ActionFailureDisposition.NON_RETRYABLE' 'technitium-provider-reported-failure' 'technitium-outcome-indeterminate'; do
  grep -Fq "$pattern" "$android_technitium_view_model"
done
for pattern in 'pendingRemoval' 'confirmed = true' 'technitium_controlled_action_confirmation'; do
  grep -Fq "$pattern" "$android_technitium_ui"
done
test "$(grep -Fc '@Header("X-Homelab-No-Fallback")' "$android_technitium_api")" -eq 5
grep -Fq 'technitium actions have stable risk classification and identity' "$android_tests"
grep -Fq 'technitium indeterminate mutation is non retryable' "$android_tests"
for pattern in 'TechnitiumControlledAction' 'case enableBlocking = "blocking.enable"' 'case addBlockedDomain = "blocked-domain.add"' 'case .technitium:' 'capabilities = [.health, .writeActions]'; do
  grep -Fq "$pattern" "$swift_core"
done
for pattern in 'PendingTechnitiumAction' 'actionConfirmMessage' 'controlledActionCoordinator.execute' 'ProviderRegistry.descriptor(for: .technitium).capabilities' 'confirmed: true' '.nonRetryable' 'technitium-provider-reported-failure' 'technitium-outcome-indeterminate'; do
  grep -Fq "$pattern" "$swift_technitium_ui"
done
grep -Fq 'requestMutationPayload' "$swift_technitium_api"
test "$(grep -Fc 'fallbackURL: ""' "$swift_technitium_api")" -eq 1
grep -Fq 'testTechnitiumActionsHaveStableRiskClassificationAndIdentity' "$swift_tests"
grep -Fq 'testTechnitiumIndeterminateMutationIsNonRetryable' "$swift_tests"

android_linux_update_models="HomelabAndroid/app/src/main/java/com/homelab/app/data/repository/LinuxUpdateRepository.kt"
android_linux_update_view_model="HomelabAndroid/app/src/main/java/com/homelab/app/ui/linux_update/LinuxUpdateViewModel.kt"
android_linux_update_ui="HomelabAndroid/app/src/main/java/com/homelab/app/ui/linux_update/LinuxUpdateDashboardScreen.kt"
android_linux_update_api="HomelabAndroid/app/src/main/java/com/homelab/app/data/remote/api/LinuxUpdateApi.kt"
swift_linux_update_ui="HomelabSwift/Homelab/Views/LinuxUpdate/LinuxUpdateDashboard.swift"
swift_linux_update_api="HomelabSwift/Homelab/Networking/LinuxUpdate/LinuxUpdateAPIClient.swift"

for required_file in "$android_linux_update_models" "$android_linux_update_view_model" "$android_linux_update_ui" "$android_linux_update_api" "$swift_linux_update_ui" "$swift_linux_update_api"; do
  test -s "$required_file"
done
for pattern in 'systems.check-all' 'cache.refresh' 'system.check' 'package.upgrade' 'system.upgrade' 'system.full-upgrade' 'system.reboot' 'ActionRisk.HIGH' 'requiresConfirmation' 'controlledRequest' 'lowercase(Locale.ROOT)'; do
  grep -Fq "$pattern" "$android_linux_update_models"
done
for pattern in 'controlledActionCoordinator.execute' 'ProviderRegistry.capabilities(ServiceType.LINUX_UPDATE)' 'confirmed: Boolean' 'ActionFailureDisposition.NON_RETRYABLE' 'linux-update-provider-reported-failure' 'linux-update-outcome-indeterminate'; do
  grep -Fq "$pattern" "$android_linux_update_view_model"
done
for pattern in 'PendingLinuxUpdateAction' 'linux_update_confirm_action_message' 'confirmed = true' 'action.requiresConfirmation'; do
  grep -Fq "$pattern" "$android_linux_update_ui"
done
test "$(grep -Fc '@Header("X-Homelab-No-Fallback")' "$android_linux_update_api")" -eq 8
grep -Fq '.addHeader("X-Homelab-No-Fallback", "true")' "$android_linux_update_models"
grep -Fq 'linux update actions have stable risk classification and identity' "$android_tests"
grep -Fq 'linux update indeterminate mutation is non retryable' "$android_tests"
for pattern in 'LinuxUpdateControlledAction' 'case checkAll = "systems.check-all"' 'case reboot = "system.reboot"' 'case .healthchecks, .dockhand, .dockmon, .linuxUpdate, .komodo, .nginxProxyManager:'; do
  grep -Fq "$pattern" "$swift_core"
done
for pattern in 'PendingLinuxUpdateAction' 'actionConfirmMessage' 'controlledActionCoordinator.execute' 'ProviderRegistry.descriptor(for: .linuxUpdate).capabilities' 'confirmed: true' '.nonRetryable' 'linux-update-provider-reported-failure' 'linux-update-outcome-indeterminate'; do
  grep -Fq "$pattern" "$swift_linux_update_ui"
done
test "$(grep -Fc 'fallbackURL: ""' "$swift_linux_update_api")" -ge 8
for pattern in 'return try await self.startUpgradePackages' 'return try await self.startUpgradePackageAlias' 'throw error'; do
  grep -Fq "$pattern" "$swift_linux_update_api"
done
grep -Fq 'testLinuxUpdateActionsHaveStableRiskClassificationAndIdentity' "$swift_tests"
grep -Fq 'testLinuxUpdateIndeterminateMutationIsNonRetryable' "$swift_tests"


android_npm_models="HomelabAndroid/app/src/main/java/com/homelab/app/data/repository/NginxProxyManagerRepository.kt"
android_npm_view_model="HomelabAndroid/app/src/main/java/com/homelab/app/ui/nginxpm/NpmDashboardViewModel.kt"
android_npm_ui="HomelabAndroid/app/src/main/java/com/homelab/app/ui/nginxpm/NpmDashboardScreen.kt"
android_npm_api="HomelabAndroid/app/src/main/java/com/homelab/app/data/remote/api/NginxProxyManagerApi.kt"
swift_npm_ui="HomelabSwift/Homelab/Views/NginxProxyManager/NpmDashboard.swift"
swift_npm_api="HomelabSwift/Homelab/Networking/NginxProxyManager/NginxProxyManagerAPIClient.swift"

for required_file in "$android_npm_models" "$android_npm_view_model" "$android_npm_ui" "$android_npm_api" "$swift_npm_ui" "$swift_npm_api"; do
  test -s "$required_file"
done
for pattern in 'proxy-host.create' 'redirection-host.create' 'stream.delete' 'dead-host.update' 'certificate.renew' 'access-list.delete' 'user.delete' 'ActionRisk.LOW' 'ActionRisk.MEDIUM' 'ActionRisk.HIGH' 'requiresConfirmation' 'controlledRequest' 'lowercase(Locale.ROOT)'; do
  grep -Fq "$pattern" "$android_npm_models"
done
for pattern in 'controlledActionCoordinator.execute' 'ProviderRegistry.capabilities(ServiceType.NGINX_PROXY_MANAGER)' 'confirmed: Boolean' 'ActionFailureDisposition.NON_RETRYABLE' 'nginx-proxy-manager-outcome-indeterminate'; do
  grep -Fq "$pattern" "$android_npm_view_model"
done
for pattern in 'pendingDisableProxyHostId' 'pendingRenewCertificateId' 'npm_disable_confirm' 'npm_renew_confirm' 'confirmed = true'; do
  grep -Fq "$pattern" "$android_npm_ui"
done
test "$(grep -Fc '@Header("X-Homelab-No-Fallback")' "$android_npm_api")" -eq 23
grep -Fq 'nginx proxy manager proxy host actions have stable risk classification and identity' "$android_tests"
grep -Fq 'nginx proxy manager configuration actions have stable risk and identity' "$android_tests"
grep -Fq 'nginx proxy manager indeterminate mutation is non retryable' "$android_tests"
for pattern in 'NpmProxyHostControlledAction' 'NpmConfigurationControlledAction' 'case create = "proxy-host.create"' 'case renewCertificate = "certificate.renew"' 'case .healthchecks, .dockhand, .dockmon, .linuxUpdate, .komodo, .nginxProxyManager:'; do
  grep -Fq "$pattern" "$swift_core"
done
for pattern in 'showingDisableConfirm' 'showingRenewConfirm' 'controlledActionCoordinator.execute' 'ProviderRegistry.descriptor(for: .nginxProxyManager).capabilities' 'confirmed: true' '.nonRetryable' 'nginx-proxy-manager-outcome-indeterminate'; do
  grep -Fq "$pattern" "$swift_npm_ui"
done
test "$(grep -Fc 'allowFallback: false' "$swift_npm_api")" -eq 23
grep -Fq 'testNpmProxyHostActionsHaveStableRiskClassificationAndIdentity' "$swift_tests"
grep -Fq 'testNpmConfigurationActionsHaveStableRiskClassificationAndIdentity' "$swift_tests"
grep -Fq 'testNpmProxyHostIndeterminateMutationIsNonRetryable' "$swift_tests"

android_crafty_api="HomelabAndroid/app/src/main/java/com/homelab/app/data/remote/api/CraftyApi.kt"
android_crafty_models="HomelabAndroid/app/src/main/java/com/homelab/app/data/repository/CraftyRepository.kt"
android_crafty_view_model="HomelabAndroid/app/src/main/java/com/homelab/app/ui/crafty/CraftyViewModel.kt"
android_crafty_ui="HomelabAndroid/app/src/main/java/com/homelab/app/ui/crafty/CraftyDashboardScreen.kt"
swift_crafty_api="HomelabSwift/Homelab/Networking/Crafty/CraftyAPIClient.swift"
swift_crafty_ui="HomelabSwift/Homelab/Views/Crafty/CraftyDashboard.swift"

for required_file in "$android_crafty_api" "$android_crafty_models" "$android_crafty_view_model" "$android_crafty_ui" "$swift_crafty_api" "$swift_crafty_ui"; do
  test -s "$required_file"
done
for pattern in 'CraftyServerAction' 'server.executable.update' 'CraftyCommandAction' 'server.command.send' 'ActionRisk.LOW' 'ActionRisk.MEDIUM' 'ActionRisk.HIGH' 'controlledRequest'; do
  grep -Fq "$pattern" "$android_crafty_models"
done
! grep -Fq 'parameters = mapOf("command"' "$android_crafty_models"
test "$(grep -Fc '@Header("X-Homelab-No-Fallback")' "$android_crafty_api")" -eq 2
for pattern in 'controlledActionCoordinator.execute' 'ActionRole.ADMIN' 'ProviderRegistry.capabilities(ServiceType.CRAFTY_CONTROLLER)' 'confirmed: Boolean' 'CraftyCommandAction.SEND' 'crafty-outcome-indeterminate'; do
  grep -Fq "$pattern" "$android_crafty_view_model"
done
for pattern in 'pendingAction' 'pendingCommand' 'confirmed = true' 'onSend(pending, true)'; do
  grep -Fq "$pattern" "$android_crafty_ui"
done
for pattern in 'enum CraftyAction' 'server.executable.update' 'enum CraftyCommandAction' 'server.command.send' 'ControlledActionRisk' 'requiresConfirmation' 'CraftyControlledOperationFailure' 'reachableMutationBaseURL'; do
  grep -Fq "$pattern" "$swift_crafty_api"
done
! grep -Fq 'parameters: ["command"' "$swift_crafty_api"
test "$(grep -Fc 'fallbackURL: ""' "$swift_crafty_api")" -eq 2
for pattern in 'pendingAction' 'pendingCommand' 'controlledActionCoordinator.execute' 'ProviderRegistry.descriptor(for: .craftyController).capabilities' 'confirmed: true' 'CraftyCommandAction.send'; do
  grep -Fq "$pattern" "$swift_crafty_ui"
done
grep -Fq 'crafty actions have stable risk identity and no persisted command payload' "$android_tests"
grep -Fq 'testCraftyActionsHaveStableRiskIdentityAndNoPersistedCommandPayload' "$swift_tests"
grep -Fq 'testCraftyMutationFailureMappingIsDeterministicAndNonRetryable' "$swift_tests"

android_pangolin_api="HomelabAndroid/app/src/main/java/com/homelab/app/data/remote/api/PangolinApi.kt"
android_pangolin_models="HomelabAndroid/app/src/main/java/com/homelab/app/data/repository/PangolinRepository.kt"
android_pangolin_view_model="HomelabAndroid/app/src/main/java/com/homelab/app/ui/pangolin/PangolinViewModel.kt"
android_pangolin_ui="HomelabAndroid/app/src/main/java/com/homelab/app/ui/pangolin/PangolinDashboardScreen.kt"
swift_pangolin_api="HomelabSwift/Homelab/Networking/Pangolin/PangolinAPIClient.swift"
swift_pangolin_ui="HomelabSwift/Homelab/Views/Pangolin/PangolinDashboard.swift"

for required_file in "$android_pangolin_api" "$android_pangolin_models" "$android_pangolin_view_model" "$android_pangolin_ui" "$swift_pangolin_api" "$swift_pangolin_ui"; do
  test -s "$required_file"
done
test "$(grep -Fc '@Header("X-Homelab-No-Fallback")' "$android_pangolin_api")" -eq 7
grep -A4 -F 'suspend fun deleteResource(' "$android_pangolin_api" | grep -Fq '@Header("X-Homelab-Instance-Id") instanceId: String,'
for pattern in 'PangolinControlledAction' 'public-resource.create' 'private-resource.disable' 'controlledRequest'; do grep -Fq "$pattern" "$android_pangolin_models"; done
for pattern in 'controlledActionCoordinator.execute' 'ProviderRegistry.capabilities(ServiceType.PANGOLIN)' 'confirmed: Boolean' 'pangolin-outcome-indeterminate'; do grep -Fq "$pattern" "$android_pangolin_view_model"; done
for pattern in 'pendingDisablePublicResource' 'confirmed = true'; do grep -Fq "$pattern" "$android_pangolin_ui"; done
for pattern in 'PangolinControlledAction' 'public-resource.create' 'private-resource.disable' 'PangolinControlledOperationFailure' 'method == "GET" ? fallbackURL : ""'; do grep -Fq "$pattern" "$swift_pangolin_api"; done
for pattern in 'controlledActionCoordinator.execute' 'ProviderRegistry.descriptor(for: .pangolin).capabilities' 'pendingDisablePublicResource' 'confirmed: true'; do grep -Fq "$pattern" "$swift_pangolin_ui"; done
grep -Fq 'pangolin actions have stable risk identity and no payload persistence' "$android_tests"
grep -Fq 'testPangolinActionsHaveStableRiskIdentityAndNoPayloadPersistence' "$swift_tests"

android_qbittorrent_repo="HomelabAndroid/app/src/main/java/com/homelab/app/data/repository/MediaArrRepository.kt"
android_qbittorrent_view_model="HomelabAndroid/app/src/main/java/com/homelab/app/ui/media/MediaServiceDashboardViewModel.kt"
android_qbittorrent_ui="HomelabAndroid/app/src/main/java/com/homelab/app/ui/media/MediaArrScreen.kt"
swift_qbittorrent_api="HomelabSwift/Homelab/Networking/Qbittorrent/QbittorrentAPIClient.swift"
swift_qbittorrent_ui="HomelabSwift/Homelab/Views/Qbittorrent/QbittorrentDashboard.swift"

for required_file in "$android_qbittorrent_repo" "$android_qbittorrent_view_model" "$android_qbittorrent_ui" "$swift_qbittorrent_api" "$swift_qbittorrent_ui"; do
  test -s "$required_file"
done
for pattern in 'enum class QbittorrentControlledAction' 'torrent.delete-with-data' 'transfer.pause-all' 'controlledRequest' 'X-Homelab-No-Fallback'; do grep -Fq "$pattern" "$android_qbittorrent_repo"; done
for pattern in 'controlledActionCoordinator.execute' 'ProviderRegistry.capabilities(serviceType)' 'confirmed: Boolean' 'QbittorrentControlledAction.forMediaArrAction'; do grep -Fq "$pattern" "$android_qbittorrent_view_model"; done
grep -Fq 'ProviderRegistry.capabilities(ServiceType.QBITTORRENT)' "$android_tests"
for pattern in 'pendingActionConfirmation' 'confirmed = true'; do grep -Fq "$pattern" "$android_qbittorrent_ui"; done
for pattern in 'enum QbittorrentControlledAction' 'torrent.delete-with-data' 'transfer.pause-all' 'QbittorrentControlledOperationFailure' 'ControlledActionRisk'; do grep -Fq "$pattern" "$swift_qbittorrent_api"; done
test "$(grep -Fc 'fallbackURL: ""' "$swift_qbittorrent_api")" -eq 8
for pattern in 'controlledActionCoordinator.execute' 'ProviderRegistry.descriptor(for: .qbittorrent).capabilities' 'pendingConfirmation' 'confirmed: action.requiresConfirmation'; do grep -Fq "$pattern" "$swift_qbittorrent_ui"; done
grep -Fq 'qbittorrent actions have stable risk identity and no payload persistence' "$android_tests"
grep -Fq 'testQbittorrentActionsHaveStableRiskIdentityAndNoPayloadPersistence' "$swift_tests"

android_patchmon_api="HomelabAndroid/app/src/main/java/com/homelab/app/data/remote/api/PatchmonApi.kt"
android_patchmon_repo="HomelabAndroid/app/src/main/java/com/homelab/app/data/repository/PatchmonRepository.kt"
android_patchmon_view_model="HomelabAndroid/app/src/main/java/com/homelab/app/ui/patchmon/PatchmonHostDetailViewModel.kt"
swift_patchmon_api="HomelabSwift/Homelab/Networking/Patchmon/PatchmonAPIClient.swift"
swift_patchmon_ui="HomelabSwift/Homelab/Views/Patchmon/PatchmonHostDetailView.swift"

for required_file in "$android_patchmon_api" "$android_patchmon_repo" "$android_patchmon_view_model" "$swift_patchmon_api" "$swift_patchmon_ui"; do
  test -s "$required_file"
done
grep -A5 -F 'suspend fun deleteHost(' "$android_patchmon_api" | grep -Fq '@Header("X-Homelab-No-Fallback")'
for pattern in 'enum class PatchmonControlledAction' 'host.delete' 'ActionRisk.HIGH' 'controlledRequest'; do grep -Fq "$pattern" "$android_patchmon_repo"; done
for pattern in 'controlledActionCoordinator.execute' 'ProviderRegistry.capabilities(ServiceType.PATCHMON)' 'PatchmonControlledAction.HOST_DELETE' 'confirmed = true' 'patchmon-outcome-indeterminate'; do grep -Fq "$pattern" "$android_patchmon_view_model"; done
for pattern in 'enum PatchmonControlledAction' 'host.delete' 'PatchmonControlledOperationFailure' 'method == "GET" ? self.fallbackURL : ""'; do grep -Fq "$pattern" "$swift_patchmon_api"; done
for pattern in 'controlledActionCoordinator.execute' 'ProviderRegistry.descriptor(for: .patchmon).capabilities' 'PatchmonControlledAction.hostDelete' 'confirmed: true'; do grep -Fq "$pattern" "$swift_patchmon_ui"; done
grep -Fq 'patchmon host delete has stable high-risk identity and no payload persistence' "$android_tests"
grep -Fq 'testPatchmonHostDeleteHasStableHighRiskIdentityAndNoPayloadPersistence' "$swift_tests"

android_media_repo="HomelabAndroid/app/src/main/java/com/homelab/app/data/repository/MediaArrRepository.kt"
android_media_view_model="HomelabAndroid/app/src/main/java/com/homelab/app/ui/media/MediaServiceDashboardViewModel.kt"
android_media_ui="HomelabAndroid/app/src/main/java/com/homelab/app/ui/media/MediaArrScreen.kt"
swift_media_core="HomelabSwift/Homelab/Models/ServiceType.swift"
swift_radarr_ui="HomelabSwift/Homelab/Views/Radarr/RadarrDashboard.swift"
swift_sonarr_ui="HomelabSwift/Homelab/Views/Sonarr/SonarrDashboard.swift"
swift_lidarr_ui="HomelabSwift/Homelab/Views/Lidarr/LidarrDashboard.swift"

for required_file in "$android_media_repo" "$android_media_view_model" "$android_media_ui" "$swift_media_core" "$swift_radarr_ui" "$swift_sonarr_ui" "$swift_lidarr_ui"; do
  test -s "$required_file"
done
for pattern in 'enum class MediaServiceControlledAction' 'library.add' 'request.approve-oldest' 'vpn.restart' 'session.destroy' 'controlledRequest'; do grep -Fq "$pattern" "$android_media_repo"; done
grep -Fq 'X-Homelab-No-Fallback' "$android_media_repo"
for pattern in 'executeControlled(' 'ProviderRegistry.capabilities(serviceType)' 'MediaServiceControlledAction' 'reasonPrefix' 'confirmed = true'; do grep -Fq "$pattern" "$android_media_view_model"; done
for pattern in 'pendingActionConfirmation' 'actionRequiresConfirmation' 'confirmed = true'; do grep -Fq "$pattern" "$android_media_ui"; done
for pattern in 'enum MediaServiceControlledAction' 'library.add' 'MediaServiceControlledOperationFailure' 'func executeControlledMediaAction'; do grep -Fq "$pattern" "$swift_media_core"; done
for ui in "$swift_radarr_ui" "$swift_sonarr_ui" "$swift_lidarr_ui"; do grep -Fq 'executeControlledMediaAction(' "$ui"; done
grep -Fq 'noFallback' "$android_media_repo"
grep -Fq 'ControlledActionErrorBox' "$swift_media_core"
grep -Fq 'media-service actions have stable per-provider risk identity and no payload persistence' "$android_tests"
grep -Fq 'testMediaServiceActionsHaveStablePerProviderRiskIdentityAndNoPayloadPersistence' "$swift_tests"

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

# Phase 4 tenant-membership gate: the policy honors an actor tenant set (nil = gate off,
# a set = enforced, empty set = deny all), the coordinator re-checks it ahead of any cached
# terminal result, and the audit record carries the resolved tenantRef.
for pattern in 'fun tenantMembershipSatisfied' 'actorTenants: Set<String>? = null' 'tenant-membership-required' 'Tenant.refOrDefault(request.tenantRef)'; do
  grep -Fq "$pattern" "$android_core"
done
grep -Fq 'val tenantRef: String = Tenant.DEFAULT_ID' "$android_core"
for pattern in 'func tenantMembershipSatisfied' 'actorTenants: Set<String>? = nil' 'tenant-membership-required' 'Tenant.refOrDefault(request.tenantRef)'; do
  grep -Fq "$pattern" "$swift_core"
done
grep -Fq 'a membership denial is not cached and does not block a later authorized submission' "$android_tests"
grep -Fq 'testControlledActionMembershipDenialIsNotCachedAndUnblocksLaterAuthorizedActor' "$swift_tests"

echo "Phase 3 controlled actions audit passed"
