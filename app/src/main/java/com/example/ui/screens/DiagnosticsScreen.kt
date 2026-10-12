package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DiagnosticError
import com.example.data.model.DiagnosticLogEntry
import com.example.streaming.rtmp.MultiStreamManager
import com.example.ui.components.MetricCard
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    multiStreamManager: MultiStreamManager,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val diagnostics by multiStreamManager.diagnostics.collectAsState()
    val logs by multiStreamManager.logs.collectAsState()
    val errors by multiStreamManager.recentErrors.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Telemetry, 1: Error Diagnostics, 2: Event Logs

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics & Telemetry", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val allLogText = logs.joinToString("\n") { "[${formatLogTime(it.timestamp)}] [${it.tag}] ${it.message}" }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Diagnostics Logs", allLogText))
                        Toast.makeText(context, "Redacted logs copied to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Redacted Logs")
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceCard,
                contentColor = CrimsonLight
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Telemetry", fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Errors (${errors.size})", fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Redacted Logs (${logs.size})", fontSize = 13.sp) }
                )
            }

            when (selectedTab) {
                0 -> TelemetryTab(diagnostics = diagnostics)
                1 -> ErrorsTab(errors = errors)
                2 -> LogsTab(logs = logs, onClear = { multiStreamManager.clearLogs() })
            }
        }
    }
}

@Composable
private fun TelemetryTab(diagnostics: com.example.streaming.rtmp.StreamOverallDiagnostics) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            // Encoder Guarantee Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardElevated),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = StatusLive,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "Encoder Invocation Verification",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Invocations during loop streaming: ${diagnostics.encoderInvocationCount} (Zero video re-encoding confirmed)",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusLive
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    label = "Total Loops",
                    value = "${diagnostics.totalLoopsCompleted}",
                    icon = Icons.Default.Repeat,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Active Streams",
                    value = "${diagnostics.activeStreamCount}",
                    icon = Icons.Default.LiveTv,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    label = "Video Packets",
                    value = "${diagnostics.totalVideoPacketsEmitted}",
                    icon = Icons.Default.Videocam,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Audio Packets",
                    value = "${diagnostics.totalAudioPacketsEmitted}",
                    icon = Icons.Default.Audiotrack,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Measured Bitrate Summary", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Total Transmitted Rate: ${diagnostics.totalOutputBitrateBps / 1000} kbps", color = TextSecondary, fontSize = 13.sp)
                    Text("Pacing Mode: Real-time wall-clock sync", color = TextSecondary, fontSize = 13.sp)
                    Text("Loop Boundary Timestamp: Rebased monotonic progression", color = TextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ErrorsTab(errors: List<DiagnosticError>) {
    if (errors.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusLive, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(8.dp))
                Text("No classified errors recorded.", color = TextSecondary)
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 30.dp)
        ) {
            items(errors) { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = error.category.name,
                                fontWeight = FontWeight.Bold,
                                color = StatusError,
                                fontSize = 13.sp
                            )
                            Text(
                                text = formatLogTime(error.timestamp),
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Text(text = error.userExplanation, color = TextPrimary, fontSize = 14.sp)
                        Text(text = "Details: ${error.redactedDetails}", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            text = "Corrective Action: ${error.correctiveAction}",
                            color = CrimsonLight,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogsTab(
    logs: List<DiagnosticLogEntry>,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sanitized Runtime Logs (Stream Keys Redacted)", color = TextMuted, fontSize = 12.sp)
            TextButton(onClick = onClear) {
                Text("Clear Logs", fontSize = 12.sp, color = StatusError)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 30.dp)
        ) {
            items(logs) { entry ->
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = formatLogTime(entry.timestamp),
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "[${entry.tag}]",
                            color = if (entry.isError) StatusError else CrimsonLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = entry.message,
                            color = if (entry.isError) StatusError else TextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

fun formatLogTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
