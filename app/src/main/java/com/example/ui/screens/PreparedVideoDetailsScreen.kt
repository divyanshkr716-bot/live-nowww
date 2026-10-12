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
import com.example.data.model.VideoEntity
import com.example.data.repository.VideoRepository
import com.example.media.preparation.VideoPreparationEngine
import com.example.ui.components.ConfirmDialog
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreparedVideoDetailsScreen(
    videoId: Long,
    videoRepository: VideoRepository,
    onNavigateBack: () -> Unit,
    onNavigateToLiveConfig: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var video by remember { mutableStateOf<VideoEntity?>(null) }
    var revalidationMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val preparationEngine = remember { VideoPreparationEngine(context) }

    LaunchedEffect(videoId) {
        video = videoRepository.getVideoById(videoId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prepared Video Details", fontWeight = FontWeight.Bold) },
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
                // Status Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (v.preparationStatus == VideoEntity.STATUS_READY) StatusLive.copy(alpha = 0.15f) else StatusError.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (v.preparationStatus == VideoEntity.STATUS_READY) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (v.preparationStatus == VideoEntity.STATUS_READY) StatusLive else StatusError,
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = if (v.preparationStatus == VideoEntity.STATUS_READY) "Ready for Live Streaming" else "Preparation Incomplete",
                                fontWeight = FontWeight.Bold,
                                color = if (v.preparationStatus == VideoEntity.STATUS_READY) StatusLive else StatusError
                            )
                            Text(
                                text = v.validationMessage.ifEmpty { "Prepared copy stored locally and validated for RTMP looping." },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                    }
                }

                // File Metrics
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Prepared Storage Reference", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            text = v.preparedFilePath ?: "No prepared file path",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )

                        HorizontalDivider(color = SurfaceBorder)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Prepared Size:", color = TextSecondary, fontSize = 13.sp)
                            Text(formatFileSize(v.preparedFileSize), color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Original Size:", color = TextSecondary, fontSize = 13.sp)
                            Text(formatFileSize(v.fileSize), color = TextPrimary, fontSize = 13.sp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Validation Status:", color = TextSecondary, fontSize = 13.sp)
                            Text(v.validationStatus, color = StatusLive, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Cache Fingerprint:", color = TextSecondary, fontSize = 13.sp)
                            Text(v.preparationFingerprint?.take(12) ?: "N/A", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }

                revalidationMessage?.let { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCardElevated),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = msg,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val path = v.preparedFilePath
                            if (path != null) {
                                val file = File(path)
                                val (isValid, msg) = preparationEngine.validatePreparedFile(file)
                                revalidationMessage = if (isValid) "Revalidation PASSED: $msg" else "Revalidation FAILED: $msg"
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("revalidate_prepared_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Revalidate")
                    }

                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("delete_prepared_copy_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Remove")
                    }
                }

                if (v.preparationStatus == VideoEntity.STATUS_READY) {
                    Button(
                        onClick = onNavigateToLiveConfig,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("start_live_with_video_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                    ) {
                        Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Stream This Video in Infinite Loop", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && video != null) {
        ConfirmDialog(
            title = "Delete Prepared Copy?",
            message = "Remove the cached prepared version of '${video!!.displayName}'? The original video file will remain safe.",
            confirmLabel = "Delete",
            isDestructive = true,
            onConfirm = {
                coroutineScope.launch {
                    videoRepository.deletePreparedFileOnly(video!!)
                    showDeleteConfirm = false
                    onNavigateBack()
                }
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
