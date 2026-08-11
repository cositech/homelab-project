package com.homelab.app.domain.action

import com.homelab.app.domain.provider.ProviderCapability
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ActionRisk {
    @SerialName("low") LOW,
    @SerialName("medium") MEDIUM,
    @SerialName("high") HIGH,
    @SerialName("critical") CRITICAL
}

@Serializable
enum class ActionRole(val level: Int) {
    VIEWER(0),
    OPERATOR(1),
    ADMIN(2)
}

@Serializable
data class ControlledActionRequest(
    val schemaVersion: String = "1.0",
    val id: String,
    val providerRef: String,
    val tenantRef: String? = null,
    val action: String,
    val targetRef: String,
    val risk: ActionRisk,
    val requestedAt: String,
    val idempotencyKey: String,
    val parameters: Map<String, String> = emptyMap(),
    val dryRun: Boolean = false,
    val confirmed: Boolean = false
)

@Serializable
enum class ActionPolicyOutcome {
    DENIED,
    CONFIRMATION_REQUIRED,
    APPROVED,
    DRY_RUN_APPROVED
}

@Serializable
data class ActionPolicyDecision(
    val outcome: ActionPolicyOutcome,
    val reasonCode: String,
    val requiredRole: ActionRole,
    val confirmationRequired: Boolean
) {
    val mayExecute: Boolean
        get() = outcome == ActionPolicyOutcome.APPROVED
}

object ControlledActionPolicy {
    private val actionPattern = Regex("^[a-z][a-z0-9.-]{1,127}$")

    fun evaluate(
        request: ControlledActionRequest,
        actorRole: ActionRole,
        providerCapabilities: Set<ProviderCapability>
    ): ActionPolicyDecision {
        val requiredRole = when (request.risk) {
            ActionRisk.LOW, ActionRisk.MEDIUM -> ActionRole.OPERATOR
            ActionRisk.HIGH, ActionRisk.CRITICAL -> ActionRole.ADMIN
        }
        val confirmationRequired = request.risk != ActionRisk.LOW

        val invalidReason = when {
            request.schemaVersion != "1.0" -> "unsupported-schema-version"
            request.id.isBlank() -> "invalid-request-id"
            request.providerRef.isBlank() -> "invalid-provider-ref"
            request.targetRef.isBlank() -> "invalid-target-ref"
            request.requestedAt.isBlank() -> "invalid-requested-at"
            !actionPattern.matches(request.action) -> "invalid-action"
            request.idempotencyKey.length !in 16..256 -> "invalid-idempotency-key"
            else -> null
        }
        if (invalidReason != null) {
            return ActionPolicyDecision(
                ActionPolicyOutcome.DENIED,
                invalidReason,
                requiredRole,
                confirmationRequired
            )
        }
        if (ProviderCapability.WRITE_ACTIONS !in providerCapabilities) {
            return ActionPolicyDecision(
                ActionPolicyOutcome.DENIED,
                "provider-write-capability-required",
                requiredRole,
                confirmationRequired
            )
        }
        if (actorRole.level < requiredRole.level) {
            return ActionPolicyDecision(
                ActionPolicyOutcome.DENIED,
                "insufficient-role",
                requiredRole,
                confirmationRequired
            )
        }
        if (request.dryRun) {
            return ActionPolicyDecision(
                ActionPolicyOutcome.DRY_RUN_APPROVED,
                "dry-run-validated",
                requiredRole,
                confirmationRequired
            )
        }
        if (confirmationRequired && !request.confirmed) {
            return ActionPolicyDecision(
                ActionPolicyOutcome.CONFIRMATION_REQUIRED,
                "explicit-confirmation-required",
                requiredRole,
                true
            )
        }
        return ActionPolicyDecision(
            ActionPolicyOutcome.APPROVED,
            "policy-approved",
            requiredRole,
            confirmationRequired
        )
    }
}

@Serializable
enum class ActionExecutionState {
    QUEUED,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    REJECTED,
    DRY_RUN
}

@Serializable
data class ActionAuditRecord(
    val auditId: String,
    val requestId: String,
    val providerRef: String,
    val action: String,
    val targetRef: String,
    val risk: ActionRisk,
    val actorRole: ActionRole,
    val idempotencyKey: String,
    val state: ActionExecutionState,
    val reasonCode: String,
    val recordedAtEpochMillis: Long
)

class ControlledActionLedger(private val maximumRecords: Int = 500) {
    init {
        require(maximumRecords > 0)
    }

    private val records = mutableListOf<ActionAuditRecord>()

    @Synchronized
    fun append(record: ActionAuditRecord) {
        records += record
        if (records.size > maximumRecords) {
            records.removeAt(0)
        }
    }

    @Synchronized
    fun snapshot(): List<ActionAuditRecord> = records.toList()
}

class ControlledActionCoordinator(
    private val ledger: ControlledActionLedger = ControlledActionLedger(),
    private val now: () -> Long = System::currentTimeMillis
) {
    private val queue = Mutex()
    private val terminalResults = mutableMapOf<String, ActionAuditRecord>()

    suspend fun execute(
        request: ControlledActionRequest,
        actorRole: ActionRole,
        providerCapabilities: Set<ProviderCapability>,
        operation: suspend () -> Unit
    ): ActionAuditRecord = queue.withLock {
        terminalResults[request.idempotencyKey]?.let { return@withLock it }

        val decision = ControlledActionPolicy.evaluate(request, actorRole, providerCapabilities)
        val result = when (decision.outcome) {
            ActionPolicyOutcome.DENIED,
            ActionPolicyOutcome.CONFIRMATION_REQUIRED -> audit(
                request, actorRole, ActionExecutionState.REJECTED, decision.reasonCode
            )
            ActionPolicyOutcome.DRY_RUN_APPROVED -> audit(
                request, actorRole, ActionExecutionState.DRY_RUN, decision.reasonCode
            )
            ActionPolicyOutcome.APPROVED -> {
                audit(request, actorRole, ActionExecutionState.QUEUED, "queued")
                try {
                    operation()
                    audit(request, actorRole, ActionExecutionState.SUCCEEDED, "completed")
                } catch (error: CancellationException) {
                    audit(request, actorRole, ActionExecutionState.CANCELLED, "cancelled")
                    throw error
                } catch (error: Exception) {
                    audit(
                        request,
                        actorRole,
                        ActionExecutionState.FAILED,
                        error.javaClass.simpleName.ifBlank { "operation-failed" }
                    )
                }
            }
        }
        terminalResults[request.idempotencyKey] = result
        result
    }

    fun auditSnapshot(): List<ActionAuditRecord> = ledger.snapshot()

    private fun audit(
        request: ControlledActionRequest,
        actorRole: ActionRole,
        state: ActionExecutionState,
        reasonCode: String
    ): ActionAuditRecord = ActionAuditRecord(
        auditId = UUID.randomUUID().toString(),
        requestId = request.id,
        providerRef = request.providerRef,
        action = request.action,
        targetRef = request.targetRef,
        risk = request.risk,
        actorRole = actorRole,
        idempotencyKey = request.idempotencyKey,
        state = state,
        reasonCode = reasonCode,
        recordedAtEpochMillis = now()
    ).also(ledger::append)
}
