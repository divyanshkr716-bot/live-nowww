package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StreamState
import com.example.streaming.rtmp.MultiStreamManager
import com.example.streaming.rtmp.StreamSessionState
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.MetricCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiLiveDashboardScreen(
    multiStreamManager: MultiStreamManager,
    onNavigateBack: () -> Unit,
    onNavigateToDiagnostics: () -> Unit
) {
    val diagnostics by multiStreamManager.diagnostics.collectAsState()
    val sessions by multiStreamManager.sessionsState.collectAsState()
    var showStopAllConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Multi-Live Dashboard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToDiagnostics) {
                        Icon(Icons.Default.BugReport, contentDescription = "Diagnostics")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = SurfaceDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Metrics Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Infinite Loop Broadcast",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Elapsed Uptime: ${formatUptime(diagnostics.totalUptimeMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        StatusBadge(state = if (diagnostics.isAnyStreaming) StreamState.LIVE else StreamState.STOPPED)
                    }

                    HorizontalDivider(color = SurfaceBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            label = "Output Rate",
                            value = "${diagnostics.totalOutputBitrateBps / 1000} kbps",
                            icon = Icons.Default.Speed,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "Loop Count",
                            value = "${diagnostics.totalLoopsCompleted}",
                            icon = Icons.Default.Repeat,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "Encoders",
                            value = "${diagnostics.encoderInvocationCount}",
                            subValue = "0 during loop",
                            icon = Icons.Default.Memory,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (diagnostics.isAnyStreaming) {
                        Button(
                            onClick = { showStopAllConfirm = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("stop_all_broadcasts_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Stop All Broadcasts", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Destinations List
            Text(
                text = "Independent Stream Destinations (${sessions.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No active stream sessions running.", color = TextMuted)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 40.dp)
                ) {
                    items(sessions.values.toList(), key = { it.destinationId }) { session ->
                        DestinationLiveCard(
                            session = session,
                            onStop = { multiStreamManager.stopDestination(session.destinationId) }
                        )
                    }
                }
            }
        }
    }

    if (showStopAllConfirm) {
        ConfirmDialog(
            title = "Stop All Broadcasts?",
            message = "This will gracefully disconnect all YouTube Live RTMP sessions and release the foreground service.",
            confirmLabel = "Stop Streaming",
            isDestructive = true,
            onConfirm = {
                multiStreamManager.stopAllStreaming()
                showStopAllConfirm = false
            },
            onDismiss = { showStopAllConfirm = false }
        )
    }
}

@Composable
private fun DestinationLiveCard(
    session: StreamSessionState,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = session.ingestUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
                StatusBadge(state = session.state)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Bitrate: ${session.measuredBitrateBps / 1000} kbps",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = "Packets: ${session.totalPacketsSent}",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                if (session.retryAttempt > 0) {
                    Text(
                        text = "Retry #${session.retryAttempt}",
                        color = StatusWarning,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            session.lastError?.let { err ->
                Text(
                    text = err,
                    color = StatusError,
                    fontSize = 12.sp
                )
            }

            if (session.state.isStreamingActive) {
                HorizontalDivider(color = SurfaceBorder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onStop,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Stop Destination", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

fun formatUptime(ms: Long): String {
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val min = (totalSec % 3600) / 60
    val sec = totalSec % 60
    return if (hours > 0) "%02d:%02d:%02d".format(hours, min, sec) else "%02d:%02d".format(min, sec)
}
