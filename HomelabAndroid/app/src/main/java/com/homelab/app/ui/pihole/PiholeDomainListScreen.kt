package com.homelab.app.ui.pihole

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.pihole.PiholeDomainDto
import com.homelab.app.data.remote.dto.pihole.PiholeDomainListType
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import com.homelab.app.ui.common.ErrorScreen

@Composable
private fun piholeDomainCardColor(): Color {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    return if (isDarkTheme) Color(0xFF24191A) else Color(0xFFFDEEEF)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiholeDomainListScreen(
    onNavigateBack: () -> Unit,
    viewModel: PiholeViewModel = hiltViewModel()
) {
    val domainsState by viewModel.domainsState.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showingAddDialog by remember { mutableStateOf(false) }
    var newDomainText by remember { mutableStateOf("") }
    var pendingRemoval by remember { mutableStateOf<PiholeDomainDto?>(null) }
    
    val selectedListType = if (selectedTabIndex == 0) PiholeDomainListType.ALLOW else PiholeDomainListType.DENY
    
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.fetchDomains()
    }

    LaunchedEffect(actionError) {
        actionError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pihole_domain_management), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        newDomainText = ""
                        showingAddDialog = true 
                    }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.pihole_add_domain))
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
            SecondaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text(stringResource(R.string.pihole_allowed)) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text(stringResource(R.string.pihole_blocked_list)) }
                )
            }

            when (val state = domainsState) {
                is UiState.Loading, is UiState.Idle -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ServiceType.PIHOLE.primaryColor)
                    }
                }
                is UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { viewModel.fetchDomains() },
                        modifier = Modifier.weight(1f)
                    )
                }
                is UiState.Offline -> {
                    ErrorScreen(
                        message = "",
                        onRetry = { viewModel.fetchDomains() },
                        isOffline = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                is UiState.Success -> {
                    val filteredDomains = state.data.filter { it.type == selectedListType }
                    if (filteredDomains.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.pihole_no_domains),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredDomains, key = { it.id }) { domain ->
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart) {
                                            pendingRemoval = domain
                                        }
                                        false
                                    }
                                )
                                
                                SwipeToDismissBox(
                                    state = dismissState,
                                    backgroundContent = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                                                .padding(end = 16.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Text(
                                                stringResource(R.string.delete), 
                                                color = MaterialTheme.colorScheme.onError, 
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    },
                                    enableDismissFromStartToEnd = false
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = piholeDomainCardColor(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = if (selectedListType == PiholeDomainListType.ALLOW) stringResource(R.string.pihole_allowed) else stringResource(R.string.pihole_blocked_list),
                                                tint = if (selectedListType == PiholeDomainListType.ALLOW) StatusGreen else StatusRed
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(
                                                text = domain.domain,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showingAddDialog) {
        AlertDialog(
            onDismissRequest = { showingAddDialog = false },
            title = { Text(stringResource(R.string.pihole_add_domain)) },
            text = {
                val listName = stringResource(if (selectedListType == PiholeDomainListType.ALLOW) R.string.pihole_allowed else R.string.pihole_blocked_list)
                Column {
                    Text(stringResource(R.string.pihole_add_domain_desc, listName))
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newDomainText,
                        onValueChange = { newDomainText = it },
                        label = { Text(stringResource(R.string.pihole_domain_placeholder)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val url = newDomainText.trim()
                        if (url.isNotEmpty()) {
                            viewModel.addDomain(url, selectedListType, confirmed = true)
                        }
                        showingAddDialog = false
                    },
                    enabled = newDomainText.trim().isNotEmpty()
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showingAddDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingRemoval?.let { domain ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(domain.domain) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeDomain(
                            domain.domain,
                            domain.type ?: selectedListType,
                            confirmed = true
                        )
                        pendingRemoval = null
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
