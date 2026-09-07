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
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.runtime.LaunchedEffect
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
    CORRELATION("By Asset"),
    BY_SITE("By Site"),
    BY_CUSTOMER("By Customer"),
    SEARCH("Search"),
    DIAGNOSTICS("Diagnostics")
}

@Composable
fun OperationsScreen(viewModel: OperationsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tenantSelection by viewModel.tenantSelection.collectAsStateWithLifecycle()
    val siteRegistry by viewModel.siteRegistry.collectAsStateWithLifecycle()
    val customerRegistry by viewModel.customerRegistry.collectAsStateWithLifecycle()
    var selectedSection by remember { mutableIntStateOf(0) }
    // "By Customer" only makes sense fanned out across every tenant - hidden the rest of the time,
    // the same rule as every other Phase-4 all-tenants-only affordance.
    val sections = remember(tenantSelection.allTenantsMode) {
        if (tenantSelection.allTenantsMode) {
            OperationsSection.entries
        } else {
            OperationsSection.entries.filterNot { it == OperationsSection.BY_CUSTOMER }
        }
    }
    LaunchedEffect(sections) {
        if (selectedSection >= sections.size) selectedSection = 0
    }

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
            OperationsSection.CORRELATION -> CorrelationSection(state.snapshot)
            OperationsSection.BY_SITE -> SiteCorrelationSection(state.snapshot, state.siteRefByInstanceId, siteRegistry, tenantSelection)
            OperationsSection.BY_CUSTOMER -> CustomerCorrelationSection(state.snapshot, state.tenantRefByInstanceId, tenantSelection, customerRegistry)
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
private fun <T> OperationsList(
    values: List<T>,
    emptyText: String,
    key: ((T) -> Any)? = null,
    content: @Composable (T) -> Unit
) {
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
        items(values, key = key) { content(it) }
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
    state = resourceHealthState(item.state),
    trailing = item.state ?: item.resourceType
)

private fun resourceHealthState(state: String?): ProviderHealthState = when (state?.lowercase()) {
    "offline", "down", "unavailable", "critical" -> ProviderHealthState.UNAVAILABLE
    "degraded", "pending", "paused", "warning", "maintenance" -> ProviderHealthState.DEGRADED
    "online", "up", "running", "healthy" -> ProviderHealthState.HEALTHY
    else -> ProviderHealthState.UNKNOWN
}

/**
 * Phase 4 "by asset" rollup: the same [OperationsSnapshot.assets] regrouped by canonical host
 * (`CanonicalAssetResolver`), so a host seen through several providers renders as one card instead
 * of several unrelated ones in the flat Assets tab.
 */
@Composable
private fun CorrelationSection(snapshot: com.homelab.app.domain.provider.OperationsSnapshot) {
    // Keyed the same way as AssetObservation.ref (includes resourceType): a provider instance can
    // expose two resource types under the same native id, so omitting it would let one silently
    // overwrite the other's entry here.
    val resourceByRef = remember(snapshot) {
        snapshot.assets.associateBy { "${it.providerId}/${it.instanceId}/${it.resourceType}/${it.resourceId}" }
    }
    // ProviderEvent carries no resourceType, so alerts can only ever be matched on the 3-part key.
    val alertCountByRef = remember(snapshot) {
        snapshot.alerts.groupingBy { "${it.providerId}/${it.instanceId}/${it.resourceId}" }.eachCount()
    }
    OperationsList(
        snapshot.correlatedAssets,
        "No assets discovered",
        key = { it.correlationId }
    ) { asset ->
        CanonicalAssetCard(asset, resourceByRef, alertCountByRef)
    }
}

@Composable
private fun CanonicalAssetCard(
    asset: com.homelab.app.domain.asset.CanonicalAsset,
    resourceByRef: Map<String, ProviderResource>,
    alertCountByRef: Map<String, Int>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = asset.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (asset.isCorrelated) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ) {
                val count = asset.providerIds.size
                Text(
                    text = if (count == 1) "1 provider" else "$count providers",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (asset.isCorrelated) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        asset.observations.forEach { observation ->
            val resource = resourceByRef[observation.ref]
            val alertCount = alertCountByRef["${observation.providerId}/${observation.instanceId}/${observation.resourceId}"] ?: 0
            OperationCard(
                title = "${observation.providerId} · ${observation.resourceType}",
                subtitle = observation.name,
                state = resourceHealthState(resource?.state),
                trailing = when {
                    alertCount > 0 -> if (alertCount == 1) "1 alert" else "$alertCount alerts"
                    else -> resource?.state ?: observation.resourceType
                }
            )
        }
    }
}

/** Keyed by tenant, not just site: two different tenants' sites (or "no site" groups) must never
 * collapse into one, and two tenants can legitimately name a site the same thing (e.g. "Rack 1"),
 * mirroring how [com.homelab.app.domain.asset.CanonicalAsset.correlationId] namespaces by tenant
 * for the exact same reason. */
private data class SiteGroupKey(val tenantRef: String, val siteId: String?)

/**
 * Phase 4 "by site" rollup: the same correlated assets as [CorrelationSection], grouped by the
 * [com.homelab.app.domain.model.Site] assigned to any of a canonical asset's member instances
 * (an asset merges observations across providers, but in practice they all belong to the same
 * physical site). A "no site" group collects assets with no member instance assigned to one.
 */
@Composable
private fun SiteCorrelationSection(
    snapshot: com.homelab.app.domain.provider.OperationsSnapshot,
    siteRefByInstanceId: Map<String, String?>,
    siteRegistry: com.homelab.app.domain.model.SiteRegistry,
    tenantSelection: TenantSelection
) {
    val resourceByRef = remember(snapshot) {
        snapshot.assets.associateBy { "${it.providerId}/${it.instanceId}/${it.resourceType}/${it.resourceId}" }
    }
    val alertCountByRef = remember(snapshot) {
        snapshot.alerts.groupingBy { "${it.providerId}/${it.instanceId}/${it.resourceId}" }.eachCount()
    }
    val siteById = remember(siteRegistry) { siteRegistry.sites.associateBy { it.id } }
    val grouped = remember(snapshot, siteRefByInstanceId, siteById) {
        snapshot.correlatedAssets.groupBy { asset ->
            val site = asset.observations.firstNotNullOfOrNull { observation ->
                siteRefByInstanceId[observation.instanceId]?.let { siteById[it] }
            }
            SiteGroupKey(tenantRef = asset.tenantRef, siteId = site?.id)
        }
    }

    if (grouped.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No assets discovered", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    // The tenant label only needs to render when more than one tenant is actually present in this
    // refresh (single-tenant installs, and a scoped-to-one-tenant view, never show it).
    val showTenantLabel = grouped.keys.map { it.tenantRef }.distinct().size > 1
    // Resolved up front from a single top-level stringResource call, not via tenantDisplayName()
    // per tenant - that helper is @Composable, and the sort comparators below are plain lambdas.
    val defaultTenantLabel = stringResource(R.string.home_default_badge)
    val tenantNameByRef = tenantSelection.tenants.associate { tenant ->
        tenant.id to if (tenant.isDefault) defaultTenantLabel else tenant.name
    }
    val (unassigned, assigned) = grouped.entries.partition { it.key.siteId == null }
    val orderedGroups = assigned.sortedWith(
        compareBy(
            { siteById[it.key.siteId]?.name?.lowercase() },
            { tenantNameByRef[it.key.tenantRef] }
        )
    ) + unassigned.sortedBy { tenantNameByRef[it.key.tenantRef] }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        orderedGroups.forEach { (groupKey, assets) ->
            item(key = "site-header:${groupKey.tenantRef}:${groupKey.siteId ?: "unassigned"}") {
                SiteGroupHeader(
                    siteName = groupKey.siteId?.let { siteById[it]?.name },
                    tenantName = tenantNameByRef[groupKey.tenantRef].takeIf { showTenantLabel },
                    count = assets.size
                )
            }
            items(assets, key = { it.correlationId }) { asset ->
                CanonicalAssetCard(asset, resourceByRef, alertCountByRef)
            }
        }
    }
}

@Composable
private fun SiteGroupHeader(siteName: String?, tenantName: String?, count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = siteName ?: stringResource(R.string.sites_none),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                tenantName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider()
    }
}

