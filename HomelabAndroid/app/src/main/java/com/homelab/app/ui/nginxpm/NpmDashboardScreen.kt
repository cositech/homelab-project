@file:OptIn(ExperimentalFoundationApi::class)

package com.homelab.app.ui.nginxpm

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.nginxpm.*
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.ui.nginxpm.components.*
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val NpmOrange = Color(0xFFF15B2A)

private const val TAB_MENU = 0
private const val TAB_PROXY_HOSTS = 1
private const val TAB_REDIR = 2
private const val TAB_STREAMS = 3
private const val TAB_DEAD_HOSTS = 4
private const val TAB_ACCESS_LISTS = 5
private const val TAB_SSL = 6
private const val TAB_USERS = 7
private const val TAB_AUDIT_LOGS = 8

private fun npmPageBackground(isDarkTheme: Boolean, accent: Color): Brush = if (isDarkTheme) {
    Brush.verticalGradient(
        listOf(
            Color(0xFF110D0A),
            Color(0xFF1A130E),
            accent.copy(alpha = 0.055f),
            Color(0xFF120E0A)
        )
    )
} else {
    Brush.verticalGradient(
        listOf(
            Color(0xFFFFFAF7),
            Color(0xFFFFF4EE),
            accent.copy(alpha = 0.03f),
            Color(0xFFFFF8F3)
        )
    )
}

private fun npmCardColor(
    isDarkTheme: Boolean,
    accent: Color? = null,
    tint: Float = if (isDarkTheme) 0.055f else 0.035f
): Color {
    val base = if (isDarkTheme) Color(0xFF271C16) else Color(0xFFFFF0E8)
    return accent?.let { lerp(base, it, tint.coerceIn(0f, 0.22f)) } ?: base
}

private fun npmRaisedCardColor(
    isDarkTheme: Boolean,
    accent: Color? = null,
    tint: Float = if (isDarkTheme) 0.05f else 0.03f
): Color {
    val base = if (isDarkTheme) Color(0xFF33251E) else Color(0xFFFFF6F1)
    return accent?.let { lerp(base, it, tint.coerceIn(0f, 0.18f)) } ?: base
}

private fun npmBorderTone(
    isDarkTheme: Boolean,
    accent: Color? = null,
    tint: Float = if (isDarkTheme) 0.15f else 0.10f
): Color {
    val base = if (isDarkTheme) Color(0xFF5A4336) else Color(0xFFE2BFA8)
    return accent?.let { lerp(base, it, tint.coerceIn(0f, 0.35f)) } ?: base
}

