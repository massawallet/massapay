package com.massapay.android.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingFlowNew(
    onComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: OnboardingViewModelNew = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showImportDialog by remember { mutableStateOf(false) }

    // Force light theme for all onboarding screens
    // Override the color scheme to use light colors
    val lightColorScheme = lightColorScheme(
        primary = Color(0xFF1976D2),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFBBDEFB),
        onPrimaryContainer = Color(0xFF0D47A1),
        secondary = Color(0xFF424242),
        onSecondary = Color.White,
        background = Color.White,
        onBackground = Color.Black,
        surface = Color(0xFFFAFAFA),
        onSurface = Color.Black,
        surfaceVariant = Color(0xFFF5F5F5),
        onSurfaceVariant = Color(0xFF424242),
        outline = Color(0xFFBDBDBD),
        error = Color(0xFFD32F2F),
        onError = Color.White
    )
    
    MaterialTheme(colorScheme = lightColorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White
        ) {
        // Handle completion - esperar un frame para asegurar que todo se guardó
        LaunchedEffect(uiState.currentStep) {
            if (uiState.currentStep == OnboardingStepNew.COMPLETED) {
                kotlinx.coroutines.delay(100) // Pequeño delay para asegurar que SecureStorage terminó
                onComplete()
            }
        }

        // Show error if exists
        uiState.error?.let { error ->
            AlertDialog(
                onDismissRequest = { /* No permitir cerrar hasta resolver */ },
                title = { Text("Error") },
                text = { Text(error) },
                confirmButton = {
                    TextButton(onClick = { viewModel.goBack() }) {
                        Text("OK")
                    }
                }
            )
        }

        AnimatedContent(
            targetState = uiState.currentStep,
            transitionSpec = {
                slideInHorizontally(
                    initialOffsetX = { 300 },
                    animationSpec = tween(300)
                ) + fadeIn() togetherWith
                slideOutHorizontally(
                    targetOffsetX = { -300 },
                    animationSpec = tween(300)
                ) + fadeOut()
            },
            label = "onboarding_transition"
        ) { step ->
            when (step) {
                OnboardingStepNew.WELCOME -> {
                    WelcomeScreenNew(
                        onCreateWallet = { viewModel.startCreateWallet() },
                        onImportWallet = { showImportDialog = true }
                    )
                }

                OnboardingStepNew.GENERATE_SEED -> {
                    GenerateSeedScreen(
                        seedWords = uiState.seedWords,
                        onContinue = { viewModel.moveToVerifySeed() },
                        onBack = { viewModel.goBack() }
                    )
                }

                OnboardingStepNew.VERIFY_SEED -> {
                    VerifySeedScreen(
                        seedWords = uiState.seedWords,
                        onVerified = { viewModel.onSeedVerified() },
                        onBack = { viewModel.goBack() }
                    )
                }

                OnboardingStepNew.SETUP_PIN -> {
                    PinSetupScreen(
                        onPinCreated = { pin, biometric ->
                            viewModel.onPinCreated(pin, biometric)
                        },
                        onBack = { viewModel.goBack() }
                    )
                }

                OnboardingStepNew.COMPLETED -> {
                    // This should trigger onComplete via LaunchedEffect
                }
            }
        }

        // Import Dialog
        if (showImportDialog) {
            ImportWalletDialog(
                onDismiss = { showImportDialog = false },
                onImport = { seedPhrase ->
                    viewModel.startImportWallet(seedPhrase)
                    showImportDialog = false
                }
            )
        }
        }  // Close Surface
    }  // Close MaterialTheme
}

