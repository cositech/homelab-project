package com.homelab.app.ui.adguard

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.adguard.AdGuardBlockedService
import com.homelab.app.data.remote.dto.adguard.AdGuardBlockedServicesSchedule
import com.homelab.app.data.remote.dto.adguard.AdGuardControlledConfigurationAction
import com.homelab.app.data.remote.dto.adguard.AdGuardControlledProtectionAction
import com.homelab.app.data.remote.dto.adguard.AdGuardFilter
import com.homelab.app.data.remote.dto.adguard.AdGuardFilteringStatus
import com.homelab.app.data.remote.dto.adguard.AdGuardQueryLogEntry
import com.homelab.app.data.remote.dto.adguard.AdGuardRewriteEntry
import com.homelab.app.data.remote.dto.adguard.AdGuardRewriteSettings
import com.homelab.app.data.remote.dto.adguard.AdGuardStats
import com.homelab.app.data.remote.dto.adguard.AdGuardStatus
import com.homelab.app.data.remote.dto.adguard.AdGuardTopItem
import com.homelab.app.data.repository.AdGuardHomeRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.domain.action.ActionExecutionState
import com.homelab.app.domain.action.ActionFailureDisposition
import com.homelab.app.domain.action.ActionOperationException
import com.homelab.app.domain.action.ActionRole
import com.homelab.app.domain.action.ControlledActionCoordinator
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.domain.provider.ProviderRegistry
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.Logger
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AdGuardHomeViewModel @Inject constructor(
    private val repository: AdGuardHomeRepository,
    private val servicesRepository: ServicesRepository,
    private val controlledActionCoordinator: ControlledActionCoordinator,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _status = MutableStateFlow<AdGuardStatus?>(null)
    val status: StateFlow<AdGuardStatus?> = _status

    private val _stats = MutableStateFlow<AdGuardStats?>(null)
    val stats: StateFlow<AdGuardStats?> = _stats

    private val _topQueried = MutableStateFlow<List<AdGuardTopItem>>(emptyList())
    val topQueried: StateFlow<List<AdGuardTopItem>> = _topQueried

    private val _topBlocked = MutableStateFlow<List<AdGuardTopItem>>(emptyList())
    val topBlocked: StateFlow<List<AdGuardTopItem>> = _topBlocked

    private val _topClients = MutableStateFlow<List<AdGuardTopItem>>(emptyList())
    val topClients: StateFlow<List<AdGuardTopItem>> = _topClients

    private val _uiState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val uiState: StateFlow<UiState<Unit>> = _uiState

    private val _queryLogState = MutableStateFlow<UiState<List<AdGuardQueryLogEntry>>>(UiState.Idle)
    val queryLogState: StateFlow<UiState<List<AdGuardQueryLogEntry>>> = _queryLogState

    private val _filtersState = MutableStateFlow<UiState<AdGuardFilteringStatus>>(UiState.Idle)
    val filtersState: StateFlow<UiState<AdGuardFilteringStatus>> = _filtersState

    private val _rewritesState = MutableStateFlow<UiState<List<AdGuardRewriteEntry>>>(UiState.Idle)
    val rewritesState: StateFlow<UiState<List<AdGuardRewriteEntry>>> = _rewritesState

    private val _rewriteSettings = MutableStateFlow<AdGuardRewriteSettings?>(null)
    val rewriteSettings: StateFlow<AdGuardRewriteSettings?> = _rewriteSettings

    data class BlockedServicesState(
        val services: List<AdGuardBlockedService> = emptyList(),
        val blockedIds: Set<String> = emptySet(),
        val schedule: AdGuardBlockedServicesSchedule? = null,
        val groups: Map<String, String> = emptyMap()
    )

    private val _blockedServicesState = MutableStateFlow<UiState<BlockedServicesState>>(UiState.Idle)
    val blockedServicesState: StateFlow<UiState<BlockedServicesState>> = _blockedServicesState

    private val _userRulesState = MutableStateFlow<UiState<List<String>>>(UiState.Idle)
    val userRulesState: StateFlow<UiState<List<String>>> = _userRulesState

    private val _isTogglingProtection = MutableStateFlow(false)
    val isTogglingProtection: StateFlow<Boolean> = _isTogglingProtection

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError

    private var queryJob: Job? = null
    private var lastQueryKey: String? = null
    private var lastQueryFetchAt: Long = 0L
    private val queryCacheMs = 15_000L
    private var lastDashboardFetchAt: Long = 0L
    private val dashboardCacheMs = 20_000L

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.ADGUARD_HOME].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        _uiState.onEach { Logger.stateTransition("AdGuardHomeViewModel", "uiState", it) }.launchIn(viewModelScope)
        _queryLogState.onEach { Logger.stateTransition("AdGuardHomeViewModel", "queryLogState", it) }.launchIn(viewModelScope)
        _filtersState.onEach { Logger.stateTransition("AdGuardHomeViewModel", "filtersState", it) }.launchIn(viewModelScope)
        _rewritesState.onEach { Logger.stateTransition("AdGuardHomeViewModel", "rewritesState", it) }.launchIn(viewModelScope)
        _blockedServicesState.onEach { Logger.stateTransition("AdGuardHomeViewModel", "blockedServicesState", it) }.launchIn(viewModelScope)
    }

    fun fetchDashboard(force: Boolean = false) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (!force && _uiState.value is UiState.Success && now - lastDashboardFetchAt < dashboardCacheMs) {
                return@launch
            }
            _uiState.value = UiState.Loading
            try {
                val statusDeferred = async { repository.getStatus(instanceId) }
                val statsDeferred = async { repository.getStats(instanceId) }

                val status = statusDeferred.await()
                val stats = statsDeferred.await()

                _status.value = status
                _stats.value = stats

                _topQueried.value = repository.mapTopItems(stats.topQueriedDomains)
                _topBlocked.value = repository.mapTopItems(stats.topBlockedDomains)
                _topClients.value = repository.mapTopItems(stats.topClients)

                lastDashboardFetchAt = now
                _uiState.value = UiState.Success(Unit)
            } catch (error: Exception) {
                val message = ErrorHandler.getMessage(context, error)
                _uiState.value = UiState.Error(message, retryAction = { fetchDashboard() })
            }
        }
    }

    fun setPreferredInstance(newInstanceId: String) {
        viewModelScope.launch {
            servicesRepository.setPreferredInstance(ServiceType.ADGUARD_HOME, newInstanceId)
        }
    }

    fun toggleProtection(durationSeconds: Int? = null) {
        if (_isTogglingProtection.value) return
        val current = _status.value?.protectionEnabled ?: return
        viewModelScope.launch {
            _isTogglingProtection.value = true
            try {
                val enabled = if (durationSeconds != null) false else !current
                val action = if (enabled) {
                    AdGuardControlledProtectionAction.ENABLE
                } else {
                    AdGuardControlledProtectionAction.DISABLE
                }
                val result = controlledActionCoordinator.execute(
                    request = action.controlledRequest(
                        instanceId = instanceId,
                        confirmed = !enabled
                    ),
                    actorRole = ActionRole.ADMIN,
                    providerCapabilities = ProviderRegistry.capabilities(ServiceType.ADGUARD_HOME)
                ) {
                    repository.setProtection(
                        instanceId,
                        enabled = enabled,
                        durationSeconds = durationSeconds
                    )
                }
                if (result.state == ActionExecutionState.SUCCEEDED) {
                    _status.value = repository.getStatus(instanceId)
                    _stats.value = repository.getStats(instanceId)
                } else {
                    _actionError.value = context.getString(
                        com.homelab.app.R.string.error_action_failed,
                        result.reasonCode
                    )
                }
            } catch (error: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, error)
            } finally {
                _isTogglingProtection.value = false
            }
        }
    }

    fun fetchQueryLog(search: String? = null, status: String? = null) {
        val key = listOf(search?.trim()?.lowercase().orEmpty(), status?.trim()?.lowercase().orEmpty()).joinToString("|")
        val now = System.currentTimeMillis()
        if (key == lastQueryKey && _queryLogState.value is UiState.Success && now - lastQueryFetchAt < queryCacheMs) return
        lastQueryKey = key
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            if (_queryLogState.value !is UiState.Success) {
                _queryLogState.value = UiState.Loading
            }
            try {
                val entries = repository.getQueryLog(instanceId, search = search, responseStatus = status)
                lastQueryFetchAt = now
                _queryLogState.value = UiState.Success(entries)
            } catch (error: Exception) {
                val message = ErrorHandler.getMessage(context, error)
                _queryLogState.value = UiState.Error(message, retryAction = { fetchQueryLog(search, status) })
            } finally {
                queryJob = null
            }
        }
    }

    fun allowQuery(domain: String) {
        viewModelScope.launch {
            try {
                val status = repository.getFilteringStatus(instanceId)
                val rule = repository.toAllowRule(domain)
                val updatedRules = if (!status.userRules.contains(rule)) {
                    if (!executeControlledConfigurationAction(
                            AdGuardControlledConfigurationAction.UPDATE_USER_RULES,
                            targetId = "global"
                        ) {
                            repository.setUserRules(instanceId, status.userRules + rule)
                        }
                    ) return@launch
                    status.userRules + rule
                } else {
                    status.userRules
                }
                _userRulesState.value = UiState.Success(updatedRules)
            } catch (error: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, error)
            }
        }
    }

    fun fetchFilters() {
        viewModelScope.launch {
            if (_filtersState.value !is UiState.Success) {
                _filtersState.value = UiState.Loading
            }
            try {
                _filtersState.value = UiState.Success(repository.getFilteringStatus(instanceId))
            } catch (error: Exception) {
                val message = ErrorHandler.getMessage(context, error)
                _filtersState.value = UiState.Error(message, retryAction = { fetchFilters() })
            }
        }
    }

    fun toggleFilter(filter: AdGuardFilter, whitelist: Boolean, enabled: Boolean) {
        viewModelScope.launch {
            try {
                if (!executeControlledConfigurationAction(
                        AdGuardControlledConfigurationAction.UPDATE_FILTER,
                        targetId = filter.id.toString()
                    ) {
                        repository.setFilter(instanceId, filter, enabled, whitelist)
                    }
                ) return@launch
                fetchFilters()
            } catch (error: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, error)
            }
        }
    }

    fun addFilter(name: String, url: String, whitelist: Boolean) {
        viewModelScope.launch {
            try {
                if (!executeControlledConfigurationAction(
                        AdGuardControlledConfigurationAction.CREATE_FILTER,
                        targetId = "new"
                    ) {
                        repository.addFilter(instanceId, name, url, whitelist)
                    }
                ) return@launch
                fetchFilters()
            } catch (error: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, error)
            }
        }
    }

    fun editFilter(filter: AdGuardFilter, newName: String, newUrl: String, whitelist: Boolean, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val updated = filter.copy(name = newName, url = newUrl, enabled = enabled)
                if (!executeControlledConfigurationAction(
                        AdGuardControlledConfigurationAction.UPDATE_FILTER,
                        targetId = filter.id.toString()
                    ) {
                        repository.setFilter(instanceId, updated, enabled, whitelist)
                    }
                ) return@launch
                fetchFilters()
            } catch (error: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, error)
            }
        }
    }

    fun removeFilter(filter: AdGuardFilter, whitelist: Boolean) {
        viewModelScope.launch {
            try {
                if (!executeControlledConfigurationAction(
                        AdGuardControlledConfigurationAction.DELETE_FILTER,
                        targetId = filter.id.toString()
                    ) {
                        repository.removeFilter(instanceId, filter.url, whitelist)
                    }
                ) return@launch
                fetchFilters()
            } catch (error: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, error)
            }
        }
    }

    fun fetchRewrites() {
        viewModelScope.launch {
            if (_rewritesState.value !is UiState.Success) {
                _rewritesState.value = UiState.Loading
            }
            try {
                val settingsDeferred = async { repository.getRewriteSettings(instanceId) }
                val rewrites = repository.getRewrites(instanceId)
                _rewriteSettings.value = settingsDeferred.await()
                _rewritesState.value = UiState.Success(rewrites)
            } catch (error: Exception) {
                val message = ErrorHandler.getMessage(context, error)
                _rewritesState.value = UiState.Error(message, retryAction = { fetchRewrites() })
            }
        }
    }

    fun addRewrite(domain: String, answer: String, enabled: Boolean = true) {
        viewModelScope.launch {
            try {
                if (!executeControlledConfigurationAction(
                        AdGuardControlledConfigurationAction.CREATE_REWRITE,
                        targetId = domain.trim().lowercase()
                    ) {
                        repository.addRewrite(instanceId, domain, answer, enabled)
                    }
                ) return@launch
                fetchRewrites()
            } catch (error: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, error)
            }
        }
    }

    fun updateRewrite(target: AdGuardRewriteEntry, update: AdGuardRewriteEntry) {
        viewModelScope.launch {
            try {
                if (!executeControlledConfigurationAction(
                        AdGuardControlledConfigurationAction.UPDATE_REWRITE,
                        targetId = target.domain.trim().lowercase()
                    ) {
                        repository.updateRewrite(instanceId, target, update)
                    }
                ) return@launch
                fetchRewrites()
            } catch (error: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, error)
            }
        }
    }

    fun deleteRewrite(entry: AdGuardRewriteEntry) {
        viewModelScope.launch {
            try {
                if (!executeControlledConfigurationAction(
                        AdGuardControlledConfigurationAction.DELETE_REWRITE,
                        targetId = entry.domain.trim().lowercase()
                    ) {
                        repository.deleteRewrite(instanceId, entry)
                    }
                ) return@launch
                fetchRewrites()
            } catch (error: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, error)
            }
        }
    }

    fun toggleRewriteSettings(enabled: Boolean) {
        viewModelScope.launch {
            try {
                if (!executeControlledConfigurationAction(
                        AdGuardControlledConfigurationAction.UPDATE_REWRITE_SETTINGS,
                        targetId = "global"
                    ) {
                        repository.updateRewriteSettings(instanceId, enabled)
                    }
                ) return@launch
                _rewriteSettings.value = repository.getRewriteSettings(instanceId)
            } catch (error: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, error)
            }
        }
    }

    fun fetchBlockedServices() {
        viewModelScope.launch {
            if (_blockedServicesState.value !is UiState.Success) {
                _blockedServicesState.value = UiState.Loading
            }
            try {
                val all = repository.getBlockedServicesAll(instanceId)
                val schedule = repository.getBlockedServicesSchedule(instanceId)
                val groupMap = all.groups.associate { group -> group.id to (group.name ?: group.id) }
                _blockedServicesState.value = UiState.Success(
                    BlockedServicesState(
                        services = all.blockedServices,
                        blockedIds = schedule.ids.toSet(),
                        schedule = schedule,
                        groups = groupMap
                    )
                )
            } catch (error: Exception) {
                val message = ErrorHandler.getMessage(context, error)
                _blockedServicesState.value = UiState.Error(message, retryAction = { fetchBlockedServices() })
            }
        }
    }

    fun updateBlockedServices(ids: Set<String>) {
        viewModelScope.launch {
            val current = (_blockedServicesState.value as? UiState.Success)?.data ?: return@launch
            try {
                val schedule = current.schedule ?: AdGuardBlockedServicesSchedule()
                if (!executeControlledConfigurationAction(
                        AdGuardControlledConfigurationAction.UPDATE_BLOCKED_SERVICES,
                        targetId = "global"
                    ) {
                        repository.updateBlockedServices(instanceId, ids.toList(), schedule)
                    }
                ) return@launch
                _blockedServicesState.value = UiState.Success(current.copy(blockedIds = ids))
            } catch (error: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, error)
            }
        }
    }

    fun fetchUserRules() {
        viewModelScope.launch {
            if (_userRulesState.value !is UiState.Success) {
                _userRulesState.value = UiState.Loading
            }
            try {
                val status = repository.getFilteringStatus(instanceId)
                _userRulesState.value = UiState.Success(status.userRules)
            } catch (error: Exception) {
                val message = ErrorHandler.getMessage(context, error)
                _userRulesState.value = UiState.Error(message, retryAction = { fetchUserRules() })
            }
        }
    }

    fun addUserRule(rule: String) {
        viewModelScope.launch {
            try {
                val current = (userRulesState.value as? UiState.Success)?.data ?: repository.getFilteringStatus(instanceId).userRules
                val updated = current + rule
                if (!executeControlledConfigurationAction(
                        AdGuardControlledConfigurationAction.UPDATE_USER_RULES,
                        targetId = "global"
                    ) {
                        repository.setUserRules(instanceId, updated)
                    }
                ) return@launch
                _userRulesState.value = UiState.Success(updated)
            } catch (error: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, error)
            }
        }
    }

    fun removeUserRule(rule: String) {
        viewModelScope.launch {
            try {
                val current = (userRulesState.value as? UiState.Success)?.data ?: repository.getFilteringStatus(instanceId).userRules
                val updated = current.filterNot { it == rule }
                if (!executeControlledConfigurationAction(
                        AdGuardControlledConfigurationAction.UPDATE_USER_RULES,
                        targetId = "global"
                    ) {
                        repository.setUserRules(instanceId, updated)
                    }
                ) return@launch
                _userRulesState.value = UiState.Success(updated)
            } catch (error: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, error)
            }
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    private suspend fun executeControlledConfigurationAction(
        action: AdGuardControlledConfigurationAction,
        targetId: String,
        operation: suspend () -> Unit
    ): Boolean {
        val result = controlledActionCoordinator.execute(
            request = action.controlledRequest(
                instanceId = instanceId,
                targetId = targetId,
                confirmed = true
            ),
            actorRole = ActionRole.ADMIN,
            providerCapabilities = ProviderRegistry.capabilities(ServiceType.ADGUARD_HOME),
            operation = {
                try {
                    operation()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: ActionOperationException) {
                    throw error
                } catch (error: Exception) {
                    throw ActionOperationException(
                        "adguard-home-outcome-indeterminate",
                        ActionFailureDisposition.NON_RETRYABLE,
                        error
                    )
                }
            }
        )
        if (result.state == ActionExecutionState.SUCCEEDED) return true
        _actionError.value = context.getString(
            com.homelab.app.R.string.error_action_failed,
            result.reasonCode
        )
        return false
    }
}