private fun npmBorderColor(
    isDarkTheme: Boolean,
    accent: Color? = null
): Color = npmBorderTone(isDarkTheme, accent = accent).copy(alpha = if (isDarkTheme) 0.58f else 0.42f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NpmDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    viewModel: NpmDashboardViewModel = hiltViewModel()
) {
    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isPerformingAction by viewModel.isPerformingAction.collectAsStateWithLifecycle()
    val npmColor = ServiceType.NGINX_PROXY_MANAGER.primaryColor
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val pageBrush = remember(isDarkTheme) { npmPageBackground(isDarkTheme, npmColor) }
    val pageGlow = remember(isDarkTheme) {
        if (isDarkTheme) npmColor.copy(alpha = 0.1f) else npmColor.copy(alpha = 0.045f)
    }
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = selectedTab != TAB_MENU) {
        viewModel.selectTab(TAB_MENU)
    }

    // Form sheet states
    var showProxyHostForm by remember { mutableStateOf(false) }
    var editingProxyHost by remember { mutableStateOf<NpmProxyHost?>(null) }
    var showAccessListForm by remember { mutableStateOf(false) }
    var editingAccessList by remember { mutableStateOf<NpmAccessList?>(null) }
    var showRedirectionForm by remember { mutableStateOf(false) }
    var editingRedirection by remember { mutableStateOf<NpmRedirectionHost?>(null) }
    var showStreamForm by remember { mutableStateOf(false) }
    var editingStream by remember { mutableStateOf<NpmStream?>(null) }
    var showDeadHostForm by remember { mutableStateOf(false) }
    var editingDeadHost by remember { mutableStateOf<NpmDeadHost?>(null) }
    var showCertificateForm by remember { mutableStateOf(false) }
    var showUserForm by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<NpmUser?>(null) }

    // Delete confirmation dialog state
    var deleteConfirmation by remember { mutableStateOf<DeleteConfirmation?>(null) }
    var pendingDisableProxyHostId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        viewModel.fetchDashboard()
    }

    LaunchedEffect(Unit) {
        viewModel.actionEvent.collectLatest { event ->
            when (event) {
                is NpmActionEvent.Success -> snackbarHostState.showSnackbar(event.message)
                is NpmActionEvent.Error -> snackbarHostState.showSnackbar(event.message)
            }
            // Close forms on success
            if (event is NpmActionEvent.Success) {
                showProxyHostForm = false
                editingProxyHost = null
                showAccessListForm = false
                editingAccessList = null
                showRedirectionForm = false
                editingRedirection = null
                showStreamForm = false
                editingStream = null
                showDeadHostForm = false
                editingDeadHost = null
                showCertificateForm = false
                showUserForm = false
                editingUser = null
            }
        }
    }

    val titleResId = when (selectedTab) {
        TAB_PROXY_HOSTS -> R.string.npm_proxy_hosts
        TAB_REDIR -> R.string.npm_redirections
        TAB_STREAMS -> R.string.npm_streams
        TAB_DEAD_HOSTS -> R.string.npm_404_hosts
        TAB_ACCESS_LISTS -> R.string.npm_access_list
        TAB_SSL -> R.string.npm_ssl_certificates
        TAB_USERS -> R.string.npm_users
        TAB_AUDIT_LOGS -> R.string.npm_audit_logs
        else -> R.string.service_nginx_proxy_manager
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleResId), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedTab == TAB_MENU) {
                            onNavigateBack()
                        } else {
                            viewModel.selectTab(TAB_MENU)
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (selectedTab != TAB_MENU) {
                        IconButton(onClick = { viewModel.selectTab(TAB_MENU) }) {
                            Icon(Icons.Default.GridView, contentDescription = stringResource(R.string.npm_overview))
                        }
                    }
                    IconButton(onClick = { viewModel.fetchDashboard() }) {
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
        floatingActionButton = {
            if (selectedTab != TAB_MENU) {
                FloatingActionButton(
                    onClick = {
                        when (selectedTab) {
                            TAB_PROXY_HOSTS -> { editingProxyHost = null; showProxyHostForm = true }
                            TAB_REDIR -> { editingRedirection = null; showRedirectionForm = true }
                            TAB_STREAMS -> { editingStream = null; showStreamForm = true }
                            TAB_DEAD_HOSTS -> { editingDeadHost = null; showDeadHostForm = true }
                            TAB_ACCESS_LISTS -> { editingAccessList = null; showAccessListForm = true }
                            TAB_SSL -> showCertificateForm = true
                            TAB_USERS -> { editingUser = null; showUserForm = true }
                        }
                    },
                    containerColor = NpmOrange,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(paddingValues)) {
                if (isPerformingAction) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = NpmOrange
                    )
                }

                when (val state = dashboardState) {
                    is UiState.Loading, is UiState.Idle -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = npmColor)
                        }
                    }
                    is UiState.Error -> {
                        ErrorScreen(
                            message = state.message,
                            onRetry = { viewModel.fetchDashboard() }
                        )
                    }
                    is UiState.Offline -> {
                        ErrorScreen(
                            message = "",
                            onRetry = { viewModel.fetchDashboard() },
                            isOffline = true
                        )
                    }
                    is UiState.Success -> {
                        val data = state.data
                        when (selectedTab) {
                            TAB_MENU -> DashboardMenu(
                                data = data,
                                instances = instances,
                                viewModel = viewModel,
                                onNavigateToInstance = onNavigateToInstance,
                                onTabSelect = { viewModel.selectTab(it) }
                            )
                        TAB_PROXY_HOSTS -> ProxyHostsTab(
                            proxyHosts = data.proxyHosts,
                            onEdit = { editingProxyHost = it; showProxyHostForm = true },
                            onDelete = { host ->
                                deleteConfirmation = DeleteConfirmation(
                                    title = host.primaryDomain,
                                    type = DeleteTargetType.PROXY_HOST,
                                    onConfirm = { viewModel.deleteProxyHost(host.id, confirmed = true) }
                                )
                            },
                            onToggle = { host, enabled ->
                                if (enabled) {
                                    viewModel.toggleProxyHost(host.id, enabled = true, confirmed = false)
                                } else {
                                    pendingDisableProxyHostId = host.id
                                }
                            }
                        )
                        TAB_REDIR -> RedirectionHostsTab(
                            redirectionHosts = data.redirectionHosts,
                            onEdit = { editingRedirection = it; showRedirectionForm = true },
                            onDelete = { host ->
                                deleteConfirmation = DeleteConfirmation(
                                    title = host.primaryDomain,
                                    type = DeleteTargetType.REDIRECTION_HOST,
                                    onConfirm = { viewModel.deleteRedirectionHost(host.id) }
                                )
                            }
                        )
                        TAB_STREAMS -> StreamsTab(
                            streams = data.streams,
                            onEdit = { editingStream = it; showStreamForm = true },
                            onDelete = { stream ->
                                deleteConfirmation = DeleteConfirmation(
                                    title = ":${stream.incomingPort}",
                                    type = DeleteTargetType.STREAM,
                                    onConfirm = { viewModel.deleteStream(stream.id) }
                                )
                            }
                        )
                        TAB_DEAD_HOSTS -> DeadHostsTab(
                            deadHosts = data.deadHosts,
                            onEdit = { editingDeadHost = it; showDeadHostForm = true },
                            onDelete = { host ->
                                deleteConfirmation = DeleteConfirmation(
                                    title = host.primaryDomain,
                                    type = DeleteTargetType.DEAD_HOST,
                                    onConfirm = { viewModel.deleteDeadHost(host.id) }
                                )
                            }
                        )
                        TAB_ACCESS_LISTS -> AccessListsTab(
                            accessLists = data.accessLists,
                            onEdit = { list ->
                                editingAccessList = list
                                showAccessListForm = true
                            },
                            onDelete = { list ->
                                deleteConfirmation = DeleteConfirmation(
                                    title = list.name,
                                    type = DeleteTargetType.ACCESS_LIST,
                                    onConfirm = { viewModel.deleteAccessList(list.id) }
                                )
                            }
                        )
                        TAB_SSL -> SslTab(
                            certificates = data.certificates,
                            onRenew = { viewModel.renewCertificate(it.id) },
                            onDelete = { cert ->
                                deleteConfirmation = DeleteConfirmation(
                                    title = cert.niceName.ifBlank { cert.primaryDomain },
                                    type = DeleteTargetType.CERTIFICATE,
                                    onConfirm = { viewModel.deleteCertificate(cert.id) }
                                )
                            }
                        )
                        TAB_USERS -> UsersTab(
                            users = data.users,
                            onEdit = { user -> editingUser = user; showUserForm = true },
                            onDelete = { user ->
                                deleteConfirmation = DeleteConfirmation(
                                    title = user.email ?: "User #${user.id}",
                                    type = DeleteTargetType.USER,
                                    onConfirm = { viewModel.deleteUser(user.id) }
                                )
                            }
                        )
                        TAB_AUDIT_LOGS -> AuditLogsTab(auditLogs = data.auditLogs)
                    }

                        // Form bottom sheets
                        if (showProxyHostForm) {
                            NpmProxyHostForm(
                                existing = editingProxyHost,
                                certificates = data.certificates,
                                accessLists = data.accessLists,
                                isLoading = isPerformingAction,
                                onDismiss = { showProxyHostForm = false; editingProxyHost = null },
                                onSave = { request ->
                                    val id = editingProxyHost?.id
                                    if (id != null) viewModel.updateProxyHost(id, request, confirmed = true)
                                    else viewModel.createProxyHost(request, confirmed = true)
                                }
                            )
                        }
                        if (showRedirectionForm) {
                            NpmRedirectionHostForm(
                                existing = editingRedirection,
                                certificates = data.certificates,
                                accessLists = data.accessLists,
                                isLoading = isPerformingAction,
                                onDismiss = { showRedirectionForm = false; editingRedirection = null },
                                onSave = { request ->
                                    val id = editingRedirection?.id
                                    if (id != null) viewModel.updateRedirectionHost(id, request)
                                    else viewModel.createRedirectionHost(request)
                                }
                            )
                        }
                        if (showStreamForm) {
                            NpmStreamForm(
                                existing = editingStream,
                                isLoading = isPerformingAction,
                                onDismiss = { showStreamForm = false; editingStream = null },
                                onSave = { request ->
                                    val id = editingStream?.id
                                    if (id != null) viewModel.updateStream(id, request)
                                    else viewModel.createStream(request)
                                }
                            )
                        }
                        if (showDeadHostForm) {
                            NpmDeadHostForm(
                                existing = editingDeadHost,
                                certificates = data.certificates,
                                isLoading = isPerformingAction,
                                onDismiss = { showDeadHostForm = false; editingDeadHost = null },
                                onSave = { request ->
                                    val id = editingDeadHost?.id
                                    if (id != null) viewModel.updateDeadHost(id, request)
                                    else viewModel.createDeadHost(request)
                                }
                            )
                        }
                        if (showCertificateForm) {
                            NpmCertificateForm(
                                isLoading = isPerformingAction,
                                onDismiss = { showCertificateForm = false },
                                onSave = { request -> viewModel.createCertificate(request) }
                            )
                        }
                        if (showAccessListForm) {
                            NpmAccessListForm(
                                editing = editingAccessList,
                                onDismiss = {
                                    showAccessListForm = false
                                    editingAccessList = null
                                },
                                onSave = { name, items, clients ->
                                    val existing = editingAccessList
                                    if (existing != null) {
                                        viewModel.updateAccessList(existing.id, name, items, clients)
                                    } else {
                                        viewModel.createAccessList(name, items, clients)
                                    }
                                }
                            )
                        }
                        if (showUserForm) {
                            NpmUserForm(
                                editing = editingUser,
                                isLoading = isPerformingAction,
                                onDismiss = {
                                    showUserForm = false
                                    editingUser = null
                                },
                                onSave = { request ->
                                    val existing = editingUser
                                    if (existing != null) {
                                        viewModel.updateUser(existing.id, request)
                                    } else {
                                        viewModel.createUser(request)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    val confirmation = deleteConfirmation
    if (confirmation != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmation = null },
            title = { Text(stringResource(R.string.npm_delete_confirm_title)) },
            text = { Text(stringResource(R.string.npm_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmation.onConfirm()
                    deleteConfirmation = null
                }) {
                    Text(stringResource(R.string.npm_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmation = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val disableHostId = pendingDisableProxyHostId
    if (disableHostId != null) {
        AlertDialog(
            onDismissRequest = { pendingDisableProxyHostId = null },
            title = { Text(stringResource(R.string.npm_disable_confirm_title)) },
            text = { Text(stringResource(R.string.npm_disable_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.toggleProxyHost(disableHostId, enabled = false, confirmed = true)
                    pendingDisableProxyHostId = null
                }) {
                    Text(stringResource(R.string.npm_disable), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDisableProxyHostId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private data class DeleteConfirmation(
    val title: String,
    val type: DeleteTargetType,
    val onConfirm: () -> Unit
)

private enum class DeleteTargetType {
    PROXY_HOST,
    REDIRECTION_HOST,
    STREAM,
    DEAD_HOST,
    CERTIFICATE,
    ACCESS_LIST,
    USER
}

// ── Tab: Users ──

@Composable
private fun UsersTab(
    users: List<NpmUser>,
    onEdit: (NpmUser) -> Unit,
    onDelete: (NpmUser) -> Unit
) {
    if (users.isEmpty()) {
        EmptyState(
            icon = Icons.Default.People,
            message = stringResource(R.string.npm_no_users)
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(users, key = { it.id }) { user ->
                UserCard(
                    user = user,
                    onEdit = { onEdit(user) },
                    onDelete = { onDelete(user) }
                )
            }
        }
    }
}

@Composable
private fun UserCard(
    user: NpmUser,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val displayName = when {
        !user.name.isNullOrBlank() -> user.name
        !user.nickname.isNullOrBlank() -> user.nickname
        !user.email.isNullOrBlank() -> user.email
        else -> "User #${user.id}"
    }

    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = npmCardColor(isDarkTheme, accent = if (user.isDisabled == true) StatusRed else ServiceType.NGINX_PROXY_MANAGER.primaryColor, tint = if (isDarkTheme) 0.08f else 0.05f),
        border = BorderStroke(1.dp, npmBorderColor(isDarkTheme, accent = if (user.isDisabled == true) StatusRed else ServiceType.NGINX_PROXY_MANAGER.primaryColor)),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onEdit, onLongClick = { showMenu = true })
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayName ?: "",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (user.isDisabled == true) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = npmRaisedCardColor(isDarkTheme, accent = StatusRed, tint = if (isDarkTheme) 0.10f else 0.07f),
                                border = BorderStroke(1.dp, npmBorderTone(isDarkTheme, accent = StatusRed).copy(alpha = if (isDarkTheme) 0.62f else 0.52f))
                            ) {
                                Text(
                                    text = stringResource(R.string.npm_disabled),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!user.email.isNullOrBlank() && user.email != displayName) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val roles = user.roles?.filter { it.isNotBlank() }?.joinToString(", ")
                if (!roles.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = roles,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit)) },
                    onClick = { showMenu = false; onEdit() },
                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.npm_delete)) },
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.Delete, null) }
                )
            }
        }
    }
}

// ── Tab: Audit Logs ──

@Composable
private fun AuditLogsTab(auditLogs: List<NpmAuditLog>) {
    if (auditLogs.isEmpty()) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            message = stringResource(R.string.npm_no_audit_logs)
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(auditLogs, key = { it.id }) { log ->
                AuditLogCard(log = log)
            }
        }
    }
}

@Composable
private fun AuditLogCard(log: NpmAuditLog) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val actionColor = auditActionColor(log.action)
    val objectLabel = listOfNotNull(
        log.objectType?.replace('-', ' ')?.replaceFirstChar { it.uppercase() },
        log.objectId?.let { "#$it" }
    ).joinToString(" • ")
    val timestamp = formatAuditTimestamp(log.createdOn)
    val userLabel = when {
        !log.user?.name.isNullOrBlank() -> log.user?.name
        !log.user?.email.isNullOrBlank() -> log.user?.email
        log.userId != null -> "ID ${log.userId}"
        else -> null
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = npmCardColor(isDarkTheme, accent = actionColor, tint = if (isDarkTheme) 0.10f else 0.06f),
        border = BorderStroke(1.dp, npmBorderColor(isDarkTheme, accent = actionColor)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = npmRaisedCardColor(isDarkTheme, accent = actionColor, tint = if (isDarkTheme) 0.12f else 0.08f)
                ) {
                    Text(
                        text = stringResource(auditActionLabelId(log.action)),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = actionColor
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (timestamp != null) {
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (objectLabel.isNotBlank()) {
                Text(
                    text = objectLabel,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!userLabel.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = userLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun auditActionLabelId(action: String?): Int {
    val normalized = action?.lowercase() ?: ""
    return when {
        "create" in normalized -> R.string.npm_audit_action_created
        "update" in normalized -> R.string.npm_audit_action_updated
        "delete" in normalized -> R.string.npm_audit_action_deleted
        else -> R.string.npm_audit_logs
    }
}

@Composable
private fun auditActionColor(action: String?): Color {
    val normalized = action?.lowercase() ?: ""
    return when {
        "create" in normalized -> StatusGreen
        "update" in normalized -> Color(0xFFFF9500)
        "delete" in normalized -> StatusRed
        else -> NpmOrange
    }
}

private fun formatAuditTimestamp(value: String?): String? {
    if (value.isNullOrBlank()) return null
    return try {
        val instant = Instant.parse(value)
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    } catch (_: Exception) {
        value
    }
}

// ── Tab: Access Lists ──

@Composable
private fun AccessListsTab(
    accessLists: List<NpmAccessList>,
    onEdit: (NpmAccessList) -> Unit,
    onDelete: (NpmAccessList) -> Unit
) {
    if (accessLists.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Lock,
            message = stringResource(R.string.npm_access_list_none)
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(accessLists, key = { it.id }) { list ->
                AccessListCard(
                    accessList = list,
                    onClick = { onEdit(list) },
                    onEdit = { onEdit(list) },
                    onDelete = { onDelete(list) }
                )
            }
        }
    }
}

@Composable
private fun AccessListCard(
    accessList: NpmAccessList,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    var showMenu by remember { mutableStateOf(false) }

    val usersCount = accessList.items?.size ?: 0
    val clientsCount = accessList.clients?.size ?: 0
    val hasAnyEntries = usersCount > 0 || clientsCount > 0

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = npmCardColor(isDarkTheme, accent = if (hasAnyEntries) NpmOrange else null, tint = if (isDarkTheme) 0.09f else 0.06f),
        border = BorderStroke(1.dp, npmBorderColor(isDarkTheme, accent = if (hasAnyEntries) NpmOrange else null)),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            accessList.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val usersLabel = stringResource(R.string.npm_access_list_users)
                        val clientsLabel = stringResource(R.string.npm_access_list_clients)
                        if (hasAnyEntries) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = buildString {
                                    if (usersCount > 0) {
                                        append("$usersCount $usersLabel")
                                    }
                                    if (clientsCount > 0) {
                                        if (isNotEmpty()) append(" • ")
                                        append("$clientsCount $clientsLabel")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                        if (hasAnyEntries) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = npmRaisedCardColor(isDarkTheme, accent = NpmOrange, tint = if (isDarkTheme) 0.10f else 0.07f)
                            ) {
                            Text(
                                text = stringResource(R.string.npm_access_list),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = NpmOrange
                            )
                        }
                    }

                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit)) },
                    onClick = { showMenu = false; onEdit() },
                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.npm_delete)) },
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.Delete, null) }
                )
            }
        }
    }
}

