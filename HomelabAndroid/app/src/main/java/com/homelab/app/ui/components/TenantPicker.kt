package com.homelab.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homelab.app.R
import com.homelab.app.domain.model.Tenant
import com.homelab.app.ui.settings.tenantDisplayName

/**
 * Which tenant a service instance belongs to. Hidden on a single-tenant install (only the
 * `default` tenant configured) so it adds no UI surface until a second tenant exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantPicker(
    tenants: List<Tenant>,
    selectedTenantId: String,
    onTenantSelected: (Tenant) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tenants.size <= 1) return

    var expanded by remember { mutableStateOf(false) }
    val selected = tenants.firstOrNull { it.id == selectedTenantId } ?: tenants.first()
    val label = stringResource(R.string.settings_tenants_title)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            OutlinedTextField(
                value = tenantDisplayName(selected),
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Groups,
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                tenants.forEach { tenant ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = tenantDisplayName(tenant),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (tenant.id == selectedTenantId) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            expanded = false
                            onTenantSelected(tenant)
                        }
                    )
                }
            }
        }
    }
}
