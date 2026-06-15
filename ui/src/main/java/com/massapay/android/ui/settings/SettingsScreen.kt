package com.massapay.android.ui.settings

import androidx.biometric.BiometricManager as AndroidBiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel

private val SettingsSheetMaxHeight = 0.9f

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onShowMnemonic: () -> Unit,
    onResetWallet: () -> Unit = {},
    onManageAccounts: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    var showSeedPhraseDialog by remember { mutableStateOf(false) }
    var showPrivateKeyDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showPinResultDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var biometricError by remember { mutableStateOf<String?>(null) }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Appearance Section
            SettingsSection(title = "Appearance") {
                var showThemeDialog by remember { mutableStateOf(false) }
                
                ModernSettingsCard {
                    ModernSettingsItem(
                        icon = Icons.Outlined.Palette,
                        title = "Theme",
                        subtitle = when (uiState.themeMode) {
                            com.massapay.android.core.preferences.ThemeMode.LIGHT -> "Light Mode"
                            com.massapay.android.core.preferences.ThemeMode.DARK -> "Dark Mode"
                            com.massapay.android.core.preferences.ThemeMode.SYSTEM -> "System Default"
                        },
                        onClick = { showThemeDialog = true }
                    )
                }
                
                if (showThemeDialog) {
                    ModernThemeDialog(
                        currentTheme = uiState.themeMode,
                        onDismiss = { showThemeDialog = false },
                        onThemeSelected = { theme ->
                            viewModel.setThemeMode(theme)
                            showThemeDialog = false
                        }
                    )
                }
            }
            
            // Security Section
            SettingsSection(title = "Security") {
                ModernSettingsCard {
                    ModernSettingsItem(
                        icon = Icons.Outlined.Lock,
                        title = "Change PIN",
                        subtitle = "Update your security PIN",
                        onClick = { showChangePinDialog = true }
                    )
                    
                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    ModernSettingsItem(
                        icon = Icons.Outlined.Fingerprint,
                        title = "Biometric Login",
                        subtitle = "Use fingerprint or face ID",
                        trailing = {
                            Switch(
                                checked = uiState.biometricEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        viewModel.enableBiometric(context as FragmentActivity) { error ->
                                            biometricError = error
                                        }
                                    } else {
                                        viewModel.disableBiometric()
                                    }
                                }
                            )
                        }
                    )
                    
                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    ModernSettingsItem(
                        icon = Icons.Outlined.Visibility,
                        title = "Show Recovery Phrase",
                        subtitle = "View your 12-word seed phrase",
                        onClick = { showSeedPhraseDialog = true }
                    )
                    
                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    ModernSettingsItem(
                        icon = Icons.Outlined.Key,
                        title = "Show Private Key",
                        subtitle = "View your private key",
                        onClick = { showPrivateKeyDialog = true }
                    )
                }
            }

            // Wallet Section with modern card
            SettingsSection(title = "Wallet") {
                ModernSettingsCard {
                    ModernSettingsItem(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        title = uiState.activeAccountName,
                        subtitle = uiState.activeWallet?.let { 
                            "${it.take(12)}...${it.takeLast(8)}"
                        } ?: "No wallet selected",
                        showArrow = false
                    )
                    
                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    ModernSettingsItem(
                        icon = Icons.Outlined.ManageAccounts,
                        title = "Manage Accounts",
                        subtitle = "Create, switch, or remove accounts",
                        onClick = onManageAccounts
                    )
                }
            }

            SettingsSection(title = "Advanced") {
                ModernSettingsCard {
                    ModernSettingsItem(
                        icon = Icons.Outlined.Image,
                        title = "NFT",
                        subtitle = "Show NFT tools on the dashboard",
                        showArrow = false,
                        trailing = {
                            Switch(
                                checked = uiState.nftEnabled,
                                onCheckedChange = viewModel::setNftEnabled
                            )
                        }
                    )

                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    ModernSettingsItem(
                        icon = Icons.Outlined.SwapHoriz,
                        title = "Swap",
                        subtitle = "Enable token swap shortcuts",
                        showArrow = false,
                        trailing = {
                            Switch(
                                checked = uiState.swapEnabled,
                                onCheckedChange = viewModel::setSwapEnabled
                            )
                        }
                    )

                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    ModernSettingsItem(
                        icon = Icons.Outlined.Layers,
                        title = "Staking",
                        subtitle = "Show staking tools on the dashboard",
                        showArrow = false,
                        trailing = {
                            Switch(
                                checked = uiState.stakingEnabled,
                                onCheckedChange = viewModel::setStakingEnabled
                            )
                        }
                    )
                }
            }

            // Transaction History Backup Section
            SettingsSection(title = "Transaction History") {
                var showExportSuccess by remember { mutableStateOf(false) }
                var showImportSheet by remember { mutableStateOf(false) }
                var exportedFilePath by remember { mutableStateOf("") }

                ModernSettingsCard {
                    ModernSettingsItem(
                        icon = Icons.Outlined.Upload,
                        title = "Export History",
                        subtitle = "Save transaction history to file",
                        onClick = {
                            viewModel.exportTransactionHistory { filePath ->
                                exportedFilePath = filePath
                                showExportSuccess = true
                            }
                        }
                    )

                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    ModernSettingsItem(
                        icon = Icons.Outlined.Download,
                        title = "Import History",
                        subtitle = "Restore transaction history from backup",
                        onClick = { showImportSheet = true }
                    )
                }

                if (showExportSuccess) {
                    SettingsModalSheet(
                        title = "Export Successful",
                        icon = Icons.Default.CheckCircle,
                        iconTint = Color(0xFF4CAF50),
                        onDismiss = { showExportSuccess = false },
                        actions = {
                            Button(
                                onClick = { showExportSuccess = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                            ) { Text("OK") }
                        }
                    ) {
                        Text("Your transaction history has been exported successfully.")
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("File saved to:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    exportedFilePath,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                if (showImportSheet) {
                    var availableBackups by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
                    var importSuccess by remember { mutableStateOf(false) }
                    var importedCount by remember { mutableStateOf(0) }
                    var importError by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(showImportSheet) {
                        val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                        val backupFiles = downloadsDir?.listFiles { file ->
                            file.name.startsWith("massapay_backup_") && file.name.endsWith(".json")
                        }?.sortedByDescending { it.lastModified() } ?: emptyList()
                        availableBackups = backupFiles
                    }

                    LaunchedEffect(uiState.error) {
                        if (uiState.error != null) {
                            importError = uiState.error
                            viewModel.clearError()
                        }
                    }

                    SettingsModalSheet(
                        title = if (importSuccess) "Import Successful" else "Import History",
                        icon = if (importSuccess) Icons.Default.CheckCircle else Icons.Outlined.Download,
                        iconTint = if (importSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                        onDismiss = {
                            showImportSheet = false
                            importSuccess = false
                            importError = null
                        },
                        actions = {
                            if (!importSuccess && importError == null) {
                                TextButton(onClick = { showImportSheet = false }) { Text("Cancel") }
                            } else {
                                Button(
                                    onClick = {
                                        showImportSheet = false
                                        importSuccess = false
                                        importError = null
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (importSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                                    )
                                ) { Text("OK") }
                            }
                        }
                    ) {
                        when {
                            importSuccess -> Text("Successfully imported $importedCount new transactions to your history.")
                            importError != null -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Text(
                                    importError ?: "",
                                    modifier = Modifier.padding(12.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            availableBackups.isEmpty() -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("No backup files found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Export your transaction history first", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                }
                            }
                            else -> {
                                Text("Select a backup file to restore:", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                availableBackups.forEach { file ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        onClick = {
                                            viewModel.importTransactionHistory(file.absolutePath) { count ->
                                                importedCount = count
                                                importSuccess = true
                                            }
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(file.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.US).format(java.util.Date(file.lastModified())),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // About Section - Collapsible
            SettingsSection(title = "About") {
                var showAboutSheet by remember { mutableStateOf(false) }
                var donationCopied by remember { mutableStateOf(false) }
                val donationAddress = "AU12TNZbt6n4bGRL3Xud2WP799M2KLYwKRu6jyX7oa462fJ8RqCsP"
                val donationAddressShort = "${donationAddress.take(9)}...${donationAddress.takeLast(8)}"
                val clipboardManager = LocalClipboardManager.current

                LaunchedEffect(donationCopied) {
                    if (donationCopied) {
                        kotlinx.coroutines.delay(1500)
                        donationCopied = false
                    }
                }

                ModernSettingsCard {
                    ModernSettingsItem(
                        icon = Icons.Outlined.Info,
                        title = "MassaConnect",
                        subtitle = "Version 1.5.0 - Tap to view details",
                        onClick = { showAboutSheet = true }
                    )
                }

                if (showAboutSheet) {
                    SettingsModalSheet(
                        title = "MassaConnect",
                        icon = Icons.Outlined.Info,
                        onDismiss = { showAboutSheet = false },
                        actions = {
                            Button(onClick = { showAboutSheet = false }) { Text("Close") }
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0xFF1a1a2e), Color(0xFF16213e))
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(72.dp).background(Color.White, RoundedCornerShape(20.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "M",
                                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 36.sp),
                                        color = Color.Black
                                    )
                                }
                                Text("MassaConnect", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.15f)) {
                                    Text("v1.5.0", modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = Color.White)
                                }
                                Text("Self-Custodial Wallet for Massa", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                            }
                        }

                        Text(
                            text = "A secure, open-source cryptocurrency wallet built specifically for the Massa blockchain. Take full control of your digital assets with cutting-edge technology.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            AboutFeatureItem(icon = Icons.Outlined.Lock, label = "Non-Custodial")
                            AboutFeatureItem(icon = Icons.Outlined.Code, label = "Open Source")
                            AboutFeatureItem(icon = Icons.Outlined.Shield, label = "Secure")
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Text("Technology Stack", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TechChip("Kotlin")
                            TechChip("Jetpack Compose")
                            TechChip("Material 3")
                            TechChip("secp256k1")
                            TechChip("Base58Check")
                            TechChip("BIP-39/44")
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Code, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Developer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("mderramus", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                            }
                            Icon(Icons.Outlined.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(21.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Support development",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "Donations help keep MassaConnect moving.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        donationAddressShort,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(donationAddress))
                                            donationCopied = true
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            if (donationCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = "Copy donation address",
                                            tint = if (donationCopied) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                                    ) {
                                        Text(
                                            "X",
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        "@mderramus",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Danger Zone with modern card
            SettingsSection(
                title = "Danger Zone",
                titleColor = MaterialTheme.colorScheme.error
            ) {
                // Detect theme for proper styling
                val bgColor = MaterialTheme.colorScheme.background
                val isDarkTheme = (bgColor.red * 0.299f + bgColor.green * 0.587f + bgColor.blue * 0.114f) < 0.5f
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (isDarkTheme) 0.dp else 4.dp
                    )
                ) {
                    ModernSettingsItem(
                        icon = Icons.Outlined.DeleteForever,
                        title = "Clear All Data",
                        subtitle = "Remove all wallets and reset app",
                        onClick = { showClearDataDialog = true },
                        textColor = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Change PIN Dialog
    if (showChangePinDialog) {
        ChangePinDialog(
            onDismiss = { 
                showChangePinDialog = false
                viewModel.clearPinChangeStatus()
            },
            onConfirm = { oldPin, newPin ->
                viewModel.changePin(oldPin, newPin)
                showChangePinDialog = false
                showPinResultDialog = true
            }
        )
    }
    
    // PIN Change Result Sheet
    if (showPinResultDialog) {
        SettingsModalSheet(
            title = if (uiState.pinChangeSuccess) "PIN Changed Successfully" else "PIN Change Failed",
            icon = if (uiState.pinChangeSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
            iconTint = if (uiState.pinChangeSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
            onDismiss = {
                showPinResultDialog = false
                viewModel.clearPinChangeStatus()
            },
            actions = {
                Button(
                    onClick = {
                        showPinResultDialog = false
                        viewModel.clearPinChangeStatus()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.pinChangeSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    )
                ) {
                    Text("OK")
                }
            }
        ) {
            Text(
                text = if (uiState.pinChangeSuccess) {
                    "Your PIN has been updated successfully. Please use the new PIN for future authentication."
                } else {
                    uiState.error ?: "Failed to change PIN. Please try again."
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    // Clear Data Confirmation with Strong Warning
    if (showClearDataDialog) {
        SettingsModalSheet(
            title = "Delete Everything?",
            icon = Icons.Default.Warning,
            iconTint = MaterialTheme.colorScheme.error,
            titleColor = MaterialTheme.colorScheme.error,
            onDismiss = { showClearDataDialog = false },
            actions = {
                FilledTonalButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showClearDataDialog = false
                        onResetWallet()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Yes, Delete Everything")
                }
            }
        ) {
            Text(
                "This action cannot be undone!",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.error
            )
            Text(
                "You are about to permanently delete:",
                style = MaterialTheme.typography.bodyMedium
            )
            Column(
                modifier = Modifier.padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("All wallet addresses", style = MaterialTheme.typography.bodyMedium)
                Text("Your recovery phrase", style = MaterialTheme.typography.bodyMedium)
                Text("Transaction history", style = MaterialTheme.typography.bodyMedium)
                Text("All app settings", style = MaterialTheme.typography.bodyMedium)
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Make sure you have backed up your recovery phrase before proceeding. Without it, you will lose access to your funds forever!",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }

    // Biometric Error Sheet
    if (biometricError != null) {
        SettingsModalSheet(
            title = "Biometric Error",
            icon = Icons.Default.Error,
            iconTint = MaterialTheme.colorScheme.error,
            onDismiss = { biometricError = null },
            actions = {
                TextButton(onClick = { biometricError = null }) {
                    Text("OK")
                }
            }
        ) {
            Text(biometricError ?: "")
        }
    }

    // Seed Phrase Dialog with Authentication
    if (showSeedPhraseDialog) {
        SeedPhraseRevealDialog(
            seedPhrase = uiState.seedPhrase ?: "",
            onDismiss = { showSeedPhraseDialog = false },
            viewModel = viewModel
        )
    }
    
    // Private Key Dialog with Authentication
    if (showPrivateKeyDialog) {
        PrivateKeyRevealDialog(
            onDismiss = { showPrivateKeyDialog = false },
            viewModel = viewModel
        )
    }

    // Error handling
    uiState.error?.let { error ->
        SettingsModalSheet(
            title = "Error",
            icon = Icons.Default.Error,
            onDismiss = { viewModel.clearError() },
            iconTint = MaterialTheme.colorScheme.error,
            actions = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            }
        ) {
            Text(error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsModalSheet(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onDismiss: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(SettingsSheetMaxHeight)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = iconTint.copy(alpha = 0.14f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = titleColor,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            ),
            color = titleColor,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        content()
    }
}

@Composable
fun ModernSettingsCard(
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable ColumnScope.() -> Unit
) {
    // Detect if dark theme based on background color
    val bgColor = MaterialTheme.colorScheme.background
    val isDarkTheme = (bgColor.red * 0.299f + bgColor.green * 0.587f + bgColor.blue * 0.114f) < 0.5f
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDarkTheme) 0.dp else 4.dp
        )
    ) {
        Column {
            content()
        }
    }
}

@Composable
fun ModernSettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    showArrow: Boolean = true
) {
    // Detect if dark theme based on background color
    val bgColor = MaterialTheme.colorScheme.background
    val isDarkTheme = (bgColor.red * 0.299f + bgColor.green * 0.587f + bgColor.blue * 0.114f) < 0.5f
    val iconContainerColor = if (isDarkTheme) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Black
    val iconTintColor = Color.White
    
    Surface(
        onClick = { onClick?.invoke() },
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with gradient background
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = iconContainerColor
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTintColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    ),
                    color = textColor
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.6f)
                    )
                }
            }

            if (trailing != null) {
                trailing()
            } else if (showArrow && onClick != null) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun ModernThemeDialog(
    currentTheme: com.massapay.android.core.preferences.ThemeMode,
    onDismiss: () -> Unit,
    onThemeSelected: (com.massapay.android.core.preferences.ThemeMode) -> Unit
) {
    SettingsModalSheet(
        title = "Select Theme",
        icon = Icons.Outlined.Palette,
        onDismiss = onDismiss,
        actions = {
            FilledTonalButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    ) {
        ThemeOption(
            icon = Icons.Outlined.LightMode,
            title = "Light Mode",
            subtitle = "Always use light theme",
            selected = currentTheme == com.massapay.android.core.preferences.ThemeMode.LIGHT,
            onClick = { onThemeSelected(com.massapay.android.core.preferences.ThemeMode.LIGHT) }
        )

        ThemeOption(
            icon = Icons.Outlined.DarkMode,
            title = "Dark Mode",
            subtitle = "Always use dark theme",
            selected = currentTheme == com.massapay.android.core.preferences.ThemeMode.DARK,
            onClick = { onThemeSelected(com.massapay.android.core.preferences.ThemeMode.DARK) }
        )

        ThemeOption(
            icon = Icons.Outlined.Brightness4,
            title = "System Default",
            subtitle = "Follow system settings",
            selected = currentTheme == com.massapay.android.core.preferences.ThemeMode.SYSTEM,
            onClick = { onThemeSelected(com.massapay.android.core.preferences.ThemeMode.SYSTEM) }
        )
    }
}

@Composable
fun ThemeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) 
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else 
            androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (selected)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (selected) 
                            androidx.compose.ui.text.font.FontWeight.SemiBold 
                        else 
                            androidx.compose.ui.text.font.FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        onClick = { onClick?.invoke() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
            }

            trailing?.invoke()
        }
    }
}

@Composable
fun ChangePinDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    SettingsModalSheet(
        title = "Change PIN",
        icon = Icons.Outlined.Lock,
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            TextButton(
                onClick = {
                    when {
                        oldPin.length != 6 || newPin.length != 6 || confirmPin.length != 6 -> {
                            error = "PIN must be 6 digits"
                        }
                        newPin != confirmPin -> {
                            error = "New PINs do not match"
                        }
                        else -> {
                            onConfirm(oldPin, newPin)
                        }
                    }
                }
            ) {
                Text("Change PIN")
            }
        }
    ) {
        OutlinedTextField(
            value = oldPin,
            onValueChange = { if (it.length <= 6) oldPin = it },
            label = { Text("Current PIN") },
            singleLine = true,
            visualTransformation = com.massapay.android.ui.onboarding.screens.PinVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = newPin,
            onValueChange = { if (it.length <= 6) newPin = it },
            label = { Text("New PIN") },
            singleLine = true,
            visualTransformation = com.massapay.android.ui.onboarding.screens.PinVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = confirmPin,
            onValueChange = { if (it.length <= 6) confirmPin = it },
            label = { Text("Confirm New PIN") },
            singleLine = true,
            visualTransformation = com.massapay.android.ui.onboarding.screens.PinVisualTransformation(),
            isError = error != null,
            modifier = Modifier.fillMaxWidth()
        )

        if (error != null) {
            Text(
                text = error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SecureRevealAuthContent(
    title: String,
    description: String,
    useBiometric: Boolean,
    biometricEnabled: Boolean,
    pin: String,
    error: String?,
    onPinChange: (String) -> Unit,
    onUsePin: () -> Unit,
    onUseBiometric: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(24.dp),
            color = primary.copy(alpha = 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (useBiometric) Icons.Default.Fingerprint else Icons.Outlined.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = primary
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Sensitive wallet data",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Only reveal this in a private place. MassaConnect will never ask you to share it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (useBiometric) {
            Button(
                onClick = onUseBiometric,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(29.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSystemInDarkTheme()) Color(0xFF2A2A2A) else Color.Black,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Authorize with Biometric", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onUsePin, modifier = Modifier.fillMaxWidth()) {
                Text("Use PIN instead")
            }
        } else {
            Text(
                "Enter your 6-digit PIN",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = pin,
                onValueChange = onPinChange,
                label = { Text("PIN") },
                singleLine = true,
                visualTransformation = com.massapay.android.ui.onboarding.screens.PinVisualTransformation(),
                isError = error != null,
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.onSurface
                )
            )
            if (biometricEnabled) {
                TextButton(onClick = onUseBiometric, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Use Biometric")
                }
            }
        }
    }
}

@Composable
private fun SeedPhraseRevealDialog(
    seedPhrase: String,
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel
) {
    var isAuthenticated by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var useBiometric by remember { mutableStateOf(false) }
    var showCopied by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val currentSeedPhrase = uiState.seedPhrase ?: seedPhrase
    val currentS1Key = uiState.s1PrivateKey ?: ""
    val isS1Import = uiState.isS1Import
    val contentToCopy = if (isS1Import) currentS1Key else currentSeedPhrase

    val seedBiometricPrompt = remember {
        activity?.let {
            BiometricPrompt(
                it,
                ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        isAuthenticated = true
                        error = null
                        viewModel.loadSeedPhrase()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        error = "Biometric authentication failed: $errString"
                        useBiometric = false
                    }

                    override fun onAuthenticationFailed() {
                        error = "Biometric authentication failed"
                    }
                }
            )
        }
    }

    val seedPromptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verify Identity")
            .setSubtitle("Authenticate to view recovery phrase")
            .setNegativeButtonText("Use PIN")
            .build()
    }

    LaunchedEffect(Unit) {
        if (uiState.biometricEnabled) {
            val biometricManager = AndroidBiometricManager.from(context)
            val canAuthenticate = biometricManager.canAuthenticate(
                AndroidBiometricManager.Authenticators.BIOMETRIC_STRONG or
                    AndroidBiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            if (canAuthenticate == AndroidBiometricManager.BIOMETRIC_SUCCESS) {
                useBiometric = true
                seedBiometricPrompt?.authenticate(seedPromptInfo)
            }
        }
    }

    SettingsModalSheet(
        title = if (isAuthenticated) {
            if (isS1Import) "Private Key (S1)" else "Recovery Phrase"
        } else {
            "Verify Identity"
        },
        icon = Icons.Outlined.Shield,
        iconTint = if (isAuthenticated) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text("Close") }
            if (!isAuthenticated && !useBiometric) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (pin.length == 6 && viewModel.verifyPin(pin)) {
                            isAuthenticated = true
                            error = null
                            viewModel.loadSeedPhrase()
                        } else {
                            error = if (pin.length == 6) "Incorrect PIN" else "PIN must be 6 digits"
                        }
                    },
                    enabled = pin.length == 6
                ) { Text("Verify") }
            } else if (isAuthenticated) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(contentToCopy))
                        showCopied = true
                    },
                    enabled = contentToCopy.isNotEmpty()
                ) {
                    Icon(
                        imageVector = if (showCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (showCopied) "Copied" else "Copy")
                }
            }
        }
    ) {
        if (!isAuthenticated) {
            SecureRevealAuthContent(
                title = if (isS1Import) "Protecting your private key" else "Protecting your recovery phrase",
                description = if (isS1Import) {
                    "Verify your identity before exposing the S1 key for this account."
                } else {
                    "Verify your identity before exposing the seed phrase for this account."
                },
                useBiometric = useBiometric,
                biometricEnabled = uiState.biometricEnabled,
                pin = pin,
                error = error,
                onPinChange = { next ->
                    if (next.length <= 6 && next.all { c -> c.isDigit() }) pin = next
                },
                onUsePin = { useBiometric = false },
                onUseBiometric = {
                    useBiometric = true
                    seedBiometricPrompt?.authenticate(seedPromptInfo)
                }
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        } else {
            RevealedSecretContent(
                isS1Import = isS1Import,
                content = contentToCopy
            )
        }
    }
}

@Composable
private fun RevealedSecretContent(
    isS1Import: Boolean,
    content: String
) {
    val title = if (isS1Import) "Private key revealed" else "Recovery phrase revealed"
    val description = if (isS1Import) {
        "Anyone with this S1 key can control this wallet."
    } else {
        "Write these words down in order and store them offline."
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.34f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.18f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.13f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (content.isBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
            ) {
                Text(
                    "No recovery information found",
                    modifier = Modifier.padding(18.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (isS1Import) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    modifier = Modifier.padding(18.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            val words = content.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            RecoveryWordGrid(words = words)
        }
    }
}

@Composable
private fun RecoveryWordGrid(words: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Recovery phrase",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            words.forEachIndexed { index, word ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f)
                        )
                        Text(
                            word,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivateKeyRevealDialog(
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel
) {
    var isAuthenticated by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var useBiometric by remember { mutableStateOf(false) }
    var showCopiedS1 by remember { mutableStateOf(false) }
    var showCopiedP1 by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val privateKeyS1 = uiState.privateKeyS1 ?: ""
    val publicKeyP1 = uiState.publicKeyP1 ?: ""

    val privKeyBiometricPrompt = remember {
        activity?.let {
            BiometricPrompt(
                it,
                ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        isAuthenticated = true
                        error = null
                        viewModel.loadPrivateKey()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        error = "Biometric authentication failed: $errString"
                        useBiometric = false
                    }

                    override fun onAuthenticationFailed() {
                        error = "Biometric authentication failed"
                    }
                }
            )
        }
    }

    val privKeyPromptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verify Identity")
            .setSubtitle("Authenticate to view private key")
            .setNegativeButtonText("Use PIN")
            .build()
    }

    LaunchedEffect(Unit) {
        if (uiState.biometricEnabled) {
            val biometricManager = AndroidBiometricManager.from(context)
            val canAuthenticate = biometricManager.canAuthenticate(
                AndroidBiometricManager.Authenticators.BIOMETRIC_STRONG or
                    AndroidBiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            if (canAuthenticate == AndroidBiometricManager.BIOMETRIC_SUCCESS) {
                useBiometric = true
                privKeyBiometricPrompt?.authenticate(privKeyPromptInfo)
            }
        }
    }

    SettingsModalSheet(
        title = if (isAuthenticated) "Export Keys" else "Verify Identity",
        icon = Icons.Outlined.Key,
        iconTint = if (isAuthenticated) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text("Close") }
            if (!isAuthenticated && !useBiometric) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (pin.length == 6 && viewModel.verifyPin(pin)) {
                            isAuthenticated = true
                            error = null
                            viewModel.loadPrivateKey()
                        } else {
                            error = if (pin.length == 6) "Incorrect PIN" else "PIN must be 6 digits"
                        }
                    },
                    enabled = pin.length == 6
                ) { Text("Verify") }
            } else if (isAuthenticated) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(privateKeyS1))
                        showCopiedS1 = true
                        showCopiedP1 = false
                    },
                    enabled = privateKeyS1.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(if (showCopiedS1) Icons.Default.Check else Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (showCopiedS1) "S1 Copied" else "S1")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(publicKeyP1))
                        showCopiedP1 = true
                        showCopiedS1 = false
                    },
                    enabled = publicKeyP1.isNotEmpty()
                ) {
                    Icon(if (showCopiedP1) Icons.Default.Check else Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (showCopiedP1) "P1 Copied" else "P1")
                }
            }
        }
    ) {
        if (!isAuthenticated) {
            SecureRevealAuthContent(
                title = "Protecting your keys",
                description = "Verify your identity before exposing export keys for this wallet.",
                useBiometric = useBiometric,
                biometricEnabled = uiState.biometricEnabled,
                pin = pin,
                error = error,
                onPinChange = { next ->
                    if (next.length <= 6 && next.all { c -> c.isDigit() }) pin = next
                },
                onUsePin = { useBiometric = false },
                onUseBiometric = {
                    useBiometric = true
                    privKeyBiometricPrompt?.authenticate(privKeyPromptInfo)
                }
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text("Warning", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                    Text("Never share your private key (S1). Anyone with this key can control your wallet. The public key (P1) is safe to share for verification.")
                }
            }
            KeyValueCard("Private Key (S1)", privateKeyS1, MaterialTheme.colorScheme.error)
            KeyValueCard("Public Key (P1)", publicKeyP1, MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun KeyValueCard(
    title: String,
    value: String,
    accentColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = accentColor)
            Text(
                text = value.ifEmpty { "Not available" },
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
// About Dialog Components
@Composable
private fun AboutFeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    // Same style as ModernSettingsItem icons
    val bgColor = MaterialTheme.colorScheme.background
    val isDarkTheme = (bgColor.red * 0.299f + bgColor.green * 0.587f + bgColor.blue * 0.114f) < 0.5f
    val iconContainerColor = if (isDarkTheme) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Black
    val iconTintColor = Color.White
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(14.dp),
            color = iconContainerColor
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTintColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AboutStatItem(icon: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = icon,
            fontSize = 24.sp
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TechChip(text: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = { content() }
    )
}
