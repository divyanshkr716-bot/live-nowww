package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StreamDestinationEntity
import com.example.data.repository.DestinationRepository
import com.example.data.security.CryptoManager
import com.example.ui.components.ConfirmDialog
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationManagementScreen(
    destinationRepository: DestinationRepository,
    onNavigateToEdit: (Long) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val destinations by destinationRepository.allDestinations.collectAsState(initial = emptyList())
    var destToDelete by remember { mutableStateOf<StreamDestinationEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stream Destinations", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigateToEdit(0) },
                containerColor = CrimsonPrimary,
                contentColor = TextPrimary,
                modifier = Modifier.testTag("add_destination_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Destination")
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
            Text(
                text = "Configured YouTube Live Destinations",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary
            )

            if (destinations.isEmpty()) {
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
                            Icons.Default.CastConnected,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "No destinations added yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = "Add a YouTube Live RTMP/RTMPS destination with your encrypted stream key.",
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
                    items(destinations, key = { it.destinationId }) { dest ->
                        DestinationItemCard(
                            destination = dest,
                            onEdit = { onNavigateToEdit(dest.destinationId) },
                            onDelete = { destToDelete = dest },
                            onToggleEnabled = { enabled ->
                                coroutineScope.launch {
                                    destinationRepository.saveDestination(
                                        destinationId = dest.destinationId,
                                        displayName = dest.displayName,
                                        ingestUrl = dest.ingestUrl,
                                        plainStreamKey = "", // keeps existing key
                                        selectedVideoId = dest.selectedVideoId,
                                        enabled = enabled,
                                        maxRetries = dest.maxRetries,
                                        backoffInitialSec = dest.backoffInitialSec
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    destToDelete?.let { dest ->
        ConfirmDialog(
            title = "Delete Destination?",
            message = "Remove '${dest.displayName}'? Encrypted stream credentials will be erased from local keystore.",
            confirmLabel = "Delete",
            isDestructive = true,
            onConfirm = {
                coroutineScope.launch {
                    destinationRepository.deleteDestination(dest)
                    destToDelete = null
                }
            },
            onDismiss = { destToDelete = null }
        )
    }
}

@Composable
private fun DestinationItemCard(
    destination: StreamDestinationEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
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
                        text = destination.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = destination.ingestUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }

                Switch(
                    checked = destination.enabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextPrimary,
                        checkedTrackColor = CrimsonPrimary
                    )
                )
            }

            // Encrypted stream key indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = StatusLive, modifier = Modifier.size(14.dp))
                Text(
                    text = "Stream Key: ••••-••••-••••-**** (Encrypted at rest)",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            HorizontalDivider(color = SurfaceBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Retries: ${destination.maxRetries} (Initial backoff: ${destination.backoffInitialSec}s)",
                    color = TextMuted,
                    fontSize = 12.sp
                )

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Destination", tint = TextSecondary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Destination", tint = StatusError.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}
