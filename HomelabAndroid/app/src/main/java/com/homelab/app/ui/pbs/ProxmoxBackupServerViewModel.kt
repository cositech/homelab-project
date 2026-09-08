package com.homelab.app.ui.pbs

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.ProxmoxBackupServerRepository
import com.homelab.app.data.repository.ProxmoxBackupSyncJob
import com.homelab.app.domain.action.ActionExecutionState
import com.homelab.app.domain.action.ActionFailureDisposition
import com.homelab.app.domain.action.ActionOperationException
import com.homelab.app.domain.action.ActionRisk
import com.homelab.app.domain.action.ActionRole
import com.homelab.app.domain.action.ControlledActionCoordinator
import com.homelab.app.domain.action.ControlledActionRequest
import com.homelab.app.domain.provider.ProviderRegistry
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A "run now" trigger for a PBS sync job (pulls backup snapshots from a remote PBS instance), not
 * a destructive mutation - mirrors the PVE backup-job-trigger precedent (`ProxmoxBackupJobAction`
 * in `ProxmoxViewModel.kt`, #75) as closely as possible.
 */
enum class ProxmoxBackupServerSyncJobAction(val wireName: String, val risk: ActionRisk) {
    TRIGGER("sync-job.trigger", ActionRisk.LOW);

    val requiresConfirmation: Boolean get() = risk != ActionRisk.LOW

    fun controlledRequest(
        instanceId: String,
        jobId: String,
        confirmed: Boolean,
        requestId: String = UUID.randomUUID().toString(),
        requestedAt: String = Instant.now().toString(),
        idempotencyKey: String = UUID.randomUUID().toString()
    ) = ControlledActionRequest(
        id = requestId,
        providerRef = "proxmox-backup-server:$instanceId",
        action = wireName,
        targetRef = "sync-job/$jobId",
        risk = risk,
        requestedAt = requestedAt,
        idempotencyKey = idempotencyKey,
        confirmed = confirmed
    )
}

@HiltViewModel
class ProxmoxBackupServerViewModel @Inject constructor(
    private val repository: ProxmoxBackupServerRepository,
    private val controlledActionCoordinator: ControlledActionCoordinator,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _syncJobsState = MutableStateFlow<UiState<List<ProxmoxBackupSyncJob>>>(UiState.Idle)
    val syncJobsState: StateFlow<UiState<List<ProxmoxBackupSyncJob>>> = _syncJobsState.asStateFlow()

    private val _triggeringJobId = MutableStateFlow<String?>(null)
    val triggeringJobId: StateFlow<String?> = _triggeringJobId.asStateFlow()

    fun fetchSyncJobs() {
        viewModelScope.launch {
            _syncJobsState.value = UiState.Loading
            _syncJobsState.value = try {
                UiState.Success(repository.getSyncJobs(instanceId))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                UiState.Error(ErrorHandler.getMessage(context, error)) { fetchSyncJobs() }
            }
        }
    }

    fun triggerSyncJob(jobId: String) {
        viewModelScope.launch {
            _triggeringJobId.value = jobId
            // The coordinator records only a bounded reason code; keep the original provider
            // exception so a definitive rejection (bad token, unknown job) still shows its real
            // message instead of a generic one - same pattern as ProxmoxViewModel.triggerBackupJob.
            var operationError: Exception? = null
            try {
                val request = ProxmoxBackupServerSyncJobAction.TRIGGER.controlledRequest(instanceId, jobId, confirmed = false)
                val result = controlledActionCoordinator.execute(
                    request = request,
                    actorRole = ActionRole.ADMIN,
                    providerCapabilities = ProviderRegistry.capabilities(ServiceType.PROXMOX_BACKUP_SERVER)
                ) {
                    try {
                        repository.triggerSyncJob(instanceId, jobId)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: ActionOperationException) {
                        throw error
                    } catch (error: IOException) {
                        // Genuine transport failure: we don't know whether the request reached
                        // the server, so triggering the job again isn't idempotent - a retry
                        // could fire a second, overlapping sync run for the same job.
                        operationError = error
                        throw ActionOperationException(
                            "pbs-sync-job-outcome-indeterminate",
                            ActionFailureDisposition.NON_RETRYABLE,
                            error
                        )
                    } catch (error: Exception) {
                        // A definitive provider response (e.g. an HTTP error) - not indeterminate,
                        // so let the coordinator's default classification (non-retryable, keyed by
                        // the real exception type) stand instead of mislabeling a known rejection
                        // as a lost/ambiguous outcome.
                        operationError = error
                        throw error
                    }
                }
                if (result.state == ActionExecutionState.SUCCEEDED) {
                    fetchSyncJobs()
                } else {
                    _syncJobsState.value = UiState.Error(
                        operationError?.let { ErrorHandler.getMessage(context, it) } ?: result.reasonCode
                    ) { fetchSyncJobs() }
                }
            } catch (e: Exception) {
                _syncJobsState.value = UiState.Error(ErrorHandler.getMessage(context, operationError ?: e)) { fetchSyncJobs() }
            } finally {
                _triggeringJobId.value = null
            }
        }
    }
}
