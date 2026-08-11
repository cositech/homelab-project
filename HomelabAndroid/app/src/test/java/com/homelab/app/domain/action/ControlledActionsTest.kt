package com.homelab.app.domain.action

import com.homelab.app.domain.provider.ProviderCapability
import com.homelab.app.ui.proxmox.ProxmoxGuestAction
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

}
