package com.massapay.android.ui.transaction

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    onClose: () -> Unit,
    onScanQr: () -> Unit,
    onTransactionSuccess: () -> Unit = {},
    viewModel: SendViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    // Consistent button colors with onboarding
    val buttonColor = if (isDarkTheme) Color(0xFF2A2A2A) else Color.Black
    val buttonTextColor = Color.White
    
    // Biometric authentication
    val biometricPrompt = remember {
        BiometricPrompt(
            context as FragmentActivity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.onAuthenticationSuccess()
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // User cancelled or error occurred
                }
                
                override fun onAuthenticationFailed() {
                    // Biometric not recognized
                }
            }
        )
    }
    
    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authorize Transaction")
            .setSubtitle("Confirm sending ${uiState.amount} MAS")
            .setNegativeButtonText("Use PIN")
            .build()
    }
    
    // Listen for biometric request
    LaunchedEffect(Unit) {
        viewModel.biometricRequest.collect {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Send MAS",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Balance Card with animation
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Available Balance",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${uiState.availableBalance} MAS",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Amount Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.Payments,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Amount to Send",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    // Amount input with MAX button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = if (uiState.isUsdMode) uiState.usdAmount else uiState.amount,
                            onValueChange = { viewModel.updateAmount(it) },
                            label = { Text(if (uiState.isUsdMode) "Amount (USD)" else "Amount (MAS)") },
                            placeholder = { Text("0.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            isError = !uiState.isValidAmount,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                cursorColor = MaterialTheme.colorScheme.onSurface
                            ),
                            trailingIcon = {
                                // Currency toggle button
                                TextButton(
                                    onClick = { viewModel.toggleCurrency() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        if (uiState.isUsdMode) "USD" else "MAS",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )
                        
                        // MAX button - smaller and aligned
                        Button(
                            onClick = { viewModel.setMaxAmount() },
                            modifier = Modifier
                                .height(48.dp)
                                .width(58.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonColor,
                                contentColor = buttonTextColor
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                "MAX",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                        }
                    }
                    
                    AnimatedVisibility(
                        visible = (uiState.isUsdMode && uiState.usdAmount.isNotEmpty()) || 
                                  (!uiState.isUsdMode && uiState.amount.isNotEmpty()),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            if (!uiState.isValidAmount) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Error,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Insufficient balance",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                // Show conversion: if USD mode, show MAS equivalent; if MAS mode, show USD equivalent
                                Text(
                                    if (uiState.isUsdMode) "≈ ${uiState.amount} MAS" else "≈ $${uiState.usdAmount} USD",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Recipient Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.PersonOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Recipient",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    OutlinedTextField(
                        value = uiState.recipientAddress,
                        onValueChange = { viewModel.updateAddress(it) },
                        label = { Text("Massa address") },
                        placeholder = { Text("AU...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        trailingIcon = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Paste button
                                IconButton(
                                    onClick = {
                                        clipboardManager.getText()?.text?.let { pastedText ->
                                            viewModel.updateAddress(pastedText.trim())
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Outlined.ContentPaste,
                                        "Paste",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                
                                // QR Scanner button
                                IconButton(onClick = onScanQr) {
                                    Icon(
                                        Icons.Outlined.QrCodeScanner,
                                        "Scan QR",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        },
                        isError = !uiState.isValidAddress,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            cursorColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    
                    AnimatedVisibility(
                        visible = !uiState.isValidAddress && uiState.recipientAddress.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Error,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Invalid Massa address",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Send button with animation
            val buttonScale by animateFloatAsState(
                targetValue = if (uiState.isValidAmount && uiState.isValidAddress) 1f else 0.95f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "buttonScale"
            )
            
            FilledTonalButton(
                onClick = { viewModel.requestAuthentication() },
                enabled = uiState.isValidAmount && uiState.isValidAddress && !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .graphicsLayer {
                        scaleX = buttonScale
                        scaleY = buttonScale
                    },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = buttonColor,
                    contentColor = buttonTextColor,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = buttonTextColor
                    )
                } else {
                    Icon(
                        Icons.Outlined.Send,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Send MAS",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    )
                }
            }
        }

        // Authentication dialog
        if (uiState.showAuthDialog) {
            com.massapay.android.ui.auth.AuthenticationDialog(
                title = "Authorize Transaction",
                subtitle = "Confirm sending ${uiState.amount} MAS",
                biometricEnabled = viewModel.getBiometricEnabled(),
                onAuthenticated = { viewModel.onAuthenticationSuccess() },
                onDismiss = { viewModel.onAuthenticationCancelled() },
                onVerifyPin = viewModel::verifyPin,
                onBiometricRequest = { viewModel.requestBiometric() }
            )
        }

        // Success dialog - Removed, using TransactionSuccessScreen instead

        // Error dialog
        uiState.error?.let { error ->
            AlertDialog(
                onDismissRequest = { },
                title = { 
                    Text(
                        "Error",
                        color = MaterialTheme.colorScheme.onSurface
                    ) 
                },
                text = { 
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    ) 
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.requestAuthentication() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("Retry")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onClose,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
    
    // Success screen overlay
    if (uiState.showSuccessScreen) {
        TransactionSuccessScreen(
            amount = uiState.amount,
            recipient = uiState.recipientAddress,
            onDismiss = {
                viewModel.dismissResultScreen()
                onTransactionSuccess() // Refresh dashboard before closing
                onClose()
            }
        )
    }
    
    // Failure screen overlay
    if (uiState.showFailureScreen) {
        TransactionFailureScreen(
            errorMessage = uiState.error ?: "Unknown error occurred",
            onDismiss = {
                viewModel.dismissResultScreen()
            }
        )
    }
}