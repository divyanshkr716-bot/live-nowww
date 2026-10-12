package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.Sort
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
import com.example.media.inspection.MediaInspector
import com.example.media.preparation.PreparationFingerprint
import com.example.ui.components.ConfirmDialog
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoLibraryScreen(
    videoRepository: VideoRepository,
    onNavigateToInspect: (Long) -> Unit,
    onNavigateToPrepare: (Long) -> Unit,
    onNavigateToDetails: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val videos by videoRepository.allVideos.collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("date") } // "date", "name", "size"
    var videoToDelete by remember { mutableStateOf<VideoEntity?>(null) }
    var videoToRemovePrepared by remember { mutableStateOf<VideoEntity?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    // SAF Document Picker for Video files (Rule: Storage Access Framework, minimal permissions)
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isImporting = true
                try {
                    // Query filename and size from content resolver
                    var displayName = "video_${System.currentTimeMillis()}.mp4"
                    var fileSize = 0L

                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) displayName = cursor.getString(nameIndex) ?: displayName
                            if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                        }
                    }

                    // Inspect media metadata
                    val inspector = MediaInspector(context)
                    val metadata = inspector.inspectMedia(uri, displayName, fileSize)

                    val sourceFingerprint = PreparationFingerprint.generateSourceFingerprint(
                        displayName,
                        fileSize,
                        metadata.durationMs
                    )

                    val videoEntity = VideoEntity(
                        displayName = displayName,
                        originalUriReference = uri.toString(),
                        sourceFingerprint = sourceFingerprint,
                        durationMs = metadata.durationMs,
                        width = metadata.width,
                        height = metadata.height,
                        frameRate = metadata.frameRate,
                        videoCodec = metadata.videoCodec,
                        audioCodec = metadata.audioCodec,
                        fileSize = fileSize,
                        preparationStatus = VideoEntity.STATUS_NOT_PREPARED,
                        validationStatus = VideoEntity.VALIDATION_PENDING,
                        keyframeIntervalSec = metadata.keyframeIntervalSec
                    )

                    val newId = videoRepository.insertVideo(videoEntity)
                    onNavigateToInspect(newId)
                } catch (e: Exception) {
                    // Handled
                } finally {
                    isImporting = false
                }
            }
        }
    }

    val filteredVideos = remember(videos, searchQuery, sortBy) {
        videos.filter {
            it.displayName.contains(searchQuery, ignoreCase = true)
        }.sortedWith { a, b ->
            when (sortBy) {
                "name" -> a.displayName.compareTo(b.displayName, ignoreCase = true)
                "size" -> b.fileSize.compareTo(a.fileSize)
                else -> b.createdAt.compareTo(a.createdAt)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Library", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
                actions = {
                    IconButton(onClick = {
                        sortBy = when (sortBy) {
                            "date" -> "name"
                            "name" -> "size"
                            else -> "date"
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort Videos")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pickerLauncher.launch(arrayOf("video/*")) },
                containerColor = CrimsonPrimary,
                contentColor = TextPrimary,
                modifier = Modifier.testTag("import_video_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Import Video")
            }
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
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_videos_input"),
                placeholder = { Text("Search imported videos...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard,
                    focusedBorderColor = CrimsonLight,
                    unfocusedBorderColor = SurfaceBorder
                ),
                shape = RoundedCornerShape(12.dp)
            )

            if (isImporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = CrimsonLight)
            }

            if (filteredVideos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching videos found" else "No videos imported yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = "Import a local video to inspect and prepare for infinite loop live streaming.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredVideos, key = { it.videoId }) { video ->
                        VideoItemCard(
                            video = video,
                            onInspect = { onNavigateToInspect(video.videoId) },
                            onPrepare = { onNavigateToPrepare(video.videoId) },
                            onDetails = { onNavigateToDetails(video.videoId) },
                            onRemovePrepared = { videoToRemovePrepared = video },
                            onDelete = { videoToDelete = video }
                        )
                    }
                }
            }
        }
    }

    // Confirmation dialogs
    videoToDelete?.let { video ->
        ConfirmDialog(
            title = "Delete Video Entry?",
            message = "This will remove '${video.displayName}' from your library and delete any prepared local copies. Original source file remains untouched.",
            confirmLabel = "Delete",
            isDestructive = true,
            onConfirm = {
                coroutineScope.launch {
                    videoRepository.deleteVideo(video)
                    videoToDelete = null
                }
            },
            onDismiss = { videoToDelete = null }
        )
    }

    videoToRemovePrepared?.let { video ->
        ConfirmDialog(
            title = "Remove Prepared Copy?",
            message = "Remove the cached prepared version of '${video.displayName}'? You will need to prepare it again before streaming.",
            confirmLabel = "Remove Copy",
            isDestructive = true,
            onConfirm = {
                coroutineScope.launch {
                    videoRepository.deletePreparedFileOnly(video)
                    videoToRemovePrepared = null
                }
            },
            onDismiss = { videoToRemovePrepared = null }
        )
    }
}

@Composable
private fun VideoItemCard(
    video: VideoEntity,
    onInspect: () -> Unit,
    onPrepare: () -> Unit,
    onDetails: () -> Unit,
    onRemovePrepared: () -> Unit,
    onDelete: () -> Unit
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
                        text = video.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "${formatDuration(video.durationMs)} • ${video.width}x${video.height} • ${formatFileSize(video.fileSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                // Preparation status badge
                val (badgeColor, label) = when (video.preparationStatus) {
                    VideoEntity.STATUS_READY -> Pair(StatusLive, "PREPARED")
                    VideoEntity.STATUS_PREPARING -> Pair(StatusConnecting, "PREPARING")
                    VideoEntity.STATUS_FAILED -> Pair(StatusError, "FAILED")
                    else -> Pair(StatusIdle, "NOT PREPARED")
                }
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = label,
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Codec tag chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CodecChip(label = "Video: ${video.videoCodec.substringAfterLast("/")}")
                CodecChip(label = "Audio: ${video.audioCodec.substringAfterLast("/")}")
                if (video.preparedFileSize > 0) {
                    CodecChip(label = "Prep Size: ${formatFileSize(video.preparedFileSize)}")
                }
            }

            HorizontalDivider(color = SurfaceBorder)

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onInspect) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Inspect", fontSize = 12.sp)
                    }

                    if (video.preparationStatus == VideoEntity.STATUS_READY) {
                        TextButton(onClick = onDetails) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = StatusLive)
                            Spacer(Modifier.width(4.dp))
                            Text("Prepared", fontSize = 12.sp, color = StatusLive)
                        }
                    } else {
                        Button(
                            onClick = onPrepare,
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Prepare Once", fontSize = 12.sp)
                        }
                    }
                }

                Row {
                    if (video.preparedFilePath != null) {
                        IconButton(onClick = onRemovePrepared) {
                            Icon(Icons.Default.LayersClear, contentDescription = "Remove Prepared Copy", tint = TextMuted)
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Video", tint = StatusError.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

@Composable
fun CodecChip(label: String) {
    Surface(
        color = ChipBackground,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1000.0) {
        "%.2f GB".format(mb / 1024.0)
    } else {
        "%.1f MB".format(mb)
    }
}
