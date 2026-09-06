package com.homelab.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.local.TenantStore
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.domain.action.ActionAuditRecord
import com.homelab.app.domain.action.ControlledActionCoordinator
import com.homelab.app.domain.action.DurableActionQueueEntry
import com.homelab.app.domain.model.ServiceInstance
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Read-only view of the Phase 3 controlled-action audit ledger and durable queue, scoped to the
 * active tenant (or fanned out across every tenant in all-tenants mode) via [TenantStore] - the
 * same scoping rule the Operations workspace already applies to instances.
 */
@HiltViewModel
class ActionHistoryViewModel @Inject constructor(
    private val controlledActionCoordinator: ControlledActionCoordinator,
    private val servicesRepository: ServicesRepository,
    private val tenantStore: TenantStore
) : ViewModel() {

    data class UiState(
        val auditRecords: List<ActionAuditRecord> = emptyList(),
        val pendingEntries: List<DurableActionQueueEntry> = emptyList(),
        val instancesById: Map<String, ServiceInstance> = emptyMap(),
        val isRefreshing: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            tenantStore.selection.collect { refresh() }
        }
    }

    /** Cancels any in-flight refresh so a tenant switch never races a stale scope onto the screen. */
    fun refresh() {
        refreshJob?.cancel()
        lateinit var job: Job
        job = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                val selection = tenantStore.current()
                val audit = if (selection.allTenantsMode) {
                    controlledActionCoordinator.auditSnapshot()
                } else {
                    controlledActionCoordinator.auditSnapshot(selection.activeTenantId)
                }
                val pending = if (selection.allTenantsMode) {
                    controlledActionCoordinator.pendingRecovery()
                } else {
                    controlledActionCoordinator.pendingRecovery(selection.activeTenantId)
                }
                val instancesById = servicesRepository.allInstances.first().associateBy { it.id }
                if (refreshJob === job) {
                    _uiState.value = UiState(
                        auditRecords = audit.sortedByDescending { it.recordedAtEpochMillis },
                        pendingEntries = pending.sortedByDescending { it.updatedAtEpochMillis },
                        instancesById = instancesById,
                        isRefreshing = false
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Swallow: a stale read of the ledger/queue isn't worth surfacing as an error,
                // and the next tenant-selection change or manual refresh will retry anyway.
            } finally {
                // Only this job's own completion/cancellation clears the flag it set - a job a
                // tenant switch superseded must not clobber the isRefreshing a newer job set.
                if (refreshJob === job) {
                    refreshJob = null
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }
        refreshJob = job
    }
}