// ── Dashboard Menu ──

private data class DashboardMenuItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val value: String,
    val label: String,
    val color: Color,
    val tabIndex: Int
)

@Composable
private fun DashboardMenu(
    data: NpmDashboardData,
    instances: List<com.homelab.app.domain.model.ServiceInstance>,
    viewModel: NpmDashboardViewModel,
    onNavigateToInstance: (String) -> Unit,
    onTabSelect: (Int) -> Unit
) {
    val proxyHostsLabel = stringResource(R.string.npm_proxy_hosts)
    val redirectionsLabel = stringResource(R.string.npm_redirections)
    val streamsLabel = stringResource(R.string.npm_streams)
    val deadHostsLabel = stringResource(R.string.npm_404_hosts)
    val accessListLabel = stringResource(R.string.npm_access_list)
    val sslLabel = stringResource(R.string.npm_ssl_certificates)
    val usersLabel = stringResource(R.string.npm_users)
    val auditLogsLabel = stringResource(R.string.npm_audit_logs)

    val items = remember(data, accessListLabel, sslLabel, usersLabel, auditLogsLabel) {
        listOf(
            DashboardMenuItem(Icons.Default.People, "${data.accessLists.size}", accessListLabel, Color(0xFF8E8E93), TAB_ACCESS_LISTS),
            DashboardMenuItem(Icons.Default.Lock, "${data.certificates.size}", sslLabel, NpmOrange, TAB_SSL),
            DashboardMenuItem(Icons.Default.Person, "${data.users.size}", usersLabel, Color(0xFF5C6BC0), TAB_USERS),
            DashboardMenuItem(Icons.AutoMirrored.Filled.ReceiptLong, "${data.auditLogs.size}", auditLogsLabel, Color(0xFF4DB6AC), TAB_AUDIT_LOGS)
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ServiceInstancePicker(
                instances = instances,
                selectedInstanceId = viewModel.instanceId,
                onInstanceSelected = { instance ->
                    viewModel.setPreferredInstance(instance.id)
                    onNavigateToInstance(instance.id)
                }
            )
        }
        item {
            DashboardHeroCard(
                report = data.hostReport,
                onProxyHosts = { onTabSelect(TAB_PROXY_HOSTS) },
                onRedirections = { onTabSelect(TAB_REDIR) },
                onStreams = { onTabSelect(TAB_STREAMS) },
                onDeadHosts = { onTabSelect(TAB_DEAD_HOSTS) }
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (row in items.chunked(2)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (item in row) {
                            DashboardMenuCard(item = item, onClick = { onTabSelect(item.tabIndex) })
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DashboardMenuCard(
    item: DashboardMenuItem,
    onClick: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = npmCardColor(isDarkTheme, accent = item.color, tint = if (isDarkTheme) 0.12f else 0.08f),
        border = BorderStroke(1.dp, npmBorderColor(isDarkTheme, accent = item.color)),
        modifier = Modifier
            .weight(1f)
            .height(86.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = item.color.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    tint = item.color,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = item.value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DashboardHeroCard(
    report: NpmHostReport,
    onProxyHosts: () -> Unit,
    onRedirections: () -> Unit,
    onStreams: () -> Unit,
    onDeadHosts: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = npmCardColor(isDarkTheme, accent = NpmOrange, tint = if (isDarkTheme) 0.10f else 0.07f),
        border = BorderStroke(1.dp, npmBorderColor(isDarkTheme, accent = NpmOrange)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onProxyHosts() }
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = NpmOrange,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = stringResource(R.string.npm_proxy_hosts),
                        tint = Color.White,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.npm_proxy_hosts),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${report.total}",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = stringResource(R.string.npm_proxy_hosts),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroStatChip(
                    label = stringResource(R.string.npm_redirections),
                    value = report.redirection,
                    color = Color(0xFF5B8DEF),
                    modifier = Modifier.weight(1f),
                    onClick = onRedirections
                )
                HeroStatChip(
                    label = stringResource(R.string.npm_streams),
                    value = report.stream,
                    color = Color(0xFF34C759),
                    modifier = Modifier.weight(1f),
                    onClick = onStreams
                )
                HeroStatChip(
                    label = stringResource(R.string.npm_404_hosts),
                    value = report.dead,
                    color = Color(0xFFFF9500),
                    modifier = Modifier.weight(1f),
                    onClick = onDeadHosts
                )
            }
        }
    }
}

@Composable
private fun HeroStatChip(
    label: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = npmRaisedCardColor(isDarkTheme, accent = color, tint = if (isDarkTheme) 0.12f else 0.08f),
        border = BorderStroke(1.dp, npmBorderTone(isDarkTheme, accent = color).copy(alpha = if (isDarkTheme) 0.62f else 0.52f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$value",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PlaceholderTab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    EmptyState(icon = icon, message = message)
}

// ── Tab: Proxy Hosts ──

@Composable
private fun ProxyHostsTab(
    proxyHosts: List<NpmProxyHost>,
    onEdit: (NpmProxyHost) -> Unit,
    onDelete: (NpmProxyHost) -> Unit,
    onToggle: (NpmProxyHost, Boolean) -> Unit
) {
    if (proxyHosts.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Language,
            message = stringResource(R.string.npm_no_proxy_hosts)
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(proxyHosts, key = { it.id }) { host ->
                ProxyHostCard(
                    proxyHost = host,
                    onClick = { onEdit(host) },
                    onEdit = { onEdit(host) },
                    onDelete = { onDelete(host) },
                    onToggle = { enabled -> onToggle(host, enabled) }
                )
            }
        }
    }
}

// ── Tab: Redirection Hosts ──

@Composable
private fun RedirectionHostsTab(
    redirectionHosts: List<NpmRedirectionHost>,
    onEdit: (NpmRedirectionHost) -> Unit,
    onDelete: (NpmRedirectionHost) -> Unit
) {
    if (redirectionHosts.isEmpty()) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.Redo,
            message = stringResource(R.string.npm_no_redirections)
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(redirectionHosts, key = { it.id }) { host ->
                RedirectionHostCard(
                    host = host,
                    onClick = { onEdit(host) },
                    onEdit = { onEdit(host) },
                    onDelete = { onDelete(host) }
                )
            }
        }
    }
}

@Composable
private fun RedirectionHostCard(
    host: NpmRedirectionHost,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    var showMenu by remember { mutableStateOf(false) }
    val statusColor = if (host.isEnabled) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = npmCardColor(isDarkTheme, accent = if (host.isEnabled) NpmOrange else MaterialTheme.colorScheme.onSurfaceVariant, tint = if (isDarkTheme) 0.09f else 0.06f),
        border = BorderStroke(1.dp, npmBorderColor(isDarkTheme, accent = if (host.isEnabled) NpmOrange else MaterialTheme.colorScheme.onSurfaceVariant)),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(statusColor))
                        Text(
                            host.primaryDomain,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = npmRaisedCardColor(isDarkTheme, accent = NpmOrange, tint = if (isDarkTheme) 0.10f else 0.07f)) {
                        Text(
                            "${host.forwardHttpCode}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = NpmOrange
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${host.forwardScheme}://${host.forwardDomainName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (host.hasSSL) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FeatureChip(icon = Icons.Default.Lock, text = "SSL")
                    }
                }
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { showMenu = false; onEdit() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                DropdownMenuItem(text = { Text(stringResource(R.string.npm_delete)) }, onClick = { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null) })
            }
        }
    }
}

