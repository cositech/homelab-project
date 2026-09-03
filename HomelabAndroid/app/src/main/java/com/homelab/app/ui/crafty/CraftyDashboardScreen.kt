package com.homelab.app.ui.crafty

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TurnedInNot
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.crafty.CraftyServerStats
import com.homelab.app.data.repository.CraftyDashboardData
import com.homelab.app.data.repository.CraftyServerAction
import com.homelab.app.data.repository.CraftyServerWithStats
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CraftyDashboardScreen(
    viewModel: CraftyViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToInstance: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val actionServerId by viewModel.actionServerId.collectAsStateWithLifecycle()
    val logsServerId by viewModel.logsServerId.collectAsStateWithLifecycle()
    val logsState by viewModel.logsState.collectAsStateWithLifecycle()
    val commandServerId by viewModel.commandServerId.collectAsStateWithLifecycle()
    val commandError by viewModel.commandError.collectAsStateWithLifecycle()
    val isSendingCommand by viewModel.isSendingCommand.collectAsStateWithLifecycle()

    val currentInstance = instances.find { it.id == viewModel.instanceId }
    val title = currentInstance?.label?.takeIf { it.isNotBlank() } ?: ServiceType.CRAFTY_CONTROLLER.displayName
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingAction by remember { mutableStateOf<PendingCraftyAction?>(null) }
    val data = (state as? UiState.Success)?.data
    val logsServerName = remember(data, logsServerId) {
        data?.servers
            ?.firstOrNull { it.server.serverId == logsServerId }
            ?.server
            ?.serverName
            ?: logsServerId?.toString().orEmpty()
    }
    val commandServerName = remember(data, commandServerId) {
        data?.servers
            ?.firstOrNull { it.server.serverId == commandServerId }
            ?.server
            ?.serverName
            ?: commandServerId?.toString().orEmpty()
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.SettingsApplications,
                            contentDescription = stringResource(R.string.settings_title)
                        )
                    }
                    IconButton(onClick = { viewModel.refresh(forceLoading = false) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh(forceLoading = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val ui = state) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = ui.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
                is UiState.Success -> {
                    CraftyContent(
                        data = ui.data,
                        instances = instances,
                        activeInstanceId = viewModel.instanceId,
                        actionServerId = actionServerId,
                        onInstanceSelected = {
                            viewModel.setPreferredInstance(it.id)
                            onNavigateToInstance(it.id)
                        },
                        onAction = { serverId, action ->
                            if (action.requiresConfirmation) {
                                pendingAction = PendingCraftyAction(serverId, action)
                            } else {
                                viewModel.performAction(serverId, action)
                            }
                        },
                        onOpenLogs = viewModel::openLogs,
                        onOpenCommand = viewModel::openCommand
                    )
                }
                is UiState.Idle, is UiState.Offline -> Unit
            }
        }
    }

    if (logsServerId != null) {
        CraftyLogsSheet(
            serverName = logsServerName,
            logsState = logsState,
            onRefresh = { viewModel.loadLogs(forceLoading = true) },
            onDismiss = viewModel::dismissLogs
        )
    }

    if (commandServerId != null) {
        CraftyCommandSheet(
            serverId = commandServerId.orEmpty(),
            serverName = commandServerName,
            isSending = isSendingCommand,
            errorMessage = commandError,
            onDismiss = viewModel::dismissCommand,
            onSend = viewModel::sendCommand
        )
    }

    pendingAction?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(stringResource(R.string.confirm)) },
            text = { Text(stringResource(R.string.game_server_confirm_action_message)) },
            confirmButton = {
                Button(onClick = {
                    pendingAction = null
                    viewModel.performAction(pending.serverId, pending.action, confirmed = true)
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingAction = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private data class PendingCraftyAction(
    val serverId: String,
    val action: CraftyServerAction
)

@Composable
private fun CraftyContent(
    data: CraftyDashboardData,
    instances: List<com.homelab.app.domain.model.ServiceInstance>,
    activeInstanceId: String,
    actionServerId: String?,
    onInstanceSelected: (com.homelab.app.domain.model.ServiceInstance) -> Unit,
    onAction: (String, CraftyServerAction) -> Unit,
    onOpenLogs: (String) -> Unit,
    onOpenCommand: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ServiceInstancePicker(
                instances = instances,
                selectedInstanceId = activeInstanceId,
                onInstanceSelected = onInstanceSelected,
                label = stringResource(R.string.service_crafty_controller)
            )
        }

        item { CraftyOverviewCard(data) }

        if (data.servers.isEmpty()) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.crafty_no_servers),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(data.servers, key = { it.server.serverId }) { entry ->
                CraftyServerCard(
                    entry = entry,
                    isActionRunning = actionServerId == entry.server.serverId,
                    onAction = onAction,
                    onOpenLogs = { onOpenLogs(entry.server.serverId) },
                    onOpenCommand = { onOpenCommand(entry.server.serverId) }
                )
            }
        }
    }
}

