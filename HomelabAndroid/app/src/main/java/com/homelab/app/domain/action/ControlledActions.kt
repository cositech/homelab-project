package com.homelab.app.domain.action

import com.homelab.app.domain.provider.ProviderCapability
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
    QUEUED, EXECUTING, RETRY_WAIT, SUCCEEDED, FAILED, CANCELLED, REJECTED, DRY_RUN, MANUAL_REVIEW
}

@Serializable
data class ActionAuditRecord(
    val auditId: String, val requestId: String, val providerRef: String,
    val action: String, val targetRef: String, val risk: ActionRisk,
    val actorRole: ActionRole, val idempotencyKey: String,
    val state: ActionExecutionState, val reasonCode: String,
    val recordedAtEpochMillis: Long
)

@Serializable
data class DurableActionQueueEntry(
    val request: ControlledActionRequest,
    val actorRole: ActionRole,
    val state: ActionExecutionState,
    val attemptCount: Int,
    val nextAttemptAtEpochMillis: Long? = null,
    val reasonCode: String,
    val updatedAtEpochMillis: Long,
    val terminalRecord: ActionAuditRecord? = null
)

interface DurableActionQueueStore {
    suspend fun snapshot(): List<DurableActionQueueEntry>
    suspend fun upsert(entry: DurableActionQueueEntry)
}

class InMemoryDurableActionQueueStore(
    initialEntries: List<DurableActionQueueEntry> = emptyList(),
    private val maximumEntries: Int = 500
) : DurableActionQueueStore {
    init { require(maximumEntries > 0) }
    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, DurableActionQueueEntry>().apply {
        initialEntries.takeLast(maximumEntries).forEach { put(it.request.idempotencyKey, it) }
    }

    override suspend fun snapshot(): List<DurableActionQueueEntry> =
        mutex.withLock { entries.values.toList() }

    override suspend fun upsert(entry: DurableActionQueueEntry) {
        mutex.withLock {
            entries.remove(entry.request.idempotencyKey)
            entries[entry.request.idempotencyKey] = entry
            while (entries.size > maximumEntries) entries.remove(entries.keys.first())
        }
    }
}

@Serializable
data class ActionRetryPolicy(
    val maximumAttempts: Int = 3,
    val initialDelayMillis: Long = 1_000,
    val maximumDelayMillis: Long = 30_000
) {
    init {
        require(maximumAttempts > 0)
        require(initialDelayMillis >= 0)
        require(maximumDelayMillis >= initialDelayMillis)
    }

    fun delayBeforeAttempt(completedAttempts: Int): Long {
        if (completedAttempts <= 0) return 0
        var value = initialDelayMillis
        repeat((completedAttempts - 1).coerceAtMost(30)) {
            value = (value * 2).coerceAtMost(maximumDelayMillis)
        }
        return value
    }

    fun permitsAutomaticRetry(risk: ActionRisk, completedAttempts: Int): Boolean =
        risk in setOf(ActionRisk.LOW, ActionRisk.MEDIUM) && completedAttempts < maximumAttempts
}

enum class ActionFailureDisposition { RETRYABLE, NON_RETRYABLE }

class ActionOperationException(
    val reasonCode: String,
    val disposition: ActionFailureDisposition,
    cause: Throwable? = null
) : Exception(reasonCode, cause)

class ControlledActionLedger(private val maximumRecords: Int = 500) {
    init { require(maximumRecords > 0) }
    private val records = mutableListOf<ActionAuditRecord>()

    @Synchronized fun append(record: ActionAuditRecord) {
        records += record
        if (records.size > maximumRecords) records.removeAt(0)
    }

    @Synchronized fun snapshot(): List<ActionAuditRecord> = records.toList()
}

