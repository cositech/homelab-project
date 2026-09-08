package com.homelab.app.ui.pbs

import com.homelab.app.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.data.repository.ProxmoxBackupSyncJob
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.proxmox.components.ProxmoxEmptyState
import com.homelab.app.ui.theme.isThemeDark
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxmoxBackupServerSyncJobsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProxmoxBackupServerViewModel = hiltViewModel()
) {
    val syncJobsState by viewModel.syncJobsState.collectAsStateWithLifecycle()
    val triggeringJobId by viewModel.triggeringJobId.collectAsStateWithLifecycle()
    val isDark = isThemeDark()
    val serviceColor = ServiceType.PROXMOX_BACKUP_SERVER.primaryColor
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.fetchSyncJobs()
    }

    fun refresh() {
        isRefreshing = true
        scope.launch {
            viewModel.fetchSyncJobs()
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pbs_sync_jobs)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refresh() },
            modifier = Modifier.padding(padding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = syncJobsState) {
                    is UiState.Idle, is UiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> {
                        ErrorScreen(
                            message = state.message,
                            onRetry = state.retryAction ?: { viewModel.fetchSyncJobs() }
                        )
                    }
                    is UiState.Success -> {
                        val jobs = state.data
                        if (jobs.isEmpty()) {
                            ProxmoxEmptyState(
                                icon = Icons.Default.Sync,
                                title = stringResource(R.string.pbs_sync_jobs_empty)
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(jobs, key = { it.id }) { job ->
                                    SyncJobCard(
                                        job = job,
                                        color = serviceColor,
                                        isDark = isDark,
                                        isTriggering = triggeringJobId == job.id,
                                        isAnyTriggering = triggeringJobId != null,
                                        onRunNow = { viewModel.triggerSyncJob(job.id) }
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncJobCard(
    job: ProxmoxBackupSyncJob,
    color: Color,
    isDark: Boolean,
    isTriggering: Boolean,
    isAnyTriggering: Boolean,
    onRunNow: () -> Unit
) {
    val cardColor = color.copy(alpha = if (isDark) 0.07f else 0.08f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = job.id,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (!job.remote.isNullOrBlank()) {
                        Text(
                            text = stringResource(
                                R.string.pbs_sync_from_remote,
                                job.remoteStore?.let { "${job.remote}/$it" } ?: job.remote
                            ),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = job.schedule ?: stringResource(R.string.proxmox_no_schedule),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = job.store,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            if (!job.comment.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = job.comment,
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onRunNow,
                enabled = !isAnyTriggering,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = color),
                contentPadding = PaddingValues(vertical = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isTriggering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.proxmox_run_now), fontSize = 12.sp)
                }
            }
        }
    }
}
