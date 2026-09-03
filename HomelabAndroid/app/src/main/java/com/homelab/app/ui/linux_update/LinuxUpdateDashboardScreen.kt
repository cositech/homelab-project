package com.homelab.app.ui.linux_update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.linux_update.LinuxUpdateDashboardStats
import com.homelab.app.data.remote.dto.linux_update.LinuxUpdateHistoryEntry
import com.homelab.app.data.remote.dto.linux_update.LinuxUpdatePackageUpdate
import com.homelab.app.data.remote.dto.linux_update.LinuxUpdateSystem
import com.homelab.app.data.repository.LinuxUpdateSystemDetail
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import kotlinx.coroutines.flow.collectLatest

private fun linuxUpdatePageBackground(isDarkTheme: Boolean, accent: Color): Brush = if (isDarkTheme) {
    Brush.verticalGradient(
        listOf(
            Color(0xFF090B0F),
            Color(0xFF11151D),
            accent.copy(alpha = 0.07f),
            Color(0xFF090C12)
        )
    )
} else {
    Brush.verticalGradient(
        listOf(
            Color(0xFFF6F8FA),
            Color(0xFFF2F4F8),
            accent.copy(alpha = 0.03f),
            Color(0xFFF7F8FA)
        )
    )
}

private fun linuxUpdateCardColor(isDarkTheme: Boolean, accent: Color): Color =
    accent.copy(alpha = if (isDarkTheme) 0.07f else 0.03f)
        .compositeOver(if (isDarkTheme) Color(0xFF151B25) else Color(0xFFFAFBFC))

private fun linuxUpdateBorderColor(isDarkTheme: Boolean, accent: Color): Color =
    accent.copy(alpha = if (isDarkTheme) 0.2f else 0.08f)