// ── Tab: Streams ──

@Composable
private fun StreamsTab(
    streams: List<NpmStream>,
    onEdit: (NpmStream) -> Unit,
    onDelete: (NpmStream) -> Unit
) {
    if (streams.isEmpty()) {
        EmptyState(
            icon = Icons.Default.SwapHoriz,
            message = stringResource(R.string.npm_no_streams)
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(streams, key = { it.id }) { stream ->
                StreamCard(
                    stream = stream,
                    onClick = { onEdit(stream) },
                    onEdit = { onEdit(stream) },
                    onDelete = { onDelete(stream) }
                )
            }
        }
    }
}

@Composable
private fun StreamCard(
    stream: NpmStream,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    var showMenu by remember { mutableStateOf(false) }
    val statusColor = when {
        !stream.isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
        stream.isOnline -> StatusGreen
        else -> StatusRed
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = npmCardColor(isDarkTheme, accent = statusColor, tint = if (isDarkTheme) 0.10f else 0.06f),
        border = BorderStroke(1.dp, npmBorderColor(isDarkTheme, accent = statusColor)),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(statusColor))
                        Text(
                            ":${stream.incomingPort}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${stream.forwardingHost}:${stream.forwardingPort}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (stream.tcpForwarding == 1) FeatureChip(icon = Icons.Default.SwapHoriz, text = "TCP")
                    if (stream.udpForwarding == 1) FeatureChip(icon = Icons.Default.SwapHoriz, text = "UDP")
                }
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { showMenu = false; onEdit() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                DropdownMenuItem(text = { Text(stringResource(R.string.npm_delete)) }, onClick = { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null) })
            }
        }
    }
}

