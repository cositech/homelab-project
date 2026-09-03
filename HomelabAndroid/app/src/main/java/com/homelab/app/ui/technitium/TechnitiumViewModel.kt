package com.homelab.app.ui.technitium

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.data.repository.TechnitiumActionResult
import com.homelab.app.data.repository.TechnitiumControlledAction
import com.homelab.app.data.repository.TechnitiumDashboardData
import com.homelab.app.data.repository.TechnitiumRepository
import com.homelab.app.data.repository.TechnitiumStatsRange
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
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TechnitiumViewModel @Inject constructor(
    private val repository: TechnitiumRepository,
    private val servicesRepository: ServicesRepository,
    private val controlledActionCoordinator: ControlledActionCoordinator,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    enum class SummaryPanel {
        CLIENTS,
        DOMAINS,
        BLOCKED,
        ZONES
    }

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])
    private val _uiState = MutableStateFlow<UiState<TechnitiumDashboardData>>(UiState.Loading)
    val uiState: StateFlow<UiState<TechnitiumDashboardData>> = _uiState.asStateFlow()

    private val _selectedRange = MutableStateFlow(TechnitiumStatsRange.LAST_HOUR)
    val selectedRange: StateFlow<TechnitiumStatsRange> = _selectedRange.asStateFlow()

    private val _selectedPanel = MutableStateFlow(SummaryPanel.CLIENTS)
    val selectedPanel: StateFlow<SummaryPanel> = _selectedPanel.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isRunningAction = MutableStateFlow(false)
    val isRunningAction: StateFlow<Boolean> = _isRunningAction.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.TECHNITIUM].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { fetchDashboard(forceLoading = true) }

    fun fetchDashboard(forceLoading: Boolean = false) {
        viewModelScope.launch {
            if (forceLoading || _uiState.value !is UiState.Success) {
                _uiState.value = UiState.Loading
            }
            _isRefreshing.value = true

            try {
                val dashboard = repository.getDashboard(instanceId = instanceId, range = _selectedRange.value)
                _uiState.value = UiState.Success(dashboard)
            } catch (error: Exception) {
                _uiState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { fetchDashboard(forceLoading = true) }
                )
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun selectRange(range: TechnitiumStatsRange) {
        if (_selectedRange.value == range) return
        _selectedRange.value = range
        fetchDashboard(forceLoading = true)
    }

    fun selectPanel(panel: SummaryPanel) {
        _selectedPanel.value = panel
    }

    fun setBlocking(enabled: Boolean, confirmed: Boolean = false) {
        val action = if (enabled) {
            TechnitiumControlledAction.ENABLE_BLOCKING
        } else {
            TechnitiumControlledAction.DISABLE_BLOCKING
        }
        executeAction(action, confirmed = confirmed) {
            repository.setBlockingEnabled(instanceId = instanceId, enabled = enabled)
        }
    }

    fun temporaryDisable(minutes: Int, confirmed: Boolean = false) {
        executeAction(TechnitiumControlledAction.TEMPORARY_DISABLE, confirmed = confirmed) {
            repository.temporaryDisableBlocking(instanceId = instanceId, minutes = minutes)
        }
    }

    fun forceUpdateBlockLists() {
        executeAction(TechnitiumControlledAction.REFRESH_BLOCK_LISTS, confirmed = false) {
            repository.forceUpdateBlockLists(instanceId = instanceId)
        }
    }

    fun addBlockedDomain(domain: String, confirmed: Boolean = false) {
        val normalizedDomain = domain.trim()
        executeAction(
            TechnitiumControlledAction.ADD_BLOCKED_DOMAIN,
            domain = normalizedDomain,
            confirmed = confirmed,
            onSucceeded = { _selectedPanel.value = SummaryPanel.BLOCKED }
        ) {
            repository.addBlockedDomain(instanceId = instanceId, domain = normalizedDomain)
        }
    }

    fun removeBlockedDomain(domain: String, confirmed: Boolean = false) {
        val normalizedDomain = domain.trim()
        executeAction(
            TechnitiumControlledAction.REMOVE_BLOCKED_DOMAIN,
            domain = normalizedDomain,
            confirmed = confirmed
        ) {
            repository.removeBlockedDomain(instanceId = instanceId, domain = normalizedDomain)
        }
    }

    private fun executeAction(
        action: TechnitiumControlledAction,
        domain: String? = null,
        confirmed: Boolean,
        onSucceeded: () -> Unit = {},
        mutation: suspend () -> TechnitiumActionResult
    ) {
        if (_isRunningAction.value) return
        viewModelScope.launch {
            _isRunningAction.value = true
            try {
                var providerMessage: String? = null
                val audit = controlledActionCoordinator.execute(
                    request = action.controlledRequest(
                        instanceId = instanceId,
                        target = action.targetRef(domain),
                        confirmed = confirmed
                    ),
                    actorRole = ActionRole.ADMIN,
                    providerCapabilities = ProviderRegistry.capabilities(ServiceType.TECHNITIUM)
                ) {
                    try {
                        val result = mutation()
                        if (!result.success) {
                            throw ActionOperationException(
                                "technitium-provider-reported-failure",
                                ActionFailureDisposition.NON_RETRYABLE
                            )
                        }
                        providerMessage = result.message
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: ActionOperationException) {
                        throw error
                    } catch (error: Exception) {
                        throw ActionOperationException(
                            "technitium-outcome-indeterminate",
                            ActionFailureDisposition.NON_RETRYABLE,
                            error
                        )
                    }
                }
                if (audit.state == ActionExecutionState.SUCCEEDED) {
                    _messages.tryEmit(providerMessage.orEmpty().ifBlank { "Technitium action completed" })
                    onSucceeded()
                    fetchDashboard(forceLoading = false)
                } else {
                    _messages.tryEmit(audit.reasonCode)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.tryEmit(ErrorHandler.getMessage(context, error))
            } finally {
                _isRunningAction.value = false
            }
        }
    }

    fun setPreferredInstance(newInstanceId: String) {
        viewModelScope.launch {
            servicesRepository.setPreferredInstance(ServiceType.TECHNITIUM, newInstanceId)
        }
    }
}
