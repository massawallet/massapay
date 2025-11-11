package com.massapay.android.ui.auth

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.massapay.android.ui.onboarding.screens.PinVisualTransformation

@Composable
fun AuthenticationDialog(
    title: String = "Authentication Required",
    subtitle: String = "Confirm your identity to proceed",
    biometricEnabled: Boolean = false,
    onAuthenticated: () -> Unit,
    onDismiss: () -> Unit,
    onVerifyPin: (String) -> Boolean,
    onBiometricRequest: () -> Unit = {}
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var usePinAuth by remember { mutableStateOf(!biometricEnabled) }
    val isDarkTheme = isSystemInDarkTheme()
    
    // Consistent button colors with rest of the app
    val buttonColor = if (isDarkTheme) Color(0xFF2A2A2A) else Color.Black
    val buttonTextColor = Color.White

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = if (usePinAuth) Icons.Default.Lock else Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                if (usePinAuth) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { 
                            if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                pin = it
                                error = null
                                if (it.length == 6) {
                                    if (onVerifyPin(it)) {
                                        onAuthenticated()
                                    } else {
                                        error = "Incorrect PIN"
                                        pin = ""
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Enter PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        visualTransformation = PinVisualTransformation(),
                        isError = error != null
                    )

                    if (error != null) {
                        Text(
                            text = error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Button(
                        onClick = { 
                            onBiometricRequest()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor,
                            contentColor = buttonTextColor
                        )
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Use Biometric")
                    }

                    TextButton(
                        onClick = { usePinAuth = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("Use PIN instead")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
fun RequireAuthentication(
    biometricEnabled: Boolean = false,
    onVerifyPin: (String) -> Boolean,
    onAuthenticated: @Composable () -> Unit
) {
    var showAuthDialog by remember { mutableStateOf(true) }
    var isAuthenticated by remember { mutableStateOf(false) }

    if (!isAuthenticated) {
        AuthenticationDialog(
            biometricEnabled = biometricEnabled,
            onAuthenticated = {
                isAuthenticated = true
                showAuthDialog = false
            },
            onDismiss = { showAuthDialog = false },
            onVerifyPin = onVerifyPin
        )
    } else {
        onAuthenticated()
    }
}
