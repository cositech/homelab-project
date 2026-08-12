package com.homelab.app.ui.pihole

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.pihole.PiholeBlockingStatus
import com.homelab.app.data.remote.dto.pihole.PiholeControlledDomainAction
import com.homelab.app.data.remote.dto.pihole.PiholeDomainDto
import com.homelab.app.data.remote.dto.pihole.PiholeDomainListType
import com.homelab.app.data.remote.dto.pihole.PiholeHistoryEntry
import com.homelab.app.data.remote.dto.pihole.PiholeQueryLogEntry
import com.homelab.app.data.remote.dto.pihole.PiholeStats
import com.homelab.app.data.remote.dto.pihole.PiholeTopClient
import com.homelab.app.data.remote.dto.pihole.PiholeTopItem
import com.homelab.app.data.repository.PiholeRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.domain.action.ActionExecutionState
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
import java.util.Date
import javax.inject.Inject
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
class PiholeViewModel @Inject constructor(
    private val repository: PiholeRepository,
    private val servicesRepository: ServicesRepository,
    private val controlledActionCoordinator: ControlledActionCoordinator,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _stats = MutableStateFlow<PiholeStats?>(null)
    val stats: StateFlow<PiholeStats?> = _stats

    private val _blocking = MutableStateFlow<PiholeBlockingStatus?>(null)
    val blocking: StateFlow<PiholeBlockingStatus?> = _blocking

    private val _topBlocked = MutableStateFlow<List<PiholeTopItem>>(emptyList())
    val topBlocked: StateFlow<List<PiholeTopItem>> = _topBlocked

    private val _topDomains = MutableStateFlow<List<PiholeTopItem>>(emptyList())
    val topDomains: StateFlow<List<PiholeTopItem>> = _topDomains

    private val _topClients = MutableStateFlow<List<PiholeTopClient>>(emptyList())
    val topClients: StateFlow<List<PiholeTopClient>> = _topClients

    private val _history = MutableStateFlow<List<PiholeHistoryEntry>>(emptyList())
    val history: StateFlow<List<PiholeHistoryEntry>> = _history

    private val _uiState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val uiState: StateFlow<UiState<Unit>> = _uiState

    private val _domainsState = MutableStateFlow<UiState<List<PiholeDomainDto>>>(UiState.Loading)
    val domainsState: StateFlow<UiState<List<PiholeDomainDto>>> = _domainsState

    private val _queriesState = MutableStateFlow<UiState<List<PiholeQueryLogEntry>>>(UiState.Loading)
    val queriesState: StateFlow<UiState<List<PiholeQueryLogEntry>>> = _queriesState

    private var queryFetchJob: Job? = null

    private val _isToggling = MutableStateFlow(false)
    val isToggling: StateFlow<Boolean> = _isToggling

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.PIHOLE].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        _uiState.onEach { Logger.stateTransition("PiholeViewModel", "uiState", it) }.launchIn(viewModelScope)
        _domainsState.onEach { Logger.stateTransition("PiholeViewModel", "domainsState", it) }.launchIn(viewModelScope)
        _queriesState.onEach { Logger.stateTransition("PiholeViewModel", "queriesState", it) }.launchIn(viewModelScope)
    }

    fun fetchAll() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val statsResult = repository.getStats(instanceId)
                val blockingResult = repository.getBlockingStatus(instanceId)

                _stats.value = statsResult
                _blocking.value = blockingResult

                val topBlockedDeferred = async { runCatching { repository.getTopBlocked(instanceId, 8) }.getOrDefault(emptyList()) }
                val topDomainsDeferred = async { runCatching { repository.getTopDomains(instanceId, 10) }.getOrDefault(emptyList()) }
                val topClientsDeferred = async { runCatching { repository.getTopClients(instanceId, 10) }.getOrDefault(emptyList()) }
                val historyDeferred = async { runCatching { repository.getQueryHistory(instanceId) }.getOrNull() }

                _topBlocked.value = topBlockedDeferred.await()
                _topDomains.value = topDomainsDeferred.await()
                _topClients.value = topClientsDeferred.await()
                _history.value = historyDeferred.await()?.history ?: emptyList()

                _uiState.value = UiState.Success(Unit)
            } catch (error: Exception) {
                val message = ErrorHandler.getMessage(context, error)
                _uiState.value = UiState.Error(message, retryAction = { fetchAll() })
            }
        }
    }

    fun setPreferredInstance(newInstanceId: String) {
        viewModelScope.launch {
            servicesRepository.setPreferredInstance(ServiceType.PIHOLE, newInstanceId)
        }
    }

    fun toggleBlocking(timer: Int? = null) {
        if (_isToggling.value) return
        val currentEnabled = _blocking.value?.isEnabled ?: return

        viewModelScope.launch {
            _isToggling.value = true
            try {
                if (timer != null) {
                    repository.setBlocking(instanceId, enabled = false, timer = timer)
                } else {
                    repository.setBlocking(instanceId, enabled = !currentEnabled)
                }
                _blocking.value = repository.getBlockingStatus(instanceId)
                _stats.value = repository.getStats(instanceId)
            } catch (error: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, error)
            } finally {
                _isToggling.value = false
            }
        }
    }

    fun fetchDomains() {
        viewModelScope.launch {
            if (_domainsState.value !is UiState.Success) {
                _domainsState.value = UiState.Loading
            }
            try {
                _domainsState.value = UiState.Success(repository.getDomains(instanceId))
            } catch (error: Exception) {
                val message = ErrorHandler.getMessage(context, error)
                _domainsState.value = UiState.Error(message, retryAction = { fetchDomains() })
            }
        }
    }

    fun addDomain(domain: String, listType: PiholeDomainListType, confirmed: Boolean = false) {
        executeDomainAction(PiholeControlledDomainAction.ADD, domain, listType, confirmed)
    }

    fun removeDomain(domain: String, listType: PiholeDomainListType, confirmed: Boolean = false) {
        executeDomainAction(PiholeControlledDomainAction.REMOVE, domain, listType, confirmed)
    }

    private fun executeDomainAction(
        action: PiholeControlledDomainAction,
        domain: String,
        listType: PiholeDomainListType,
        confirmed: Boolean
    ) {
        viewModelScope.launch {
            try {
                val result = controlledActionCoordinator.execute(
                    request = action.controlledRequest(instanceId, domain, listType, confirmed),
                    actorRole = ActionRole.ADMIN,
                    providerCapabilities = ProviderRegistry.capabilities(ServiceType.PIHOLE)
                ) {
                    when (action) {
                        PiholeControlledDomainAction.ADD -> repository.addDomain(instanceId, domain, listType)
                        PiholeControlledDomainAction.REMOVE -> repository.removeDomain(instanceId, domain, listType)
                    }
                }
                if (result.state == ActionExecutionState.SUCCEEDED) {
                    _domainsState.value = UiState.Success(repository.getDomains(instanceId))
                } else {
                    _actionError.value = context.getString(
                        com.homelab.app.R.string.error_action_failed,
                        result.reasonCode
                    )
                }
            } catch (error: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, error)
            }
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun fetchRecentQueries(windowSeconds: Long = 15 * 60) {
        if (queryFetchJob?.isActive == true) return
        queryFetchJob = viewModelScope.launch {
            val until = Date().time / 1000
            val currentQueries = (_queriesState.value as? UiState.Success)?.data ?: emptyList()
            if (currentQueries.isEmpty() && _queriesState.value !is UiState.Success) {
                _queriesState.value = UiState.Loading
            }

            val from = if (currentQueries.isEmpty()) until - windowSeconds else (until - 90).coerceAtLeast(0)
            try {
                val fetched = repository.getQueries(instanceId, from = from, until = until)
                _queriesState.value = UiState.Success(
                    (currentQueries + fetched)
                        .distinctBy { it.id }
                        .sortedByDescending { it.timestamp }
                        .take(500)
                )
            } catch (error: Exception) {
                if (currentQueries.isEmpty()) {
                    val message = ErrorHandler.getMessage(context, error)
                    _queriesState.value = UiState.Error(message, retryAction = { fetchRecentQueries() })
                }
            } finally {
                queryFetchJob = null
            }
        }
    }
}
