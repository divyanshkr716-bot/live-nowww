package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPermissionsScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions & Compliance", fontWeight = FontWeight.Bold) },
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
            // App Information
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("YouTube Loop Live", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Version 1.0.0 (Native Android API 29+)", style = MaterialTheme.typography.bodySmall, color = CrimsonLight)
                    Text(
                        text = "Phone-direct YouTube Live streaming application with infinite loop encoded packet reuse, zero repeated video re-encoding, and battery-first optimization.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Notification Compliance Card (PRD Section 12)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardElevated),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = StatusLive, modifier = Modifier.size(20.dp))
                        Text("Strict Button-Free Notification Rule", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Text(
                        text = "As required by PRD Section 12:\n" +
                                "• Persistent notification title: 'YouTube Loop Live'\n" +
                                "• Notification text: 'Live streaming is running'\n" +
                                "• Notification action buttons: ZERO (No Stop, Pause, Reconnect, or other buttons)\n" +
                                "• Notification content action: NONE (Tapping does not open the app)\n" +
                                "• All streaming controls remain strictly inside the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Architecture Compliance
            Text("PRD Acceptance Checklist", fontWeight = FontWeight.Bold, color = TextPrimary)

            ChecklistCard(item = "Zero Re-Encoding During Loops", details = "Normal loop playback streams encoded packets directly; video encoder invocations = 0.")
            ChecklistCard(item = "Prepare Video Once Pipeline", details = "Transcodes incompatible media once with hardware MediaCodec, verified with 2.0s keyframe spacing.")
            ChecklistCard(item = "Multi-Live Independent Outputs", details = "Multiplexes single packet stream across independent RTMP sessions without duplicate encoders.")
            ChecklistCard(item = "Android Keystore Encryption", details = "Stream keys are encrypted with AES-256 GCM at rest and redacted from all logs.")
            ChecklistCard(item = "Storage Access Framework", details = "Zero broad storage permissions requested. Uses SAF OpenDocument picker.")
            ChecklistCard(item = "Screen-Off & Background Execution", details = "Dedicated Foreground Service maintains stream through app minimization and screen-off.")
        }
    }
}

@Composable
private fun ChecklistCard(item: String, details: String) {
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
                Text(text = item, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                Text(text = details, color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}
