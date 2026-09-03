package com.homelab.app.ui.patchmon

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.patchmon.PatchmonAgentQueueResponse
import com.homelab.app.data.remote.dto.patchmon.PatchmonHostInfo
import com.homelab.app.data.remote.dto.patchmon.PatchmonHostNetwork
import com.homelab.app.data.remote.dto.patchmon.PatchmonHostStats
import com.homelab.app.data.remote.dto.patchmon.PatchmonHostSystem
import com.homelab.app.data.remote.dto.patchmon.PatchmonIntegrationsResponse
import com.homelab.app.data.remote.dto.patchmon.PatchmonNotesResponse
import com.homelab.app.data.remote.dto.patchmon.PatchmonPackagesResponse
import com.homelab.app.data.remote.dto.patchmon.PatchmonReportsResponse
import com.homelab.app.data.repository.PatchmonApiException
import com.homelab.app.data.repository.PatchmonControlledAction
import com.homelab.app.data.repository.PatchmonRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.domain.action.ActionExecutionState
import com.homelab.app.domain.action.ActionFailureDisposition
import com.homelab.app.domain.action.ActionOperationException
import com.homelab.app.domain.action.ActionRole
import com.homelab.app.domain.action.ControlledActionCoordinator
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.domain.provider.ProviderRegistry
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PatchmonHostDetailViewModel @Inject constructor(
    private val patchmonRepository: PatchmonRepository,
    private val controlledActionCoordinator: ControlledActionCoordinator,
    private val servicesRepository: ServicesRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    enum class DetailTab {
        OVERVIEW,
        SYSTEM,
        NETWORK,
        PACKAGES,
        REPORTS,
        AGENT,
        DOCKER,
        NOTES
    }

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])
    val hostId: String = checkNotNull(savedStateHandle["hostId"])

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.PATCHMON].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedTab = MutableStateFlow(DetailTab.OVERVIEW)
    val selectedTab: StateFlow<DetailTab> = _selectedTab.asStateFlow()

    private val _infoState = MutableStateFlow<UiState<PatchmonHostInfo>>(UiState.Loading)
    val infoState: StateFlow<UiState<PatchmonHostInfo>> = _infoState.asStateFlow()

    private val _statsState = MutableStateFlow<UiState<PatchmonHostStats>>(UiState.Loading)
    val statsState: StateFlow<UiState<PatchmonHostStats>> = _statsState.asStateFlow()

    private val _systemState = MutableStateFlow<UiState<PatchmonHostSystem>>(UiState.Idle)
    val systemState: StateFlow<UiState<PatchmonHostSystem>> = _systemState.asStateFlow()

    private val _networkState = MutableStateFlow<UiState<PatchmonHostNetwork>>(UiState.Idle)
    val networkState: StateFlow<UiState<PatchmonHostNetwork>> = _networkState.asStateFlow()

    private val _packagesState = MutableStateFlow<UiState<PatchmonPackagesResponse>>(UiState.Idle)
    val packagesState: StateFlow<UiState<PatchmonPackagesResponse>> = _packagesState.asStateFlow()

    private val _reportsState = MutableStateFlow<UiState<PatchmonReportsResponse>>(UiState.Idle)
    val reportsState: StateFlow<UiState<PatchmonReportsResponse>> = _reportsState.asStateFlow()

    private val _agentQueueState = MutableStateFlow<UiState<PatchmonAgentQueueResponse>>(UiState.Idle)
    val agentQueueState: StateFlow<UiState<PatchmonAgentQueueResponse>> = _agentQueueState.asStateFlow()

    private val _notesState = MutableStateFlow<UiState<PatchmonNotesResponse>>(UiState.Idle)
    val notesState: StateFlow<UiState<PatchmonNotesResponse>> = _notesState.asStateFlow()

    private val _integrationsState = MutableStateFlow<UiState<PatchmonIntegrationsResponse>>(UiState.Loading)
    val integrationsState: StateFlow<UiState<PatchmonIntegrationsResponse>> = _integrationsState.asStateFlow()

    private val _updatesOnly = MutableStateFlow(true)
    val updatesOnly: StateFlow<Boolean> = _updatesOnly.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val _isDeleted = MutableStateFlow(false)
    val isDeleted: StateFlow<Boolean> = _isDeleted.asStateFlow()

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()

    val dockerEnabled: StateFlow<Boolean> = integrationsState
        .map { state ->
            val integrations = (state as? UiState.Success)?.data?.integrations.orEmpty()
            integrations["docker"]?.enabled == true
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        refreshOverview(force = true)
    }

    fun selectTab(tab: DetailTab) {
        if (_selectedTab.value == tab) return
        _selectedTab.value = tab
        when (tab) {
            DetailTab.OVERVIEW -> refreshOverview(force = false)
            DetailTab.SYSTEM -> loadSystem(force = false)
            DetailTab.NETWORK -> loadNetwork(force = false)
            DetailTab.PACKAGES -> loadPackages(force = false)
            DetailTab.REPORTS -> loadReports(force = false)
            DetailTab.AGENT -> loadAgentQueue(force = false)
            DetailTab.DOCKER -> loadIntegrations(force = false)
            DetailTab.NOTES -> loadNotes(force = false)
        }
    }

    fun refreshCurrentTab() {
        when (_selectedTab.value) {
            DetailTab.OVERVIEW -> refreshOverview(force = true)
            DetailTab.SYSTEM -> loadSystem(force = true)
            DetailTab.NETWORK -> loadNetwork(force = true)
            DetailTab.PACKAGES -> loadPackages(force = true)
            DetailTab.REPORTS -> loadReports(force = true)
            DetailTab.AGENT -> loadAgentQueue(force = true)
            DetailTab.DOCKER -> loadIntegrations(force = true)
            DetailTab.NOTES -> loadNotes(force = true)
        }
    }

    fun toggleUpdatesOnly() {
        _updatesOnly.value = !_updatesOnly.value
        loadPackages(force = true)
    }

    fun deleteHost() {
        viewModelScope.launch {
            if (_isDeleting.value) return@launch
            _deleteError.value = null
            _isDeleting.value = true
            try {
                // The detail screen already presents an explicit destructive confirmation dialog.
                // The coordinator records only a bounded reason code; keep the original provider
                // exception so the UI still renders PatchMon's localized error mappings.
                var operationError: Exception? = null
                val audit = controlledActionCoordinator.execute(
                    request = PatchmonControlledAction.HOST_DELETE.controlledRequest(
                        instanceId = instanceId,
                        targetRef = "host/$hostId",
                        confirmed = true
                    ),
                    actorRole = ActionRole.ADMIN,
                    providerCapabilities = ProviderRegistry.capabilities(ServiceType.PATCHMON)
                ) {
                    try {
                        patchmonRepository.deleteHost(instanceId = instanceId, hostId = hostId)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: ActionOperationException) {
                        throw error
                    } catch (error: Exception) {
                        operationError = error
                        throw controlledFailure(error)
                    }
                }
                if (audit.state != ActionExecutionState.SUCCEEDED) {
                    throw operationError ?: IllegalStateException(audit.reasonCode)
                }
                _isDeleted.value = true
            } catch (error: Exception) {
                _deleteError.value = ErrorHandler.getMessage(context, error)
            } finally {
                _isDeleting.value = false
            }
        }
    }

    private fun controlledFailure(error: Exception): ActionOperationException {
        val reasonCode = when (error) {
            is IOException -> "patchmon-outcome-indeterminate"
            is PatchmonApiException -> when (error.kind) {
                PatchmonApiException.Kind.CONNECTION_ERROR -> "patchmon-outcome-indeterminate"
                PatchmonApiException.Kind.INVALID_CREDENTIALS -> "patchmon-invalid-credentials"
                PatchmonApiException.Kind.RATE_LIMITED -> "patchmon-rate-limited"
                PatchmonApiException.Kind.SERVER_ERROR -> "patchmon-server-error"
                else -> "patchmon-${error.kind.name.lowercase(java.util.Locale.ROOT).replace('_', '-')}"
            }
            else -> "patchmon-provider-error"
        }
        return ActionOperationException(
            reasonCode = reasonCode,
            disposition = ActionFailureDisposition.NON_RETRYABLE,
            cause = error
        )
    }

    fun clearDeleteError() {
        _deleteError.value = null
    }

    private fun refreshOverview(force: Boolean) {
        loadInfo(force)
        loadStats(force)
        loadIntegrations(force)
        loadNotes(force)
    }

    private fun loadInfo(force: Boolean) {
        if (!force && _infoState.value is UiState.Success) return
        viewModelScope.launch {
            _infoState.value = UiState.Loading
            try {
                _infoState.value = UiState.Success(
                    patchmonRepository.getHostInfo(instanceId = instanceId, hostId = hostId)
                )
            } catch (error: Exception) {
                _infoState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { loadInfo(force = true) }
                )
            }
        }
    }

    private fun loadStats(force: Boolean) {
        if (!force && _statsState.value is UiState.Success) return
        viewModelScope.launch {
            _statsState.value = UiState.Loading
            try {
                _statsState.value = UiState.Success(
                    patchmonRepository.getHostStats(instanceId = instanceId, hostId = hostId)
                )
            } catch (error: Exception) {
                _statsState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { loadStats(force = true) }
                )
            }
        }
    }

    private fun loadSystem(force: Boolean) {
        if (!force && _systemState.value is UiState.Success) return
        viewModelScope.launch {
            _systemState.value = UiState.Loading
            try {
                _systemState.value = UiState.Success(
                    patchmonRepository.getHostSystem(instanceId = instanceId, hostId = hostId)
                )
            } catch (error: Exception) {
                _systemState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { loadSystem(force = true) }
                )
            }
        }
    }

    private fun loadNetwork(force: Boolean) {
        if (!force && _networkState.value is UiState.Success) return
        viewModelScope.launch {
            _networkState.value = UiState.Loading
            try {
                _networkState.value = UiState.Success(
                    patchmonRepository.getHostNetwork(instanceId = instanceId, hostId = hostId)
                )
            } catch (error: Exception) {
                _networkState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { loadNetwork(force = true) }
                )
            }
        }
    }

    private fun loadPackages(force: Boolean) {
        if (!force && _packagesState.value is UiState.Success) return
        viewModelScope.launch {
            _packagesState.value = UiState.Loading
            try {
                _packagesState.value = UiState.Success(
                    patchmonRepository.getHostPackages(
                        instanceId = instanceId,
                        hostId = hostId,
                        updatesOnly = _updatesOnly.value
                    )
                )
            } catch (error: Exception) {
                _packagesState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { loadPackages(force = true) }
                )
            }
        }
    }

    private fun loadReports(force: Boolean) {
        if (!force && _reportsState.value is UiState.Success) return
        viewModelScope.launch {
            _reportsState.value = UiState.Loading
            try {
                _reportsState.value = UiState.Success(
                    patchmonRepository.getHostReports(instanceId = instanceId, hostId = hostId, limit = 20)
                )
            } catch (error: Exception) {
                _reportsState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { loadReports(force = true) }
                )
            }
        }
    }

    private fun loadAgentQueue(force: Boolean) {
        if (!force && _agentQueueState.value is UiState.Success) return
        viewModelScope.launch {
            _agentQueueState.value = UiState.Loading
            try {
                _agentQueueState.value = UiState.Success(
                    patchmonRepository.getHostAgentQueue(instanceId = instanceId, hostId = hostId, limit = 20)
                )
            } catch (error: Exception) {
                _agentQueueState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { loadAgentQueue(force = true) }
                )
            }
        }
    }

    private fun loadNotes(force: Boolean) {
        if (!force && _notesState.value is UiState.Success) return
        viewModelScope.launch {
            _notesState.value = UiState.Loading
            try {
                _notesState.value = UiState.Success(
                    patchmonRepository.getHostNotes(instanceId = instanceId, hostId = hostId)
                )
            } catch (error: Exception) {
                _notesState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { loadNotes(force = true) }
                )
            }
        }
    }

    private fun loadIntegrations(force: Boolean) {
        if (!force && _integrationsState.value is UiState.Success) return
        viewModelScope.launch {
            _integrationsState.value = UiState.Loading
            try {
                _integrationsState.value = UiState.Success(
                    patchmonRepository.getHostIntegrations(instanceId = instanceId, hostId = hostId)
                )
            } catch (error: Exception) {
                _integrationsState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { loadIntegrations(force = true) }
                )
            }
        }
    }
}