@Composable
private fun CraftyOverviewCard(data: CraftyDashboardData) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.crafty_servers),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OverviewMetric(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.crafty_running_servers),
                    value = "${data.runningServers}/${data.totalServers}",
                    icon = Icons.Default.Dns
                )
                OverviewMetric(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.crafty_total_players),
                    value = data.totalPlayers.toString(),
                    icon = Icons.Default.Groups
                )
            }
        }
    }
}

@Composable
private fun OverviewMetric(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector
) {
    ElevatedCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CraftyServerCard(
    entry: CraftyServerWithStats,
    isActionRunning: Boolean,
    onAction: (String, CraftyServerAction) -> Unit,
    onOpenLogs: () -> Unit,
    onOpenCommand: () -> Unit
) {
    val stats = entry.stats
    val isTransientState = stats?.waitingStart == true || stats?.updating == true || stats?.downloading == true
    val actionsEnabled = !isActionRunning && !isTransientState
    val accent = when {
        stats == null -> MaterialTheme.colorScheme.outline
        stats.crashed -> MaterialTheme.colorScheme.error
        stats.running -> Color(0xFF16A34A)
        stats.waitingStart || stats.updating || stats.downloading -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    val unavailable = stringResource(R.string.not_available)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.server.serverName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = entry.server.type.orEmpty().ifBlank { stringResource(R.string.crafty_server_type) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(text = statusLabel(stats)) },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = accent.copy(alpha = 0.14f),
                        disabledLabelColor = accent
                    )
                )

                if (isActionRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailPill(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Groups,
                    label = stringResource(R.string.crafty_players),
                    value = "${stats?.online ?: 0}/${stats?.max ?: 0}"
                )
                DetailPill(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Speed,
                    label = stringResource(R.string.crafty_cpu),
                    value = stats?.cpu?.let { String.format(Locale.getDefault(), "%.1f%%", it) } ?: unavailable
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailPill(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Memory,
                    label = stringResource(R.string.crafty_memory),
                    value = stats?.mem ?: unavailable
                )
                DetailPill(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.SettingsApplications,
                    label = stringResource(R.string.crafty_version),
                    value = stats?.version ?: unavailable
                )
            }

            if (!stats?.worldName.isNullOrBlank()) {
                Text(
                    text = "${stringResource(R.string.crafty_world)}: ${stats?.worldName.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CraftyActionButton(
                    label = stringResource(R.string.crafty_action_start),
                    icon = Icons.Default.PlayArrow,
                    enabled = actionsEnabled && stats?.running != true,
                    primary = true,
                    onClick = { onAction(entry.server.serverId, CraftyServerAction.START) }
                )
                CraftyActionButton(
                    label = stringResource(R.string.crafty_action_stop),
                    icon = Icons.Default.Stop,
                    enabled = actionsEnabled && stats?.running == true,
                    onClick = { onAction(entry.server.serverId, CraftyServerAction.STOP) }
                )
                CraftyActionButton(
                    label = stringResource(R.string.crafty_action_restart),
                    icon = Icons.Default.Refresh,
                    enabled = actionsEnabled && stats?.running == true,
                    onClick = { onAction(entry.server.serverId, CraftyServerAction.RESTART) }
                )
                CraftyActionButton(
                    label = stringResource(R.string.crafty_action_update),
                    icon = Icons.Default.SettingsApplications,
                    enabled = actionsEnabled,
                    onClick = { onAction(entry.server.serverId, CraftyServerAction.UPDATE) }
                )
                CraftyActionButton(
                    label = stringResource(R.string.crafty_action_backup),
                    icon = Icons.Default.TurnedInNot,
                    enabled = actionsEnabled,
                    onClick = { onAction(entry.server.serverId, CraftyServerAction.BACKUP) }
                )
                CraftyActionButton(
                    label = stringResource(R.string.crafty_action_logs),
                    icon = Icons.AutoMirrored.Filled.Subject,
                    enabled = true,
                    onClick = onOpenLogs
                )
                CraftyActionButton(
                    label = stringResource(R.string.crafty_action_command),
                    icon = Icons.Default.Code,
                    enabled = true,
                    onClick = onOpenCommand
                )
                CraftyActionButton(
                    label = stringResource(R.string.crafty_action_kill),
                    icon = Icons.Default.Warning,
                    enabled = actionsEnabled && stats?.running == true,
                    destructive = true,
                    onClick = { onAction(entry.server.serverId, CraftyServerAction.KILL) }
                )
            }
        }
    }
}

