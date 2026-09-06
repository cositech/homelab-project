package com.homelab.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.domain.model.Site
import com.homelab.app.domain.model.Tenant

/**
 * Manages the [Site]s belonging to a single tenant, reached by tapping that tenant's "Sites"
 * button on [TenantsScreen]. A site is an optional attribute an instance can be assigned to (see
 * the instance create/edit flow) - there is no "active site" concept the way there is for tenants.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitesScreen(
    tenantId: String,
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val tenantSelection by viewModel.tenantSelection.collectAsStateWithLifecycle()
    val siteRegistry by viewModel.siteRegistry.collectAsStateWithLifecycle()
    val tenant = tenantSelection.tenants.firstOrNull { it.id == tenantId } ?: Tenant.DEFAULT
    val sites = siteRegistry.sitesForTenant(tenantId)

    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Site?>(null) }
    var pendingDelete by remember { mutableStateOf<Site?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_sites_title_for_tenant, tenantDisplayName(tenant)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FilledTonalButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.sites_add_site))
                }
            }

            if (sites.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.sites_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }

            items(sites, key = { it.id }) { site ->
                SiteRow(
                    site = site,
                    onRename = { renaming = site },
                    onDelete = { pendingDelete = site }
                )
            }
        }
    }

    if (showAddDialog) {
        SiteEditDialog(
            title = stringResource(R.string.sites_add_site),
            initialName = "",
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                viewModel.addSite(tenantId, name)
                showAddDialog = false
            }
        )
    }

    renaming?.let { site ->
        SiteEditDialog(
            title = stringResource(R.string.tenants_rename),
            initialName = site.name,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                viewModel.renameSite(site.id, name)
                renaming = null
            }
        )
    }

    pendingDelete?.let { site ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = stringResource(R.string.delete)) },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.tenants_delete_confirm_message, site.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeSite(site.id)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(stringResource(R.string.delete), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }
}

@Composable
private fun SiteRow(
    site: Site,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = site.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRename,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(stringResource(R.string.tenants_rename), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(stringResource(R.string.delete), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SiteEditDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.tenants_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(stringResource(R.string.save), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    )
}
