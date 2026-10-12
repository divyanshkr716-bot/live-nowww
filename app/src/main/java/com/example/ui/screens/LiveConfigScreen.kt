package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.data.model.StreamDestinationEntity
import com.example.data.model.VideoEntity
import com.example.data.repository.DestinationRepository
import com.example.data.repository.VideoRepository
import com.example.service.StreamForegroundService
import com.example.streaming.rtmp.MultiStreamManager
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveConfigScreen(
    videoRepository: VideoRepository,
    destinationRepository: DestinationRepository,
    multiStreamManager: MultiStreamManager,
    onNavigateBack: () -> Unit,
    onNavigateToMultiLive: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val videos by videoRepository.allVideos.collectAsState(initial = emptyList())
    val destinations by destinationRepository.allDestinations.collectAsState(initial = emptyList())

    val readyVideos = remember(videos) {
        videos.filter { it.preparationStatus == VideoEntity.STATUS_READY && it.preparedFilePath != null }
    }

    var selectedVideoId by remember { mutableStateOf<Long?>(readyVideos.firstOrNull()?.videoId) }
    val selectedDestinationIds = remember { mutableStateListOf<Long>() }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isStarting by remember { mutableStateOf(false) }

    LaunchedEffect(readyVideos) {
        if (selectedVideoId == null && readyVideos.isNotEmpty()) {
            selectedVideoId = readyVideos.first().videoId
        }
    }

    LaunchedEffect(destinations) {
        if (selectedDestinationIds.isEmpty()) {
            destinations.filter { it.enabled }.forEach { selectedDestinationIds.add(it.destinationId) }
        }
    }

    // Permission launcher for Android 13+ (POST_NOTIFICATIONS)
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Continue regardless of grant to avoid blocking live stream setup
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Configuration", fontWeight = FontWeight.Bold) },
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
            // Video Selection
            Text(
                text = "1. Select Prepared Video for Infinite Loop",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            if (readyVideos.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StatusWarning.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("No Prepared Videos Ready", fontWeight = FontWeight.Bold, color = StatusWarning)
                        Text(
                            text = "You must prepare a video once in the Video Library before starting an infinite loop stream.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }
            } else {
                readyVideos.forEach { video ->
                    val isSelected = selectedVideoId == video.videoId
                    Card(
                        onClick = { selectedVideoId = video.videoId },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) CrimsonDark.copy(alpha = 0.3f) else SurfaceCard
                        ),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, CrimsonLight) else null,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = video.displayName,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${formatDuration(video.durationMs)} • ${video.width}x${video.height} • ${formatFileSize(video.preparedFileSize)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedVideoId = video.videoId },
                                colors = RadioButtonDefaults.colors(selectedColor = CrimsonLight)
                            )
                        }
                    }
                }
            }

            // Destinations Selection
            Text(
                text = "2. Select Output Destinations",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            if (destinations.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StatusWarning.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("No Destinations Configured", fontWeight = FontWeight.Bold, color = StatusWarning)
                        Text(
                            text = "Please add at least one YouTube Live destination with your stream key.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }
            } else {
                destinations.forEach { dest ->
                    val isChecked = selectedDestinationIds.contains(dest.destinationId)
                    Card(
                        onClick = {
                            if (isChecked) selectedDestinationIds.remove(dest.destinationId)
                            else selectedDestinationIds.add(dest.destinationId)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isChecked) SurfaceCardElevated else SurfaceCard
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dest.displayName,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = dest.ingestUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) selectedDestinationIds.add(dest.destinationId)
                                    else selectedDestinationIds.remove(dest.destinationId)
                                },
                                colors = CheckboxDefaults.colors(checkedColor = CrimsonLight)
                            )
                        }
                    }
                }
            }

            // Foreground Service notice
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = StatusLive, modifier = Modifier.size(18.dp))
                        Text("Background Streaming Service", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                    Text(
                        text = "Streaming is maintained by a dedicated Foreground Service with a persistent notification. Continues when app is minimized, in Recent Apps, or screen is turned off.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            errorMessage?.let { err ->
                Text(text = err, color = StatusError, fontSize = 13.sp)
            }

            Button(
                onClick = {
                    val vid = selectedVideoId
                    if (vid == null) {
                        errorMessage = "Please select a prepared video."
                        return@Button
                    }
                    if (selectedDestinationIds.isEmpty()) {
                        errorMessage = "Please select at least one destination."
                        return@Button
                    }

                    isStarting = true
                    errorMessage = null

                    coroutineScope.launch {
                        // Start foreground service first
                        StreamForegroundService.start(context)

                        // Start stream sessions
                        val result = multiStreamManager.startStreaming(
                            videoId = vid,
                            destinationIds = selectedDestinationIds.toList()
                        )

                        isStarting = false
                        if (result.isSuccess) {
                            onNavigateToMultiLive()
                        } else {
                            errorMessage = result.exceptionOrNull()?.message ?: "Failed to start streaming."
                        }
                    }
                },
                enabled = readyVideos.isNotEmpty() && selectedDestinationIds.isNotEmpty() && !isStarting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("launch_infinite_loop_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
            ) {
                if (isStarting) {
                    CircularProgressIndicator(color = TextPrimary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Connecting RTMP...")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start Infinite Loop Live", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