// ── Tab: Dead Hosts ──

@Composable
private fun DeadHostsTab(
    deadHosts: List<NpmDeadHost>,
    onEdit: (NpmDeadHost) -> Unit,
    onDelete: (NpmDeadHost) -> Unit
) {
    if (deadHosts.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Block,
            message = stringResource(R.string.npm_no_dead_hosts)
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(deadHosts, key = { it.id }) { host ->
                DeadHostCard(
                    host = host,
                    onClick = { onEdit(host) },
                    onEdit = { onEdit(host) },
                    onDelete = { onDelete(host) }
                )
            }
        }
    }
}

@Composable
private fun DeadHostCard(
    host: NpmDeadHost,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    var showMenu by remember { mutableStateOf(false) }
    val statusColor = if (host.isEnabled) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = npmCardColor(isDarkTheme, accent = if (host.isEnabled) StatusRed else MaterialTheme.colorScheme.onSurfaceVariant, tint = if (isDarkTheme) 0.08f else 0.05f),
        border = BorderStroke(1.dp, npmBorderColor(isDarkTheme, accent = if (host.isEnabled) StatusRed else MaterialTheme.colorScheme.onSurfaceVariant)),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(statusColor))
                    Text(
                        host.primaryDomain,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (host.domainNames.size > 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        host.domainNames.drop(1).joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (host.hasSSL) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FeatureChip(icon = Icons.Default.Lock, text = "SSL")
                    }
                }
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { showMenu = false; onEdit() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                DropdownMenuItem(text = { Text(stringResource(R.string.npm_delete)) }, onClick = { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null) })
            }
        }
    }
}

