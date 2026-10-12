package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.DestinationRepository
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDestinationScreen(
    destinationId: Long,
    destinationRepository: DestinationRepository,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var displayName by remember { mutableStateOf("") }
    var ingestUrl by remember { mutableStateOf("rtmp://a.rtmp.youtube.com/live2") }
    var streamKey by remember { mutableStateOf("") }
    var showStreamKey by remember { mutableStateOf(false) }
    var maxRetries by remember { mutableIntStateOf(5) }
    var backoffSec by remember { mutableIntStateOf(2) }
    var isEditingExisting by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(destinationId) {
        if (destinationId > 0) {
            val existing = destinationRepository.getDestinationById(destinationId)
            if (existing != null) {
                isEditingExisting = true
                displayName = existing.displayName
                ingestUrl = existing.ingestUrl
                maxRetries = existing.maxRetries
                backoffSec = existing.backoffInitialSec
                // Do not expose plaintext key in field unless user types a new one
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditingExisting) "Edit Destination" else "New Destination", fontWeight = FontWeight.Bold) },
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
            // Destination Name
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Destination Name") },
                placeholder = { Text("e.g. YouTube Live Stream") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("destination_name_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard,
                    focusedBorderColor = CrimsonLight,
                    unfocusedBorderColor = SurfaceBorder
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Ingest URL
            OutlinedTextField(
                value = ingestUrl,
                onValueChange = { ingestUrl = it },
                label = { Text("RTMP / RTMPS Ingest URL") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ingest_url_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard,
                    focusedBorderColor = CrimsonLight,
                    unfocusedBorderColor = SurfaceBorder
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // URL Presets
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuggestionChip(
                    onClick = { ingestUrl = "rtmp://a.rtmp.youtube.com/live2" },
                    label = { Text("YouTube RTMP", fontSize = 11.sp) }
                )
                SuggestionChip(
                    onClick = { ingestUrl = "rtmps://a.rtmp.youtube.com/live2:443/live2" },
                    label = { Text("YouTube RTMPS (TLS)", fontSize = 11.sp) }
                )
            }

            // Stream Key (Keystore Encrypted)
            OutlinedTextField(
                value = streamKey,
                onValueChange = { streamKey = it },
                label = { Text(if (isEditingExisting && streamKey.isEmpty()) "Stream Key (unchanged if blank)" else "Stream Key") },
                placeholder = { Text("xxxx-xxxx-xxxx-xxxx") },
                visualTransformation = if (showStreamKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showStreamKey = !showStreamKey }) {
                        Icon(
                            imageVector = if (showStreamKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle visibility"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stream_key_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard,
                    focusedBorderColor = CrimsonLight,
                    unfocusedBorderColor = SurfaceBorder
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Keystore notice
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = StatusLive, modifier = Modifier.size(20.dp))
                    Text(
                        text = "Encrypted with AES-256 GCM backed by Android Keystore. Keys never appear in logs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Reconnection & Retry Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Automatic Reconnection Policy", fontWeight = FontWeight.Bold, color = TextPrimary)

                    Text("Max Retry Attempts: $maxRetries", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Slider(
                        value = maxRetries.toFloat(),
                        onValueChange = { maxRetries = it.toInt() },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(thumbColor = CrimsonLight, activeTrackColor = CrimsonPrimary)
                    )

                    Text("Initial Backoff Delay: ${backoffSec}s (Exponential to 30s max)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Slider(
                        value = backoffSec.toFloat(),
                        onValueChange = { backoffSec = it.toInt() },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(thumbColor = CrimsonLight, activeTrackColor = CrimsonPrimary)
                    )
                }
            }

            validationError?.let { err ->
                Text(text = err, color = StatusError, fontSize = 13.sp)
            }

            Button(
                onClick = {
                    if (displayName.isBlank()) {
                        validationError = "Please enter a destination name."
                        return@Button
                    }
                    if (ingestUrl.isBlank()) {
                        validationError = "Please enter a valid ingest URL."
                        return@Button
                    }
                    if (!isEditingExisting && streamKey.isBlank()) {
                        validationError = "Please enter a YouTube stream key."
                        return@Button
                    }

                    coroutineScope.launch {
                        destinationRepository.saveDestination(
                            destinationId = destinationId,
                            displayName = displayName.trim(),
                            ingestUrl = ingestUrl.trim(),
                            plainStreamKey = streamKey.trim(),
                            selectedVideoId = null,
                            enabled = true,
                            maxRetries = maxRetries,
                            backoffInitialSec = backoffSec
                        )
                        onNavigateBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_destination_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Destination", fontWeight = FontWeight.Bold)
            }
        }
    }
}
