package com.homelab.app.ui.calagopus

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.calagopus.CalagopusResources
import com.homelab.app.data.remote.dto.calagopus.CalagopusServer
import com.homelab.app.data.repository.CalagopusPowerAction
import com.homelab.app.data.repository.CalagopusRepository
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
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CalagopusServerWithResources(
    val server: CalagopusServer,
    val resources: CalagopusResources?
)

@HiltViewModel
class CalagopusViewModel @Inject constructor(
    private val repository: CalagopusRepository,
    private val servicesRepository: ServicesRepository,
    private val controlledActionCoordinator: ControlledActionCoordinator,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _uiState = MutableStateFlow<UiState<List<CalagopusServerWithResources>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<CalagopusServerWithResources>>> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _actionServerId = MutableStateFlow<String?>(null)
    val actionServerId: StateFlow<String?> = _actionServerId.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var refreshJob: Job? = null
    private var refreshRequestId: Long = 0L

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.CALAGOPUS].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh(forceLoading = true)
    }

    fun refresh(forceLoading: Boolean = false) {
        val requestId = ++refreshRequestId
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (forceLoading || _uiState.value !is UiState.Success) {
                _uiState.value = UiState.Loading
            }
            _isRefreshing.value = true
            try {
                val servers = repository.getServers(instanceId)
                val enriched = coroutineScope {
                    servers.chunked(4).flatMap { chunk ->
                        chunk.map { server ->
                            async {
                                val res = runCatching {
                                    repository.getServerResources(instanceId, server.uuidShort)
                                }.getOrNull()
                                CalagopusServerWithResources(server = server, resources = res)
                            }
                        }.awaitAll()
                    }
                }
                _uiState.value = UiState.Success(enriched)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, e),
                    retryAction = { refresh(forceLoading = true) }
                )
            } finally {
                if (requestId == refreshRequestId) {
                    _isRefreshing.value = false
                }
            }
        }
    }

    fun sendPowerSignal(
        uuidShort: String,
        action: CalagopusPowerAction,
        confirmed: Boolean = false
    ) {
        if (_actionServerId.value != null) return
        viewModelScope.launch {
            _actionServerId.value = uuidShort
            try {
                val audit = controlledActionCoordinator.execute(
                    request = action.controlledRequest(instanceId, uuidShort, confirmed),
                    actorRole = ActionRole.ADMIN,
                    providerCapabilities = ProviderRegistry.capabilities(ServiceType.CALAGOPUS)
                ) {
                    try {
                        repository.sendPowerSignal(instanceId, uuidShort, action)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: ActionOperationException) {
                        throw error
                    } catch (error: Exception) {
                        throw ActionOperationException(
                            "calagopus-outcome-indeterminate",
                            ActionFailureDisposition.NON_RETRYABLE,
                            error
                        )
                    }
                }
                if (audit.state != ActionExecutionState.SUCCEEDED) {
                    _messages.emit(audit.reasonCode)
                    return@launch
                }
                _messages.emit(context.getString(com.homelab.app.R.string.calagopus_action_sent))
                repeat(if (action == CalagopusPowerAction.KILL) 3 else 6) {
                    delay(1500L)
                    runCatching {
                        val updated = repository.getServerResources(instanceId, uuidShort)
                        val current = _uiState.value
                        if (current is UiState.Success) {
                            _uiState.value = UiState.Success(
                                current.data.map { server ->
                                    if (server.server.uuidShort == uuidShort) server.copy(resources = updated) else server
                                }
                            )
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.emit(ErrorHandler.getMessage(context, error))
            } finally {
                _actionServerId.value = null
            }
        }
    }
}