// ── Tab: SSL Certificates ──

@Composable
private fun SslTab(
    certificates: List<NpmCertificate>,
    onRenew: (NpmCertificate) -> Unit,
    onDelete: (NpmCertificate) -> Unit
) {
    if (certificates.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Lock,
            message = stringResource(R.string.npm_no_certificates)
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(certificates, key = { it.id }) { cert ->
                CertificateCard(cert = cert, onRenew = { onRenew(cert) }, onDelete = { onDelete(cert) })
            }
        }
    }
}

@Composable
private fun CertificateCard(
    cert: NpmCertificate,
    onRenew: () -> Unit,
    onDelete: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val formattedDate = cert.expiresOn?.let { exp ->
        try {
            val instant = Instant.parse(exp)
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.systemDefault())
                .format(instant)
        } catch (_: Exception) {
            null
        }
    }
    val expiryResId = when {
        cert.isExpired -> R.string.npm_expired
        formattedDate != null -> R.string.npm_expires
        else -> null
    }
    val expiryColor = if (cert.isExpired) StatusRed else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = npmCardColor(isDarkTheme, accent = if (cert.isExpired) StatusRed else if (cert.isLetsEncrypt) StatusGreen else NpmOrange, tint = if (isDarkTheme) 0.10f else 0.06f),
        border = BorderStroke(1.dp, npmBorderColor(isDarkTheme, accent = if (cert.isExpired) StatusRed else if (cert.isLetsEncrypt) StatusGreen else NpmOrange)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        cert.niceName.ifBlank { cert.primaryDomain },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (cert.domainNames.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            cert.domainNames.joinToString(", "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (cert.isLetsEncrypt) npmRaisedCardColor(isDarkTheme, accent = StatusGreen, tint = if (isDarkTheme) 0.10f else 0.07f)
                    else npmRaisedCardColor(isDarkTheme, accent = NpmOrange, tint = if (isDarkTheme) 0.08f else 0.05f),
                    border = BorderStroke(1.dp, npmBorderTone(isDarkTheme, accent = if (cert.isLetsEncrypt) StatusGreen else NpmOrange).copy(alpha = if (isDarkTheme) 0.62f else 0.52f))
                ) {
                    Text(
                        if (cert.isLetsEncrypt) stringResource(R.string.npm_letsencrypt) else stringResource(R.string.npm_custom_cert),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (cert.isLetsEncrypt) StatusGreen else NpmOrange
                    )
                }
            }

            if (expiryResId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (expiryResId == R.string.npm_expires) stringResource(expiryResId, formattedDate!!) else stringResource(expiryResId),
                    style = MaterialTheme.typography.bodySmall, 
                    color = expiryColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (cert.isLetsEncrypt) {
                    OutlinedButton(
                        onClick = onRenew,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.npm_renew))
                    }
                }
                OutlinedButton(
                    onClick = onDelete,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.npm_delete))
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(top = 60.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = message,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
