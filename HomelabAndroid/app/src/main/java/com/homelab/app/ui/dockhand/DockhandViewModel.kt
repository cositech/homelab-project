package com.homelab.app.ui.dockhand

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.DockhandContainer
import com.homelab.app.data.repository.DockhandContainerAction
import com.homelab.app.data.repository.DockhandContainerDetail
import com.homelab.app.data.repository.DockhandContainerFilter
import com.homelab.app.data.repository.DockhandDashboardData
import com.homelab.app.data.repository.DockhandEnvironment
import com.homelab.app.data.repository.DockhandRepository
import com.homelab.app.data.repository.DockhandScheduleDetail
import com.homelab.app.data.repository.DockhandScheduleItem
import com.homelab.app.data.repository.DockhandStack
import com.homelab.app.data.repository.DockhandStackAction
import com.homelab.app.data.repository.DockhandStackDetail
import com.homelab.app.data.repository.LocalPreferencesRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.domain.action.ActionExecutionState
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@HiltViewModel
class DockhandViewModel @Inject constructor(
    private val repository: DockhandRepository,
    private val servicesRepository: ServicesRepository,
    private val preferencesRepository: LocalPreferencesRepository,
    private val controlledActionCoordinator: ControlledActionCoordinator,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _uiState = MutableStateFlow<UiState<DockhandDashboardData>>(UiState.Loading)
    val uiState: StateFlow<UiState<DockhandDashboardData>> = _uiState.asStateFlow()

    private val _selectedEnvironmentId = MutableStateFlow<String?>(null)
    val selectedEnvironmentId: StateFlow<String?> = _selectedEnvironmentId.asStateFlow()

    private val _containerFilter = MutableStateFlow(DockhandContainerFilter.ALL)
    val containerFilter: StateFlow<DockhandContainerFilter> = _containerFilter.asStateFlow()

    private val _selectedContainerId = MutableStateFlow<String?>(null)
    val selectedContainerId: StateFlow<String?> = _selectedContainerId.asStateFlow()

    private val _selectedStackName = MutableStateFlow<String?>(null)
    val selectedStackName: StateFlow<String?> = _selectedStackName.asStateFlow()
    private val _stackDetailState = MutableStateFlow<UiState<DockhandStackDetail>>(UiState.Idle)
    val stackDetailState: StateFlow<UiState<DockhandStackDetail>> = _stackDetailState.asStateFlow()

    private val _selectedScheduleId = MutableStateFlow<String?>(null)
    val selectedScheduleId: StateFlow<String?> = _selectedScheduleId.asStateFlow()
    private val _scheduleDetailState = MutableStateFlow<UiState<DockhandScheduleDetail>>(UiState.Idle)
    val scheduleDetailState: StateFlow<UiState<DockhandScheduleDetail>> = _scheduleDetailState.asStateFlow()

    private val _containerDetailState = MutableStateFlow<UiState<DockhandContainerDetail>>(UiState.Idle)
    val containerDetailState: StateFlow<UiState<DockhandContainerDetail>> = _containerDetailState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isRunningAction = MutableStateFlow(false)
    val isRunningAction: StateFlow<Boolean> = _isRunningAction.asStateFlow()
    private val _isSavingCompose = MutableStateFlow(false)
    val isSavingCompose: StateFlow<Boolean> = _isSavingCompose.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    private val _isScreenActive = MutableStateFlow(true)
    private var autoRefreshJob: Job? = null

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.DOCKHAND].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredContainers: StateFlow<List<DockhandContainer>> = combine(uiState, containerFilter) { state, filter ->
        val containers = (state as? UiState.Success)?.data?.containers.orEmpty()
        when (filter) {
            DockhandContainerFilter.ALL -> containers
            DockhandContainerFilter.RUNNING -> containers.filter { it.isRunning }
            DockhandContainerFilter.STOPPED -> containers.filter { !it.isRunning }
            DockhandContainerFilter.ISSUES -> containers.filter { it.isIssue }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedStack: StateFlow<DockhandStack?> = combine(uiState, selectedStackName) { state, selectedName ->
        val stacks = (state as? UiState.Success)?.data?.stacks.orEmpty()
        stacks.firstOrNull { it.name == selectedName }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val selectedSchedule: StateFlow<DockhandScheduleItem?> = combine(uiState, selectedScheduleId) { state, selectedId ->
        val schedules = (state as? UiState.Success)?.data?.schedules.orEmpty()
        schedules.firstOrNull { it.id == selectedId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val selectedEnvironment: StateFlow<DockhandEnvironment?> = combine(uiState, selectedEnvironmentId) { state, selectedId ->
        val envs = (state as? UiState.Success)?.data?.environments.orEmpty()
        selectedId?.let { id -> envs.firstOrNull { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    data class DockhandSettingsUiState(
        val autoRefreshEnabled: Boolean = true,
        val refreshIntervalSeconds: Int = 45,
        val activityLimit: Int = 25,
        val showRawActivity: Boolean = false
    )

    val settingsUiState: StateFlow<DockhandSettingsUiState> = combine(
        preferencesRepository.dockhandAutoRefreshEnabled,
        preferencesRepository.dockhandRefreshIntervalSeconds,
        preferencesRepository.dockhandActivityLimit,
        preferencesRepository.dockhandShowRawActivity
    ) { autoRefresh, refreshInterval, activityLimit, showRaw ->
        DockhandSettingsUiState(
            autoRefreshEnabled = autoRefresh,
            refreshIntervalSeconds = refreshInterval,
            activityLimit = activityLimit,
            showRawActivity = showRaw
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DockhandSettingsUiState())

    init {
        fetchDashboard(forceLoading = true)
        combine(settingsUiState, _selectedEnvironmentId, _isScreenActive) { settings, _, isScreenActive ->
            settings to isScreenActive
        }.onEach { (settings, isScreenActive) ->
            restartAutoRefresh(settings, isScreenActive)
        }.launchIn(viewModelScope)
    }

    fun fetchDashboard(forceLoading: Boolean = false) {
        viewModelScope.launch {
            if (forceLoading || _uiState.value !is UiState.Success) {
                _uiState.value = UiState.Loading
            }
            _isRefreshing.value = true
            try {
                val env = _selectedEnvironmentId.value
                val data = repository.getDashboard(instanceId = instanceId, env = env)

                _uiState.value = UiState.Success(data)

                val selectedContainer = _selectedContainerId.value
                if (selectedContainer != null && data.containers.none { it.id == selectedContainer }) {
                    closeContainerDetail()
                }

                val selectedStackName = _selectedStackName.value
                if (selectedStackName != null && data.stacks.none { it.name == selectedStackName }) {
                    closeStackDetail()
                }
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

    fun selectEnvironment(environmentId: String?) {
        if (_selectedEnvironmentId.value == environmentId) return
        _selectedEnvironmentId.value = environmentId
        fetchDashboard(forceLoading = true)
    }

    fun selectFilter(filter: DockhandContainerFilter) {
        _containerFilter.value = if (_containerFilter.value == filter && filter != DockhandContainerFilter.ALL) {
            DockhandContainerFilter.ALL
        } else {
            filter
        }
    }

    fun openContainerDetail(containerId: String) {
        _selectedContainerId.value = containerId
        _containerDetailState.value = UiState.Loading
        refreshContainerDetail(forceLoading = true)
    }

    fun closeContainerDetail() {
        _selectedContainerId.value = null
        _containerDetailState.value = UiState.Idle
    }

    fun refreshContainerDetail(forceLoading: Boolean = false) {
        val containerId = _selectedContainerId.value ?: return
        viewModelScope.launch {
            if (forceLoading || _containerDetailState.value !is UiState.Success) {
                _containerDetailState.value = UiState.Loading
            }

            try {
                val env = currentContainer()?.environmentId ?: _selectedEnvironmentId.value
                val detail = repository.getContainerDetail(
                    instanceId = instanceId,
                    env = env,
                    containerId = containerId
                )
                _containerDetailState.value = UiState.Success(detail)
            } catch (error: Exception) {
                _containerDetailState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { refreshContainerDetail(forceLoading = true) }
                )
            }
        }
    }

    fun openStackDetail(stackName: String) {
        _selectedStackName.value = stackName
        _stackDetailState.value = UiState.Loading
        refreshStackDetail(forceLoading = true)
    }

    fun closeStackDetail() {
        _selectedStackName.value = null
        _stackDetailState.value = UiState.Idle
    }

    fun refreshStackDetail(forceLoading: Boolean = false) {
        val stack = selectedStack.value ?: return
        viewModelScope.launch {
            if (forceLoading || _stackDetailState.value !is UiState.Success) {
                _stackDetailState.value = UiState.Loading
            }
            try {
                val detail = repository.getStackDetail(
                    instanceId = instanceId,
                    env = stack.environmentId ?: _selectedEnvironmentId.value,
                    stackName = stack.name
                )
                _stackDetailState.value = UiState.Success(detail)
            } catch (error: Exception) {
                _stackDetailState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { refreshStackDetail(forceLoading = true) }
                )
            }
        }
    }

    fun openScheduleDetail(scheduleId: String) {
        _selectedScheduleId.value = scheduleId
        _scheduleDetailState.value = UiState.Loading
        refreshScheduleDetail(forceLoading = true)
    }

    fun closeScheduleDetail() {
        _selectedScheduleId.value = null
        _scheduleDetailState.value = UiState.Idle
    }

    fun refreshScheduleDetail(forceLoading: Boolean = false) {
        val schedule = selectedSchedule.value ?: return
        viewModelScope.launch {
            if (forceLoading || _scheduleDetailState.value !is UiState.Success) {
                _scheduleDetailState.value = UiState.Loading
            }
            try {
                val detail = repository.getScheduleDetail(
                    instanceId = instanceId,
                    env = schedule.environmentId ?: _selectedEnvironmentId.value,
                    scheduleId = schedule.id
                )
                _scheduleDetailState.value = UiState.Success(detail)
            } catch (error: Exception) {
                _scheduleDetailState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { refreshScheduleDetail(forceLoading = true) }
                )
            }
        }
    }

    fun runContainerAction(action: DockhandContainerAction, confirmed: Boolean = false) {
        val containerId = _selectedContainerId.value ?: return
        if (_isRunningAction.value) return

        viewModelScope.launch {
            _isRunningAction.value = true
            try {
                val env = currentContainer()?.environmentId ?: _selectedEnvironmentId.value
                var message: String? = null
                val audit = controlledActionCoordinator.execute(
                    request = action.controlledRequest(instanceId, env, containerId, confirmed),
                    actorRole = ActionRole.ADMIN,
                    providerCapabilities = ProviderRegistry.capabilities(ServiceType.DOCKHAND)
                ) {
                    message = repository.runContainerAction(instanceId, env, containerId, action).message
                }
                if (audit.state == ActionExecutionState.SUCCEEDED) {
                    message?.let(_messages::tryEmit)
                    fetchDashboard(forceLoading = false)
                    refreshContainerDetail(forceLoading = false)
                } else {
                    _messages.tryEmit(audit.reasonCode)
                }
            } catch (error: Exception) {
                _messages.tryEmit(ErrorHandler.getMessage(context, error))
            } finally {
                _isRunningAction.value = false
            }
        }
    }

    fun runStackAction(action: DockhandStackAction, confirmed: Boolean = false) {
        val stack = selectedStack.value ?: return
        if (_isRunningAction.value) return

        viewModelScope.launch {
            _isRunningAction.value = true
            try {
                val env = stack.environmentId ?: _selectedEnvironmentId.value
                var message: String? = null
                val audit = controlledActionCoordinator.execute(
                    request = action.controlledRequest(instanceId, env, stack.name, confirmed),
                    actorRole = ActionRole.ADMIN,
                    providerCapabilities = ProviderRegistry.capabilities(ServiceType.DOCKHAND)
                ) {
                    message = repository.runStackAction(instanceId, env, stack.name, action).message
                }
                if (audit.state == ActionExecutionState.SUCCEEDED) {
                    message?.let(_messages::tryEmit)
                    fetchDashboard(forceLoading = false)
                    refreshStackDetail(forceLoading = false)
                } else {
                    _messages.tryEmit(audit.reasonCode)
                }
            } catch (error: Exception) {
                _messages.tryEmit(ErrorHandler.getMessage(context, error))
            } finally {
                _isRunningAction.value = false
            }
        }
    }

    fun saveStackCompose(compose: String) {
        val stack = selectedStack.value ?: return
        val payload = compose.trim()
        if (payload.isEmpty() || _isSavingCompose.value) return

        viewModelScope.launch {
            _isSavingCompose.value = true
            try {
                val result = repository.updateStackCompose(
                    instanceId = instanceId,
                    env = stack.environmentId ?: _selectedEnvironmentId.value,
                    stackName = stack.name,
                    compose = payload
                )
                _messages.tryEmit(result.message)
                fetchDashboard(forceLoading = false)
                refreshStackDetail(forceLoading = false)
            } catch (error: Exception) {
                _messages.tryEmit(ErrorHandler.getMessage(context, error))
            } finally {
                _isSavingCompose.value = false
            }
        }
    }

    private fun currentContainer(): DockhandContainer? {
        val id = _selectedContainerId.value ?: return null
        return (_uiState.value as? UiState.Success)?.data?.containers?.firstOrNull { it.id == id }
    }

    fun setPreferredInstance(newInstanceId: String) {
        viewModelScope.launch {
            servicesRepository.setPreferredInstance(ServiceType.DOCKHAND, newInstanceId)
        }
    }

    fun setScreenActive(active: Boolean) {
        if (_isScreenActive.value == active) return
        _isScreenActive.value = active
    }

    fun updateSettings(update: (DockhandSettingsUiState) -> DockhandSettingsUiState) {
        val next = update(settingsUiState.value)
        viewModelScope.launch {
            preferencesRepository.setDockhandAutoRefreshEnabled(next.autoRefreshEnabled)
            preferencesRepository.setDockhandRefreshIntervalSeconds(next.refreshIntervalSeconds)
            preferencesRepository.setDockhandActivityLimit(next.activityLimit)
            preferencesRepository.setDockhandShowRawActivity(next.showRawActivity)
        }
    }

    private fun restartAutoRefresh(settings: DockhandSettingsUiState, isScreenActive: Boolean) {
        autoRefreshJob?.cancel()
        autoRefreshJob = null

        if (!settings.autoRefreshEnabled || !isScreenActive) return

        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(settings.refreshIntervalSeconds.coerceIn(15, 300) * 1_000L)
                if (_isRunningAction.value || _isRefreshing.value) continue
                fetchDashboard(forceLoading = false)
            }
        }
    }

override fun onCleared() {
        autoRefreshJob?.cancel()
        super.onCleared()
    }
}
