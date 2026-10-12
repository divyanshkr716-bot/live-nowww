package com.example.ui.screens

import android.content.Context
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.SettingsRepository
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatterySaverScreen(
    settingsRepository: SettingsRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settings by settingsRepository.settingsFlow.collectAsState(initial = com.example.data.repository.UserSettings())

    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val isSystemPowerSave = powerManager?.isPowerSaveMode == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Battery Saver & Thermal", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Priority Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = StatusLive, modifier = Modifier.size(32.dp))
                    Column {
                        Text("Battery Efficiency Priority", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            text = "Zero video re-encoding during normal loops, packet-level streaming, no background preview rendering, and bounded wake locks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Battery Saver Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(12.dp)
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Aggressive Battery Saver", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(
                                "Caps concurrent destinations to 1, reduces logging frequency, and increases network packet buffering.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = settings.batterySaverEnabled,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch { settingsRepository.setBatterySaver(enabled) }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = TextPrimary, checkedTrackColor = CrimsonPrimary),
                            modifier = Modifier.testTag("battery_saver_switch")
                        )
                    }

                    if (isSystemPowerSave) {
                        Surface(
                            color = StatusWarning.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "System Battery Saver is currently ON. Background execution is constrained by Android OS.",
                                color = StatusWarning,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // Max Active Streams
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Concurrent Stream Destination Limit", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Limit: ${settings.maxActiveStreams} active destination(s)", color = TextSecondary, fontSize = 13.sp)
                    Slider(
                        value = settings.maxActiveStreams.toFloat(),
                        onValueChange = { max ->
                            coroutineScope.launch { settingsRepository.setMaxActiveStreams(max.toInt()) }
                        },
                        valueRange = 1f..4f,
                        steps = 2,
                        colors = SliderDefaults.colors(thumbColor = CrimsonLight, activeTrackColor = CrimsonPrimary)
                    )
                }
            }

            // Optimization Checklist (PRD Section 13)
            Text("Active Battery Optimizations", fontWeight = FontWeight.Bold, color = TextPrimary)

            OptimizationItem(title = "No Video Re-Encoding During Loops", desc = "Prepared encoded packets fed directly to RTMP, eliminating continuous GPU/CPU encoder load.")
            OptimizationItem(title = "Zero Background Preview Rendering", desc = "Surface view rendering stops when app is minimized to save GPU power.")
            OptimizationItem(title = "Event-Driven Buffered I/O", desc = "64KB-512KB buffered file reads and event-driven TCP/TLS socket output.")
            OptimizationItem(title = "Minimal Partial Wake Lock", desc = "CPU wake lock is acquired strictly while streaming is active and immediately released when stopped.")
        }
    }
}

@Composable
private fun OptimizationItem(title: String, desc: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusLive, modifier = Modifier.size(18.dp))
            Column {
                Text(text = title, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                Text(text = desc, color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}
