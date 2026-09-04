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
import com.homelab.app.domain.model.Tenant
import com.homelab.app.domain.model.TenantKind

/**
 * Phase 4 tenant management. A single-tenant install (the implicit `default` tenant only) sees
 * this screen only via its entry point in Settings; every tenant it lists always includes the
 * default one, which can be activated but never renamed or removed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val selection by viewModel.tenantSelection.collectAsStateWithLifecycle()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Tenant?>(null) }
    var pendingDelete by remember { mutableStateOf<Tenant?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_tenants_title)) },
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
                    Text(stringResource(R.string.tenants_add_tenant))
                }
            }

            items(selection.tenants, key = { it.id }) { tenant ->
                TenantRow(
                    tenant = tenant,
                    isActive = tenant.id == selection.activeTenantId,
                    onSetActive = { viewModel.setActiveTenant(tenant.id) },
                    onRename = { renaming = tenant },
                    onDelete = { pendingDelete = tenant }
                )
            }
        }
    }

    if (showAddDialog) {
        TenantEditDialog(
            title = stringResource(R.string.tenants_add_tenant),
            initialName = "",
            initialKind = TenantKind.CUSTOMER,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, kind ->
                viewModel.addTenant(name, kind)
                showAddDialog = false
            }
        )
    }

    renaming?.let { tenant ->
        TenantEditDialog(
            title = stringResource(R.string.tenants_rename),
            initialName = tenant.name,
            initialKind = tenant.kind,
            showKindSelector = false,
            onDismiss = { renaming = null },
            onConfirm = { name, _ ->
                viewModel.renameTenant(tenant.id, name)
                renaming = null
            }
        )
    }

    pendingDelete?.let { tenant ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = stringResource(R.string.delete)) },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.tenants_delete_confirm_message, tenant.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeTenant(tenant.id)
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
private fun TenantRow(
    tenant: Tenant,
    isActive: Boolean,
    onSetActive: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tenant.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (tenant.kind == TenantKind.CUSTOMER) {
                            stringResource(R.string.tenants_kind_customer)
                        } else {
                            stringResource(R.string.tenants_kind_personal)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.tenants_active_badge),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isActive) {
                    FilledTonalButton(
                        onClick = onSetActive,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(stringResource(R.string.tenants_set_active), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!tenant.isDefault) {
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
}

@Composable
private fun TenantEditDialog(
    title: String,
    initialName: String,
    initialKind: TenantKind,
    showKindSelector: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String, TenantKind) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var kind by remember { mutableStateOf(initialKind) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tenants_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (showKindSelector) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = kind == TenantKind.PERSONAL,
                            onClick = { kind = TenantKind.PERSONAL },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text(stringResource(R.string.tenants_kind_personal), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        SegmentedButton(
                            selected = kind == TenantKind.CUSTOMER,
                            onClick = { kind = TenantKind.CUSTOMER },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text(stringResource(R.string.tenants_kind_customer), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), kind) },
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
