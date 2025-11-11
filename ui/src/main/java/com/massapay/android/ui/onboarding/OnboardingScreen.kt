package com.massapay.android.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.res.painterResource
import com.massapay.android.ui.R
import com.massapay.android.ui.onboarding.screens.VerifyMnemonicScreen
import com.massapay.android.ui.onboarding.screens.SetPinScreen
import com.massapay.android.ui.onboarding.screens.ConfirmPinScreen
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.massapay.android.core.util.Constants
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.IconButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextField
import androidx.compose.ui.text.input.TextFieldValue

@Composable
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
fun OnboardingScreen(
    onComplete: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showImportDialog by remember { mutableStateOf(false) }
    var importInput by remember { mutableStateOf(TextFieldValue("")) }
    var importType by remember { mutableStateOf("mnemonic") } // "mnemonic" or "privatekey"
    var acceptTerms by remember { mutableStateOf(false) }
    
    DisableScreenshots {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top app bar with back handling
                if (uiState.currentStep != OnboardingStep.WELCOME) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            // If on welcome, call external back, otherwise step back in viewModel
                            if (uiState.currentStep == OnboardingStep.WELCOME) onBack() else viewModel.goBack()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
                AnimatedContent(
                    targetState = uiState.currentStep,
                    transitionSpec = {
                        slideInHorizontally(
                            initialOffsetX = { 300 },
                            animationSpec = tween(300)
                        ) + fadeIn() with
                        slideOutHorizontally(
                            targetOffsetX = { -300 },
                            animationSpec = tween(300)
                        ) + fadeOut()
                    }
                ) { step ->
                    when (step) {
                        OnboardingStep.WELCOME -> WelcomeScreen(
                            onCreateNew = { if (acceptTerms) viewModel.generateNewWallet() },
                            onImport = { if (acceptTerms) showImportDialog = true },
                            acceptTerms = acceptTerms,
                            onAcceptChange = { acceptTerms = it }
                        )
                        OnboardingStep.SHOW_MNEMONIC -> ShowMnemonicScreen(
                            mnemonic = uiState.mnemonic,
                            onNext = { viewModel.goBack() }
                        )
                        OnboardingStep.VERIFY_MNEMONIC -> VerifyMnemonicScreen(
                            mnemonic = uiState.mnemonic,
                            verifiedWords = uiState.verifiedWords,
                            onVerifyWord = viewModel::verifyMnemonicWord
                        )
                        OnboardingStep.SET_PIN -> SetPinScreen(
                            onPinSet = viewModel::setPin
                        )
                        OnboardingStep.CONFIRM_PIN -> ConfirmPinScreen(
                            onPinConfirm = viewModel::confirmPin
                        )
                        OnboardingStep.SETUP_BIOMETRIC -> com.massapay.android.ui.onboarding.screens.BiometricSetupScreen(
                            onEnableBiometric = { viewModel.enableBiometric(true) },
                            onSkip = { viewModel.skipBiometric() }
                        )
                        OnboardingStep.COMPLETE -> {
                            LaunchedEffect(Unit) { onComplete() }
                        }
                    }
                }

                // Import dialog
                if (showImportDialog) {
                    AlertDialog(
                        onDismissRequest = { 
                            showImportDialog = false
                            importType = "mnemonic" // Reset to default
                        },
                        title = { Text("Import Wallet") },
                        text = {
                            Column(
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Tabs para seleccionar tipo de importación
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = importType == "mnemonic",
                                        onClick = { importType = "mnemonic" },
                                        label = { Text("Recovery Phrase") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = importType == "privatekey",
                                        onClick = { importType = "privatekey" },
                                        label = { Text("Private Key") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                
                                // Texto explicativo según el tipo
                                Text(
                                    text = if (importType == "mnemonic") {
                                        "Enter your recovery phrase (12 or 24 words)"
                                    } else {
                                        "Enter your Massa private key (starts with S1...)"
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                
                                OutlinedTextField(
                                    value = importInput,
                                    onValueChange = { importInput = it },
                                    placeholder = { 
                                        Text(
                                            if (importType == "mnemonic") {
                                                "word1 word2 word3 ..."
                                            } else {
                                                "S1..."
                                            }
                                        ) 
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = if (importType == "mnemonic") 3 else 2,
                                    maxLines = if (importType == "mnemonic") 4 else 3
                                )
                                
                                Text(
                                    text = if (importType == "mnemonic") {
                                        "Make sure to enter the words in the correct order, separated by spaces."
                                    } else {
                                        "Your private key will be securely encrypted and stored on this device."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        confirmButton = {
                            val isValid = if (importType == "mnemonic") {
                                importInput.text.trim().split(Regex("\\s+")).size in listOf(12, 24)
                            } else {
                                importInput.text.trim().startsWith("S1")
                            }
                            
                            Button(
                                onClick = {
                                    if (importType == "mnemonic") {
                                        viewModel.importWallet(importInput.text.trim())
                                    } else {
                                        viewModel.importFromPrivateKey(importInput.text.trim())
                                    }
                                    showImportDialog = false
                                    importInput = TextFieldValue("") // Clear input
                                    importType = "mnemonic" // Reset to default
                                },
                                enabled = isValid
                            ) {
                                Text("Import")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { 
                                showImportDialog = false
                                importType = "mnemonic" // Reset to default
                            }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                // Error handling
                uiState.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreen(
    onCreateNew: () -> Unit,
    onImport: () -> Unit,
    acceptTerms: Boolean,
    onAcceptChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // App Logo
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                painter = painterResource(id = R.drawable.ic_logo),
                contentDescription = "Massa Pay Logo",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp)
            )
        }

        Text(
            text = "Welcome to Massa Pay",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Your secure Massa Network wallet",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        var showTermsDialog by remember { mutableStateOf(false) }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { showTermsDialog = true }
        ) {
            Checkbox(checked = acceptTerms, onCheckedChange = onAcceptChange)
            Spacer(Modifier.width(8.dp))
            Text(
                "I accept the Terms and Conditions",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Button(
            onClick = onCreateNew,
            modifier = Modifier.fillMaxWidth(),
            enabled = acceptTerms
        ) {
            Text("Create New Wallet")
        }
        
        OutlinedButton(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth(),
            enabled = acceptTerms
        ) {
            Text("Import Existing Wallet")
        }

        if (showTermsDialog) {
            AlertDialog(
                onDismissRequest = { showTermsDialog = false },
                title = { Text("Terms and Conditions") },
                text = {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            """
                            1. Wallet Security
                            - You are responsible for maintaining the security of your recovery phrase
                            - Never share your recovery phrase with anyone
                            - We cannot recover your wallet if you lose your recovery phrase
                            
                            2. Risks
                            - Cryptocurrency transactions are irreversible
                            - The value of cryptocurrencies can be volatile
                            - Always verify transaction details before confirming
                            
                            3. Privacy
                            - We do not store your private keys or recovery phrase
                            - Your wallet data is encrypted on your device
                            
                            4. Usage
                            - This wallet is for use with the Massa Network
                            - Follow all applicable laws and regulations
                            """.trimIndent()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onAcceptChange(true)
                            showTermsDialog = false
                        }
                    ) {
                        Text("Accept")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showTermsDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
private fun ShowMnemonicScreen(
    mnemonic: List<String>,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Write Down Your Recovery Phrase",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "These ${mnemonic.size} words are the only way to restore your wallet. Write them down and keep them safe.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Grid(
                items = mnemonic.mapIndexed { index, word -> 
                    "${index + 1}. $word"
                },
                columns = 2,
                modifier = Modifier.padding(16.dp)
            )
        }
        
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("I've Written It Down")
        }
    }
}

@Composable
private fun Grid(
    items: List<String>,
    columns: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowItems.forEach { item ->
                    Text(
                        text = item,
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                // Fill empty spaces if last row is incomplete
                repeat(columns - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DisableScreenshots(content: @Composable () -> Unit) {
    val windowInfo = LocalWindowInfo.current
    DisposableEffect(windowInfo) {
        // noop on this platform
        onDispose { }
    }
    content()
}