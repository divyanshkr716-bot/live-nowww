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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PreparationPreset
import com.example.data.model.VideoEntity
import com.example.data.repository.VideoRepository
import com.example.media.preparation.PreparationFingerprint
import com.example.media.preparation.PreparationResult
import com.example.media.preparation.VideoPreparationEngine
import com.example.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepareVideoScreen(
    videoId: Long,
    videoRepository: VideoRepository,
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var video by remember { mutableStateOf<VideoEntity?>(null) }

    var selectedPreset by remember { mutableStateOf(PreparationPreset.PASSTHROUGH_REMUX) }
    var isPreparing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var stageDescription by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var preparationJob by remember { mutableStateOf<Job?>(null) }

    val preparationEngine = remember { VideoPreparationEngine(context) }

    LaunchedEffect(videoId) {
        video = videoRepository.getVideoById(videoId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prepare Video Once", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !isPreparing) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = SurfaceDark
    ) { innerPadding ->
        val v = video
        if (v == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CrimsonLight)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Video Summary Header
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = v.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Duration: ${formatDuration(v.durationMs)} • Source: ${v.width}x${v.height} • ${formatFileSize(v.fileSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                if (isPreparing) {
                    // Preparation In Progress Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCardElevated),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Preparing Video for Streaming",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .testTag("preparation_progress_bar"),
                                color = CrimsonPrimary,
                                trackColor = SurfaceBorder
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stageDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = CrimsonLight
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    preparationJob?.cancel()
                                    isPreparing = false
                                    stageDescription = "Preparation cancelled by user."
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                                modifier = Modifier.testTag("cancel_preparation_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Cancel Preparation")
                            }
                        }
                    }
                } else {
                    // Preset Selection
                    Text(
                        text = "Select Streaming Quality Preset",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    PreparationPreset.values().forEach { preset ->
                        PresetOptionCard(
                            preset = preset,
                            isSelected = selectedPreset == preset,
                            onClick = { selectedPreset = preset }
                        )
                    }

                    // Keyframe & Monotonicity Guarantee Notice
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
                                Icon(Icons.Default.Verified, contentDescription = null, tint = StatusLive, modifier = Modifier.size(20.dp))
                                Text("One-Time Preparation Rules", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            }
                            Text(
                                text = "• Target ~2-second keyframe spacing (YouTube Live requirement)\n" +
                                        "• Remuxes without decoding if already compatible (H.264/AAC)\n" +
                                        "• Stores prepared media locally; subsequent loops reuse packets directly with 0 encoder passes",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    errorMessage?.let { err ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = StatusError.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = err,
                                color = StatusError,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            isPreparing = true
                            progress = 0f
                            stageDescription = "Starting preparation..."
                            errorMessage = null

                            preparationJob = coroutineScope.launch {
                                // Mark in DB as preparing
                                videoRepository.updateVideo(v.copy(preparationStatus = VideoEntity.STATUS_PREPARING))

                                val result = preparationEngine.prepareVideo(
                                    video = v,
                                    preset = selectedPreset,
                                    onProgress = { p, stage ->
                                        progress = p
                                        stageDescription = stage
                                    }
                                )

                                when (result) {
                                    is PreparationResult.Success -> {
                                        val settingsFp = PreparationFingerprint.generateSettingsFingerprint(selectedPreset)
                                        val updatedVideo = v.copy(
                                            preparedFilePath = result.preparedFile.absolutePath,
                                            preparedFileSize = result.preparedFileSize,
                                            preparationFingerprint = settingsFp,
                                            preparationStatus = VideoEntity.STATUS_READY,
                                            validationStatus = VideoEntity.VALIDATION_VALID,
                                            validationMessage = result.validationMessage,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                        videoRepository.updateVideo(updatedVideo)
                                        isPreparing = false
                                        onNavigateToDetails(videoId)
                                    }
                                    is PreparationResult.Error -> {
                                        videoRepository.updateVideo(
                                            v.copy(
                                                preparationStatus = VideoEntity.STATUS_FAILED,
                                                validationStatus = VideoEntity.VALIDATION_INVALID,
                                                validationMessage = result.message
                                            )
                                        )
                                        isPreparing = false
                                        errorMessage = result.message
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("start_preparation_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                    ) {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Start One-Time Preparation", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetOptionCard(
    preset: PreparationPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CrimsonDark.copy(alpha = 0.25f) else SurfaceCard
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
                    text = preset.title,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) TextPrimary else TextSecondary,
                    fontSize = 14.sp
                )
                Text(
                    text = if (preset.targetVideoBitrateBps > 0) {
                        "Target: ${preset.targetWidth}x${preset.targetHeight} @ ${preset.targetFps}fps, ${preset.targetVideoBitrateBps / 1000}kbps, 2s keyframe"
                    } else {
                        "Preserves original video/audio packets if compatible, otherwise normalizes container"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = CrimsonLight)
            )
        }
    }
}
