package com.homelab.app.ui.healthchecks

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.healthchecks.HealthchecksChannel
import com.homelab.app.data.remote.dto.healthchecks.HealthchecksCheck
import com.homelab.app.R
import com.homelab.app.data.remote.dto.healthchecks.HealthchecksCheckPayload
import com.homelab.app.data.remote.dto.healthchecks.HealthchecksControlledCheckAction
import com.homelab.app.data.repository.HealthchecksRepository
import com.homelab.app.domain.action.ActionExecutionState
import com.homelab.app.domain.action.ActionRole
import com.homelab.app.domain.action.ControlledActionCoordinator
import com.homelab.app.domain.provider.ProviderRegistry
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HealthchecksEditorViewModel @Inject constructor(
    private val repository: HealthchecksRepository,
    private val controlledActionCoordinator: ControlledActionCoordinator,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])
    private val checkId: String? = savedStateHandle["checkId"]

    private val _existing = MutableStateFlow<HealthchecksCheck?>(null)
    val existing: StateFlow<HealthchecksCheck?> = _existing

    private val _channels = MutableStateFlow<List<HealthchecksChannel>>(emptyList())
    val channels: StateFlow<List<HealthchecksChannel>> = _channels

    private val _uiState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val uiState: StateFlow<UiState<Unit>> = _uiState

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    val isEditing: Boolean get() = checkId != null

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val channelsTask = async { repository.listChannels(instanceId) }
                val checkTask = async {
                    checkId?.let { repository.getCheck(instanceId, it) }
                }
                _channels.value = channelsTask.await()
                _existing.value = checkTask.await()
                _uiState.value = UiState.Success(Unit)
            } catch (error: Exception) {
                _uiState.value = UiState.Error(ErrorHandler.getMessage(context, error), retryAction = { load() })
            }
        }
    }

    fun save(
        payload: HealthchecksCheckPayload,
        confirmed: Boolean = false,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            try {
                val existingCheck = _existing.value
                if (existingCheck?.uuid == null && checkId != null) {
                    throw IllegalStateException("Read-only API key")
                }
                val action = if (existingCheck?.uuid != null) {
                    HealthchecksControlledCheckAction.UPDATE
                } else {
                    HealthchecksControlledCheckAction.CREATE
                }
                val targetId = existingCheck?.uuid ?: "new"
                val result = controlledActionCoordinator.execute(
                    request = action.controlledRequest(instanceId, targetId, confirmed),
                    actorRole = ActionRole.ADMIN,
                    providerCapabilities = ProviderRegistry.capabilities(ServiceType.HEALTHCHECKS)
                ) {
                    if (existingCheck?.uuid != null) {
                        repository.updateCheck(instanceId, existingCheck.uuid, payload)
                    } else {
                        repository.createCheck(instanceId, payload)
                    }
                }
                if (result.state == ActionExecutionState.SUCCEEDED) {
                    onComplete()
                } else {
                    _error.value = context.getString(R.string.error_action_failed, result.reasonCode)
                }
            } catch (error: Exception) {
                _error.value = ErrorHandler.getMessage(context, error)
            } finally {
                _isSaving.value = false
            }
        }
    }
}
