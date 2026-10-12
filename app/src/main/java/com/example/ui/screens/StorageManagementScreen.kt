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
import com.example.data.repository.VideoRepository
import com.example.ui.components.ConfirmDialog
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageManagementScreen(
    videoRepository: VideoRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val videos by videoRepository.allVideos.collectAsState(initial = emptyList())

    val preparedDir = remember { File(context.filesDir, "prepared_videos") }
    var preparedTotalBytes by remember { mutableLongStateOf(0L) }
    var freeSpaceBytes by remember { mutableLongStateOf(0L) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    fun refreshSpace() {
        val files = preparedDir.listFiles() ?: emptyArray()
        preparedTotalBytes = files.sumOf { it.length() }
        freeSpaceBytes = preparedDir.usableSpace
    }

    LaunchedEffect(videos) {
        refreshSpace()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage Management", fontWeight = FontWeight.Bold) },
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
            // Space Overview
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("App Storage Usage", fontWeight = FontWeight.Bold, color = TextPrimary)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Prepared Videos Cache:", color = TextSecondary)
                        Text(formatFileSize(preparedTotalBytes), color = CrimsonLight, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Available Free Storage:", color = TextSecondary)
                        Text(formatFileSize(freeSpaceBytes), color = StatusLive, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Preservation rule
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = StatusLive, modifier = Modifier.size(24.dp))
                    Column {
                        Text("Original Media Preservation", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                        Text(
                            text = "Original user files are never modified or deleted. Only prepared copies in app-internal cache are managed here.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (preparedTotalBytes > 0) {
                Button(
                    onClick = { showClearAllConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clear_prepared_cache_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clear All Prepared Videos (${formatFileSize(preparedTotalBytes)})")
                }
            }
        }
    }

    if (showClearAllConfirm) {
        ConfirmDialog(
            title = "Clear All Prepared Copies?",
            message = "This will remove all locally prepared media copies to free up space. Original videos in your library will remain intact.",
            confirmLabel = "Clear All Cache",
            isDestructive = true,
            onConfirm = {
                coroutineScope.launch {
                    videos.forEach { v ->
                        if (v.preparedFilePath != null) {
                            videoRepository.deletePreparedFileOnly(v)
                        }
                    }
                    val files = preparedDir.listFiles() ?: emptyArray()
                    files.forEach { it.delete() }
                    refreshSpace()
                    showClearAllConfirm = false
                }
            },
            onDismiss = { showClearAllConfirm = false }
        )
    }
}
