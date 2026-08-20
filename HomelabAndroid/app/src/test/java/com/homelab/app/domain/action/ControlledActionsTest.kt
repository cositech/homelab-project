package com.homelab.app.domain.action

import com.homelab.app.data.remote.dto.adguard.AdGuardControlledProtectionAction
import com.homelab.app.data.remote.dto.healthchecks.HealthchecksControlledCheckAction
import com.homelab.app.data.remote.dto.pihole.PiholeControlledDomainAction
import com.homelab.app.data.remote.dto.pihole.PiholeDomainListType
import com.homelab.app.data.remote.dto.portainer.ContainerAction
import com.homelab.app.data.repository.CalagopusPowerAction
import com.homelab.app.data.repository.DockhandContainerAction
import com.homelab.app.data.repository.DockmonControlledAction
import com.homelab.app.data.repository.KomodoStackAction
import com.homelab.app.data.repository.NpmProxyHostControlledAction
import com.homelab.app.data.repository.NpmConfigurationControlledAction
import com.homelab.app.data.repository.LinuxUpdateControlledAction
import com.homelab.app.data.repository.PterodactylPowerAction
import com.homelab.app.data.repository.TechnitiumControlledAction
import com.homelab.app.data.repository.DockhandStackAction
import com.homelab.app.domain.provider.ProviderCapability
import com.homelab.app.domain.provider.ProviderRegistry
import com.homelab.app.util.ServiceType
import com.homelab.app.ui.proxmox.ProxmoxGuestAction
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlledActionsTest {
    private fun request(
        risk: ActionRisk = ActionRisk.MEDIUM,
        dryRun: Boolean = false,
        confirmed: Boolean = false,
        idempotencyKey: String = "0123456789abcdef"
    ) = ControlledActionRequest(
        id = "request-1",
        providerRef = "proxmox:cluster-a",
        action = "guest.shutdown",
        targetRef = "qemu/101",
        risk = risk,
        requestedAt = "1970-01-01T00:00:01Z",
        idempotencyKey = idempotencyKey,
        dryRun = dryRun,
        confirmed = confirmed
    )

    @Test
    fun `viewer cannot execute write actions`() {
        val decision = ControlledActionPolicy.evaluate(
            request(confirmed = true),
            ActionRole.VIEWER,
            providerCapabilities = setOf(ProviderCapability.WRITE_ACTIONS)
        )

        assertEquals(ActionPolicyOutcome.DENIED, decision.outcome)
        assertEquals("insufficient-role", decision.reasonCode)
    }

    @Test
    fun `medium risk requires explicit confirmation`() {
        val decision = ControlledActionPolicy.evaluate(
            request(),
            ActionRole.OPERATOR,
            providerCapabilities = setOf(ProviderCapability.WRITE_ACTIONS)
        )

        assertEquals(ActionPolicyOutcome.CONFIRMATION_REQUIRED, decision.outcome)
        assertFalse(decision.mayExecute)
    }

    @Test
    fun `high risk requires administrator role`() {
        val decision = ControlledActionPolicy.evaluate(
            request(risk = ActionRisk.HIGH, confirmed = true),
            ActionRole.OPERATOR,
            providerCapabilities = setOf(ProviderCapability.WRITE_ACTIONS)
        )

        assertEquals(ActionPolicyOutcome.DENIED, decision.outcome)
        assertEquals(ActionRole.ADMIN, decision.requiredRole)
    }

    @Test
    fun `dry run validates without invoking provider mutation`() = runTest {
        var invocations = 0
        val coordinator = ControlledActionCoordinator(now = { 2_000 })

        val result = coordinator.execute(
            request(dryRun = true),
            ActionRole.OPERATOR,
            providerCapabilities = setOf(ProviderCapability.WRITE_ACTIONS)
        ) {
            invocations += 1
        }

        assertEquals(ActionExecutionState.DRY_RUN, result.state)
        assertEquals(0, invocations)
    }

    @Test
    fun `idempotency returns previous terminal result`() = runTest {
        var invocations = 0
        val coordinator = ControlledActionCoordinator(now = { 3_000 })
        val confirmed = request(confirmed = true)

        val first = coordinator.execute(confirmed, ActionRole.OPERATOR, setOf(ProviderCapability.WRITE_ACTIONS)) {
            invocations += 1
        }
        val second = coordinator.execute(confirmed, ActionRole.OPERATOR, setOf(ProviderCapability.WRITE_ACTIONS)) {
            invocations += 1
        }

        assertEquals(ActionExecutionState.SUCCEEDED, first.state)
        assertEquals(first, second)
        assertEquals(1, invocations)
        assertEquals(
            listOf(ActionExecutionState.QUEUED, ActionExecutionState.EXECUTING, ActionExecutionState.SUCCEEDED),
            coordinator.auditSnapshot().map { it.state }
        )
    }

    @Test
    fun `provider capability is enforced before execution`() = runTest {
        var invoked = false
        val coordinator = ControlledActionCoordinator()

        val result = coordinator.execute(
            request(confirmed = true),
            ActionRole.ADMIN,
            providerCapabilities = emptySet()
        ) {
            invoked = true
        }

        assertEquals(ActionExecutionState.REJECTED, result.state)
        assertEquals("provider-write-capability-required", result.reasonCode)
        assertFalse(invoked)
        assertTrue(coordinator.auditSnapshot().isNotEmpty())
    }

    @Test
    fun `idempotency survives bounded audit pruning`() = runTest {
        var invocations = 0
        val coordinator = ControlledActionCoordinator(
            ledger = ControlledActionLedger(maximumRecords = 1),
            now = { 4_000 }
        )
        val firstRequest = request(confirmed = true, idempotencyKey = "first-key-0000001")
        val secondRequest = request(confirmed = true, idempotencyKey = "second-key-000001")

        coordinator.execute(firstRequest, ActionRole.OPERATOR, setOf(ProviderCapability.WRITE_ACTIONS)) {
            invocations += 1
        }
        coordinator.execute(secondRequest, ActionRole.OPERATOR, setOf(ProviderCapability.WRITE_ACTIONS)) {
            invocations += 1
        }
        coordinator.execute(firstRequest, ActionRole.OPERATOR, setOf(ProviderCapability.WRITE_ACTIONS)) {
            invocations += 1
        }

        assertEquals(2, invocations)
        assertEquals(1, coordinator.auditSnapshot().size)
    }

    @Test
    fun `coroutine cancellation is audited and propagated`() = runTest {
        val coordinator = ControlledActionCoordinator()
        var propagated = false

        try {
            coordinator.execute(
                request(confirmed = true),
                ActionRole.OPERATOR,
                setOf(ProviderCapability.WRITE_ACTIONS)
            ) {
                throw CancellationException("cancel test")
            }
        } catch (_: CancellationException) {
            propagated = true
        }

        assertTrue(propagated)
        assertEquals(ActionExecutionState.CANCELLED, coordinator.auditSnapshot().last().state)
    }

    @Test
    fun `proxmox lifecycle request uses normalized provider and target references`() {
        val request = ProxmoxGuestAction.STOP.controlledRequest(
            instanceId = "cluster-a",
            node = "pve01",
            vmid = 101,
            isQemu = true,
            confirmed = true,
            requestId = "request-proxmox-1",
            requestedAt = "1970-01-01T00:00:00Z",
            idempotencyKey = "idempotency-key-0001"
        )

        assertEquals("proxmox:cluster-a", request.providerRef)
        assertEquals("guest.stop", request.action)
        assertEquals("qemu/101@pve01", request.targetRef)
        assertEquals(ActionRisk.HIGH, request.risk)
        assertTrue(request.confirmed)
    }

    @Test
    fun `proxmox lifecycle risk controls explicit confirmation`() {
        assertFalse(ProxmoxGuestAction.START.requiresConfirmation)
        assertTrue(ProxmoxGuestAction.SHUTDOWN.requiresConfirmation)
        assertTrue(ProxmoxGuestAction.REBOOT.requiresConfirmation)
        assertTrue(ProxmoxGuestAction.STOP.requiresConfirmation)
    }

    @Test
    fun `retryable low risk failure uses bounded exponential retry`() = runTest {
        val delays = mutableListOf<Long>()
        var invocations = 0
        val coordinator = ControlledActionCoordinator(
            retryPolicy = ActionRetryPolicy(maximumAttempts = 3, initialDelayMillis = 10, maximumDelayMillis = 40),
            now = { 5_000 },
            waitBeforeRetry = { delays += it }
        )

        val result = coordinator.execute(
            request(risk = ActionRisk.LOW, confirmed = true),
            ActionRole.OPERATOR,
            setOf(ProviderCapability.WRITE_ACTIONS)
        ) {
            invocations += 1
            if (invocations < 3) {
                throw ActionOperationException("provider-unavailable", ActionFailureDisposition.RETRYABLE)
            }
        }

        assertEquals(ActionExecutionState.SUCCEEDED, result.state)
        assertEquals(3, invocations)
        assertEquals(listOf(10L, 20L), delays)
    }

    @Test
    fun `dockhand indeterminate mutation is non retryable`() = runTest {
        var invocations = 0
        val coordinator = ControlledActionCoordinator(waitBeforeRetry = {})
        val dockhandRequest = DockhandContainerAction.RESTART.controlledRequest(
            instanceId = "instance-a",
            environmentId = "production",
            containerId = "web-01",
            confirmed = true,
            requestId = "request-dockhand-restart",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "dockhand-restart-key-0001"
        )

        val result = coordinator.execute(
            dockhandRequest,
            ActionRole.ADMIN,
            setOf(ProviderCapability.WRITE_ACTIONS)
        ) {
            invocations += 1
            throw ActionOperationException(
                "dockhand-outcome-indeterminate",
                ActionFailureDisposition.NON_RETRYABLE
            )
        }

        assertEquals(ActionExecutionState.FAILED, result.state)
        assertEquals(1, invocations)
        assertEquals("dockhand-outcome-indeterminate", result.reasonCode)
    }

    @Test
    fun `high risk transport failure requires manual review without retry`() = runTest {
        var invocations = 0
        val coordinator = ControlledActionCoordinator(waitBeforeRetry = {})

        val result = coordinator.execute(
            request(risk = ActionRisk.HIGH, confirmed = true),
            ActionRole.ADMIN,
            setOf(ProviderCapability.WRITE_ACTIONS)
        ) {
            invocations += 1
            throw ActionOperationException("transport-error", ActionFailureDisposition.RETRYABLE)
        }

        assertEquals(ActionExecutionState.MANUAL_REVIEW, result.state)
        assertEquals(1, invocations)
        assertEquals("automatic-retry-forbidden-transport-error", result.reasonCode)
    }

    @Test
    fun `terminal idempotency result survives coordinator reconstruction`() = runTest {
        val store = InMemoryDurableActionQueueStore()
        val confirmed = request(risk = ActionRisk.LOW, confirmed = true)
        var invocations = 0

        ControlledActionCoordinator(durableStore = store).execute(
            confirmed, ActionRole.OPERATOR, setOf(ProviderCapability.WRITE_ACTIONS)
        ) { invocations += 1 }

        val recovered = ControlledActionCoordinator(durableStore = store).execute(
            confirmed, ActionRole.OPERATOR, setOf(ProviderCapability.WRITE_ACTIONS)
        ) { invocations += 1 }

        assertEquals(ActionExecutionState.SUCCEEDED, recovered.state)
        assertEquals(1, invocations)
    }

    @Test
    fun `interrupted execution recovers as manual review`() = runTest {
        val interrupted = DurableActionQueueEntry(
            request = request(risk = ActionRisk.LOW, confirmed = true),
            actorRole = ActionRole.OPERATOR,
            state = ActionExecutionState.EXECUTING,
            attemptCount = 1,
            reasonCode = "executing",
            updatedAtEpochMillis = 1_000
        )
        val coordinator = ControlledActionCoordinator(
            durableStore = InMemoryDurableActionQueueStore(listOf(interrupted)),
            now = { 2_000 }
        )

        val recovered = coordinator.pendingRecovery().single()

        assertEquals(ActionExecutionState.MANUAL_REVIEW, recovered.state)
        assertEquals("interrupted-execution", recovered.reasonCode)
    }

    @Test
    fun `durable queue omits action parameters`() = runTest {
        val store = InMemoryDurableActionQueueStore()
        val coordinator = ControlledActionCoordinator(durableStore = store)

        coordinator.execute(
            request(risk = ActionRisk.LOW, confirmed = true).copy(parameters = mapOf("secret" to "value")),
            ActionRole.OPERATOR,
            setOf(ProviderCapability.WRITE_ACTIONS)
        ) {}

        assertTrue(store.snapshot().single().request.parameters.isEmpty())
    }

    @Test
    fun `terminal persistence failure never retries completed provider mutation`() = runTest {
        val backing = InMemoryDurableActionQueueStore()
        val store = object : DurableActionQueueStore {
            override suspend fun snapshot(): List<DurableActionQueueEntry> = backing.snapshot()

            override suspend fun upsert(entry: DurableActionQueueEntry) {
                if (entry.state == ActionExecutionState.SUCCEEDED) {
                    throw IOException("simulated persistence failure")
                }
                backing.upsert(entry)
            }
        }
        var invocations = 0
        val coordinator = ControlledActionCoordinator(durableStore = store)

        val result = coordinator.execute(
            request(risk = ActionRisk.LOW, confirmed = true),
            ActionRole.OPERATOR,
            setOf(ProviderCapability.WRITE_ACTIONS)
        ) { invocations += 1 }

        assertEquals(ActionExecutionState.MANUAL_REVIEW, result.state)
        assertEquals("terminal-persistence-failed", result.reasonCode)
        assertEquals(1, invocations)
    }

    @Test
    fun `recovered terminal key rejects different action identity`() = runTest {
        val store = InMemoryDurableActionQueueStore()
        val original = request(risk = ActionRisk.LOW, confirmed = true)

        ControlledActionCoordinator(durableStore = store).execute(
            original,
            ActionRole.OPERATOR,
            setOf(ProviderCapability.WRITE_ACTIONS)
        ) {}

        var invoked = false
        val result = ControlledActionCoordinator(durableStore = store).execute(
            original.copy(targetRef = "qemu/999"),
            ActionRole.OPERATOR,
            setOf(ProviderCapability.WRITE_ACTIONS)
        ) { invoked = true }

        assertEquals(ActionExecutionState.REJECTED, result.state)
        assertEquals("idempotency-key-conflict", result.reasonCode)
        assertFalse(invoked)
    }

    @Test
    fun `portainer container actions have stable risk classification and identity`() {
        assertEquals(ActionRisk.LOW, ContainerAction.start.risk)
        assertEquals(ActionRisk.MEDIUM, ContainerAction.stop.risk)
        assertEquals(ActionRisk.MEDIUM, ContainerAction.restart.risk)
        assertEquals(ActionRisk.HIGH, ContainerAction.kill.risk)
        assertTrue(ContainerAction.pause.requiresConfirmation)
        assertFalse(ContainerAction.start.requiresConfirmation)

        val request = ContainerAction.stop.controlledRequest(
            instanceId = "instance-1",
            endpointId = 7,
            containerId = "container-42",
            confirmed = true,
            requestId = "request-portainer",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "0123456789abcdef"
        )

        assertEquals("portainer:instance-1", request.providerRef)
        assertEquals("container.stop", request.action)
        assertEquals("endpoint/7/container/container-42", request.targetRef)
        assertTrue(request.confirmed)
    }

    @Test
    fun `dockhand lifecycle actions have stable risk classification and identity`() {
        assertEquals(ActionRisk.LOW, DockhandContainerAction.START.risk)
        assertEquals(ActionRisk.MEDIUM, DockhandContainerAction.STOP.risk)
        assertEquals(ActionRisk.MEDIUM, DockhandStackAction.RESTART.risk)
        assertFalse(DockhandContainerAction.START.requiresConfirmation)
        assertTrue(DockhandStackAction.STOP.requiresConfirmation)

        val containerRequest = DockhandContainerAction.RESTART.controlledRequest(
            instanceId = "INSTANCE-A",
            environmentId = "Production",
            containerId = "Web-01",
            confirmed = true,
            requestId = "request-dockhand-container",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "dockhand-container-key-0001"
        )
        assertEquals("dockhand:instance-a", containerRequest.providerRef)
        assertEquals("container.restart", containerRequest.action)
        assertEquals("environment/production/container/web-01", containerRequest.targetRef)
        assertTrue(containerRequest.confirmed)

        val stackRequest = DockhandStackAction.START.controlledRequest(
            instanceId = "INSTANCE-A",
            environmentId = null,
            stackName = "Core-Stack",
            confirmed = false,
            requestId = "request-dockhand-stack",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "dockhand-stack-key-000001"
        )
        assertEquals("stack.start", stackRequest.action)
        assertEquals("environment/default/stack/core-stack", stackRequest.targetRef)
        assertTrue(ProviderRegistry.capabilities(ServiceType.DOCKHAND).contains(ProviderCapability.WRITE_ACTIONS))
    }

    @Test
    fun `dockmon actions have stable risk classification and identity`() {
        assertEquals(ActionRisk.MEDIUM, DockmonControlledAction.RESTART.risk)
        assertEquals(ActionRisk.HIGH, DockmonControlledAction.UPDATE.risk)
        assertTrue(DockmonControlledAction.RESTART.requiresConfirmation)
        assertTrue(DockmonControlledAction.UPDATE.requiresConfirmation)

        val request = DockmonControlledAction.UPDATE.controlledRequest(
            instanceId = "INSTANCE-A",
            containerId = "Web-01",
            confirmed = true,
            requestId = "request-dockmon-update",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "dockmon-update-key-0001"
        )
        assertEquals("dockmon:instance-a", request.providerRef)
        assertEquals("container.update", request.action)
        assertEquals("container/web-01", request.targetRef)
        assertTrue(request.confirmed)
        assertTrue(ProviderCapability.WRITE_ACTIONS in ProviderRegistry.capabilities(ServiceType.DOCKMON))
    }

    @Test
    fun `dockmon indeterminate mutation is non retryable`() = runTest {
        var invocations = 0
        val coordinator = ControlledActionCoordinator(waitBeforeRetry = {})
        val request = DockmonControlledAction.RESTART.controlledRequest(
            instanceId = "instance-a",
            containerId = "web-01",
            confirmed = true,
            requestId = "request-dockmon-restart",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "dockmon-restart-key-0001"
        )

        val result = coordinator.execute(
            request,
            ActionRole.ADMIN,
            setOf(ProviderCapability.WRITE_ACTIONS)
        ) {
            invocations += 1
            throw ActionOperationException(
                "dockmon-outcome-indeterminate",
                ActionFailureDisposition.NON_RETRYABLE
            )
        }

        assertEquals(ActionExecutionState.FAILED, result.state)
        assertEquals(1, invocations)
        assertEquals("dockmon-outcome-indeterminate", result.reasonCode)
    }

    @Test
    fun `game server power actions have stable risk classification and identity`() {
        assertEquals(ActionRisk.LOW, PterodactylPowerAction.START.risk)
        assertEquals(ActionRisk.MEDIUM, PterodactylPowerAction.STOP.risk)
        assertEquals(ActionRisk.MEDIUM, PterodactylPowerAction.RESTART.risk)
        assertEquals(ActionRisk.HIGH, PterodactylPowerAction.KILL.risk)
        assertFalse(PterodactylPowerAction.START.requiresConfirmation)
        assertTrue(PterodactylPowerAction.RESTART.requiresConfirmation)
        assertEquals(ActionRisk.LOW, CalagopusPowerAction.START.risk)
        assertEquals(ActionRisk.HIGH, CalagopusPowerAction.KILL.risk)

        val pterodactylRequest = PterodactylPowerAction.KILL.controlledRequest(
            instanceId = "INSTANCE-A",
            identifier = "MC-Primary",
            confirmed = true,
            requestId = "request-pterodactyl-kill",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "pterodactyl-kill-key"
        )
        assertEquals("pterodactyl:instance-a", pterodactylRequest.providerRef)
        assertEquals("server.power.kill", pterodactylRequest.action)
        assertEquals("server/mc-primary", pterodactylRequest.targetRef)
        assertTrue(pterodactylRequest.confirmed)

        val calagopusRequest = CalagopusPowerAction.RESTART.controlledRequest(
            instanceId = "INSTANCE-B",
            uuidShort = "Game-02",
            confirmed = true,
            requestId = "request-calagopus-restart",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "calagopus-restart-key"
        )
        assertEquals("calagopus:instance-b", calagopusRequest.providerRef)
        assertEquals("server.power.restart", calagopusRequest.action)
        assertEquals("server/game-02", calagopusRequest.targetRef)
        assertTrue(ProviderCapability.WRITE_ACTIONS in ProviderRegistry.capabilities(ServiceType.PTERODACTYL))
        assertTrue(ProviderCapability.WRITE_ACTIONS in ProviderRegistry.capabilities(ServiceType.CALAGOPUS))
    }

    @Test
    fun `game server indeterminate mutations are non retryable`() = runTest {
        val cases = listOf(
            PterodactylPowerAction.RESTART.controlledRequest(
                "instance-a", "mc-primary", true,
                "request-pterodactyl", "1970-01-01T00:00:01Z", "pterodactyl-key-0001"
            ) to "pterodactyl-outcome-indeterminate",
            CalagopusPowerAction.RESTART.controlledRequest(
                "instance-b", "game-02", true,
                "request-calagopus", "1970-01-01T00:00:01Z", "calagopus-key-0001"
            ) to "calagopus-outcome-indeterminate"
        )

        cases.forEach { (request, reasonCode) ->
            var invocations = 0
            val result = ControlledActionCoordinator(waitBeforeRetry = {}).execute(
                request,
                ActionRole.ADMIN,
                setOf(ProviderCapability.WRITE_ACTIONS)
            ) {
                invocations += 1
                throw ActionOperationException(reasonCode, ActionFailureDisposition.NON_RETRYABLE)
            }
            assertEquals(ActionExecutionState.FAILED, result.state)
            assertEquals(1, invocations)
            assertEquals(reasonCode, result.reasonCode)
        }
    }

    @Test
    fun `komodo stack actions have stable risk classification and identity`() {
        assertEquals(ActionRisk.HIGH, KomodoStackAction.DEPLOY.risk)
        assertEquals(ActionRisk.MEDIUM, KomodoStackAction.START.risk)
        assertEquals(ActionRisk.MEDIUM, KomodoStackAction.STOP.risk)
        assertEquals(ActionRisk.MEDIUM, KomodoStackAction.RESTART.risk)
        assertTrue(KomodoStackAction.DEPLOY.requiresConfirmation)
        assertTrue(KomodoStackAction.RESTART.requiresConfirmation)

        val request = KomodoStackAction.DEPLOY.controlledRequest(
            instanceId = "INSTANCE-A",
            stackId = "Core-Stack",
            confirmed = true,
            requestId = "request-komodo-deploy",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "komodo-deploy-key-0001"
        )
        assertEquals("komodo:instance-a", request.providerRef)
        assertEquals("stack.deploy", request.action)
        assertEquals("stack/core-stack", request.targetRef)
        assertTrue(request.confirmed)
        assertTrue(ProviderCapability.WRITE_ACTIONS in ProviderRegistry.capabilities(ServiceType.KOMODO))
    }

    @Test
    fun `komodo indeterminate mutation is non retryable`() = runTest {
        var invocations = 0
        val coordinator = ControlledActionCoordinator(waitBeforeRetry = {})
        val request = KomodoStackAction.RESTART.controlledRequest(
            instanceId = "instance-a",
            stackId = "core-stack",
            confirmed = true,
            requestId = "request-komodo-restart",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "komodo-restart-key-0001"
        )

        val result = coordinator.execute(
            request,
            ActionRole.ADMIN,
            setOf(ProviderCapability.WRITE_ACTIONS)
        ) {
            invocations += 1
            throw ActionOperationException(
                "komodo-outcome-indeterminate",
                ActionFailureDisposition.NON_RETRYABLE
            )
        }

        assertEquals(ActionExecutionState.FAILED, result.state)
        assertEquals(1, invocations)
        assertEquals("komodo-outcome-indeterminate", result.reasonCode)
    }

    @Test
    fun `linux update actions have stable risk classification and identity`() {
        assertEquals(ActionRisk.LOW, LinuxUpdateControlledAction.CHECK_ALL.risk)
        assertEquals(ActionRisk.LOW, LinuxUpdateControlledAction.REFRESH_CACHE.risk)
        assertEquals(ActionRisk.LOW, LinuxUpdateControlledAction.CHECK_SYSTEM.risk)
        assertEquals(ActionRisk.MEDIUM, LinuxUpdateControlledAction.UPGRADE_PACKAGE.risk)
        assertEquals(ActionRisk.HIGH, LinuxUpdateControlledAction.UPGRADE_ALL.risk)
        assertEquals(ActionRisk.HIGH, LinuxUpdateControlledAction.FULL_UPGRADE.risk)
        assertEquals(ActionRisk.HIGH, LinuxUpdateControlledAction.REBOOT.risk)
        assertFalse(LinuxUpdateControlledAction.CHECK_SYSTEM.requiresConfirmation)
        assertTrue(LinuxUpdateControlledAction.UPGRADE_PACKAGE.requiresConfirmation)

        val action = LinuxUpdateControlledAction.UPGRADE_PACKAGE
        val request = action.controlledRequest(
            instanceId = "INSTANCE-A",
            target = action.targetRef(systemId = 42, packageName = "OpenSSL"),
            confirmed = true,
            requestId = "request-linux-update-package",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "linux-update-package-key-01"
        )
        assertEquals("linux-update:instance-a", request.providerRef)
        assertEquals("package.upgrade", request.action)
        assertEquals("system/42/package/openssl", request.targetRef)
        assertTrue(request.confirmed)
        assertTrue(
            ProviderCapability.WRITE_ACTIONS in
                ProviderRegistry.capabilities(ServiceType.LINUX_UPDATE)
        )
    }

    @Test
    fun `linux update indeterminate mutation is non retryable`() = runTest {
        var invocations = 0
        val coordinator = ControlledActionCoordinator(waitBeforeRetry = {})
        val action = LinuxUpdateControlledAction.CHECK_SYSTEM
        val request = action.controlledRequest(
            instanceId = "instance-a",
            target = action.targetRef(systemId = 42),
            confirmed = false,
            requestId = "request-linux-update-check",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "linux-update-check-key-001"
        )

        val result = coordinator.execute(
            request,
            ActionRole.ADMIN,
            setOf(ProviderCapability.WRITE_ACTIONS)
        ) {
            invocations += 1
            throw ActionOperationException(
                "linux-update-outcome-indeterminate",
                ActionFailureDisposition.NON_RETRYABLE
            )
        }

        assertEquals(ActionExecutionState.FAILED, result.state)
        assertEquals(1, invocations)
        assertEquals("linux-update-outcome-indeterminate", result.reasonCode)
    }

    @Test
    fun `technitium actions have stable risk classification and identity`() {
        assertEquals(ActionRisk.LOW, TechnitiumControlledAction.ENABLE_BLOCKING.risk)
        assertEquals(ActionRisk.MEDIUM, TechnitiumControlledAction.DISABLE_BLOCKING.risk)
        assertEquals(ActionRisk.MEDIUM, TechnitiumControlledAction.TEMPORARY_DISABLE.risk)
        assertEquals(ActionRisk.LOW, TechnitiumControlledAction.REFRESH_BLOCK_LISTS.risk)
        assertEquals(ActionRisk.HIGH, TechnitiumControlledAction.ADD_BLOCKED_DOMAIN.risk)
        assertEquals(ActionRisk.MEDIUM, TechnitiumControlledAction.REMOVE_BLOCKED_DOMAIN.risk)
        assertFalse(TechnitiumControlledAction.ENABLE_BLOCKING.requiresConfirmation)
        assertTrue(TechnitiumControlledAction.ADD_BLOCKED_DOMAIN.requiresConfirmation)

        val action = TechnitiumControlledAction.ADD_BLOCKED_DOMAIN
        val request = action.controlledRequest(
            instanceId = "INSTANCE-A",
            target = action.targetRef("Example.COM"),
            confirmed = true,
            requestId = "request-technitium-domain",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "technitium-domain-key-01"
        )
        assertEquals("technitium:instance-a", request.providerRef)
        assertEquals("blocked-domain.add", request.action)
        assertEquals("blocked-domain/example.com", request.targetRef)
        assertTrue(request.confirmed)
        assertTrue(ProviderCapability.WRITE_ACTIONS in ProviderRegistry.capabilities(ServiceType.TECHNITIUM))
    }

    @Test
    fun `technitium indeterminate mutation is non retryable`() = runTest {
        var invocations = 0
        val coordinator = ControlledActionCoordinator(waitBeforeRetry = {})
        val action = TechnitiumControlledAction.DISABLE_BLOCKING
        val request = action.controlledRequest(
            instanceId = "instance-a",
            target = action.targetRef(),
            confirmed = true,
            requestId = "request-technitium-disable",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "technitium-disable-key-1"
        )

        val result = coordinator.execute(
            request,
            ActionRole.ADMIN,
            setOf(ProviderCapability.WRITE_ACTIONS)
        ) {
            invocations += 1
            throw ActionOperationException(
                "technitium-outcome-indeterminate",
                ActionFailureDisposition.NON_RETRYABLE
            )
        }

        assertEquals(ActionExecutionState.FAILED, result.state)
        assertEquals(1, invocations)
        assertEquals("technitium-outcome-indeterminate", result.reasonCode)
    }

    @Test
    fun `pihole domain actions require confirmation and have stable identity`() {
        assertEquals(ActionRisk.HIGH, PiholeControlledDomainAction.ADD.risk)
        assertEquals(ActionRisk.MEDIUM, PiholeControlledDomainAction.REMOVE.risk)
        assertFalse(
            ActionRetryPolicy().permitsAutomaticRetry(
                PiholeControlledDomainAction.ADD.risk,
                completedAttempts = 0
            )
        )

        val request = PiholeControlledDomainAction.REMOVE.controlledRequest(
            instanceId = "instance-1",
            domain = "Example.COM",
            listType = PiholeDomainListType.DENY,
            confirmed = true,
            requestId = "request-pihole",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "0123456789abcdef"
        )

        assertEquals("pi-hole:instance-1", request.providerRef)
        assertEquals("domain.remove", request.action)
        assertEquals("domain/deny/example.com", request.targetRef)
        assertTrue(request.confirmed)
        assertTrue(
            ProviderCapability.WRITE_ACTIONS in
                ProviderRegistry.capabilities(ServiceType.PIHOLE)
        )
    }

    @Test
    fun `adguard protection actions have stable risk classification and identity`() {
        assertEquals(ActionRisk.LOW, AdGuardControlledProtectionAction.ENABLE.risk)
        assertEquals(ActionRisk.MEDIUM, AdGuardControlledProtectionAction.DISABLE.risk)

        val request = AdGuardControlledProtectionAction.DISABLE.controlledRequest(
            instanceId = "instance-1",
            confirmed = true,
            requestId = "request-adguard",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "0123456789abcdef"
        )

        assertEquals("adguard-home:instance-1", request.providerRef)
        assertEquals("protection.disable", request.action)
        assertEquals("protection/global", request.targetRef)
        assertTrue(request.confirmed)
        assertTrue(
            ProviderCapability.WRITE_ACTIONS in
                ProviderRegistry.capabilities(ServiceType.ADGUARD_HOME)
        )
    }

    @Test
    fun `healthchecks check actions have stable risk classification and identity`() {
        assertEquals(ActionRisk.HIGH, HealthchecksControlledCheckAction.CREATE.risk)
        assertFalse(
            ActionRetryPolicy().permitsAutomaticRetry(
                HealthchecksControlledCheckAction.CREATE.risk,
                completedAttempts = 1
            )
        )
        assertEquals(ActionRisk.MEDIUM, HealthchecksControlledCheckAction.UPDATE.risk)
        assertEquals(ActionRisk.MEDIUM, HealthchecksControlledCheckAction.UPDATE_CHANNELS.risk)
        assertEquals(ActionRisk.MEDIUM, HealthchecksControlledCheckAction.PAUSE.risk)
        assertEquals(ActionRisk.MEDIUM, HealthchecksControlledCheckAction.RESUME.risk)
        assertEquals(ActionRisk.HIGH, HealthchecksControlledCheckAction.DELETE.risk)

        val request = HealthchecksControlledCheckAction.DELETE.controlledRequest(
            instanceId = "instance-1",
            checkId = "check-42",
            confirmed = true,
            requestId = "request-healthchecks",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "0123456789abcdef"
        )

        assertEquals("healthchecks:instance-1", request.providerRef)
        assertEquals("check.delete", request.action)
        assertEquals("check/check-42", request.targetRef)
        assertTrue(request.confirmed)
        assertEquals(
            "check.channels.update",
            HealthchecksControlledCheckAction.UPDATE_CHANNELS.wireName
        )
    }

    @Test
    fun `healthchecks provider declares controlled write actions`() {
        assertTrue(
            ProviderCapability.WRITE_ACTIONS in
                ProviderRegistry.capabilities(ServiceType.HEALTHCHECKS)
        )
    }

    @Test
    fun `portainer provider declares controlled write actions`() {
        assertTrue(
            ProviderCapability.WRITE_ACTIONS in
                ProviderRegistry.capabilities(ServiceType.PORTAINER)
        )
    }


    @Test
    fun `nginx proxy manager proxy host actions have stable risk classification and identity`() {
        assertEquals(ActionRisk.HIGH, NpmProxyHostControlledAction.CREATE.risk)
        assertEquals(ActionRisk.HIGH, NpmProxyHostControlledAction.UPDATE.risk)
        assertEquals(ActionRisk.LOW, NpmProxyHostControlledAction.ENABLE.risk)
        assertEquals(ActionRisk.MEDIUM, NpmProxyHostControlledAction.DISABLE.risk)
        assertEquals(ActionRisk.HIGH, NpmProxyHostControlledAction.DELETE.risk)
        assertFalse(NpmProxyHostControlledAction.ENABLE.requiresConfirmation)
        assertTrue(NpmProxyHostControlledAction.DISABLE.requiresConfirmation)

        val request = NpmProxyHostControlledAction.DELETE.controlledRequest(
            instanceId = "INSTANCE-A",
            hostId = 42,
            confirmed = true,
            requestId = "request-npm-proxy-host",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "npm-proxy-host-key-001"
        )

        assertEquals("nginx-proxy-manager:instance-a", request.providerRef)
        assertEquals("proxy-host.delete", request.action)
        assertEquals("proxy-host/42", request.targetRef)
        assertTrue(request.confirmed)
        assertTrue(
            ProviderCapability.WRITE_ACTIONS in
                ProviderRegistry.capabilities(ServiceType.NGINX_PROXY_MANAGER)
        )
    }

    @Test
    fun `nginx proxy manager configuration actions have stable risk and identity`() {
        NpmConfigurationControlledAction.values()
            .filterNot { it == NpmConfigurationControlledAction.RENEW_CERTIFICATE }
            .forEach {
                assertEquals(ActionRisk.HIGH, it.risk)
                assertTrue(it.requiresConfirmation)
            }
        assertEquals(ActionRisk.MEDIUM, NpmConfigurationControlledAction.RENEW_CERTIFICATE.risk)
        assertTrue(NpmConfigurationControlledAction.RENEW_CERTIFICATE.requiresConfirmation)

        val request = NpmConfigurationControlledAction.DELETE_ACCESS_LIST.controlledRequest(
            instanceId = "INSTANCE-A",
            targetId = 17,
            confirmed = true,
            requestId = "request-npm-access-list",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "npm-access-list-key-01"
        )
        val createRequest = NpmConfigurationControlledAction.CREATE_REDIRECTION_HOST.controlledRequest(
            instanceId = "INSTANCE-A",
            targetId = null,
            confirmed = true,
            requestId = "request-npm-redirection",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "npm-redirection-key-01"
        )

        assertEquals("nginx-proxy-manager:instance-a", request.providerRef)
        assertEquals("access-list.delete", request.action)
        assertEquals("access-list/17", request.targetRef)
        assertEquals("redirection-host/new", createRequest.targetRef)
        assertTrue(request.confirmed)
    }

    @Test
    fun `nginx proxy manager indeterminate mutation is non retryable`() = runTest {
        var invocations = 0
        val coordinator = ControlledActionCoordinator(waitBeforeRetry = {})
        val request = NpmProxyHostControlledAction.ENABLE.controlledRequest(
            instanceId = "instance-a",
            hostId = 42,
            confirmed = false,
            requestId = "request-npm-enable",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "npm-proxy-enable-key-01"
        )

        val result = coordinator.execute(
            request,
            ActionRole.ADMIN,
            setOf(ProviderCapability.WRITE_ACTIONS)
        ) {
            invocations += 1
            throw ActionOperationException(
                "nginx-proxy-manager-outcome-indeterminate",
                ActionFailureDisposition.NON_RETRYABLE
            )
        }

        assertEquals(ActionExecutionState.FAILED, result.state)
        assertEquals(1, invocations)
        assertEquals("nginx-proxy-manager-outcome-indeterminate", result.reasonCode)
    }

}
