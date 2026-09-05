package com.homelab.app.ui.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.domain.model.TenantSelection
import com.homelab.app.domain.provider.ProviderDiagnostic
import com.homelab.app.domain.provider.ProviderEvent
import com.homelab.app.domain.provider.ProviderHealth
import com.homelab.app.domain.provider.ProviderHealthState
import com.homelab.app.domain.provider.ProviderResource
import com.homelab.app.ui.settings.tenantDisplayName

private enum class OperationsSection(val label: String) {
    HEALTH("Health"),
    ALERTS("Alerts"),
    ASSETS("Assets"),
    SEARCH("Search"),
    DIAGNOSTICS("Diagnostics")
}

@Composable
fun OperationsScreen(viewModel: OperationsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tenantSelection by viewModel.tenantSelection.collectAsStateWithLifecycle()
    var selectedSection by remember { mutableIntStateOf(0) }
    val sections = remember { OperationsSection.entries }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Operations", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${state.snapshot.health.size} providers · ${state.snapshot.alerts.size} alerts · ${state.snapshot.assets.size} assets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TenantSwitcherChip(
                selection = tenantSelection,
                onSelectTenant = viewModel::setActiveTenant,
                onSetAllTenantsMode = viewModel::setAllTenantsMode
            )
            IconButton(onClick = viewModel::refresh, enabled = !state.isRefreshing) {
                if (state.isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh operations")
                }
            }
        }

        if (state.isRefreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        state.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        ScrollableTabRow(selectedTabIndex = selectedSection, edgePadding = 12.dp) {
            sections.forEachIndexed { index, section ->
                Tab(
                    selected = selectedSection == index,
                    onClick = { selectedSection = index },
                    text = { Text(section.label) }
                )
            }
        }

        when (sections[selectedSection]) {
            OperationsSection.HEALTH -> OperationsList(state.snapshot.health, "No provider health data") { HealthCard(it) }
            OperationsSection.ALERTS -> OperationsList(state.snapshot.alerts, "No active alerts") { AlertCard(it) }
            OperationsSection.ASSETS -> OperationsList(state.snapshot.assets, "No assets discovered") { AssetCard(it) }
            OperationsSection.DIAGNOSTICS -> OperationsList(state.snapshot.diagnostics, "No diagnostics available") { DiagnosticCard(it) }
            OperationsSection.SEARCH -> SearchSection(state.snapshot)
        }
    }
}

/**
 * Compact tenant-scope affordance for the global operations chrome. Hidden on a single-tenant
 * install (only the `default` tenant configured), same rule as [com.homelab.app.ui.components.TenantPicker].
 */
@Composable
private fun TenantSwitcherChip(
    selection: TenantSelection,
    onSelectTenant: (String) -> Unit,
    onSetAllTenantsMode: (Boolean) -> Unit
) {
    if (selection.isSingleTenant) return

    var expanded by remember { mutableStateOf(false) }
    val label = if (selection.allTenantsMode) {
        stringResource(R.string.tenants_all_mode_label)
    } else {
        tenantDisplayName(selection.activeTenant)
    }
    val contentDescription = stringResource(R.string.operations_tenant_switcher)

    Box {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            onClick = { expanded = true },
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Groups,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.tenants_all_mode_label),
                        fontWeight = if (selection.allTenantsMode) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = {
                    expanded = false
                    onSetAllTenantsMode(true)
                },
                trailingIcon = {
                    if (selection.allTenantsMode) Icon(Icons.Default.Check, contentDescription = null)
                }
            )
            HorizontalDivider()
            selection.tenants.forEach { tenant ->
                val isSelected = !selection.allTenantsMode && tenant.id == selection.activeTenantId
                DropdownMenuItem(
                    text = {
                        Text(
                            text = tenantDisplayName(tenant),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelectTenant(tenant.id)
                    },
                    trailingIcon = {
                        if (isSelected) Icon(Icons.Default.Check, contentDescription = null)
                    }
                )
            }
        }
    }
}

@Composable
private fun <T> OperationsList(values: List<T>, emptyText: String, content: @Composable (T) -> Unit) {
    if (values.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(values) { content(it) }
    }
}

@Composable
private fun SearchSection(snapshot: com.homelab.app.domain.provider.OperationsSnapshot) {
    var query by remember { mutableStateOf("") }
    val results = remember(snapshot, query) { snapshot.search(query) }
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search all operations data") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
        if (query.isBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Search providers, alerts, assets and diagnostics", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (results.isEmpty) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matching operations data", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(results.health) { HealthCard(it) }
                items(results.alerts) { AlertCard(it) }
                items(results.assets) { AssetCard(it) }
                items(results.diagnostics) { DiagnosticCard(it) }
            }
        }
    }
}

@Composable
private fun HealthCard(item: ProviderHealth) = OperationCard(
    title = item.providerId,
    subtitle = item.message ?: item.state.name.lowercase(),
    state = item.state,
    trailing = item.state.name.lowercase()
)

@Composable
private fun AlertCard(item: ProviderEvent) = OperationCard(
    title = item.message,
    subtitle = "${item.providerId} · ${item.resourceId ?: item.instanceId}",
    state = if (item.severity.equals("critical", true)) ProviderHealthState.UNAVAILABLE else ProviderHealthState.DEGRADED,
    trailing = item.severity.lowercase()
)

@Composable
private fun AssetCard(item: ProviderResource) = OperationCard(
    title = item.name,
    subtitle = "${item.providerId} · ${item.resourceType} · ${item.resourceId}",
    state = when (item.state?.lowercase()) {
        "offline", "down", "unavailable", "critical" -> ProviderHealthState.UNAVAILABLE
        "degraded", "pending", "paused", "warning", "maintenance" -> ProviderHealthState.DEGRADED
        "online", "up", "running", "healthy" -> ProviderHealthState.HEALTHY
        else -> ProviderHealthState.UNKNOWN
    },
    trailing = item.state ?: item.resourceType
)

@Composable
private fun DiagnosticCard(item: ProviderDiagnostic) = OperationCard(
    title = item.displayName,
    subtitle = "${item.endpoint} · TLS ${item.tlsMode} · ${item.capabilities.size} capabilities",
    state = item.state,
    trailing = item.providerId
)

@Composable
private fun OperationCard(
    title: String,
    subtitle: String,
    state: ProviderHealthState,
    trailing: String
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(10.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(statusColor(state))
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(3.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(trailing, style = MaterialTheme.typography.labelSmall, color = statusColor(state), modifier = Modifier.padding(start = 10.dp))
        }
    }
}

private fun statusColor(state: ProviderHealthState): Color = when (state) {
    ProviderHealthState.HEALTHY -> Color(0xFF2E7D32)
    ProviderHealthState.DEGRADED -> Color(0xFFF9A825)
    ProviderHealthState.UNAVAILABLE -> Color(0xFFC62828)
    ProviderHealthState.UNKNOWN -> Color(0xFF607D8B)
}
