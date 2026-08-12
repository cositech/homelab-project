package com.homelab.app.ui.portainer

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.R
import com.homelab.app.data.remote.dto.portainer.ContainerAction
import com.homelab.app.data.remote.dto.portainer.ContainerDetail
import com.homelab.app.data.remote.dto.portainer.ContainerStats
import com.homelab.app.data.repository.PortainerRepository
import com.homelab.app.util.ServiceType
import com.homelab.app.domain.action.ActionExecutionState
import com.homelab.app.domain.action.ActionRole
import com.homelab.app.domain.action.ControlledActionCoordinator
import com.homelab.app.domain.provider.ProviderRegistry
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContainerDetailViewModel @Inject constructor(
    private val repository: PortainerRepository,
    private val controlledActionCoordinator: ControlledActionCoordinator,
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val instanceId: String = checkNotNull(savedStateHandle["instanceId"])
    private val endpointId: Int = checkNotNull(savedStateHandle["endpointId"])
    private val containerId: String = checkNotNull(savedStateHandle["containerId"])

    private val _container = MutableStateFlow<ContainerDetail?>(null)
    val container: StateFlow<ContainerDetail?> = _container

    private val _stats = MutableStateFlow<ContainerStats?>(null)
    val stats: StateFlow<ContainerStats?> = _stats

    private val _logs = MutableStateFlow("")
    val logs: StateFlow<String> = _logs

    private val _composeFile = MutableStateFlow<String?>(null)
    val composeFile: StateFlow<String?> = _composeFile

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        fetchContainerDetails()
    }

    fun fetchContainerDetails() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val detail = repository.getContainerDetail(instanceId, endpointId, containerId)
                _container.value = detail

                // Fetch compose file if it's part of a stack
                // Try multiple labels used by Portainer/Docker Compose
                val labels = detail.config.labels
                var stackId = labels["com.portainer.stack.id"]?.toIntOrNull()
                
                // If no direct stack ID, we might need to search by stack name (com.docker.compose.project or com.portainer.stack.name)
                // However, the API requires a numeric stackId. 
                // In iOS we also search if it's missing.
                
                if (stackId == null) {
                    val stackName = labels["com.docker.compose.project"] ?: labels["com.portainer.stack.name"]
                    if (stackName != null) {
                        try {
                            // Search for stack by name
                            val stacks = repository.getStacks(instanceId, endpointId)
                            stackId = stacks.find { it.name == stackName }?.id
                        } catch (e: Exception) {
                            Logger.e("ContainerDetailVM", "Failed to search stack by name: $stackName", e)
                        }
                    }
                }

                if (stackId != null) {
                    try {
                        _composeFile.value = repository.getStackFile(instanceId, stackId)
                    } catch (e: Exception) {
                        Logger.e("ContainerDetailVM", "Failed to fetch stack file for ID $stackId", e)
                        _composeFile.value = null 
                    }
                } else {
                    _composeFile.value = null
                }
            } catch (e: Exception) {
                _error.value = ErrorHandler.getMessage(context, e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchStats() {
        viewModelScope.launch {
            try {
                _stats.value = repository.getContainerStats(instanceId, endpointId, containerId)
            } catch (e: Exception) {
                // Ignore stats errors as they can be flaky
            }
        }
    }

    fun fetchLogs() {
        viewModelScope.launch {
            try {
                _logs.value = repository.getContainerLogs(instanceId, endpointId, containerId, tail = 100)
            } catch (e: Exception) {
                _logs.value = ErrorHandler.getMessage(context, e)
            }
        }
    }

    fun executeAction(action: ContainerAction, confirmed: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = controlledActionCoordinator.execute(
                    request = action.controlledRequest(instanceId, endpointId, containerId, confirmed),
                    actorRole = ActionRole.ADMIN,
                    providerCapabilities = ProviderRegistry.capabilities(ServiceType.PORTAINER)
                ) {
                    when (action) {
                        ContainerAction.start -> repository.startContainer(instanceId, endpointId, containerId)
                        ContainerAction.stop -> repository.stopContainer(instanceId, endpointId, containerId)
                        ContainerAction.restart -> repository.restartContainer(instanceId, endpointId, containerId)
                        ContainerAction.kill -> repository.killContainer(instanceId, endpointId, containerId)
                        ContainerAction.pause -> repository.pauseContainer(instanceId, endpointId, containerId)
                        ContainerAction.unpause -> repository.unpauseContainer(instanceId, endpointId, containerId)
                    }
                }
                if (result.state == ActionExecutionState.SUCCEEDED) fetchContainerDetails()
                else {
                    _error.value = context.getString(R.string.error_action_failed, result.reasonCode)
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = ErrorHandler.getMessage(context, e)
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
