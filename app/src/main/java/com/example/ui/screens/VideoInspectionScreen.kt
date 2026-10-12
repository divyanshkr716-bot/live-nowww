package com.example.ui.screens

import android.net.Uri
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
import com.example.data.model.MediaMetadata
import com.example.data.model.VideoEntity
import com.example.data.repository.VideoRepository
import com.example.media.inspection.MediaInspector
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoInspectionScreen(
    videoId: Long,
    videoRepository: VideoRepository,
    onNavigateBack: () -> Unit,
    onNavigateToPrepare: (Long) -> Unit
) {
    val context = LocalContext.current
    var video by remember { mutableStateOf<VideoEntity?>(null) }
    var metadata by remember { mutableStateOf<MediaMetadata?>(null) }
    var isInspecting by remember { mutableStateOf(true) }

    LaunchedEffect(videoId) {
        val v = videoRepository.getVideoById(videoId)
        video = v
        if (v != null) {
            val inspector = MediaInspector(context)
            val uri = Uri.parse(v.originalUriReference)
            val meta = inspector.inspectMedia(uri, v.displayName, v.fileSize)
            metadata = meta
            isInspecting = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Inspection", fontWeight = FontWeight.Bold) },
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
        if (isInspecting || video == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CrimsonLight)
            }
        } else {
            val meta = metadata
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header info
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = video!!.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Original file size: ${formatFileSize(video!!.fileSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                // Compatibility Verdict Card
                val isCompatible = meta?.isDirectStreamingCompatible == true
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCompatible) StatusLive.copy(alpha = 0.15f) else StatusWarning.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isCompatible) Icons.Default.CheckCircle else Icons.Default.Build,
                            contentDescription = null,
                            tint = if (isCompatible) StatusLive else StatusWarning,
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = if (isCompatible) "Ready for Passthrough Remuxing" else "One-Time Preparation Required",
                                fontWeight = FontWeight.Bold,
                                color = if (isCompatible) StatusLive else StatusWarning
                            )
                            Text(
                                text = meta?.compatibilitySummary ?: "Analyzing media characteristics...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                    }
                }

                // Deep Inspection Grid
                Text(
                    text = "Stream Compatibility Diagnostics",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                InspectionRow(label = "Container Format", value = meta?.containerFormat?.uppercase() ?: "MP4", isOk = true)
                InspectionRow(label = "Video Codec", value = meta?.videoCodec ?: "Unknown", isOk = meta?.videoCodec?.contains("avc", ignoreCase = true) == true)
                InspectionRow(label = "Audio Codec", value = meta?.audioCodec ?: "Unknown", isOk = meta?.audioCodec?.contains("mp4a", ignoreCase = true) == true || meta?.hasAudioTrack == false)
                InspectionRow(label = "Resolution", value = "${meta?.width} x ${meta?.height}", isOk = (meta?.width ?: 0) > 0)
                InspectionRow(label = "Frame Rate", value = "${String.format("%.1f", meta?.frameRate ?: 0.0)} fps", isOk = (meta?.frameRate ?: 0.0) >= 20)
                InspectionRow(label = "Keyframe Interval", value = "${String.format("%.2f", meta?.keyframeIntervalSec ?: 0.0)}s (Target ~2.0s)", isOk = (meta?.keyframeIntervalSec ?: 0.0) in 1.0..4.0)
                InspectionRow(label = "Timestamps Monotonic", value = if (meta?.isTimestampContinuous == true) "Continuous & Aligned" else "Non-Monotonic", isOk = meta?.isTimestampContinuous == true)
                InspectionRow(label = "Audio Track Present", value = if (meta?.hasAudioTrack == true) "Yes (${meta.audioChannels}ch @ ${meta.audioSampleRate}Hz)" else "No (Silent video supported)", isOk = true)

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { onNavigateToPrepare(videoId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("proceed_to_prepare_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Proceed to Prepare Video Once", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun InspectionRow(
    label: String,
    value: String,
    isOk: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = TextSecondary, fontSize = 13.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = value, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Icon(
                    imageVector = if (isOk) Icons.Default.Check else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isOk) StatusLive else StatusWarning,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
