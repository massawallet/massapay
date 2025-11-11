package com.massapay.android.ui.onboarding

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetupScreen(
    onPinCreated: (String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    var step by remember { mutableStateOf(PinStep.CREATE) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var showBiometricDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Icon
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = when (step) {
                    PinStep.CREATE -> "Create PIN"
                    PinStep.CONFIRM -> "Confirm PIN"
                },
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = when (step) {
                    PinStep.CREATE -> "Create a 6-digit PIN to secure your wallet"
                    PinStep.CONFIRM -> "Enter your PIN again to confirm"
                },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            when (step) {
                PinStep.CREATE, PinStep.CONFIRM -> {
                    // PIN Dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        repeat(6) { index ->
                            PinDot(
                                isFilled = if (step == PinStep.CREATE)
                                    index < pin.length
                                else
                                    index < confirmPin.length
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Number Pad
                    NumberPad(
                        onNumberClick = { number ->
                            showError = false
                            if (step == PinStep.CREATE && pin.length < 6) {
                                pin += number
                                if (pin.length == 6) {
                                    step = PinStep.CONFIRM
                                }
                            } else if (step == PinStep.CONFIRM && confirmPin.length < 6) {
                                confirmPin += number
                                if (confirmPin.length == 6) {
                                    if (pin == confirmPin) {
                                        // PIN confirmado, mostrar diálogo de biometría
                                        showBiometricDialog = true
                                    } else {
                                        showError = true
                                        confirmPin = ""
                                    }
                                }
                            }
                        },
                        onDeleteClick = {
                            if (step == PinStep.CREATE && pin.isNotEmpty()) {
                                pin = pin.dropLast(1)
                            } else if (step == PinStep.CONFIRM && confirmPin.isNotEmpty()) {
                                confirmPin = confirmPin.dropLast(1)
                            }
                        }
                    )

                    if (showError) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "❌ PINs don't match. Try again.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Back Button
            if (step == PinStep.CREATE) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        text = "Go Back",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        // Biometric Setup Dialog
        if (showBiometricDialog) {
            BiometricSetupDialog(
                onEnableBiometric = {
                    // Only mark biometric enabled after successful authentication
                    showBiometricDialog = false
                    onPinCreated(pin, true)
                },
                onSkip = {
                    showBiometricDialog = false
                    onPinCreated(pin, false)
                },
                onDismiss = {
                    showBiometricDialog = false
                    onPinCreated(pin, false)
                }
            )
        }
    }
}

@Composable
private fun PinDot(isFilled: Boolean) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .background(
                color = if (isFilled) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                shape = CircleShape
            )
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.onBackground,
                shape = CircleShape
            )
    )
}

@Composable
private fun NumberPad(
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Rows 1-3
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9")
        ).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { number ->
                    NumberButton(
                        text = number,
                        onClick = { onNumberClick(number) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Row 4: Empty, 0, Delete
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.weight(1f))
            NumberButton(
                text = "0",
                onClick = { onNumberClick("0") },
                modifier = Modifier.weight(1f)
            )
            NumberButton(
                text = "⌫",
                onClick = onDeleteClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun NumberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private enum class PinStep {
    CREATE, CONFIRM
}

@Composable
private fun BiometricSetupDialog(
    onEnableBiometric: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var biometricError by remember { mutableStateOf<String?>(null) }
    var debugMessage by remember { mutableStateOf("Initializing...") }
    
    // Get FragmentActivity directly
    val activity = remember(context) {
        (context as? FragmentActivity).also {
            android.util.Log.d("BiometricSetup", "Activity type: ${context.javaClass.simpleName}, is FragmentActivity: ${it != null}")
            debugMessage = "Activity: ${if (it != null) "FragmentActivity found" else "Not FragmentActivity - ${context.javaClass.simpleName}"}"
        }
    }
    
    // Check if biometric is available
    val biometricManager = remember {
        androidx.biometric.BiometricManager.from(context)
    }
    
    val canAuthenticate = remember {
        val result = biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
        android.util.Log.d("BiometricSetup", "canAuthenticate result: $result")
        result
    }
    
    // Setup biometric prompt callback
    val authenticationCallback = remember {
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                android.util.Log.d("BiometricSetup", "Authentication succeeded!")
                debugMessage = "Success!"
                onEnableBiometric()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                android.util.Log.e("BiometricSetup", "Authentication error: $errorCode - $errString")
                debugMessage = "Error: $errorCode - $errString"
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED -> {
                        biometricError = null
                    }
                    else -> {
                        biometricError = "Error ($errorCode): $errString"
                    }
                }
            }

            override fun onAuthenticationFailed() {
                android.util.Log.w("BiometricSetup", "Authentication failed")
                debugMessage = "Failed - try again"
                biometricError = "Fingerprint not recognized. Try again."
            }
        }
    }
    
    val biometricPrompt = remember {
        activity?.let {
            android.util.Log.d("BiometricSetup", "Creating BiometricPrompt for activity: ${it.javaClass.simpleName}")
            BiometricPrompt(it, ContextCompat.getMainExecutor(context), authenticationCallback)
        } ?: run {
            android.util.Log.e("BiometricSetup", "Activity is null! Cannot create BiometricPrompt")
            null
        }
    }

    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verify Your Fingerprint")
            .setSubtitle("Place your finger on the sensor")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Enable Biometric Authentication?",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column {
                when (canAuthenticate) {
                    androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS -> {
                        Text(
                            text = "Use your fingerprint to unlock Massa Pay quickly and securely.",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                        Text(
                            text = "This device doesn't support biometric authentication.",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                        Text(
                            text = "Biometric hardware is currently unavailable.",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                        Text(
                            text = "No fingerprint enrolled. Please add a fingerprint in your device settings first.",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {
                        Text(
                            text = "Biometric authentication is not available (code: $canAuthenticate).",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                // Debug message
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = debugMessage,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
                
                biometricError?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            if (canAuthenticate == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
                Button(
                    onClick = {
                        android.util.Log.d("BiometricSetup", "Enable button clicked")
                        biometricError = null
                        debugMessage = "Starting authentication..."
                        try {
                            if (biometricPrompt != null) {
                                android.util.Log.d("BiometricSetup", "Calling authenticate()")
                                biometricPrompt.authenticate(promptInfo)
                                debugMessage = "Authenticate() called"
                            } else {
                                android.util.Log.e("BiometricSetup", "BiometricPrompt is null!")
                                biometricError = "Error: BiometricPrompt is null"
                                debugMessage = "Error: Prompt is null"
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("BiometricSetup", "Exception: ${e.message}", e)
                            biometricError = "Failed to start: ${e.message}"
                            debugMessage = "Exception: ${e.javaClass.simpleName}"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enable Biometric", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (canAuthenticate == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) 
                        "Skip for Now" 
                    else 
                        "Continue without Biometric",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp)
    )
}
