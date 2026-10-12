package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StreamState
import com.example.ui.theme.*

@Composable
fun StatusBadge(
    state: StreamState,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when (state) {
        StreamState.LIVE -> Triple(StatusLive.copy(alpha = 0.2f), StatusLive, "● LIVE")
        StreamState.CONNECTING -> Triple(StatusConnecting.copy(alpha = 0.2f), StatusConnecting, "CONNECTING")
        StreamState.RECONNECTING -> Triple(StatusWarning.copy(alpha = 0.2f), StatusWarning, "RECONNECTING")
        StreamState.PREPARING -> Triple(StatusConnecting.copy(alpha = 0.2f), StatusConnecting, "PREPARING")
        StreamState.READY -> Triple(StatusLive.copy(alpha = 0.2f), StatusLive, "READY")
        StreamState.STOPPING -> Triple(StatusWarning.copy(alpha = 0.2f), StatusWarning, "STOPPING")
        StreamState.STOPPED -> Triple(StatusIdle.copy(alpha = 0.2f), StatusIdle, "STOPPED")
        StreamState.ERROR -> Triple(StatusError.copy(alpha = 0.2f), StatusError, "ERROR")
        StreamState.IDLE -> Triple(StatusIdle.copy(alpha = 0.2f), StatusIdle, "IDLE")
    }

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    icon: ImageVector? = null,
    subValue: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SurfaceCardElevated),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = CrimsonLight,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (subValue != null) {
                Text(
                    text = subValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        text = { Text(text = message, color = TextSecondary) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) StatusError else CrimsonPrimary
                ),
                modifier = Modifier.testTag("dialog_confirm_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_dismiss_button")
            ) {
                Text("Cancel")
            }
        },
        containerColor = SurfaceCard,
        shape = RoundedCornerShape(16.dp)
    )
}