private data class PendingLinuxUpdateAction(
    val systemAction: LinuxUpdateViewModel.SystemAction? = null,
    val packageName: String? = null
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LinuxUpdateDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    viewModel: LinuxUpdateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val filteredSystems by viewModel.filteredSystems.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val selectedSystemId by viewModel.selectedSystemId.collectAsStateWithLifecycle()
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val isRunningAction by viewModel.isRunningAction.collectAsStateWithLifecycle()
    val isRunningDashboardAction by viewModel.isRunningDashboardAction.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var pendingAction by remember { mutableStateOf<PendingLinuxUpdateAction?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(selectedSystemId) {
        if (selectedSystemId == null) pendingAction = null
    }

    val accent = ServiceType.LINUX_UPDATE.primaryColor
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val pageBrush = remember(isDarkTheme) { linuxUpdatePageBackground(isDarkTheme, accent) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.service_linux_update),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.runDashboardAction(LinuxUpdateViewModel.DashboardAction.CHECK_ALL) },
                        enabled = !isRunningDashboardAction
                    ) {
                        Icon(
                            Icons.Default.Update,
                            contentDescription = stringResource(R.string.linux_update_action_check_all)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.runDashboardAction(LinuxUpdateViewModel.DashboardAction.REFRESH_CACHE) },
                        enabled = !isRunningDashboardAction
                    ) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = stringResource(R.string.linux_update_action_refresh_cache)
                        )
                    }
                    IconButton(onClick = { viewModel.fetchDashboard(forceLoading = false) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                UiState.Loading, UiState.Idle -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = accent)
                    }
                }

                is UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { state.retryAction?.invoke() ?: viewModel.fetchDashboard(forceLoading = true) },
                        modifier = Modifier.padding(paddingValues)
                    )
                }

                UiState.Offline -> {
                    ErrorScreen(
                        message = "",
                        onRetry = { viewModel.fetchDashboard(forceLoading = true) },
                        isOffline = true,
                        modifier = Modifier.padding(paddingValues)
                    )
                }

                is UiState.Success -> {
                    val content = state.data
                    val allSystems = content.systems

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (instances.isNotEmpty()) {
                            item {
                                ServiceInstancePicker(
                                    instances = instances,
                                    selectedInstanceId = viewModel.instanceId,
                                    onInstanceSelected = { instance ->
                                        viewModel.setPreferredInstance(instance.id)
                                        onNavigateToInstance(instance.id)
                                    },
                                    label = stringResource(R.string.linux_update_instance_label)
                                )
                            }
                        }

                        item {
                            LinuxUpdateOverviewCard(
                                stats = content.stats,
                                totalSystems = allSystems.size,
                                accent = accent,
                                isRefreshing = isRefreshing || isRunningDashboardAction,
                                selectedFilter = selectedFilter,
                                onSelectFilter = viewModel::selectFilter
                            )
                        }

                        if (filteredSystems.isEmpty()) {
                            item {
                                LinuxUpdateEmptyState(
                                    isFiltered = allSystems.isNotEmpty() && selectedFilter != LinuxUpdateViewModel.Filter.ALL
                                )
                            }
                        } else {
                            itemsIndexed(filteredSystems, key = { _, system -> system.id }) { _, system ->
                                LinuxUpdateSystemCard(
                                    system = system,
                                    accent = accent,
                                    onClick = { viewModel.openSystemDetail(system.id) }
                                )
                            }
                        }
                    }
                }
            }

            if (selectedSystemId != null) {
                ModalBottomSheet(onDismissRequest = {
                    pendingAction = null
                    viewModel.closeSystemDetail()
                }) {
                    LinuxUpdateSystemDetailSheet(
                        detailState = detailState,
                        isRunningAction = isRunningAction,
                        onRefresh = { viewModel.refreshSystemDetail(forceLoading = false) },
                        onAction = { action ->
                            if (action.requiresConfirmation) {
                                pendingAction = PendingLinuxUpdateAction(systemAction = action)
                            } else {
                                viewModel.runSystemAction(action)
                            }
                        },
                        onUpgradePackage = { packageName ->
                            pendingAction = PendingLinuxUpdateAction(packageName = packageName)
                        }
                    )
                }
            }
        }
    }

    pendingAction?.let { pending ->
        val actionLabel = when (pending.systemAction) {
            LinuxUpdateViewModel.SystemAction.UPGRADE_ALL -> stringResource(R.string.linux_update_action_upgrade)
            LinuxUpdateViewModel.SystemAction.FULL_UPGRADE -> stringResource(R.string.linux_update_action_full_upgrade)
            LinuxUpdateViewModel.SystemAction.REBOOT -> stringResource(R.string.linux_update_action_reboot)
            LinuxUpdateViewModel.SystemAction.CHECK -> stringResource(R.string.linux_update_action_check)
            null -> "${stringResource(R.string.linux_update_action_upgrade_package)}: ${pending.packageName.orEmpty()}"
        }
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(stringResource(R.string.linux_update_confirm_action, actionLabel)) },
            text = { Text(stringResource(R.string.linux_update_confirm_action_message)) },
            confirmButton = {
                Button(onClick = {
                    pendingAction = null
                    pending.systemAction?.let { viewModel.runSystemAction(it, confirmed = true) }
                        ?: viewModel.runPackageUpgrade(pending.packageName.orEmpty(), confirmed = true)
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingAction = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun LinuxUpdateOverviewCard(
    stats: LinuxUpdateDashboardStats,
    totalSystems: Int,
    accent: Color,
    isRefreshing: Boolean,
    selectedFilter: LinuxUpdateViewModel.Filter,
    onSelectFilter: (LinuxUpdateViewModel.Filter) -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = linuxUpdateCardColor(isDarkTheme, accent),
        border = BorderStroke(1.dp, linuxUpdateBorderColor(isDarkTheme, accent)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServiceIcon(
                    type = ServiceType.LINUX_UPDATE,
                    size = 48.dp,
                    iconSize = 30.dp,
                    cornerRadius = 12.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.linux_update_overview_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = accent
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LinuxUpdateStatTile(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.linux_update_total_systems),
                    value = totalSystems.toString(),
                    icon = Icons.Default.Dns,
                    tint = accent,
                    selected = selectedFilter == LinuxUpdateViewModel.Filter.ALL,
                    onClick = {
                        val next = if (selectedFilter == LinuxUpdateViewModel.Filter.ALL) {
                            LinuxUpdateViewModel.Filter.ALL
                        } else {
                            LinuxUpdateViewModel.Filter.ALL
                        }
                        onSelectFilter(next)
                    }
                )
                LinuxUpdateStatTile(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.linux_update_updates_total),
                    value = stats.totalUpdates.toString(),
                    icon = Icons.Default.Update,
                    tint = Color(0xFFF59E0B),
                    selected = selectedFilter == LinuxUpdateViewModel.Filter.UPDATES,
                    onClick = {
                        onSelectFilter(
                            if (selectedFilter == LinuxUpdateViewModel.Filter.UPDATES) {
                                LinuxUpdateViewModel.Filter.ALL
                            } else {
                                LinuxUpdateViewModel.Filter.UPDATES
                            }
                        )
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LinuxUpdateStatTile(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.linux_update_check_issues),
                    value = stats.checkIssues.toString(),
                    icon = Icons.Default.ErrorOutline,
                    tint = MaterialTheme.colorScheme.error,
                    selected = selectedFilter == LinuxUpdateViewModel.Filter.ISSUES,
                    onClick = {
                        onSelectFilter(
                            if (selectedFilter == LinuxUpdateViewModel.Filter.ISSUES) {
                                LinuxUpdateViewModel.Filter.ALL
                            } else {
                                LinuxUpdateViewModel.Filter.ISSUES
                            }
                        )
                    }
                )
                LinuxUpdateStatTile(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.linux_update_needs_reboot),
                    value = stats.needsReboot.toString(),
                    icon = Icons.Default.Sync,
                    tint = accent,
                    selected = selectedFilter == LinuxUpdateViewModel.Filter.REBOOT,
                    onClick = {
                        onSelectFilter(
                            if (selectedFilter == LinuxUpdateViewModel.Filter.REBOOT) {
                                LinuxUpdateViewModel.Filter.ALL
                            } else {
                                LinuxUpdateViewModel.Filter.REBOOT
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun LinuxUpdateStatTile(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            tint.copy(alpha = 0.18f).compositeOver(MaterialTheme.colorScheme.surface)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
        },
        border = if (selected) BorderStroke(1.dp, tint.copy(alpha = 0.45f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = 0.16f),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                }
            }
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LinuxUpdateSystemCard(
    system: LinuxUpdateSystem,
    accent: Color,
    onClick: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val statusColor = systemStatusColor(system)
    val statusText = systemStatusLabel(system)

    val borderColor = if (system.hasCheckIssue || system.isReachableFlag == false) {
        MaterialTheme.colorScheme.error.copy(alpha = if (isDarkTheme) 0.55f else 0.28f)
    } else {
        linuxUpdateBorderColor(isDarkTheme, accent)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = linuxUpdateCardColor(isDarkTheme, accent),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = system.name.ifBlank { system.hostname.ifBlank { "System" } },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (system.securityCount > 0) {
                    BadgeNumber(
                        value = system.securityCount,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                if (system.updateCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    BadgeNumber(
                        value = system.updateCount,
                        tint = Color(0xFFF59E0B)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.patchmon_open_details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = system.osSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricPill(label = stringResource(R.string.patchmon_security_chip), value = system.securityCount.toString())
                MetricPill(label = stringResource(R.string.linux_update_updates_total), value = system.updateCount.toString())
                MetricPill(label = stringResource(R.string.linux_update_detail_kept_back), value = system.keptBackCount.toString())
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(statusColor, CircleShape)
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                if (system.needsRebootFlag) {
                    Spacer(modifier = Modifier.width(8.dp))
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                stringResource(R.string.patchmon_reboot_badge),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (!system.cacheAge.isNullOrBlank()) {
                    Text(
                        text = system.cacheAge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            system.activeOperation?.let { operation ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = accent
                    )
                    Text(
                        text = operationLabel(operation.type),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun LinuxUpdateSystemDetailSheet(
    detailState: UiState<LinuxUpdateSystemDetail>,
    isRunningAction: Boolean,
    onRefresh: () -> Unit,
    onAction: (LinuxUpdateViewModel.SystemAction) -> Unit,
    onUpgradePackage: (String) -> Unit
) {
    when (val state = detailState) {
        UiState.Loading, UiState.Idle -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { state.retryAction?.invoke() ?: onRefresh() }) {
                    Text(stringResource(R.string.retry))
                }
            }
        }

        UiState.Offline -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.error_server_unreachable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onRefresh) {
                    Text(stringResource(R.string.retry))
                }
            }
        }

        is UiState.Success -> {
            LinuxUpdateSystemDetailContent(
                detail = state.data,
                isRunningAction = isRunningAction,
                onRefresh = onRefresh,
                onAction = onAction,
                onUpgradePackage = onUpgradePackage
            )
        }
    }
}

@Composable
private fun LinuxUpdateSystemDetailContent(
    detail: LinuxUpdateSystemDetail,
    isRunningAction: Boolean,
    onRefresh: () -> Unit,
    onAction: (LinuxUpdateViewModel.SystemAction) -> Unit,
    onUpgradePackage: (String) -> Unit
) {
    val system = detail.system

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 720.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = system.name.ifBlank { system.hostname.ifBlank { "System" } },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = system.osSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        item {
            OutlinedButton(onClick = onRefresh, enabled = !isRunningAction) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.refresh))
            }
        }

        item {
            LinuxUpdateActionButtons(
                supportsFullUpgrade = system.supportsFullUpgrade,
                isRunningAction = isRunningAction,
                onAction = onAction
            )
        }

        item {
            SectionTitle(title = stringResource(R.string.linux_update_detail_system_info))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DetailRow(stringResource(R.string.patchmon_detail_hostname), system.hostname)
                    DetailRow(stringResource(R.string.patchmon_architecture), system.arch ?: stringResource(R.string.not_available))
                    DetailRow(stringResource(R.string.linux_update_updates_total), system.updateCount.toString())
                    DetailRow(stringResource(R.string.patchmon_security_chip), system.securityCount.toString())
                    DetailRow(stringResource(R.string.linux_update_detail_kept_back), system.keptBackCount.toString())
                    DetailRow(stringResource(R.string.linux_update_detail_status), systemStatusLabel(system))
                    DetailRow(stringResource(R.string.linux_update_detail_cache_age), system.cacheAge ?: stringResource(R.string.not_available))
                    DetailRow(
                        stringResource(R.string.linux_update_detail_last_check),
                        system.lastCheck?.completedAt ?: system.lastCheck?.startedAt ?: stringResource(R.string.not_available)
                    )
                }
            }
        }

        item {
            SectionTitle(
                title = stringResource(R.string.linux_update_detail_updates),
                trailing = detail.updates.size.toString()
            )
        }

        if (detail.updates.isEmpty()) {
            item {
                PlaceholderCard(text = stringResource(R.string.linux_update_detail_no_updates))
            }
        } else {
            items(detail.updates, key = { it.id }) { update ->
                LinuxUpdatePackageCard(
                    item = update,
                    isRunningAction = isRunningAction,
                    upgradeLabel = stringResource(R.string.linux_update_action_upgrade_package),
                    onUpgradePackage = { onUpgradePackage(update.packageName) }
                )
            }
        }

        item {
            SectionTitle(
                title = stringResource(R.string.linux_update_detail_hidden_updates),
                trailing = detail.hiddenUpdates.size.toString()
            )
        }

        if (detail.hiddenUpdates.isEmpty()) {
            item {
                PlaceholderCard(text = stringResource(R.string.linux_update_detail_no_hidden_updates))
            }
        } else {
            items(detail.hiddenUpdates, key = { it.id }) { update ->
                LinuxUpdatePackageCard(
                    item = update,
                    isRunningAction = true,
                    upgradeLabel = stringResource(R.string.linux_update_detail_hidden_tag),
                    onUpgradePackage = {}
                )
            }
        }

        item {
            SectionTitle(
                title = stringResource(R.string.linux_update_detail_history),
                trailing = detail.history.size.toString()
            )
        }

        if (detail.history.isEmpty()) {
            item {
                PlaceholderCard(text = stringResource(R.string.linux_update_detail_no_history))
            }
        } else {
            items(detail.history, key = { it.id }) { entry ->
                LinuxUpdateHistoryCard(entry = entry)
            }
        }
    }
}

@Composable
private fun LinuxUpdateActionButtons(
    supportsFullUpgrade: Boolean,
    isRunningAction: Boolean,
    onAction: (LinuxUpdateViewModel.SystemAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onAction(LinuxUpdateViewModel.SystemAction.CHECK) },
                enabled = !isRunningAction,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.linux_update_action_check), maxLines = 1)
            }

            OutlinedButton(
                onClick = { onAction(LinuxUpdateViewModel.SystemAction.UPGRADE_ALL) },
                enabled = !isRunningAction,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Update, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.linux_update_action_upgrade), maxLines = 1)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (supportsFullUpgrade) {
                OutlinedButton(
                    onClick = { onAction(LinuxUpdateViewModel.SystemAction.FULL_UPGRADE) },
                    enabled = !isRunningAction,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.linux_update_action_full_upgrade), maxLines = 1)
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            OutlinedButton(
                onClick = { onAction(LinuxUpdateViewModel.SystemAction.REBOOT) },
                enabled = !isRunningAction,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.linux_update_action_reboot), maxLines = 1)
            }
        }
    }
}

