package com.massapay.android.ui.agentbridge

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.massapay.android.network.agentbridge.AgentConnectionState

@Composable
fun AgentConnectionDialog(
    connectionState: AgentConnectionState,
    isDarkTheme: Boolean,
    onScanQR: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val dialogColor = if (isDarkTheme) Color(0xFF121212) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val accentColor = Color(0xFF6366F1)
    val isConnected = connectionState is AgentConnectionState.Connected

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogColor,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Icon(
                imageVector = Icons.Default.LinkOff,
                contentDescription = null,
                tint = accentColor
            )
        },
        title = {
            Text(
                text = "Massa Agent",
                color = textColor,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = when (connectionState) {
                        is AgentConnectionState.Connected -> "Connected to Massa Agent."
                        is AgentConnectionState.Connecting -> "Connecting to Massa Agent..."
                        is AgentConnectionState.Error -> connectionState.message
                        AgentConnectionState.Disconnected -> "Massa Agent bridge is not available in this build."
                    },
                    color = textColor.copy(alpha = 0.75f)
                )
                Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                Text(
                    text = "Local buy and sell rolls still work from this wallet.",
                    color = textColor.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = if (isConnected) onDisconnect else onScanQR,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isConnected) "Disconnect" else "Scan QR")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = textColor.copy(alpha = 0.7f))
            }
        }
    )
}
