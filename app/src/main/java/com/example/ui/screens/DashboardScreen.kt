package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StreamState
import com.example.streaming.rtmp.MultiStreamManager
import com.example.ui.components.MetricCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*

@Composable
fun DashboardScreen(
    multiStreamManager: MultiStreamManager,
    onNavigateToVideos: () -> Unit,
    onNavigateToDestinations: () -> Unit,
    onNavigateToLiveConfig: () -> Unit,
    onNavigateToMultiLive: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToBatterySaver: () -> Unit
) {
    val diagnostics by multiStreamManager.diagnostics.collectAsState()
    val sessions by multiStreamManager.sessionsState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Live Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (diagnostics.isAnyStreaming) CrimsonDark.copy(alpha = 0.4f) else SurfaceCard
            ),
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
                            text = "YouTube Loop Live",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (diagnostics.isAnyStreaming) "Phone-Direct Infinite Streaming Active" else "Ready to stream prepared video",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    StatusBadge(state = if (diagnostics.isAnyStreaming) StreamState.LIVE else StreamState.IDLE)
                }

                HorizontalDivider(color = SurfaceBorder)

                // Quick stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        label = "Bitrate",
                        value = "${diagnostics.totalOutputBitrateBps / 1000} kbps",
                        icon = Icons.Default.Speed,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "Loops",
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

                // Action Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (diagnostics.isAnyStreaming) {
                        Button(
                            onClick = onNavigateToMultiLive,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("view_active_streams_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                        ) {
                            Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Manage Streams (${diagnostics.activeStreamCount})")
                        }

                        OutlinedButton(
                            onClick = { multiStreamManager.stopAllStreaming() },
                            modifier = Modifier.testTag("stop_all_streams_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stop All")
                        }
                    } else {
                        Button(
                            onClick = onNavigateToLiveConfig,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("start_streaming_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Configure & Start Live Stream", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Feature Navigation Quick Grid
        Text(
            text = "Workspace Modules",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModuleNavCard(
                title = "Video Library",
                subtitle = "Import & Prepare Once",
                icon = Icons.Default.VideoLibrary,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToVideos
            )
            ModuleNavCard(
                title = "Destinations",
                subtitle = "YouTube Stream Keys",
                icon = Icons.Default.CastConnected,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToDestinations
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModuleNavCard(
                title = "Diagnostics",
                subtitle = "Telemetry & Error Logs",
                icon = Icons.Default.BugReport,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToDiagnostics
            )
            ModuleNavCard(
                title = "Battery Saver",
                subtitle = "Thermal & Resource Opt",
                icon = Icons.Default.BatteryChargingFull,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToBatterySaver
            )
        }

        // Battery & Performance Guarantee Notice Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.EnergySavingsLeaf,
                    contentDescription = null,
                    tint = StatusLive,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "Zero Re-Encoding Guarantee",
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Prepared encoded packets are directly looped through RTMP without decoding/encoding passes, maximizing battery life during 24/7 broadcasts.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleNavCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CrimsonLight,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}