@Composable
private fun ImportWalletDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var importType by remember { mutableStateOf("mnemonic") } // "mnemonic" or "privatekey"
    var wordCount by remember { mutableStateOf(24) } // 12 or 24
    var words by remember { mutableStateOf(List(24) { "" }) }
    var privateKeyInput by remember { mutableStateOf("") }
    val clipboardManager = androidx.compose.ui.platform.LocalContext.current.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Import Wallet",
                fontSize = 24.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Import type selector (Mnemonic vs Private Key)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { importType = "mnemonic" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (importType == "mnemonic") Color.Black else Color.White,
                            contentColor = if (importType == "mnemonic") Color.White else Color.Black
                        ),
                        border = BorderStroke(2.dp, Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Phrase Seed",
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                    Button(
                        onClick = { importType = "privatekey" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (importType == "privatekey") Color.Black else Color.White,
                            contentColor = if (importType == "privatekey") Color.White else Color.Black
                        ),
                        border = BorderStroke(2.dp, Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Private Key",
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                }
                
                // Content based on import type
                if (importType == "mnemonic") {
                    // Word count selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { 
                                wordCount = 12
                                words = List(12) { words.getOrElse(it) { "" } }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (wordCount == 12) Color.Black else Color.White,
                                contentColor = if (wordCount == 12) Color.White else Color.Black
                            ),
                            border = BorderStroke(2.dp, Color.Black),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("12 Words")
                        }
                        Button(
                            onClick = { 
                                wordCount = 24
                                words = List(24) { words.getOrElse(it) { "" } }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (wordCount == 24) Color.Black else Color.White,
                                contentColor = if (wordCount == 24) Color.White else Color.Black
                            ),
                            border = BorderStroke(2.dp, Color.Black),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("24 Words")
                        }
                    }
                    
                    // Paste button
                    OutlinedButton(
                        onClick = {
                            val clipData = clipboardManager?.primaryClip
                            val pastedText = clipData?.getItemAt(0)?.text?.toString() ?: ""
                            val pastedWords = pastedText.trim().split(Regex("\\s+"))
                            
                            if (pastedWords.size == 12 || pastedWords.size == 24) {
                                wordCount = pastedWords.size
                                words = pastedWords.mapIndexed { index, word -> 
                                    if (index < wordCount) word.lowercase().trim() else ""
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.Black
                        ),
                        border = BorderStroke(2.dp, Color.Black),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Paste from Clipboard", color = Color.Black)
                    }
                    
                    Text(
                        text = "Enter your recovery phrase (one word per box):",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    
                    // Grid of word input boxes
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (row in 0 until (wordCount / 3)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (col in 0 until 3) {
                                        val index = row * 3 + col
                                        if (index < wordCount) {
                                            Column(
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = "${index + 1}",
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                                                )
                                                OutlinedTextField(
                                                    value = words[index],
                                                    onValueChange = { newValue ->
                                                        words = words.toMutableList().apply {
                                                            this[index] = newValue.lowercase().trim()
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    singleLine = true,
                                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                                        cursorColor = MaterialTheme.colorScheme.primary
                                                    ),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Private Key import
                    Text(
                        text = "Enter your Massa private key (starts with S1...):",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    
                    OutlinedTextField(
                        value = privateKeyInput,
                        onValueChange = { privateKeyInput = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("S1...", fontSize = 13.sp) },
                        minLines = 3,
                        maxLines = 4,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    
                    // Paste button for private key
                    OutlinedButton(
                        onClick = {
                            val clipData = clipboardManager?.primaryClip
                            val pastedText = clipData?.getItemAt(0)?.text?.toString() ?: ""
                            privateKeyInput = pastedText.trim()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.Black
                        ),
                        border = BorderStroke(2.dp, Color.Black),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Paste from Clipboard", color = Color.Black)
                    }
                    
                    Text(
                        text = "Your private key will be securely encrypted and stored on this device.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            val isValid = if (importType == "mnemonic") {
                val filledWords = words.take(wordCount).filter { it.isNotBlank() }
                filledWords.size == wordCount
            } else {
                privateKeyInput.startsWith("S1") && privateKeyInput.length > 10
            }
            
            Button(
                onClick = { 
                    val importData = if (importType == "mnemonic") {
                        words.take(wordCount).joinToString(" ")
                    } else {
                        "S1_IMPORT:$privateKeyInput" // Special marker for S1 import
                    }
                    onImport(importData)
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White,
                    disabledContainerColor = Color.LightGray,
                    disabledContentColor = Color.Gray
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Import Wallet")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Cancel")
            }
        }
    )
}
