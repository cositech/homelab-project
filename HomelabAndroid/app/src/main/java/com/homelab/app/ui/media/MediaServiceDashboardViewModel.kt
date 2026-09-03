package com.homelab.app.ui.media

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.MediaArrAction
import com.homelab.app.data.repository.MediaArrActionResult
import com.homelab.app.data.repository.MediaArrRequestConfiguration
import com.homelab.app.data.repository.MediaArrRequestConfigurationRequiredException
import com.homelab.app.data.repository.MediaArrRequestSelection
import com.homelab.app.data.repository.MediaArrRepository
import com.homelab.app.data.repository.MediaArrSearchResultItem
import com.homelab.app.data.repository.MediaArrSnapshot
import com.homelab.app.data.repository.MediaServiceControlledAction
import com.homelab.app.data.repository.QbittorrentControlledAction
import com.homelab.app.data.repository.ServiceInstancesRepository
import com.homelab.app.domain.action.ActionExecutionState
import com.homelab.app.domain.action.ActionFailureDisposition
import com.homelab.app.domain.action.ActionOperationException
import com.homelab.app.domain.action.ActionRole
import com.homelab.app.domain.action.ControlledActionCoordinator
import com.homelab.app.domain.action.ControlledActionRequest
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.domain.provider.ProviderRegistry
import com.homelab.app.util.ServiceType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MediaServiceDashboardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaArrRepository: MediaArrRepository,
    private val controlledActionCoordinator: ControlledActionCoordinator,
    private val serviceInstancesRepository: ServiceInstancesRepository
) : ViewModel() {

    data class PendingRequestConfiguration(
        val item: MediaArrSearchResultItem,
        val configuration: MediaArrRequestConfiguration
    )

    private val instanceId: String = savedStateHandle.get<String>("instanceId")
        ?: throw IllegalStateException("Missing instanceId")

    val serviceType: ServiceType = savedStateHandle.get<String>("serviceType")
        ?.let(ServiceType::valueOf)
        ?: ServiceType.UNKNOWN

    private val _snapshot = MutableStateFlow<MediaArrSnapshot?>(null)
    val snapshot: StateFlow<MediaArrSnapshot?> = _snapshot

    private val _instance = MutableStateFlow<ServiceInstance?>(null)
    val instance: StateFlow<ServiceInstance?> = _instance

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _lastActionMessage = MutableStateFlow<MediaArrActionResult?>(null)
    val lastActionMessage: StateFlow<MediaArrActionResult?> = _lastActionMessage

    private val _searchResults = MutableStateFlow<List<MediaArrSearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<MediaArrSearchResultItem>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError

    private val _lastSearchQuery = MutableStateFlow("")
    val lastSearchQuery: StateFlow<String> = _lastSearchQuery

    private val _pendingRequestConfiguration = MutableStateFlow<PendingRequestConfiguration?>(null)
    val pendingRequestConfiguration: StateFlow<PendingRequestConfiguration?> = _pendingRequestConfiguration

    fun load() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _instance.value = serviceInstancesRepository.getInstance(instanceId)
                _snapshot.value = mediaArrRepository.loadSnapshot(instanceId)
            } catch (error: Exception) {
                _error.value = error.message ?: "Failed to load media snapshot"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Medium- and high-risk media-service mutations require explicit confirmation before execution. */
    fun actionRequiresConfirmation(action: MediaArrAction): Boolean =
        QbittorrentControlledAction.forMediaArrAction(action)?.requiresConfirmation == true ||
            MediaServiceControlledAction.forMediaArrAction(action)?.requiresConfirmation == true

    fun runAction(action: MediaArrAction, confirmed: Boolean = false) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _lastActionMessage.value = null
            try {
                val qbAction = QbittorrentControlledAction.forMediaArrAction(action)
                val mediaAction = MediaServiceControlledAction.forMediaArrAction(action)
                _lastActionMessage.value = when {
                    qbAction != null -> executeControlled(
                        qbAction.controlledRequest(instanceId, "transfer/all", confirmed)
                    ) { mediaArrRepository.runAction(instanceId, action) }
                    mediaAction != null -> executeControlled(
                        mediaAction.controlledRequest(serviceType, instanceId, mediaAction.defaultTargetRef(), confirmed)
                    ) { mediaArrRepository.runAction(instanceId, action) }
                    else -> mediaArrRepository.runAction(instanceId, action)
                }
                _snapshot.value = mediaArrRepository.loadSnapshot(instanceId)
            } catch (error: Exception) {
                _error.value = error.message ?: "Action failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun runQbTorrentAction(
        hash: String,
        name: String?,
        action: MediaArrAction,
        confirmed: Boolean = false
    ) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _lastActionMessage.value = null
            try {
                val controlled = QbittorrentControlledAction.forMediaArrAction(action)
                val safeHash = hash.trim()
                _lastActionMessage.value = if (controlled != null) {
                    executeControlled(
                        controlled.controlledRequest(instanceId, "torrent/$safeHash", confirmed)
                    ) {
                        mediaArrRepository.runQbittorrentTorrentAction(
                            instanceId = instanceId,
                            torrentHash = hash,
                            torrentName = name,
                            action = action
                        )
                    }
                } else {
                    mediaArrRepository.runQbittorrentTorrentAction(
                        instanceId = instanceId,
                        torrentHash = hash,
                        torrentName = name,
                        action = action
                    )
                }
                _snapshot.value = mediaArrRepository.loadSnapshot(instanceId)
            } catch (error: Exception) {
                _error.value = error.message ?: "Action failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun executeControlled(
        request: ControlledActionRequest,
        operation: suspend () -> MediaArrActionResult
    ): MediaArrActionResult {
        var result: MediaArrActionResult? = null
        // The add-content flow throws to request quality/root-folder selection before it mutates;
        // that is UI flow control, not a provider failure, so re-surface it to the caller intact.
        var configurationRequired: MediaArrRequestConfigurationRequiredException? = null
        val audit = controlledActionCoordinator.execute(
            request = request,
            actorRole = ActionRole.ADMIN,
            providerCapabilities = ProviderRegistry.capabilities(serviceType)
        ) {
            try {
                result = operation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: MediaArrRequestConfigurationRequiredException) {
                configurationRequired = error
                throw error
            } catch (error: ActionOperationException) {
                throw error
            } catch (error: Exception) {
                throw controlledFailure(error)
            }
        }
        configurationRequired?.let { throw it }
        if (audit.state != ActionExecutionState.SUCCEEDED) {
            throw IllegalStateException(audit.reasonCode)
        }
        return result ?: throw IllegalStateException("$reasonPrefix-provider-error")
    }

    private val reasonPrefix: String
        get() = serviceType.name.lowercase(java.util.Locale.ROOT).replace('_', '-')

    private fun controlledFailure(error: Exception): ActionOperationException {
        // requestRaw() reports HTTP failures as IllegalStateException("<code>: <message>").
        val httpCode = Regex("^(\\d{3}):").find(error.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        val reasonCode = when {
            error is IOException -> "$reasonPrefix-outcome-indeterminate"
            httpCode == 401 || httpCode == 403 -> "$reasonPrefix-invalid-credentials"
            httpCode != null -> "$reasonPrefix-http-$httpCode"
            error is IllegalStateException -> "$reasonPrefix-provider-reported-failure"
            else -> "$reasonPrefix-provider-error"
        }
        return ActionOperationException(
            reasonCode = reasonCode,
            disposition = ActionFailureDisposition.NON_RETRYABLE,
            cause = error
        )
    }

    /** Approving or declining a specific media request is medium risk and needs explicit confirmation. */
    val jellyseerrRequestActionRequiresConfirmation: Boolean
        get() = MediaServiceControlledAction.REQUEST_APPROVE.requiresConfirmation

    /** Destroying a scraper session is medium risk and needs explicit confirmation. */
    val flaresolverrDestroyRequiresConfirmation: Boolean
        get() = MediaServiceControlledAction.SESSION_DESTROY.requiresConfirmation

    fun runJellyseerrRequestAction(
        requestId: Int,
        title: String?,
        approve: Boolean,
        confirmed: Boolean = false
    ) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _lastActionMessage.value = null
            try {
                val controlled = if (approve) {
                    MediaServiceControlledAction.REQUEST_APPROVE
                } else {
                    MediaServiceControlledAction.REQUEST_DECLINE
                }
                _lastActionMessage.value = executeControlled(
                    controlled.controlledRequest(serviceType, instanceId, "request/$requestId", confirmed)
                ) {
                    mediaArrRepository.runJellyseerrRequestAction(
                        instanceId = instanceId,
                        requestId = requestId,
                        title = title,
                        approve = approve
                    )
                }
                _snapshot.value = mediaArrRepository.loadSnapshot(instanceId)
            } catch (error: Exception) {
                _error.value = error.message ?: "Action failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun destroyFlaresolverrSession(sessionId: String, confirmed: Boolean = false) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _lastActionMessage.value = null
            try {
                val safeSession = sessionId.trim()
                _lastActionMessage.value = executeControlled(
                    MediaServiceControlledAction.SESSION_DESTROY.controlledRequest(
                        serviceType, instanceId, "session/$safeSession", confirmed
                    )
                ) {
                    mediaArrRepository.destroyFlaresolverrSession(instanceId, sessionId)
                }
                _snapshot.value = mediaArrRepository.loadSnapshot(instanceId)
            } catch (error: Exception) {
                _error.value = error.message ?: "Action failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun consumeActionMessage() {
        _lastActionMessage.value = null
    }

    fun clearError() {
        _error.value = null
    }

    fun search(query: String) {
        val normalized = query.trim()
        _lastSearchQuery.value = normalized
        if (normalized.length < 2) {
            _searchResults.value = emptyList()
            _searchError.value = null
            _isSearching.value = false
            return
        }
        if (_isSearching.value) return

        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            try {
                _searchResults.value = mediaArrRepository.searchContent(instanceId, normalized)
            } catch (error: Exception) {
                _searchError.value = error.message ?: "Search failed"
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearch() {
        _lastSearchQuery.value = ""
        _searchResults.value = emptyList()
        _searchError.value = null
        _isSearching.value = false
    }

    fun requestSearchResult(item: MediaArrSearchResultItem) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _lastActionMessage.value = null
            try {
                // Selecting a specific search result to add is the explicit user action; the quality
                // and root-folder dialog (when shown) is its confirmation, so mark it confirmed here.
                _lastActionMessage.value = executeControlled(
                    MediaServiceControlledAction.LIBRARY_ADD.controlledRequest(
                        serviceType, instanceId, "library/${item.requestId.orEmpty().ifBlank { "new" }}", confirmed = true
                    )
                ) {
                    mediaArrRepository.requestSearchResult(instanceId, item)
                }
                _snapshot.value = mediaArrRepository.loadSnapshot(instanceId)
                _pendingRequestConfiguration.value = null
            } catch (error: MediaArrRequestConfigurationRequiredException) {
                _pendingRequestConfiguration.value = PendingRequestConfiguration(
                    item = item,
                    configuration = error.configuration
                )
            } catch (error: Exception) {
                _error.value = error.message ?: "Request failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun confirmRequestConfiguration(selection: MediaArrRequestSelection) {
        val pending = _pendingRequestConfiguration.value ?: return
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _lastActionMessage.value = null
            try {
                _lastActionMessage.value = executeControlled(
                    MediaServiceControlledAction.LIBRARY_ADD.controlledRequest(
                        serviceType, instanceId,
                        "library/${pending.item.requestId.orEmpty().ifBlank { "new" }}", confirmed = true
                    )
                ) {
                    mediaArrRepository.requestSearchResult(
                        instanceId = instanceId,
                        item = pending.item,
                        selection = selection
                    )
                }
                _snapshot.value = mediaArrRepository.loadSnapshot(instanceId)
                _pendingRequestConfiguration.value = null
            } catch (error: MediaArrRequestConfigurationRequiredException) {
                _pendingRequestConfiguration.value = pending.copy(configuration = error.configuration)
            } catch (error: Exception) {
                _error.value = error.message ?: "Request failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun dismissPendingRequestConfiguration() {
        _pendingRequestConfiguration.value = null
    }
}
