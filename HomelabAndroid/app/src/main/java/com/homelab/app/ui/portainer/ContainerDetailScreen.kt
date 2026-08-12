package com.homelab.app.ui.portainer

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.homelab.app.R
import com.homelab.app.data.remote.dto.portainer.*
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.ui.theme.StatusOrange
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.ResourceFormatters
import java.time.Instant

@Composable
private fun portainerDetailCardColor(
    accent: Color? = null,
    tint: Float = 0.08f
): Color {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val base = if (isDarkTheme) Color(0xFF121C2A) else Color(0xFFF4F4F1)
    return accent?.let { lerp(base, it, tint.coerceIn(0f, 0.22f)) } ?: base
}

@Composable
private fun portainerDetailRaisedColor(
    accent: Color? = null,
    tint: Float = 0.06f
): Color {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val base = if (isDarkTheme) Color(0xFF1A2638) else Color(0xFFF9F9F7)
    return accent?.let { lerp(base, it, tint.coerceIn(0f, 0.18f)) } ?: base
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: ContainerDetailViewModel = hiltViewModel()
) {
    val container by viewModel.container.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val composeFile by viewModel.composeFile.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val tabs = remember(composeFile) {
        val list = mutableListOf<Int>(R.string.portainer_info_tab, R.string.portainer_stats, R.string.portainer_logs, R.string.portainer_env)
        if (composeFile != null) list.add(R.string.portainer_compose)
        list
    }
    
    var selectedTabIndex by remember(tabs) { mutableIntStateOf(0) }
    var pendingAction by remember { mutableStateOf<ContainerAction?>(null) }

    LaunchedEffect(selectedTabIndex, tabs) {
        val tabResId = tabs.getOrNull(selectedTabIndex)
        when (tabResId) {
            R.string.portainer_stats -> viewModel.fetchStats()
            R.string.portainer_logs -> viewModel.fetchLogs()
        }
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(stringResource(R.string.portainer_confirm_action, action.displayName)) },
            text = { Text(stringResource(R.string.portainer_confirm_action_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingAction = null
                    viewModel.executeAction(action, confirmed = true)
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(container?.displayName ?: stringResource(R.string.portainer_details), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (isLoading && container == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ServiceType.PORTAINER.primaryColor)
            }
        } else if (error != null && container == null) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = error ?: stringResource(R.string.error_unknown),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                container?.let { detail ->
                    ContainerHeaderCard(
                        detail = detail,
                        onAction = { action ->
                            if (action.requiresConfirmation) pendingAction = action
                            else viewModel.executeAction(action)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Tabs + Content card
                val haptic = LocalHapticFeedback.current
                val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
                val shellAccent = if (isDarkTheme) ServiceType.PORTAINER.primaryColor else null
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = portainerDetailCardColor(accent = shellAccent, tint = if (isDarkTheme) 0.09f else 0f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = portainerDetailRaisedColor(accent = shellAccent, tint = if (isDarkTheme) 0.06f else 0f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                            ) {
                                tabs.forEachIndexed { index, resId ->
                                    val selected = selectedTabIndex == index
                                    val textColor = if (selected) ServiceType.PORTAINER.primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(if (selected) ServiceType.PORTAINER.primaryColor.copy(alpha = 0.12f) else Color.Transparent)
                                            .clickable {
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                selectedTabIndex = index
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(resId),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            color = textColor
                                        )
                                    }
                                    if (index < tabs.lastIndex) {
                                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val tabResId = tabs.getOrNull(selectedTabIndex)
                            when (tabResId) {
                                R.string.portainer_info_tab -> InfoTabContent(detail = container)
                                R.string.portainer_stats -> StatsTabContent(stats = stats)
                                R.string.portainer_logs -> LogsTabContent(logs = logs)
                                R.string.portainer_env -> EnvTabContent(detail = container)
                                R.string.portainer_compose -> ComposeTabContent(composeFile = composeFile)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoTabContent(
    detail: com.homelab.app.data.remote.dto.portainer.ContainerDetail?
) {
    if (detail == null) return

    InfoSection(
        title = stringResource(R.string.portainer_section_container),
        icon = Icons.Default.Dns
    ) {
        InfoRow(stringResource(R.string.portainer_id_label), detail.id.ifBlank { stringResource(R.string.not_available) })
        InfoRow(stringResource(R.string.portainer_created), detail.created.takeIf { it.isNotBlank() }?.take(10) ?: stringResource(R.string.not_available))
        InfoRow(stringResource(R.string.portainer_hostname_label), detail.config.hostname.ifBlank { stringResource(R.string.not_available) })
        InfoRow(stringResource(R.string.portainer_workdir_label), detail.config.workingDir ?: stringResource(R.string.not_available))
        InfoRow(stringResource(R.string.portainer_image), detail.config.image.ifBlank { stringResource(R.string.not_available) })
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    InfoSection(
        title = stringResource(R.string.portainer_section_network),
        icon = Icons.Default.Public
    ) {
        InfoRow(stringResource(R.string.portainer_network_mode_label), detail.hostConfig.networkMode.ifBlank { stringResource(R.string.not_available) })
        val networks = detail.networkSettings.networks
        if (networks.isEmpty()) {
            InfoRow(stringResource(R.string.portainer_network_ip), stringResource(R.string.not_available))
        } else {
            networks.forEach { (name, net) ->
                InfoRow(name, net.ipAddress.ifBlank { stringResource(R.string.not_available) })
            }
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    InfoSection(
        title = stringResource(R.string.portainer_section_volumes),
        icon = Icons.Default.Storage
    ) {
        if (detail.mounts.isEmpty()) {
            Text(
                text = stringResource(R.string.portainer_volumes_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            detail.mounts.forEach { mount ->
                VolumeChip(mount = mount)
            }
        }
    }
}

@Composable
private fun ContainerHeaderCard(
    detail: com.homelab.app.data.remote.dto.portainer.ContainerDetail,
    onAction: (ContainerAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusLabel = when {
        detail.state.paused -> stringResource(R.string.portainer_paused)
        detail.state.running -> stringResource(R.string.portainer_running)
        else -> stringResource(R.string.portainer_stopped)
    }
    val uptimeSeconds = calculateUptimeSeconds(detail.state.startedAt)
    val uptimeLabel = uptimeSeconds?.let {
        ResourceFormatters.formatUptimeHours(it.toDouble(), androidx.compose.ui.platform.LocalContext.current)
    } ?: detail.state.status.ifBlank { stringResource(R.string.not_available) }
    val statusColor = when {
        detail.state.paused -> Color(0xFFFFB74D)
        detail.state.running -> Color(0xFF4CAF50)
        else -> Color(0xFFF44336)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = portainerDetailCardColor(accent = statusColor, tint = if (MaterialTheme.colorScheme.background.luminance() < 0.45f) 0.10f else 0.06f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.45f) 0.68f else 0.52f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = detail.displayName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                val pillStatus = if (detail.state.paused) "paused" else detail.state.status
                StatusPill(label = statusLabel, status = pillStatus)
            }

            Text(
                text = detail.config.image.ifBlank { stringResource(R.string.not_available) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "${stringResource(R.string.beszel_uptime)}: $uptimeLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val actions = buildList {
                if (detail.state.running && !detail.state.paused) {
                    add(ActionSegment(stringResource(R.string.portainer_stop), Icons.Default.Stop, StatusRed) { onAction(ContainerAction.stop) })
                    add(ActionSegment(stringResource(R.string.portainer_restart), Icons.Default.Refresh, StatusOrange) { onAction(ContainerAction.restart) })
                    add(ActionSegment(stringResource(R.string.portainer_pause), Icons.Default.Pause, Color(0xFF64B5F6)) { onAction(ContainerAction.pause) })
                } else if (detail.state.paused) {
                    add(ActionSegment(stringResource(R.string.portainer_resume), Icons.Default.PlayArrow, StatusGreen) { onAction(ContainerAction.unpause) })
                    add(ActionSegment(stringResource(R.string.portainer_stop), Icons.Default.Stop, StatusRed) { onAction(ContainerAction.stop) })
                } else {
                    add(ActionSegment(stringResource(R.string.portainer_start), Icons.Default.PlayArrow, StatusGreen) { onAction(ContainerAction.start) })
                }
            }
            ActionSegmentGroup(actions = actions)
        }
    }
}

@Composable
private fun StatusPill(label: String, status: String) {
    val color = when (status.lowercase()) {
        "running" -> Color(0xFF4CAF50)
        "paused" -> Color(0xFFFFB74D)
        "exited", "dead" -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private data class ActionSegment(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
private fun ActionSegmentGroup(actions: List<ActionSegment>) {
    if (actions.isEmpty()) return
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val shellAccent = if (isDarkTheme) ServiceType.PORTAINER.primaryColor else null
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = portainerDetailRaisedColor(accent = shellAccent, tint = if (isDarkTheme) 0.06f else 0f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
        ) {
            actions.forEachIndexed { index, action ->
                val isLast = index == actions.lastIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(action.color.copy(alpha = 0.12f))
                        .clickable { action.onClick() }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(action.icon, contentDescription = action.label, tint = action.color, modifier = Modifier.size(16.dp))
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                            color = action.color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                }
                if (!isLast) {
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun InfoSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
            Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
        content()
    }
}

@Composable
private fun VolumeChip(mount: com.homelab.app.data.remote.dto.portainer.ContainerMount) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val chipAccent = if (isDarkTheme) MaterialTheme.colorScheme.primary else null
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = portainerDetailRaisedColor(accent = chipAccent, tint = if (isDarkTheme) 0.10f else 0f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = portainerDetailRaisedColor(accent = chipAccent, tint = if (isDarkTheme) 0.12f else 0f)
                ) {
                    Text(
                        text = mount.type,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = mount.destination,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            if (mount.source.isNotBlank()) {
                Text(
                    text = mount.source,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatsTabContent(stats: com.homelab.app.data.remote.dto.portainer.ContainerStats?) {
    if (stats == null) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ServiceType.PORTAINER.primaryColor)
        }
        return
    }

    // CPU Calculation (simplified)
    val cpuDelta = stats.cpu_stats.cpu_usage.total_usage - stats.precpu_stats.cpu_usage.total_usage
    val systemDelta = (stats.cpu_stats.system_cpu_usage ?: 0) - (stats.precpu_stats.system_cpu_usage ?: 0)
    var cpuPercent = 0.0
    if (systemDelta > 0.0 && cpuDelta > 0.0) {
        cpuPercent = (cpuDelta.toDouble() / systemDelta.toDouble()) * (stats.cpu_stats.online_cpus ?: 1) * 100.0
    }

    // Mem Calculation (simplified)
    val memUsage = stats.memory_stats.usage - (stats.memory_stats.stats?.cache ?: 0)
    val memLimit = stats.memory_stats.limit
    val memPercent = if (memLimit > 0) (memUsage.toDouble() / memLimit.toDouble()) * 100 else 0.0
    val context = androidx.compose.ui.platform.LocalContext.current
    val memoryLabel = if (memLimit > 0) {
        "${com.homelab.app.util.ResourceFormatters.formatBytes(memUsage.toDouble(), context)} / ${com.homelab.app.util.ResourceFormatters.formatBytes(memLimit.toDouble(), context)}"
    } else {
        stringResource(R.string.not_available)
    }

    val totalRx = stats.networks?.values?.sumOf { it.rx_bytes } ?: 0L
    val totalTx = stats.networks?.values?.sumOf { it.tx_bytes } ?: 0L
    val totalNet = totalRx + totalTx

    val cpuProgress = (cpuPercent / 100.0).coerceIn(0.0, 1.0).toFloat()
    val memProgress = (memPercent / 100.0).coerceIn(0.0, 1.0).toFloat()
    val memPercentLabel = if (memLimit > 0) String.format("%.2f%%", memPercent) else stringResource(R.string.not_available)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = portainerDetailRaisedColor(accent = Color(0xFF64B5F6), tint = if (MaterialTheme.colorScheme.background.luminance() < 0.45f) 0.10f else 0.06f)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.portainer_cpu_usage),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = String.format("%.2f%%", cpuPercent),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ServiceType.PORTAINER.primaryColor
                )
                LinearProgressIndicator(
                    progress = { cpuProgress },
                    color = ServiceType.PORTAINER.primaryColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = portainerDetailRaisedColor(accent = Color(0xFFFFB74D), tint = if (MaterialTheme.colorScheme.background.luminance() < 0.45f) 0.10f else 0.06f)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.beszel_memory),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = memoryLabel,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                LinearProgressIndicator(
                    progress = { memProgress },
                    color = Color(0xFFFFB74D),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                )
                Text(
                    text = stringResource(R.string.portainer_memory_percent) + ": " + memPercentLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (stats.networks != null && stats.networks.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = portainerDetailRaisedColor(accent = Color(0xFF81C784), tint = if (MaterialTheme.colorScheme.background.luminance() < 0.45f) 0.10f else 0.06f)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.portainer_section_network),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    InfoRow(
                        label = stringResource(R.string.beszel_download),
                        value = com.homelab.app.util.ResourceFormatters.formatBytes(totalRx.toDouble(), context)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow(
                        label = stringResource(R.string.beszel_upload),
                        value = com.homelab.app.util.ResourceFormatters.formatBytes(totalTx.toDouble(), context)
                    )
                    if (totalNet > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        InfoRow(
                            label = stringResource(R.string.beszel_network_io),
                            value = com.homelab.app.util.ResourceFormatters.formatBytes(totalNet.toDouble(), context)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvTabContent(detail: com.homelab.app.data.remote.dto.portainer.ContainerDetail?) {
    val env = detail?.config?.env.orEmpty().filter { it.isNotBlank() }
    if (env.isEmpty()) {
        Text(
            text = stringResource(R.string.portainer_env_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        env.forEach { entry ->
            val parts = entry.split("=", limit = 2)
            val key = parts.firstOrNull().orEmpty()
            val value = parts.getOrNull(1).orEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = key,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (value.isNotBlank()) value else stringResource(R.string.not_available),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun LogsTabContent(logs: String?) {
    if (logs == null) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ServiceType.PORTAINER.primaryColor)
        }
        return
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1E1E), // Dark terminal background
        modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 500.dp)
    ) {
        SelectionContainer {
            Text(
                text = logs.takeLast(5000), // Prevent massive text layout lag
                color = Color(0xFF00FF00), // Terminal green
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun ComposeTabContent(composeFile: String?) {
    if (composeFile == null) return

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1E1E), // Dark editor background
        modifier = Modifier.fillMaxWidth().heightIn(min = 400.dp)
    ) {
        SelectionContainer {
            Text(
                text = composeFile,
                color = Color(0xFFF8F8F2), // Light yaml text
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

private fun calculateUptimeSeconds(startedAt: String?): Long? {
    if (startedAt.isNullOrBlank()) return null
    return runCatching {
        val started = Instant.parse(startedAt)
        val now = Instant.now()
        kotlin.math.max(0, now.epochSecond - started.epochSecond)
    }.getOrNull()
}