@Composable
private fun LinuxUpdatePackageCard(
    item: LinuxUpdatePackageUpdate,
    isRunningAction: Boolean,
    upgradeLabel: String,
    onUpgradePackage: () -> Unit
) {
    val badgeColor = if (item.isSecurityFlag) MaterialTheme.colorScheme.error else Color(0xFFF59E0B)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.packageName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (item.isSecurityFlag) {
                    BadgeNumber(value = 1, tint = MaterialTheme.colorScheme.error)
                }
                if (item.isKeptBackFlag) {
                    Spacer(modifier = Modifier.width(6.dp))
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(stringResource(R.string.linux_update_detail_kept_back), maxLines = 1) }
                    )
                }
            }

            Text(
                text = "${item.currentVersion ?: "-"} -> ${item.newVersion ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.pkgManager.ifBlank { "system" },
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.weight(1f))

                OutlinedButton(
                    onClick = onUpgradePackage,
                    enabled = !isRunningAction && upgradeLabel != stringResource(R.string.linux_update_detail_hidden_tag)
                ) {
                    Text(upgradeLabel)
                }
            }
        }
    }
}

@Composable
private fun LinuxUpdateHistoryCard(entry: LinuxUpdateHistoryEntry) {
    val statusColor = when (entry.status.lowercase()) {
        "success", "done" -> Color(0xFF16A34A)
        "warning" -> Color(0xFFF59E0B)
        "failed", "error" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val headline = when (entry.action.lowercase()) {
        "check" -> stringResource(R.string.linux_update_action_check)
        "upgrade_all" -> stringResource(R.string.linux_update_action_upgrade)
        "full_upgrade_all" -> stringResource(R.string.linux_update_action_full_upgrade)
        "upgrade_package" -> stringResource(R.string.linux_update_action_upgrade_package)
        "reboot" -> stringResource(R.string.linux_update_action_reboot)
        else -> entry.action.replace('_', ' ').replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase() else char.toString()
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = entry.status.ifBlank { "-" },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            entry.completedAt?.let { completedAt ->
                Text(
                    text = completedAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val body = entry.error?.takeIf { it.isNotBlank() }
                ?: entry.output?.takeIf { it.isNotBlank() }
            if (body != null) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.55f)
        )
    }
}

@Composable
private fun PlaceholderCard(text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BadgeNumber(value: Int, tint: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = tint.copy(alpha = 0.15f)
    ) {
        Text(
            text = value.toString(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LinuxUpdateEmptyState(isFiltered: Boolean) {
    val icon = if (isFiltered) Icons.Default.Warning else Icons.Default.WifiOff
    val text = if (isFiltered) {
        stringResource(R.string.linux_update_no_systems_filter)
    } else {
        stringResource(R.string.linux_update_no_systems)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun operationLabel(type: String): String {
    return when (type.lowercase()) {
        "check" -> stringResource(R.string.home_verifying)
        "upgrade_all", "full_upgrade_all", "upgrade_package" -> stringResource(R.string.linux_update_updates_total)
        "reboot" -> stringResource(R.string.patchmon_reboot_required)
        else -> type.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

@Composable
private fun systemStatusLabel(system: LinuxUpdateSystem): String {
    return when {
        system.hasCheckIssue -> stringResource(R.string.linux_update_status_issue)
        system.isReachableFlag == false -> stringResource(R.string.linux_update_status_unreachable)
        system.updateCount > 0 -> stringResource(R.string.linux_update_status_updates)
        else -> stringResource(R.string.linux_update_status_up_to_date)
    }
}

@Composable
private fun systemStatusColor(system: LinuxUpdateSystem): Color {
    return when {
        system.hasCheckIssue -> MaterialTheme.colorScheme.error
        system.isReachableFlag == false -> MaterialTheme.colorScheme.error
        system.updateCount > 0 -> Color(0xFFF59E0B)
        else -> Color(0xFF16A34A)
    }
}