@Composable
private fun CraftyActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    primary: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val modifier = Modifier.defaultMinSize(minWidth = 120.dp)

    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
        ) {
            ActionButtonContent(label = label, icon = icon)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            colors = if (destructive) {
                ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.outlinedButtonColors()
            }
        ) {
            ActionButtonContent(label = label, icon = icon)
        }
    }
}

@Composable
private fun ActionButtonContent(label: String, icon: ImageVector) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null)
        Text(
            text = label,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailPill(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String
) {
    ElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CraftyLogsSheet(
    serverName: String,
    logsState: UiState<List<String>>,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.crafty_action_logs),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = serverName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            }

            when (logsState) {
                is UiState.Loading -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is UiState.Error -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = logsState.message,
                            color = MaterialTheme.colorScheme.error
                        )
                        OutlinedButton(onClick = onRefresh) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
                is UiState.Success -> {
                    if (logsState.data.isEmpty()) {
                        Text(
                            text = stringResource(R.string.crafty_logs_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 220.dp, max = 520.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(logsState.data) { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
                is UiState.Idle, is UiState.Offline -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CraftyCommandSheet(
    serverId: String,
    serverName: String,
    isSending: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSend: (String, Boolean) -> Unit
) {
    var command by rememberSaveable(serverId) { mutableStateOf("") }
    var pendingCommand by rememberSaveable(serverId) { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.crafty_action_command),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = serverName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.crafty_command_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                enabled = !isSending,
                placeholder = { Text(stringResource(R.string.crafty_command_placeholder)) }
            )
            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isSending
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = { pendingCommand = command.trim() },
                    enabled = !isSending && command.trim().isNotEmpty()
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.crafty_command_send))
                    }
                }
            }
        }
    }

    pendingCommand?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingCommand = null },
            title = { Text(stringResource(R.string.confirm)) },
            text = { Text(stringResource(R.string.game_server_confirm_action_message)) },
            confirmButton = {
                Button(onClick = {
                    pendingCommand = null
                    onSend(pending, true)
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingCommand = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun statusLabel(stats: CraftyServerStats?): String {
    return when {
        stats == null -> stringResource(R.string.crafty_status_offline)
        stats.crashed -> stringResource(R.string.crafty_status_crashed)
        stats.updating || stats.downloading -> stringResource(R.string.crafty_status_updating)
        stats.waitingStart -> stringResource(R.string.crafty_status_starting)
        stats.running -> stringResource(R.string.crafty_status_running)
        else -> stringResource(R.string.crafty_status_stopped)
    }
}