class ControlledActionCoordinator(
    private val ledger: ControlledActionLedger = ControlledActionLedger(),
    private val durableStore: DurableActionQueueStore = InMemoryDurableActionQueueStore(),
    private val retryPolicy: ActionRetryPolicy = ActionRetryPolicy(),
    private val now: () -> Long = System::currentTimeMillis,
    private val waitBeforeRetry: suspend (Long) -> Unit = { delay(it) }
) {
    private val queue = Mutex()
    private val terminalResults = mutableMapOf<String, ActionAuditRecord>()
    private val durableEntries = mutableMapOf<String, DurableActionQueueEntry>()
    private var recovered = false

    suspend fun execute(
        request: ControlledActionRequest,
        actorRole: ActionRole,
        providerCapabilities: Set<ProviderCapability>,
        operation: suspend () -> Unit
    ): ActionAuditRecord = queue.withLock {
        recoverLocked()
        terminalResults[request.idempotencyKey]?.let { return@withLock it }

        val existing = durableEntries[request.idempotencyKey]
        if (existing != null && !existing.request.hasSameIdentity(request)) {
            return@withLock audit(request, actorRole, ActionExecutionState.REJECTED, "idempotency-key-conflict")
        }
        if (existing?.state == ActionExecutionState.MANUAL_REVIEW) {
            val result = existing.terminalRecord ?: audit(
                request, actorRole, ActionExecutionState.MANUAL_REVIEW, existing.reasonCode
            )
            terminalResults[request.idempotencyKey] = result
            return@withLock result
        }

        val decision = ControlledActionPolicy.evaluate(request, actorRole, providerCapabilities)
        when (decision.outcome) {
            ActionPolicyOutcome.DENIED, ActionPolicyOutcome.CONFIRMATION_REQUIRED -> {
                val result = audit(request, actorRole, ActionExecutionState.REJECTED, decision.reasonCode)
                persistTerminal(request, actorRole, result, 0)
                return@withLock result
            }
            ActionPolicyOutcome.DRY_RUN_APPROVED -> {
                val result = audit(request, actorRole, ActionExecutionState.DRY_RUN, decision.reasonCode)
                persistTerminal(request, actorRole, result, 0)
                return@withLock result
            }
            ActionPolicyOutcome.APPROVED -> Unit
        }

        var entry = existing ?: DurableActionQueueEntry(
            request.sanitizedForPersistence(), actorRole, ActionExecutionState.QUEUED, 0,
            reasonCode = "queued", updatedAtEpochMillis = now()
        ).also {
            persist(it)
            audit(request, actorRole, ActionExecutionState.QUEUED, "queued")
        }

        entry.nextAttemptAtEpochMillis?.let {
            val remaining = it - now()
            if (remaining > 0) waitBeforeRetry(remaining)
        }

        while (true) {
            entry = entry.copy(
                state = ActionExecutionState.EXECUTING,
                attemptCount = entry.attemptCount + 1,
                nextAttemptAtEpochMillis = null,
                reasonCode = "executing",
                updatedAtEpochMillis = now()
            )
            persist(entry)
            audit(request, actorRole, ActionExecutionState.EXECUTING, "attempt-${entry.attemptCount}")

            try {
                operation()
                val result = audit(request, actorRole, ActionExecutionState.SUCCEEDED, "completed")
                persistTerminal(request, actorRole, result, entry.attemptCount)
                terminalResults[request.idempotencyKey] = result
                return@withLock result
            } catch (error: CancellationException) {
                val result = audit(request, actorRole, ActionExecutionState.CANCELLED, "cancelled")
                persist(entry.copy(
                    state = ActionExecutionState.MANUAL_REVIEW,
                    reasonCode = "cancelled-during-execution",
                    updatedAtEpochMillis = now(),
                    terminalRecord = result
                ))
                terminalResults[request.idempotencyKey] = result
                throw error
            } catch (error: Exception) {
                val failure = classifyFailure(error)
                if (failure.disposition == ActionFailureDisposition.RETRYABLE &&
                    retryPolicy.permitsAutomaticRetry(request.risk, entry.attemptCount)
                ) {
                    val retryDelay = retryPolicy.delayBeforeAttempt(entry.attemptCount)
                    entry = entry.copy(
                        state = ActionExecutionState.RETRY_WAIT,
                        nextAttemptAtEpochMillis = now() + retryDelay,
                        reasonCode = failure.reasonCode,
                        updatedAtEpochMillis = now()
                    )
                    persist(entry)
                    audit(request, actorRole, ActionExecutionState.RETRY_WAIT, failure.reasonCode)
                    waitBeforeRetry(retryDelay)
                    continue
                }

                val review = failure.disposition == ActionFailureDisposition.RETRYABLE
                val state = if (review) ActionExecutionState.MANUAL_REVIEW else ActionExecutionState.FAILED
                val reason = when {
                    review && request.risk in setOf(ActionRisk.HIGH, ActionRisk.CRITICAL) ->
                        "automatic-retry-forbidden-${failure.reasonCode}"
                    review -> "retry-exhausted-${failure.reasonCode}"
                    else -> failure.reasonCode
                }
                val result = audit(request, actorRole, state, reason)
                persistTerminal(request, actorRole, result, entry.attemptCount)
                terminalResults[request.idempotencyKey] = result
                return@withLock result
            }
        }
    }

    suspend fun pendingRecovery(): List<DurableActionQueueEntry> = queue.withLock {
        recoverLocked()
        durableEntries.values.filter {
            it.state in setOf(ActionExecutionState.QUEUED, ActionExecutionState.RETRY_WAIT, ActionExecutionState.MANUAL_REVIEW)
        }
    }

    fun auditSnapshot(): List<ActionAuditRecord> = ledger.snapshot()

    private suspend fun recoverLocked() {
        if (recovered) return
        durableStore.snapshot().forEach { stored ->
            val entry = if (stored.state == ActionExecutionState.EXECUTING) {
                val result = audit(stored.request, stored.actorRole, ActionExecutionState.MANUAL_REVIEW, "interrupted-execution")
                stored.copy(
                    state = ActionExecutionState.MANUAL_REVIEW,
                    reasonCode = "interrupted-execution",
                    updatedAtEpochMillis = now(),
                    terminalRecord = result
                ).also { durableStore.upsert(it) }
            } else stored
            durableEntries[entry.request.idempotencyKey] = entry
            entry.terminalRecord?.let { terminalResults[entry.request.idempotencyKey] = it }
        }
        recovered = true
    }

    private suspend fun persist(entry: DurableActionQueueEntry) {
        durableStore.upsert(entry)
        durableEntries[entry.request.idempotencyKey] = entry
    }

    private suspend fun persistTerminal(
        request: ControlledActionRequest,
        actorRole: ActionRole,
        result: ActionAuditRecord,
        attemptCount: Int
    ) {
        persist(DurableActionQueueEntry(
            request.sanitizedForPersistence(), actorRole, result.state, attemptCount,
            reasonCode = result.reasonCode, updatedAtEpochMillis = now(), terminalRecord = result
        ))
    }

    private fun classifyFailure(error: Exception): ActionOperationException = when (error) {
        is ActionOperationException -> error
        is IOException -> ActionOperationException("transport-error", ActionFailureDisposition.RETRYABLE, error)
        else -> ActionOperationException(
            error.javaClass.simpleName.ifBlank { "operation-failed" },
            ActionFailureDisposition.NON_RETRYABLE, error
        )
    }

    private fun audit(
        request: ControlledActionRequest,
        actorRole: ActionRole,
        state: ActionExecutionState,
        reasonCode: String
    ): ActionAuditRecord = ActionAuditRecord(
        UUID.randomUUID().toString(), request.id, request.providerRef, request.action,
        request.targetRef, request.risk, actorRole, request.idempotencyKey,
        state, reasonCode, now()
    ).also(ledger::append)

    private fun ControlledActionRequest.sanitizedForPersistence() = copy(parameters = emptyMap())

    private fun ControlledActionRequest.hasSameIdentity(other: ControlledActionRequest) =
        providerRef == other.providerRef && tenantRef == other.tenantRef &&
            action == other.action && targetRef == other.targetRef && risk == other.risk
}
