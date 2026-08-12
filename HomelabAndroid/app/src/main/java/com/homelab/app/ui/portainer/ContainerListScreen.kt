package com.homelab.app.ui.portainer

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.portainer.ContainerAction
import com.homelab.app.data.remote.dto.portainer.PortainerContainer
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import java.text.SimpleDateFormat
import java.util.*

@Composable
private fun portainerListCardColor(
    accent: Color? = null,
    tint: Float = 0.08f
): Color {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val base = if (isDarkTheme) Color(0xFF121C2A) else Color(0xFFF4F4F1)
    return accent?.let { lerp(base, it, tint.coerceIn(0f, 0.22f)) } ?: base
}

@Composable
private fun portainerListRaisedColor(
    accent: Color? = null,
    tint: Float = 0.06f
): Color {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val base = if (isDarkTheme) Color(0xFF1A2638) else Color(0xFFF9F9F7)
    return accent?.let { lerp(base, it, tint.coerceIn(0f, 0.18f)) } ?: base
}

@Composable
private fun portainerListBorderColor(
    accent: Color? = null,
    tint: Float = 0.16f
): Color {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val base = if (isDarkTheme) Color(0xFF34465F) else Color(0xFFD4D6D1)
    return accent?.let { lerp(base, it, tint.coerceIn(0f, 0.18f)) } ?: base
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (endpointId: Int, containerId: String) -> Unit,
    viewModel: ContainerListViewModel = hiltViewModel()
) {
    val containers by viewModel.filteredContainers.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val actionInProgress by viewModel.actionInProgress.collectAsStateWithLifecycle()
    val containerStats by viewModel.containerStats.collectAsStateWithLifecycle()
    
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val snackbarHostState = remember { SnackbarHostState() }
    var pendingAction by remember { mutableStateOf<Pair<String, ContainerAction>?>(null) }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    pendingAction?.let { (containerId, action) ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(stringResource(R.string.portainer_confirm_action, action.displayName)) },
            text = { Text(stringResource(R.string.portainer_confirm_action_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingAction = null
                    viewModel.performAction(containerId, action, confirmed = true)
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.portainer_containers), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text(stringResource(R.string.portainer_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.portainer_search_hint), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = portainerListRaisedColor(),
                    focusedContainerColor = portainerListRaisedColor(),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = ServiceType.PORTAINER.primaryColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(ContainerFilter.ALL, ContainerFilter.RUNNING, ContainerFilter.STOPPED)
                items(filters) { f ->
                    val isSelected = filter == f
                    val label = when (f) {
                        ContainerFilter.ALL -> stringResource(R.string.all)
                        ContainerFilter.RUNNING -> stringResource(R.string.portainer_running)
                        ContainerFilter.STOPPED -> stringResource(R.string.portainer_stopped)
                    }
                    val count = counts[f] ?: 0
                    
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) ServiceType.PORTAINER.primaryColor.copy(alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.45f) 0.14f else 0.08f) else portainerListRaisedColor(),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, ServiceType.PORTAINER.primaryColor.copy(alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.45f) 0.34f else 0.22f)) else androidx.compose.foundation.BorderStroke(1.dp, portainerListBorderColor().copy(alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.45f) 0.72f else 0.52f)),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            viewModel.setFilter(f)
                        }
                    ) {
                        Text(
                            text = "$label ($count)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = if (isSelected) ServiceType.PORTAINER.primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ServiceType.PORTAINER.primaryColor)
                }
            } else if (containers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.portainer_no_containers), modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.portainer_no_containers), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(containers, key = { it.id }) { container ->
                        LaunchedEffect(container.id) {
                            viewModel.fetchContainerStats(container.id)
                        }
                        ContainerRowCard(
                            container = container,
                            stats = containerStats[container.id],
                            actionInProgress = actionInProgress == container.id,
                            onAction = { action ->
                                if (action.requiresConfirmation) pendingAction = container.id to action
                                else viewModel.performAction(container.id, action)
                            },
                            onClick = { onNavigateToDetail(viewModel.endpointId, container.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContainerRowCard(
    container: PortainerContainer,
    stats: com.homelab.app.data.remote.dto.portainer.ContainerStats?,
    actionInProgress: Boolean,
    onAction: (ContainerAction) -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "BouncyCard"
    )
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val stateColor = when (container.state.lowercase()) {
        "running" -> Color(0xFF4CAF50)
        "paused" -> Color(0xFFFFB74D)
        "exited", "dead" -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource, 
                indication = null, 
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onClick()
                }
            ),
        shape = RoundedCornerShape(16.dp),
        color = portainerListCardColor(
            accent = stateColor,
            tint = if (stateColor == MaterialTheme.colorScheme.onSurfaceVariant) 0.04f else if (MaterialTheme.colorScheme.background.luminance() < 0.45f) 0.10f else 0.06f
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            portainerListBorderColor(
                accent = stateColor,
                tint = if (MaterialTheme.colorScheme.background.luminance() < 0.45f) 0.22f else 0.14f
            ).copy(alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.45f) 0.78f else 0.62f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusDot(status = container.state)
                Text(
                    text = container.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (actionInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        when (container.state) {
                            "running" -> {
                                QuickActionIcon(
                                    icon = Icons.Default.Stop,
                                    color = Color(0xFFF44336),
                                    onClick = { onAction(ContainerAction.stop) },
                                    contentDescription = stringResource(R.string.portainer_stop)
                                )
                                QuickActionIcon(
                                    icon = Icons.Default.Pause,
                                    color = Color(0xFF2196F3),
                                    onClick = { onAction(ContainerAction.pause) },
                                    contentDescription = stringResource(R.string.portainer_pause)
                                )
                                QuickActionIcon(
                                    icon = Icons.Default.Refresh,
                                    color = Color(0xFFFF9800),
                                    onClick = { onAction(ContainerAction.restart) },
                                    contentDescription = stringResource(R.string.portainer_restart)
                                )
                            }
                            "paused" -> {
                                QuickActionIcon(
                                    icon = Icons.Default.PlayArrow,
                                    color = Color(0xFF4CAF50),
                                    onClick = { onAction(ContainerAction.unpause) },
                                    contentDescription = stringResource(R.string.portainer_resume)
                                )
                                QuickActionIcon(
                                    icon = Icons.Default.Stop,
                                    color = Color(0xFFF44336),
                                    onClick = { onAction(ContainerAction.stop) },
                                    contentDescription = stringResource(R.string.portainer_stop)
                                )
                            }
                            else -> {
                                QuickActionIcon(
                                    icon = Icons.Default.PlayArrow,
                                    color = Color(0xFF4CAF50),
                                    onClick = { onAction(ContainerAction.start) },
                                    contentDescription = stringResource(R.string.portainer_start)
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Text(
                text = container.image,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = container.status.ifBlank { stringResource(R.string.not_available) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatUnixDateTime(container.created),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Ports
            if (container.ports.isNotEmpty()) {
                val visiblePorts = container.ports.filter { it.publicPort != null }.take(3)
                if (visiblePorts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        visiblePorts.forEach { port ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = portainerListRaisedColor(accent = Color(0xFF2196F3), tint = if (MaterialTheme.colorScheme.background.luminance() < 0.45f) 0.12f else 0.08f)
                            ) {
                                Text(
                                    text = "${port.publicPort}:${port.privatePort}/${port.type}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF2196F3),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val cpuPercent = stats?.let { calculateCpuPercent(it) }
            val memUsage = stats?.let { calculateMemUsage(it) }
            val netBytes = stats?.let { calculateNetworkBytes(it) }
            val pids = stats?.pids_stats?.current

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStatChip(
                    icon = Icons.Default.Memory,
                    label = stringResource(R.string.portainer_cpu_short),
                    value = cpuPercent?.let { String.format("%.1f%%", it) } ?: stringResource(R.string.not_available),
                    color = Color(0xFF64B5F6),
                    modifier = Modifier.weight(1f)
                )
                MiniStatChip(
                    icon = Icons.Default.Storage,
                    label = stringResource(R.string.portainer_mem_short),
                    value = memUsage?.let { formatIosBytes(it) } ?: stringResource(R.string.not_available),
                    color = Color(0xFFFFB74D),
                    modifier = Modifier.weight(1f)
                )
                MiniStatChip(
                    icon = Icons.Default.Public,
                    label = stringResource(R.string.portainer_net_short),
                    value = netBytes?.let { formatIosBytes(it) } ?: stringResource(R.string.not_available),
                    color = Color(0xFF81C784),
                    modifier = Modifier.weight(1f)
                )
                MiniStatChip(
                    icon = Icons.Default.FormatListNumbered,
                    label = stringResource(R.string.portainer_pids_short),
                    value = pids?.takeIf { it > 0 }?.toString() ?: stringResource(R.string.not_available),
                    color = Color(0xFFB0BEC5),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatusDot(status: String) {
    val color = when (status) {
        "running" -> Color(0xFF4CAF50)
        "paused" -> Color(0xFFFFB74D)
        "exited", "dead" -> Color(0xFFF44336)
        else -> Color.Gray
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun QuickActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    contentDescription: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "BouncyAction"
    )
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = color,
            modifier = Modifier.padding(6.dp).size(16.dp)
        )
    }
}

@Composable
private fun MiniStatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = portainerListRaisedColor(accent = color, tint = if (MaterialTheme.colorScheme.background.luminance() < 0.45f) 0.12f else 0.08f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(14.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }
    }
}

private fun formatIosBytes(bytes: Double): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val format = when {
        unitIndex == 0 -> "%.0f %s"
        value >= 100 -> "%.0f %s"
        value >= 10 -> "%.1f %s"
        else -> "%.1f %s"
    }
    return String.format(format, value, units[unitIndex])
}

private fun calculateCpuPercent(stats: com.homelab.app.data.remote.dto.portainer.ContainerStats): Double {
    val cpuDelta = stats.cpu_stats.cpu_usage.total_usage - stats.precpu_stats.cpu_usage.total_usage
    val systemDelta = (stats.cpu_stats.system_cpu_usage ?: 0) - (stats.precpu_stats.system_cpu_usage ?: 0)
    if (systemDelta <= 0 || cpuDelta <= 0) return 0.0
    return (cpuDelta.toDouble() / systemDelta.toDouble()) * (stats.cpu_stats.online_cpus ?: 1) * 100.0
}

private fun calculateMemUsage(stats: com.homelab.app.data.remote.dto.portainer.ContainerStats): Double {
    val cache = stats.memory_stats.stats?.cache ?: 0
    val usage = stats.memory_stats.usage - cache
    return usage.toDouble().coerceAtLeast(0.0)
}

private fun calculateNetworkBytes(stats: com.homelab.app.data.remote.dto.portainer.ContainerStats): Double {
    val networks = stats.networks ?: return 0.0
    val total = networks.values.sumOf { it.rx_bytes + it.tx_bytes }
    return total.toDouble()
}

private fun formatUnixDateTime(unixTime: Long): String {
    val date = java.util.Date(unixTime * 1000)
    val formatter = java.text.SimpleDateFormat("dd/MM/yy, HH:mm", java.util.Locale.getDefault())
    return formatter.format(date)
}
// --- Local formatters ---
