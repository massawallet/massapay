package com.massapay.android.ui.lock

import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    onWalletReset: () -> Unit = {},
    viewModel: LockViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current as FragmentActivity
    var pin by remember { mutableStateOf("") }
    var showPinInput by remember { mutableStateOf(false) }
    
    val biometricPrompt = remember {
        BiometricPrompt(
            context,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.onBiometricSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    viewModel.onBiometricError(errString.toString())
                }
                override fun onAuthenticationFailed() {
                    viewModel.onBiometricError("Authentication failed")
                }
            }
        )
    }
    
    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock MassaPay")
            .setSubtitle("Use your biometric credential")
            .setNegativeButtonText("Use PIN Instead")
            .build()
    }
    
    LaunchedEffect(uiState.showBiometricPrompt) {
        if (uiState.showBiometricPrompt) {
            biometricPrompt.authenticate(promptInfo)
            viewModel.resetBiometricPrompt()
        }
    }
    
    LaunchedEffect(uiState.isUnlocked) {
        if (uiState.isUnlocked) {
            onUnlocked()
        }
    }

    LaunchedEffect(uiState.walletReset) {
        if (uiState.walletReset) {
            onWalletReset()
        }
    }

    val isDarkTheme = isSystemInDarkTheme()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top section - Logo and branding
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                
                // Logo with shadow (no animation)
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape
                        )
                        .background(
                            color = Color.Black,
                            shape = CircleShape
                        )
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = com.massapay.android.ui.R.drawable.massapay_logo),
                        contentDescription = "MassaPay Logo",
                        modifier = Modifier.size(110.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // App name
                Text(
                    text = "MassaPay",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Description
                Text(
                    text = if (uiState.biometricAvailable && !showPinInput) 
                        "Access your wallet securely"
                    else 
                        "Protect your crypto",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            // Middle section - Authentication
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.biometricAvailable && !showPinInput) {
                    // Animación pulsante para el botón biométrico
                    val infiniteTransition = rememberInfiniteTransition(label = "biometric_pulse")
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.05f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scale"
                    )
                    
                    // Subtitle
                    Text(
                        text = "Unlock",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    
                    // Biometric unlock
                    Button(
                        onClick = { viewModel.authenticateWithBiometric() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .scale(scale),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDarkTheme) Color.White else Color.Black,
                            contentColor = if (isDarkTheme) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 12.dp
                        )
                    ) {
                        Icon(
                            Icons.Default.Fingerprint, 
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Tap to Unlock",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.isLoading && !uiState.showBiometricPrompt) {
                            Spacer(modifier = Modifier.width(12.dp))
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    TextButton(
                        onClick = { 
                            showPinInput = true
                            viewModel.clearError()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Use PIN Instead",
                            fontSize = 15.sp
                        )
                    }

                    TextButton(
                        onClick = { viewModel.showForgotPasswordDialog(true) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Forgot Password?",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    // PIN unlock
                    Text(
                        text = if (uiState.error != null) uiState.error!! else "Enter your PIN",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (uiState.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        repeat(6) { index ->
                            PinDot(
                                isFilled = index < pin.length,
                                isError = uiState.error != null
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    NumberPad(
                        onNumberClick = { number ->
                            if (pin.length < 6) {
                                if (pin.isEmpty()) {
                                    viewModel.clearError()
                                }
                                pin += number
                                if (pin.length == 6) {
                                    viewModel.verifyPin(pin)
                                    if (uiState.error != null) {
                                        pin = ""
                                    }
                                }
                            }
                        },
                        onDeleteClick = {
                            if (pin.isNotEmpty()) {
                                pin = pin.dropLast(1)
                            }
                        }
                    )

                    TextButton(
                        onClick = { viewModel.showForgotPasswordDialog(true) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Forgot Password?",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Bottom spacer
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Forgot Password Dialog
        if (uiState.showForgotPasswordDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.showForgotPasswordDialog(false) },
                icon = {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                },
                title = {
                    Text(
                        text = "Reset Wallet?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "WARNING: This action cannot be undone!",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Resetting your password will permanently delete:",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• Your wallet and all funds", fontSize = 14.sp)
                        Text("• Your seed phrase", fontSize = 14.sp)
                        Text("• All transaction history", fontSize = 14.sp)
                        Text("• All settings", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Make sure you have backed up your seed phrase before continuing.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.resetWallet()
                            viewModel.showForgotPasswordDialog(false)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Reset Everything")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.showForgotPasswordDialog(false) }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun PinDot(isFilled: Boolean, isError: Boolean = false) {
    val dotColor = when {
        isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onBackground
    }
    
    Box(
        modifier = Modifier
            .size(16.dp)
            .background(
                color = if (isFilled) dotColor else Color.Transparent,
                shape = CircleShape
            )
            .border(
                width = 2.dp,
                color = dotColor,
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        // Rows 1-3
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9")
        ).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