/**
 * Phase 4 "by customer" rollup: per-tenant health and alert counts on one screen, reachable only
 * in all-tenants mode (a single-tenant view already shows everything for that one tenant). Reads
 * the same [snapshot] every other section reads - no extra requests, no extra state - resolving
 * each health/alert record's `instanceId` back to a tenant via [tenantRefByInstanceId].
 */
@Composable
private fun CustomerCorrelationSection(
    snapshot: com.homelab.app.domain.provider.OperationsSnapshot,
    tenantRefByInstanceId: Map<String, String>,
    tenantSelection: TenantSelection,
    customerRegistry: com.homelab.app.domain.model.CustomerRegistry
) {
    val healthByTenant = remember(snapshot, tenantRefByInstanceId) {
        snapshot.health.groupBy { tenantRefByInstanceId[it.instanceId] }
    }
    val alertCountByTenant = remember(snapshot, tenantRefByInstanceId) {
        snapshot.alerts.groupingBy { tenantRefByInstanceId[it.instanceId] }.eachCount()
    }
    val defaultTenantLabel = stringResource(R.string.home_default_badge)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(tenantSelection.tenants, key = { it.id }) { tenant ->
            val tenantName = if (tenant.isDefault) defaultTenantLabel else tenant.name
            val health = healthByTenant[tenant.id].orEmpty()
            TenantHealthSummaryCard(
                tenantName = tenantName,
                customerAccountName = customerRegistry.customerFor(tenant.id)?.accountName,
                healthyCount = health.count { it.state == ProviderHealthState.HEALTHY },
                degradedCount = health.count { it.state == ProviderHealthState.DEGRADED },
                unavailableCount = health.count { it.state == ProviderHealthState.UNAVAILABLE },
                alertCount = alertCountByTenant[tenant.id] ?: 0
            )
        }
    }
}

@Composable
private fun TenantHealthSummaryCard(
    tenantName: String,
    customerAccountName: String?,
    healthyCount: Int,
    degradedCount: Int,
    unavailableCount: Int,
    alertCount: Int
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tenantName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    customerAccountName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (alertCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = if (alertCount == 1) "1 alert" else "$alertCount alerts",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Healthy: $healthyCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Degraded: $degradedCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Unavailable: $unavailableCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

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
