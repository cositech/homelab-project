package com.homelab.app.domain.action

import com.homelab.app.data.remote.dto.adguard.AdGuardControlledConfigurationAction
import com.homelab.app.data.remote.dto.adguard.AdGuardControlledProtectionAction
import com.homelab.app.data.remote.dto.healthchecks.HealthchecksControlledCheckAction
import com.homelab.app.data.remote.dto.pihole.PiholeControlledDomainAction
import com.homelab.app.data.remote.dto.pihole.PiholeDomainListType
import com.homelab.app.data.remote.dto.portainer.ContainerAction
import com.homelab.app.data.remote.dto.portainer.PortainerControlledConfigurationAction
import com.homelab.app.data.repository.CraftyCommandAction
import com.homelab.app.data.repository.CraftyServerAction
import com.homelab.app.data.repository.CalagopusPowerAction
import com.homelab.app.data.repository.DockhandContainerAction
import com.homelab.app.data.repository.DockmonControlledAction
import com.homelab.app.data.repository.KomodoStackAction
import com.homelab.app.data.repository.NpmProxyHostControlledAction
import com.homelab.app.data.repository.NpmConfigurationControlledAction
import com.homelab.app.data.repository.PangolinControlledAction
import com.homelab.app.data.repository.PatchmonControlledAction
import com.homelab.app.data.repository.QbittorrentControlledAction
import com.homelab.app.data.repository.MediaArrAction
import com.homelab.app.data.repository.MediaServiceControlledAction
import com.homelab.app.data.repository.LinuxUpdateControlledAction
import com.homelab.app.data.repository.PterodactylPowerAction
import com.homelab.app.data.repository.TechnitiumControlledAction
import com.homelab.app.data.repository.DockhandStackAction
import com.homelab.app.domain.provider.ProviderCapability
import com.homelab.app.domain.provider.ProviderRegistry
import com.homelab.app.util.ServiceType
import com.homelab.app.ui.proxmox.ProxmoxBackupJobAction
import com.homelab.app.ui.proxmox.ProxmoxCloneMigrateAction
import com.homelab.app.ui.proxmox.ProxmoxFirewallAction
import com.homelab.app.ui.proxmox.ProxmoxGuestAction
import com.homelab.app.ui.proxmox.ProxmoxSnapshotAction
import com.homelab.app.ui.proxmox.ProxmoxStorageContentAction
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlledActionsTest {

    private class FakeTenantScope(
        private val tenantByProviderRef: Map<String, String> = emptyMap(),
        private val membership: Set<String> = setOf("default")
    ) : ControlledActionTenantScope {
        override suspend fun tenantRefFor(providerRef: String): String? = tenantByProviderRef[providerRef]
        override suspend fun membershipRefs(): Set<String> = membership
    }

    private fun request(
        risk: ActionRisk = ActionRisk.MEDIUM,
        dryRun: Boolean = false,
        confirmed: Boolean = false,
        idempotencyKey: String = "0123456789abcdef",
        tenantRef: String? = null
    ) = ControlledActionRequest(
        id = "request-1",
        providerRef = "proxmox:cluster-a",
        tenantRef = tenantRef,
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
    fun `null tenant membership leaves the gate a no-op`() {
        val decision = ControlledActionPolicy.evaluate(
            request(risk = ActionRisk.LOW, tenantRef = "acme"),
            ActionRole.OPERATOR,
            providerCapabilities = setOf(ProviderCapability.WRITE_ACTIONS),
            actorTenants = null
        )

        assertEquals(ActionPolicyOutcome.APPROVED, decision.outcome)
    }

    @Test
    fun `a configured but empty membership set denies every tenant`() {
        val decision = ControlledActionPolicy.evaluate(
            request(risk = ActionRisk.LOW, tenantRef = null),
            ActionRole.ADMIN,
            providerCapabilities = setOf(ProviderCapability.WRITE_ACTIONS),
            actorTenants = emptySet()
        )

        assertEquals(ActionPolicyOutcome.DENIED, decision.outcome)
        assertEquals("tenant-membership-required", decision.reasonCode)
    }

    @Test
    fun `the coordinator stamps the target instance tenant onto an unscoped request`() = runTest {
        val coordinator = ControlledActionCoordinator(
            now = { 2_000 },
            tenantScope = FakeTenantScope(
                tenantByProviderRef = mapOf("proxmox:cluster-a" to "acme"),
                membership = setOf("default", "acme")
            )
        )

        val result = coordinator.execute(
            request(risk = ActionRisk.LOW),
            ActionRole.OPERATOR,
            providerCapabilities = setOf(ProviderCapability.WRITE_ACTIONS)
        ) {}

        assertEquals(ActionExecutionState.SUCCEEDED, result.state)
        assertEquals("acme", result.tenantRef)
    }

    @Test
    fun `an explicit request tenant is not overwritten by the scope`() = runTest {
        val coordinator = ControlledActionCoordinator(
            now = { 2_000 },
            tenantScope = FakeTenantScope(
                tenantByProviderRef = mapOf("proxmox:cluster-a" to "acme"),
                membership = setOf("default", "acme", "globex")
            )
        )

        val result = coordinator.execute(
            request(risk = ActionRisk.LOW, tenantRef = "globex"),
            ActionRole.OPERATOR,
            providerCapabilities = setOf(ProviderCapability.WRITE_ACTIONS)
        ) {}

        assertEquals("globex", result.tenantRef)
    }

    @Test
    fun `the coordinator denies when the scoped tenant is outside device membership`() = runTest {
        var invocations = 0
        val coordinator = ControlledActionCoordinator(
            now = { 2_000 },
            tenantScope = FakeTenantScope(
                tenantByProviderRef = mapOf("proxmox:cluster-a" to "ghost"),
                membership = setOf("default")
            )
        )

        val result = coordinator.execute(
            request(risk = ActionRisk.LOW),
            ActionRole.OPERATOR,
            providerCapabilities = setOf(ProviderCapability.WRITE_ACTIONS)
        ) { invocations += 1 }

        assertEquals(ActionExecutionState.REJECTED, result.state)
        assertEquals("tenant-membership-required", result.reasonCode)
        assertEquals(0, invocations)
    }

    @Test
    fun `an unresolvable target tenant is rejected fail-closed`() = runTest {
        var invocations = 0
        val coordinator = ControlledActionCoordinator(
            now = { 2_000 },
            tenantScope = FakeTenantScope(tenantByProviderRef = emptyMap(), membership = setOf("default"))
        )

        val result = coordinator.execute(
            request(risk = ActionRisk.LOW),
            ActionRole.OPERATOR,
            providerCapabilities = setOf(ProviderCapability.WRITE_ACTIONS)
        ) { invocations += 1 }

        assertEquals(ActionExecutionState.REJECTED, result.state)
        assertEquals("target-tenant-unresolved", result.reasonCode)
        assertEquals(0, invocations)
    }

    @Test
    fun `an explicit actor tenant set overrides the scope default`() = runTest {
        val coordinator = ControlledActionCoordinator(
            now = { 2_000 },
            tenantScope = FakeTenantScope(
                tenantByProviderRef = mapOf("proxmox:cluster-a" to "acme"),
                membership = emptySet()
            )
        )

        val result = coordinator.execute(
            request(risk = ActionRisk.LOW),
            ActionRole.OPERATOR,
            providerCapabilities = setOf(ProviderCapability.WRITE_ACTIONS),
            actorTenants = setOf("acme")
        ) {}

        assertEquals(ActionExecutionState.SUCCEEDED, result.state)
        assertEquals("acme", result.tenantRef)
    }

    @Test
    fun `a membership denial is not cached and does not block a later authorized submission`() = runTest {
        var invocations = 0
        val coordinator = ControlledActionCoordinator(now = { 2_000 })
        val req = request(risk = ActionRisk.LOW, tenantRef = "acme")

        val denied = coordinator.execute(
            req, ActionRole.OPERATOR,
            providerCapabilities = setOf(ProviderCapability.WRITE_ACTIONS),
            actorTenants = setOf("default")
        ) { invocations += 1 }
        assertEquals(ActionExecutionState.REJECTED, denied.state)

        val allowed = coordinator.execute(
            req, ActionRole.OPERATOR,
            providerCapabilities = setOf(ProviderCapability.WRITE_ACTIONS),
            actorTenants = setOf("acme")
        ) { invocations += 1 }

        assertEquals(ActionExecutionState.SUCCEEDED, allowed.state)
        assertEquals(1, invocations)
    }

    @Test
    fun `actor outside the target tenant is denied before role and capability checks`() {
        val decision = ControlledActionPolicy.evaluate(
            request(risk = ActionRisk.HIGH, tenantRef = "acme"),
            ActionRole.VIEWER,
            providerCapabilities = emptySet(),
            actorTenants = setOf("default", "globex")
        )

        assertEquals(ActionPolicyOutcome.DENIED, decision.outcome)
        assertEquals("tenant-membership-required", decision.reasonCode)
    }

    @Test
    fun `actor inside the target tenant passes the gate`() {
        val decision = ControlledActionPolicy.evaluate(
            request(risk = ActionRisk.LOW, tenantRef = "acme"),
            ActionRole.OPERATOR,
            providerCapabilities = setOf(ProviderCapability.WRITE_ACTIONS),
            actorTenants = setOf("acme")
        )

        assertEquals(ActionPolicyOutcome.APPROVED, decision.outcome)
    }

    @Test
    fun `a null tenant ref is gated as the default tenant`() {
        val denied = ControlledActionPolicy.evaluate(
            request(risk = ActionRisk.LOW, tenantRef = null),
            ActionRole.OPERATOR,
            providerCapabilities = setOf(ProviderCapability.WRITE_ACTIONS),
            actorTenants = setOf("acme")
        )
        assertEquals("tenant-membership-required", denied.reasonCode)

        val allowed = ControlledActionPolicy.evaluate(
            request(risk = ActionRisk.LOW, tenantRef = null),
            ActionRole.OPERATOR,
            providerCapabilities = setOf(ProviderCapability.WRITE_ACTIONS),
            actorTenants = setOf("default")
        )
        assertEquals(ActionPolicyOutcome.APPROVED, allowed.outcome)
    }

    @Test
    fun `audit record carries the resolved tenant ref`() = runTest {
        val coordinator = ControlledActionCoordinator(now = { 2_000 })

        val result = coordinator.execute(
            request(risk = ActionRisk.LOW, tenantRef = "acme"),
            ActionRole.OPERATOR,
            providerCapabilities = setOf(ProviderCapability.WRITE_ACTIONS),
            actorTenants = setOf("acme")
        ) {}

        assertEquals(ActionExecutionState.SUCCEEDED, result.state)
        assertEquals("acme", result.tenantRef)
    }

    @Test
    fun `coordinator denies actions targeting a tenant the actor is not in`() = runTest {
        var invocations = 0
        val coordinator = ControlledActionCoordinator(now = { 2_000 })

        val result = coordinator.execute(
            request(risk = ActionRisk.LOW, tenantRef = "acme"),
            ActionRole.OPERATOR,
            providerCapabilities = setOf(ProviderCapability.WRITE_ACTIONS),
            actorTenants = setOf("default")
        ) {
            invocations += 1
        }

        assertEquals(ActionExecutionState.REJECTED, result.state)
        assertEquals("tenant-membership-required", result.reasonCode)
        assertEquals(0, invocations)
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
    fun `audit and queue reads scope to one tenant with no cross-tenant leakage`() = runTest {
        val coordinator = ControlledActionCoordinator(waitBeforeRetry = {})
        val acmeRequest = request(
            risk = ActionRisk.HIGH,
            confirmed = true,
            idempotencyKey = "acme0123456789ab",
            tenantRef = "acme"
        )
        val globexRequest = request(
            risk = ActionRisk.HIGH,
            confirmed = true,
            idempotencyKey = "globex0123456789",
            tenantRef = "globex"
        )

        // Lands in MANUAL_REVIEW (HIGH risk forbids automatic retry), so it stays in the durable
        // queue's pending-recovery set.
        coordinator.execute(
            acmeRequest, ActionRole.ADMIN,
            providerCapabilities = setOf(ProviderCapability.WRITE_ACTIONS)
        ) { throw ActionOperationException("transport-error", ActionFailureDisposition.RETRYABLE) }
        coordinator.execute(
            globexRequest, ActionRole.ADMIN,
            providerCapabilities = setOf(ProviderCapability.WRITE_ACTIONS)
        ) { throw ActionOperationException("transport-error", ActionFailureDisposition.RETRYABLE) }

        val acmeAudit = coordinator.auditSnapshot("acme")
        assertTrue(acmeAudit.isNotEmpty())
        assertTrue(acmeAudit.all { it.tenantRef == "acme" })

        val globexAudit = coordinator.auditSnapshot("globex")
        assertTrue(globexAudit.isNotEmpty())
        assertTrue(globexAudit.all { it.tenantRef == "globex" })

        val acmeRecovery = coordinator.pendingRecovery("acme")
        assertTrue(acmeRecovery.isNotEmpty())
        assertTrue(acmeRecovery.all { it.request.tenantRef == "acme" })
        assertTrue(coordinator.pendingRecovery("globex").none { it.request.tenantRef == "acme" })
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
    fun `proxmox snapshot request uses normalized provider and target references`() {
        val request = ProxmoxSnapshotAction.ROLLBACK.controlledRequest(
            instanceId = "cluster-a",
            node = "pve01",
            vmid = 101,
            isQemu = true,
            snapname = "pre-upgrade",
            confirmed = true,
            requestId = "request-proxmox-2",
            requestedAt = "1970-01-01T00:00:00Z",
            idempotencyKey = "idempotency-key-0002"
        )

        assertEquals("proxmox:cluster-a", request.providerRef)
        assertEquals("snapshot.rollback", request.action)
        assertEquals("qemu/101@pve01/snapshot/pre-upgrade", request.targetRef)
        assertEquals(ActionRisk.HIGH, request.risk)
        assertTrue(request.confirmed)
    }

    @Test
    fun `proxmox snapshot risk controls explicit confirmation`() {
        assertTrue(ProxmoxSnapshotAction.CREATE.requiresConfirmation)
        assertTrue(ProxmoxSnapshotAction.DELETE.requiresConfirmation)
        assertTrue(ProxmoxSnapshotAction.ROLLBACK.requiresConfirmation)
        assertEquals(ActionRisk.MEDIUM, ProxmoxSnapshotAction.CREATE.risk)
        assertEquals(ActionRisk.MEDIUM, ProxmoxSnapshotAction.DELETE.risk)
        assertEquals(ActionRisk.HIGH, ProxmoxSnapshotAction.ROLLBACK.risk)
    }

    @Test
    fun `proxmox storage content delete request uses normalized references`() {
        val request = ProxmoxStorageContentAction.DELETE.controlledRequest(
            instanceId = "cluster-a",
            node = "pve01",
            storage = "local",
            volume = "local:iso/debian.iso",
            confirmed = true,
            requestId = "request-proxmox-3",
            requestedAt = "1970-01-01T00:00:00Z",
            idempotencyKey = "idempotency-key-0003"
        )

        assertEquals("proxmox:cluster-a", request.providerRef)
        assertEquals("storage-content.delete", request.action)
        assertEquals("storage/local@pve01/local:iso/debian.iso", request.targetRef)
        assertEquals(ActionRisk.HIGH, request.risk)
        assertTrue(request.confirmed)
        assertTrue(ProxmoxStorageContentAction.DELETE.requiresConfirmation)
    }

    @Test
    fun `proxmox firewall risk classes match enable low disable medium`() {
        assertFalse(ProxmoxFirewallAction.ENABLE.requiresConfirmation)
        assertTrue(ProxmoxFirewallAction.DISABLE.requiresConfirmation)
        assertEquals(ActionRisk.LOW, ProxmoxFirewallAction.ENABLE.risk)
        assertEquals(ActionRisk.MEDIUM, ProxmoxFirewallAction.DISABLE.risk)

        val request = ProxmoxFirewallAction.DISABLE.controlledRequest(
            instanceId = "cluster-a",
            confirmed = true,
            requestId = "request-proxmox-4",
            requestedAt = "1970-01-01T00:00:00Z",
            idempotencyKey = "idempotency-key-0004"
        )
        assertEquals("proxmox:cluster-a", request.providerRef)
        assertEquals("firewall.disable", request.action)
        assertEquals("firewall/cluster", request.targetRef)
    }

    @Test
    fun `proxmox backup job trigger is low risk and needs no confirmation`() {
        assertFalse(ProxmoxBackupJobAction.TRIGGER.requiresConfirmation)
        assertEquals(ActionRisk.LOW, ProxmoxBackupJobAction.TRIGGER.risk)

        val request = ProxmoxBackupJobAction.TRIGGER.controlledRequest(
            instanceId = "cluster-a",
            jobId = "backup-nightly",
            confirmed = false,
            requestId = "request-proxmox-5",
            requestedAt = "1970-01-01T00:00:00Z",
            idempotencyKey = "idempotency-key-0005"
        )
        assertEquals("proxmox:cluster-a", request.providerRef)
        assertEquals("backup-job.trigger", request.action)
        assertEquals("backup-job/backup-nightly", request.targetRef)
    }

    @Test
    fun `proxmox clone is medium risk and migrate is high risk`() {
        assertTrue(ProxmoxCloneMigrateAction.CLONE.requiresConfirmation)
        assertTrue(ProxmoxCloneMigrateAction.MIGRATE.requiresConfirmation)
        assertEquals(ActionRisk.MEDIUM, ProxmoxCloneMigrateAction.CLONE.risk)
        assertEquals(ActionRisk.HIGH, ProxmoxCloneMigrateAction.MIGRATE.risk)
    }

    @Test
    fun `proxmox clone request uses normalized provider and target references`() {
        val request = ProxmoxCloneMigrateAction.CLONE.controlledRequest(
            instanceId = "cluster-a",
            node = "pve01",
            vmid = 101,
            isQemu = true,
            target = "105",
            confirmed = true,
            requestId = "request-proxmox-6",
            requestedAt = "1970-01-01T00:00:00Z",
            idempotencyKey = "idempotency-key-0006"
        )
        assertEquals("proxmox:cluster-a", request.providerRef)
        assertEquals("guest.clone", request.action)
        assertEquals("qemu/101@pve01/clone/105", request.targetRef)
    }

    @Test
    fun `proxmox migrate request uses normalized provider and target references`() {
        val request = ProxmoxCloneMigrateAction.MIGRATE.controlledRequest(
            instanceId = "cluster-a",
            node = "pve01",
            vmid = 101,
            isQemu = false,
            target = "pve02",
            confirmed = true,
            requestId = "request-proxmox-7",
            requestedAt = "1970-01-01T00:00:00Z",
            idempotencyKey = "idempotency-key-0007"
        )
        assertEquals("proxmox:cluster-a", request.providerRef)
        assertEquals("guest.migrate", request.action)
        assertEquals("lxc/101@pve01/migrate/pve02", request.targetRef)
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
    fun `adguard configuration actions are high risk and have stable identity`() {
        assertTrue(
            AdGuardControlledConfigurationAction.entries
                .filterNot { it == AdGuardControlledConfigurationAction.UPDATE_REWRITE_SETTINGS }
                .all { it.risk == ActionRisk.HIGH }
        )
        assertEquals(
            ActionRisk.MEDIUM,
            AdGuardControlledConfigurationAction.UPDATE_REWRITE_SETTINGS.risk
        )
        assertFalse(
            ActionRetryPolicy().permitsAutomaticRetry(
                AdGuardControlledConfigurationAction.CREATE_REWRITE.risk,
                completedAttempts = 0
            )
        )

        val request = AdGuardControlledConfigurationAction.UPDATE_FILTER.controlledRequest(
            instanceId = "instance-1",
            targetId = "42",
            confirmed = true,
            requestId = "request-adguard-config",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "0123456789abcdef"
        )

        assertEquals("adguard-home:instance-1", request.providerRef)
        assertEquals("filter-list.update", request.action)
        assertEquals("filter-list/42", request.targetRef)
        assertTrue(request.confirmed)
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
    fun `portainer configuration actions require confirmation and have stable identity`() {
        assertEquals(
            ActionRisk.MEDIUM,
            PortainerControlledConfigurationAction.RENAME_CONTAINER.risk
        )
        assertEquals(
            ActionRisk.HIGH,
            PortainerControlledConfigurationAction.UPDATE_STACK.risk
        )
        assertTrue(
            PortainerControlledConfigurationAction.RENAME_CONTAINER.requiresConfirmation
        )
        assertTrue(
            PortainerControlledConfigurationAction.UPDATE_STACK.requiresConfirmation
        )
        assertFalse(
            ActionRetryPolicy().permitsAutomaticRetry(
                PortainerControlledConfigurationAction.UPDATE_STACK.risk,
                completedAttempts = 0
            )
        )

        val renameRequest =
            PortainerControlledConfigurationAction.RENAME_CONTAINER.controlledRequest(
                instanceId = "instance-1",
                endpointId = 7,
                targetId = "container-42",
                confirmed = true,
                requestId = "request-portainer-rename",
                requestedAt = "1970-01-01T00:00:01Z",
                idempotencyKey = "portainer-rename-key-01"
            )
        val stackRequest =
            PortainerControlledConfigurationAction.UPDATE_STACK.controlledRequest(
                instanceId = "instance-1",
                endpointId = 7,
                targetId = "23",
                confirmed = true,
                requestId = "request-portainer-stack",
                requestedAt = "1970-01-01T00:00:01Z",
                idempotencyKey = "portainer-stack-key-01"
            )

        assertEquals("portainer:instance-1", renameRequest.providerRef)
        assertEquals("container.rename", renameRequest.action)
        assertEquals("endpoint/7/container/container-42", renameRequest.targetRef)
        assertEquals("portainer:instance-1", stackRequest.providerRef)
        assertEquals("stack.update", stackRequest.action)
        assertEquals("endpoint/7/stack/23", stackRequest.targetRef)
        assertTrue(renameRequest.confirmed)
        assertTrue(stackRequest.confirmed)
        assertTrue(renameRequest.parameters.isEmpty())
        assertTrue(stackRequest.parameters.isEmpty())
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

    @Test
    fun `crafty actions have stable risk identity and no persisted command payload`() {
        assertEquals(ActionRisk.LOW, CraftyServerAction.START.risk)
        assertEquals(ActionRisk.MEDIUM, CraftyServerAction.STOP.risk)
        assertEquals(ActionRisk.MEDIUM, CraftyServerAction.RESTART.risk)
        assertEquals(ActionRisk.MEDIUM, CraftyServerAction.BACKUP.risk)
        assertEquals(ActionRisk.HIGH, CraftyServerAction.UPDATE.risk)
        assertEquals(ActionRisk.HIGH, CraftyServerAction.KILL.risk)
        assertFalse(CraftyServerAction.START.requiresConfirmation)
        assertTrue(CraftyServerAction.STOP.requiresConfirmation)
        assertTrue(CraftyCommandAction.SEND.requiresConfirmation)

        val lifecycleRequest = CraftyServerAction.UPDATE.controlledRequest(
            instanceId = "INSTANCE-A",
            serverId = " SERVER-42 ",
            confirmed = true,
            requestId = "request-crafty-update",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "crafty-update-key-001"
        )
        val commandRequest = CraftyCommandAction.SEND.controlledRequest(
            instanceId = "INSTANCE-A",
            serverId = " SERVER-42 ",
            confirmed = true,
            requestId = "request-crafty-command",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "crafty-command-key-01"
        )

        assertEquals("crafty-controller:instance-a", lifecycleRequest.providerRef)
        assertEquals("server.executable.update", lifecycleRequest.action)
        assertEquals("server/server-42", lifecycleRequest.targetRef)
        assertEquals(ActionRisk.HIGH, commandRequest.risk)
        assertEquals("server.command.send", commandRequest.action)
        assertEquals("server/server-42", commandRequest.targetRef)
        assertTrue(lifecycleRequest.confirmed)
        assertTrue(commandRequest.confirmed)
        assertTrue(lifecycleRequest.parameters.isEmpty())
        assertTrue(commandRequest.parameters.isEmpty())
        assertTrue(
            ProviderCapability.WRITE_ACTIONS in
                ProviderRegistry.capabilities(ServiceType.CRAFTY_CONTROLLER)
        )
    }
    @Test
    fun `pangolin actions have stable risk identity and no payload persistence`() {
        assertEquals(ActionRisk.HIGH, PangolinControlledAction.PUBLIC_RESOURCE_CREATE.risk)
        assertEquals(ActionRisk.HIGH, PangolinControlledAction.PRIVATE_RESOURCE_UPDATE.risk)
        assertEquals(ActionRisk.LOW, PangolinControlledAction.PUBLIC_RESOURCE_ENABLE.risk)
        assertEquals(ActionRisk.MEDIUM, PangolinControlledAction.PUBLIC_RESOURCE_DISABLE.risk)
        assertFalse(PangolinControlledAction.PUBLIC_RESOURCE_ENABLE.requiresConfirmation)
        assertTrue(PangolinControlledAction.PUBLIC_RESOURCE_DISABLE.requiresConfirmation)

        val request = PangolinControlledAction.PUBLIC_RESOURCE_UPDATE.controlledRequest(
            instanceId = "INSTANCE-A",
            targetRef = " PUBLIC-RESOURCE/42 ",
            confirmed = true,
            requestId = "request-pangolin-update",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "pangolin-update-key-01"
        )

        assertEquals("pangolin:instance-a", request.providerRef)
        assertEquals("public-resource.update", request.action)
        assertEquals("public-resource/42", request.targetRef)
        assertTrue(request.confirmed)
        assertTrue(request.parameters.isEmpty())
        assertTrue(
            ProviderCapability.WRITE_ACTIONS in ProviderRegistry.capabilities(ServiceType.PANGOLIN)
        )
    }

    @Test
    fun `qbittorrent actions have stable risk identity and no payload persistence`() {
        assertEquals(ActionRisk.LOW, QbittorrentControlledAction.TORRENT_RESUME.risk)
        assertEquals(ActionRisk.MEDIUM, QbittorrentControlledAction.TORRENT_PAUSE.risk)
        assertEquals(ActionRisk.MEDIUM, QbittorrentControlledAction.TRANSFER_TOGGLE_ALT_SPEED.risk)
        assertEquals(ActionRisk.HIGH, QbittorrentControlledAction.TORRENT_DELETE.risk)
        assertEquals(ActionRisk.HIGH, QbittorrentControlledAction.TORRENT_DELETE_WITH_DATA.risk)
        assertFalse(QbittorrentControlledAction.TORRENT_RESUME.requiresConfirmation)
        assertFalse(QbittorrentControlledAction.TRANSFER_REANNOUNCE_ALL.requiresConfirmation)
        assertTrue(QbittorrentControlledAction.TORRENT_PAUSE.requiresConfirmation)
        assertTrue(QbittorrentControlledAction.TORRENT_DELETE_WITH_DATA.requiresConfirmation)

        assertEquals(
            QbittorrentControlledAction.TORRENT_DELETE_WITH_DATA,
            QbittorrentControlledAction.forMediaArrAction(MediaArrAction.QBITTORRENT_DELETE_TORRENT_WITH_DATA)
        )
        assertEquals(
            QbittorrentControlledAction.TRANSFER_PAUSE_ALL,
            QbittorrentControlledAction.forMediaArrAction(MediaArrAction.QBITTORRENT_PAUSE_ALL)
        )
        assertEquals(null, QbittorrentControlledAction.forMediaArrAction(MediaArrAction.RADARR_RSS_SYNC))

        val request = QbittorrentControlledAction.TORRENT_DELETE_WITH_DATA.controlledRequest(
            instanceId = "INSTANCE-A",
            targetRef = " TORRENT/ABCDEF ",
            confirmed = true,
            requestId = "request-qbittorrent-delete",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "qbittorrent-delete-key-01"
        )

        assertEquals("qbittorrent:instance-a", request.providerRef)
        assertEquals("torrent.delete-with-data", request.action)
        assertEquals("torrent/abcdef", request.targetRef)
        assertTrue(request.confirmed)
        assertTrue(request.parameters.isEmpty())
        assertTrue(
            ProviderCapability.WRITE_ACTIONS in ProviderRegistry.capabilities(ServiceType.QBITTORRENT)
        )
    }

    @Test
    fun `patchmon host delete has stable high-risk identity and no payload persistence`() {
        assertEquals(ActionRisk.HIGH, PatchmonControlledAction.HOST_DELETE.risk)
        assertTrue(PatchmonControlledAction.HOST_DELETE.requiresConfirmation)

        val request = PatchmonControlledAction.HOST_DELETE.controlledRequest(
            instanceId = "INSTANCE-A",
            targetRef = " HOST/9F2C ",
            confirmed = true,
            requestId = "request-patchmon-delete",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "patchmon-delete-key-01"
        )

        assertEquals("patchmon:instance-a", request.providerRef)
        assertEquals("host.delete", request.action)
        assertEquals("host/9f2c", request.targetRef)
        assertTrue(request.confirmed)
        assertTrue(request.parameters.isEmpty())
        assertTrue(
            ProviderCapability.WRITE_ACTIONS in ProviderRegistry.capabilities(ServiceType.PATCHMON)
        )
    }

    @Test
    fun `media-service actions have stable per-provider risk identity and no payload persistence`() {
        assertEquals(ActionRisk.LOW, MediaServiceControlledAction.COMMAND_RSS_SYNC.risk)
        assertEquals(ActionRisk.LOW, MediaServiceControlledAction.COMMAND_HEALTH_CHECK.risk)
        assertEquals(ActionRisk.LOW, MediaServiceControlledAction.INDEXER_TEST.risk)
        assertEquals(ActionRisk.MEDIUM, MediaServiceControlledAction.LIBRARY_ADD.risk)
        assertEquals(ActionRisk.MEDIUM, MediaServiceControlledAction.REQUEST_APPROVE.risk)
        assertEquals(ActionRisk.MEDIUM, MediaServiceControlledAction.VPN_RESTART.risk)
        assertEquals(ActionRisk.MEDIUM, MediaServiceControlledAction.SESSION_DESTROY.risk)
        assertFalse(MediaServiceControlledAction.COMMAND_SEARCH_MISSING.requiresConfirmation)
        assertTrue(MediaServiceControlledAction.LIBRARY_ADD.requiresConfirmation)
        assertTrue(MediaServiceControlledAction.REQUEST_DECLINE.requiresConfirmation)

        assertEquals(
            MediaServiceControlledAction.COMMAND_SEARCH_MISSING,
            MediaServiceControlledAction.forMediaArrAction(MediaArrAction.SONARR_SEARCH_MISSING)
        )
        assertEquals(
            MediaServiceControlledAction.VPN_RESTART,
            MediaServiceControlledAction.forMediaArrAction(MediaArrAction.GLUETUN_RESTART_VPN)
        )
        assertEquals(null, MediaServiceControlledAction.forMediaArrAction(MediaArrAction.QBITTORRENT_PAUSE_ALL))

        val radarr = MediaServiceControlledAction.COMMAND_RSS_SYNC.controlledRequest(
            serviceType = ServiceType.RADARR,
            instanceId = "INSTANCE-A",
            targetRef = " COMMAND/ALL ",
            confirmed = false,
            requestId = "request-media-radarr",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "media-radarr-key-01"
        )
        assertEquals("radarr:instance-a", radarr.providerRef)
        assertEquals("command.rss-sync", radarr.action)
        assertEquals("command/all", radarr.targetRef)
        assertFalse(radarr.confirmed)
        assertTrue(radarr.parameters.isEmpty())

        val jellyseerr = MediaServiceControlledAction.REQUEST_APPROVE.controlledRequest(
            serviceType = ServiceType.JELLYSEERR,
            instanceId = "INSTANCE-A",
            targetRef = "request/42",
            confirmed = true,
            requestId = "request-media-jellyseerr",
            requestedAt = "1970-01-01T00:00:01Z",
            idempotencyKey = "media-jellyseerr-key-01"
        )
        assertEquals("jellyseerr:instance-a", jellyseerr.providerRef)
        assertEquals("request.approve", jellyseerr.action)
        assertTrue(jellyseerr.confirmed)

        for (type in listOf(
            ServiceType.RADARR, ServiceType.SONARR, ServiceType.LIDARR,
            ServiceType.JELLYSEERR, ServiceType.PROWLARR, ServiceType.GLUETUN, ServiceType.FLARESOLVERR
        )) {
            assertTrue(ProviderCapability.WRITE_ACTIONS in ProviderRegistry.capabilities(type))
        }
    }
}
