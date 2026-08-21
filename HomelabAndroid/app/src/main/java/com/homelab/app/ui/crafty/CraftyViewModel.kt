package com.homelab.app.ui.crafty

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.CraftyDashboardData
import com.homelab.app.data.repository.CraftyRepository
import com.homelab.app.data.repository.CraftyServerAction
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.data.repository.CraftyApiException
import com.homelab.app.data.repository.CraftyCommandAction
import com.homelab.app.domain.action.ActionExecutionState
import com.homelab.app.domain.action.ActionFailureDisposition
import com.homelab.app.domain.action.ActionOperationException
import com.homelab.app.domain.action.ActionRole
import com.homelab.app.domain.action.ControlledActionCoordinator
import com.homelab.app.domain.provider.ProviderRegistry
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CraftyViewModel @Inject constructor(
    private val craftyRepository: CraftyRepository,
    private val controlledActionCoordinator: ControlledActionCoordinator,
    private val servicesRepository: ServicesRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _uiState = MutableStateFlow<UiState<CraftyDashboardData>>(UiState.Loading)
    val uiState: StateFlow<UiState<CraftyDashboardData>> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _actionServerId = MutableStateFlow<String?>(null)
    val actionServerId: StateFlow<String?> = _actionServerId.asStateFlow()

    private val _logsServerId = MutableStateFlow<String?>(null)
    val logsServerId: StateFlow<String?> = _logsServerId.asStateFlow()

    private val _logsState = MutableStateFlow<UiState<List<String>>>(UiState.Idle)
    val logsState: StateFlow<UiState<List<String>>> = _logsState.asStateFlow()

    private val _commandServerId = MutableStateFlow<String?>(null)
    val commandServerId: StateFlow<String?> = _commandServerId.asStateFlow()

    private val _commandError = MutableStateFlow<String?>(null)
    val commandError: StateFlow<String?> = _commandError.asStateFlow()

    private val _isSendingCommand = MutableStateFlow(false)
    val isSendingCommand: StateFlow<Boolean> = _isSendingCommand.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var refreshJob: Job? = null
    private var logsJob: Job? = null
    private var refreshRequestId: Long = 0L

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.CRAFTY_CONTROLLER].orEmpty() }
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
                _uiState.value = UiState.Success(craftyRepository.getDashboard(instanceId))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { refresh(forceLoading = true) }
                )
            } finally {
                if (requestId == refreshRequestId) {
                    _isRefreshing.value = false
                }
            }
        }
    }

    fun performAction(serverId: String, action: CraftyServerAction, confirmed: Boolean = false) {
        viewModelScope.launch {
            _actionServerId.value = serverId
            try {
                val audit = controlledActionCoordinator.execute(
                    request = action.controlledRequest(instanceId, serverId, confirmed),
                    actorRole = ActionRole.ADMIN,
                    providerCapabilities = ProviderRegistry.capabilities(ServiceType.CRAFTY_CONTROLLER)
                ) {
                    try {
                        craftyRepository.sendAction(instanceId, serverId, action)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: ActionOperationException) {
                        throw error
                    } catch (error: Exception) {
                        throw controlledFailure(error)
                    }
                }
                if (audit.state != ActionExecutionState.SUCCEEDED) {
                    _messages.emit(audit.reasonCode)
                    return@launch
                }
                _messages.emit(context.getString(com.homelab.app.R.string.crafty_action_sent))
                syncServerAfterAction(serverId, action)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.emit(ErrorHandler.getMessage(context, error))
            } finally {
                _actionServerId.value = null
            }
        }
    }

    fun openLogs(serverId: String) {
        _logsServerId.value = serverId
        loadLogs(forceLoading = true)
    }

    fun dismissLogs() {
        logsJob?.cancel()
        _logsServerId.value = null
        _logsState.value = UiState.Idle
    }

    fun loadLogs(forceLoading: Boolean = false) {
        val serverId = _logsServerId.value ?: return
        logsJob?.cancel()
        logsJob = viewModelScope.launch {
            if (forceLoading || _logsState.value !is UiState.Success) {
                _logsState.value = UiState.Loading
            }
            try {
                _logsState.value = UiState.Success(craftyRepository.getServerLogs(instanceId, serverId))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _logsState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { loadLogs(forceLoading = true) }
                )
            }
        }
    }

    fun openCommand(serverId: String) {
        _commandServerId.value = serverId
        _commandError.value = null
    }

    fun dismissCommand() {
        if (_isSendingCommand.value) return
        _commandServerId.value = null
        _commandError.value = null
    }

    fun sendCommand(command: String, confirmed: Boolean = false) {
        val serverId = _commandServerId.value ?: return
        val normalizedCommand = command.trim()
        if (normalizedCommand.isBlank() || _isSendingCommand.value) return

        viewModelScope.launch {
            _isSendingCommand.value = true
            _commandError.value = null
            try {
                val audit = controlledActionCoordinator.execute(
                    request = CraftyCommandAction.SEND.controlledRequest(instanceId, serverId, confirmed),
                    actorRole = ActionRole.ADMIN,
                    providerCapabilities = ProviderRegistry.capabilities(ServiceType.CRAFTY_CONTROLLER)
                ) {
                    try {
                        craftyRepository.sendCommand(instanceId, serverId, normalizedCommand)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: ActionOperationException) {
                        throw error
                    } catch (error: Exception) {
                        throw controlledFailure(error)
                    }
                }
                if (audit.state != ActionExecutionState.SUCCEEDED) {
                    _commandError.value = audit.reasonCode
                    return@launch
                }
                _commandServerId.value = null
                _messages.emit(context.getString(com.homelab.app.R.string.crafty_command_sent))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _commandError.value = ErrorHandler.getMessage(context, error)
            } finally {
                _isSendingCommand.value = false
            }
        }
    }

    fun setPreferredInstance(newInstanceId: String) {
        viewModelScope.launch {
            servicesRepository.setPreferredInstance(ServiceType.CRAFTY_CONTROLLER, newInstanceId)
        }
    }

    private suspend fun syncServerAfterAction(serverId: String, action: CraftyServerAction) {
        val attempts = when (action) {
            CraftyServerAction.BACKUP, CraftyServerAction.UPDATE -> 4
            CraftyServerAction.START, CraftyServerAction.STOP, CraftyServerAction.RESTART, CraftyServerAction.KILL -> 6
        }

        repeat(attempts) { attempt ->
            if (attempt > 0) delay(1_000)

            val latestStats = runCatching {
                craftyRepository.getServerStats(instanceId, serverId)
            }.getOrNull() ?: return@repeat

            val currentData = (_uiState.value as? UiState.Success)?.data ?: return@repeat
            val updatedServers = currentData.servers.map { entry ->
                if (entry.server.serverId == serverId) entry.copy(stats = latestStats) else entry
            }
            _uiState.value = UiState.Success(currentData.copy(servers = updatedServers))
        }
    }

    private fun controlledFailure(error: Exception): ActionOperationException {
        val reasonCode = when (error) {
            is CraftyApiException -> when (error.kind) {
                CraftyApiException.Kind.CONNECTION_ERROR -> "crafty-outcome-indeterminate"
                CraftyApiException.Kind.INVALID_CREDENTIALS -> "crafty-invalid-credentials"
                CraftyApiException.Kind.SERVER_ERROR -> "crafty-provider-error"
            }
            is IllegalStateException -> "crafty-provider-reported-failure"
            else -> "crafty-provider-error"
        }
        return ActionOperationException(
            reasonCode = reasonCode,
            disposition = ActionFailureDisposition.NON_RETRYABLE,
            cause = error
        )
    }
}
