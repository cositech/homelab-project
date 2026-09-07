package com.homelab.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.domain.action.ActionAuditRecord
import com.homelab.app.domain.action.ActionExecutionState
import com.homelab.app.domain.action.DurableActionQueueEntry
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.domain.model.Tenant
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.util.ServiceType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Read-only Phase 3/4 consumer for the controlled-action audit ledger and durable queue: a
 * "History" tab (every recorded action, most recent first) and a "Pending" tab (queued, awaiting
 * retry, or flagged for manual review), both scoped to the active tenant by [ActionHistoryViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: ActionHistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tenantSelection by viewModel.tenantSelection.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val formatter = rememberActionHistoryFormatter()
    // Only in all-tenants mode, and only when more than one tenant is actually present among the
    // records on screen right now - unlike Health/Alerts/Assets, ActionAuditRecord/
    // ControlledActionRequest already carry tenantRef directly, so no instanceId indirection is
    // needed here, just a tenantRef -> display-name lookup.
    val defaultTenantLabel = stringResource(R.string.home_default_badge)
    val tenantLabelByTenantRef = remember(tenantSelection, uiState.auditRecords, uiState.pendingEntries, defaultTenantLabel) {
        val distinctTenantRefs = uiState.auditRecords.map { it.tenantRef }.toSet() +
            uiState.pendingEntries.map { Tenant.refOrDefault(it.request.tenantRef) }.toSet()
        if (!tenantSelection.allTenantsMode || distinctTenantRefs.size <= 1) {
            emptyMap()
        } else {
            val tenantById = tenantSelection.tenants.associateBy { it.id }
            distinctTenantRefs.associateWith { tenantRef ->
                tenantById[tenantRef]?.let { if (it.isDefault) defaultTenantLabel else it.name } ?: tenantRef
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_action_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(stringResource(R.string.action_history_tab_history), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(stringResource(R.string.action_history_tab_pending), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (uiState.isRefreshing) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                }
            } else if (selectedTab == 0) {
                if (uiState.auditRecords.isEmpty()) {
                    ActionHistoryEmptyState(stringResource(R.string.action_history_empty_history))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 0.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.auditRecords, key = { it.auditId }) { record ->
                            AuditRecordRow(record, uiState.instancesById, formatter, tenantLabelByTenantRef[record.tenantRef])
                        }
                    }
                }
            } else {
                if (uiState.pendingEntries.isEmpty()) {
                    ActionHistoryEmptyState(stringResource(R.string.action_history_empty_pending))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 0.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.pendingEntries, key = { it.request.idempotencyKey }) { entry ->
                            PendingEntryRow(entry, uiState.instancesById, formatter, tenantLabelByTenantRef[Tenant.refOrDefault(entry.request.tenantRef)])
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionHistoryEmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun instanceLabelFor(providerRef: String, instancesById: Map<String, ServiceInstance>): Pair<String, ServiceType?> {
    val instanceId = providerRef.substringAfter(':', "").trim()
    val instance = instancesById[instanceId]
    return (instance?.label ?: providerRef) to instance?.type
}

private fun stateColor(state: ActionExecutionState): androidx.compose.ui.graphics.Color = when (state) {
    ActionExecutionState.SUCCEEDED -> androidx.compose.ui.graphics.Color(0xFF43A047)
    ActionExecutionState.FAILED, ActionExecutionState.REJECTED -> androidx.compose.ui.graphics.Color(0xFFE53935)
    ActionExecutionState.MANUAL_REVIEW -> androidx.compose.ui.graphics.Color(0xFFFB8C00)
    ActionExecutionState.RETRY_WAIT, ActionExecutionState.QUEUED, ActionExecutionState.EXECUTING -> androidx.compose.ui.graphics.Color(0xFF1E88E5)
    ActionExecutionState.CANCELLED -> androidx.compose.ui.graphics.Color(0xFF9E9E9E)
    ActionExecutionState.DRY_RUN -> androidx.compose.ui.graphics.Color(0xFF8E24AA)
}

@Composable
private fun AuditRecordRow(
    record: ActionAuditRecord,
    instancesById: Map<String, ServiceInstance>,
    formatter: DateTimeFormatter,
    tenantLabel: String? = null
) {
    val (label, type) = instanceLabelFor(record.providerRef, instancesById)
    ActionHistoryRowCard(stateColor = stateColor(record.state)) {
        RowHeader(label = label, type = type, action = record.action, risk = record.risk.name, tenantLabel = tenantLabel)
        Text(
            text = record.targetRef,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = record.state.name,
                style = MaterialTheme.typography.labelSmall,
                color = stateColor(record.state),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = formatter.format(Instant.ofEpochMilli(record.recordedAtEpochMillis)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (record.reasonCode.isNotBlank() && record.reasonCode != record.state.name.lowercase()) {
            Text(
                text = record.reasonCode,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun PendingEntryRow(
    entry: DurableActionQueueEntry,
    instancesById: Map<String, ServiceInstance>,
    formatter: DateTimeFormatter,
    tenantLabel: String? = null
) {
    val (label, type) = instanceLabelFor(entry.request.providerRef, instancesById)
    ActionHistoryRowCard(stateColor = stateColor(entry.state)) {
        RowHeader(label = label, type = type, action = entry.request.action, risk = entry.request.risk.name, tenantLabel = tenantLabel)
        Text(
            text = entry.request.targetRef,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = entry.state.name,
                style = MaterialTheme.typography.labelSmall,
                color = stateColor(entry.state),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.action_history_attempt_count, entry.attemptCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        entry.nextAttemptAtEpochMillis?.let { nextAttempt ->
            Text(
                text = stringResource(
                    R.string.action_history_next_attempt,
                    formatter.format(Instant.ofEpochMilli(nextAttempt))
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun RowHeader(label: String, type: ServiceType?, action: String, risk: String, tenantLabel: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (type != null) {
            ServiceIcon(type = type, size = 28.dp, cornerRadius = 8.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                tenantLabel?.let {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Text(
                text = action,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Text(
                text = risk,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ActionHistoryRowCard(
    stateColor: androidx.compose.ui.graphics.Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .background(stateColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content
            )
        }
    }
}

@Composable
private fun rememberActionHistoryFormatter(): DateTimeFormatter = remember {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
}
