package com.massapay.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.massapay.android.security.storage.SecureStorage
import com.massapay.android.ui.dashboard.DashboardScreen
import com.massapay.android.ui.lock.LockScreen
import com.massapay.android.ui.onboarding.OnboardingFlowNew
import com.massapay.android.ui.qrscanner.QrScannerScreen
import com.massapay.android.ui.settings.SettingsScreen
import com.massapay.android.ui.transaction.ReceiveScreen
import com.massapay.android.ui.transaction.SendScreen
import com.massapay.android.ui.transaction.SendViewModel
import com.massapay.android.ui.swap.SwapScreen
import com.massapay.android.ui.charts.ChartsScreen
import com.massapay.android.ui.portfolio.PortfolioScreen
import com.massapay.android.ui.agentbridge.AgentQRScannerScreen
import com.massapay.android.ui.theme.MassaPayTheme
import com.massapay.android.core.preferences.AdvancedFeatureManager
import com.massapay.android.core.model.NFT
import com.massapay.android.core.preferences.ThemeManager
import com.massapay.android.core.preferences.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import javax.inject.Inject

private enum class HomeSheet {
    SEND,
    RECEIVE,
    SWAP,
    NFT,
    SETTINGS
}

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    
    @Inject
    lateinit var secureStorage: SecureStorage
    
    @Inject
    lateinit var themeManager: ThemeManager

    @Inject
    lateinit var advancedFeatureManager: AdvancedFeatureManager
    
    @Inject
    lateinit var sessionManager: SessionManager
    
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the AndroidX splash screen BEFORE super.onCreate so the system
        // splash uses our transparent icon (no app-icon zoom) and white background.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        // Enable edge-to-edge display
        enableEdgeToEdge()
        
        setContent {
            val themeMode by themeManager.themeMode.collectAsState(initial = ThemeMode.LIGHT)
            val advancedFeatures by advancedFeatureManager.featureState.collectAsState(
                initial = com.massapay.android.core.preferences.AdvancedFeatureState()
            )
            val isSystemInDarkTheme = isSystemInDarkTheme()
            var showSplash by remember { mutableStateOf(true) }
            val isLocked by sessionManager.isLocked.collectAsState()
            
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme
            }
            
            // Set window background color immediately based on theme
            SideEffect {
                window.decorView.setBackgroundColor(
                    if (darkTheme) android.graphics.Color.BLACK 
                    else android.graphics.Color.WHITE
                )
            }
            
            if (showSplash) {
                AnimatedSplashScreen(onFinished = { showSplash = false })
            } else {
                MassaPayTheme(darkTheme = darkTheme) {
                // Update system bars colors based on theme
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as FragmentActivity).window
                        val insetsController = WindowCompat.getInsetsController(window, view)
                        
                        // Get colors from current theme
                        val backgroundColor = if (darkTheme) {
                            Color(0xFF000000) // Dark background
                        } else {
                            Color(0xFFFFFFFF) // Light background
                        }
                        
                        // Set status bar color (top bar with time, battery, etc)
                        window.statusBarColor = backgroundColor.toArgb()
                        
                        // Set navigation bar color (bottom bar with home, back, etc)
                        window.navigationBarColor = backgroundColor.toArgb()
                        
                        // Set icon colors - dark icons on light theme, light icons on dark theme
                        insetsController.isAppearanceLightStatusBars = !darkTheme
                        insetsController.isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                
                val navController = rememberNavController()
                
                // Determine the initial route based on app state
                val hasWallet = secureStorage.hasWallet()
                val onboardingCompleted = secureStorage.isOnboardingCompleted()
                
                // Show lock screen if session is locked, otherwise show appropriate screen
                val startDestination = when {
                    !onboardingCompleted -> "onboarding"
                    isLocked && hasWallet -> "lock"
                    hasWallet -> "home"
                    else -> "onboarding"
                }
                
                // Monitor session state and navigate to lock when locked
                LaunchedEffect(isLocked) {
                    if (isLocked && hasWallet && onboardingCompleted) {
                        // Navigate to lock screen when session becomes locked
                        if (navController.currentDestination?.route != "lock" && 
                            navController.currentDestination?.route != "onboarding") {
                            navController.navigate("lock") {
                                popUpTo(0) { inclusive = false }
                            }
                        }
                    }
                }
                
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    enterTransition = {
                        if (
                            initialState.destination.route == "onboarding" &&
                            targetState.destination.route == "home"
                        ) {
                            slideInVertically(
                                initialOffsetY = { fullHeight -> -fullHeight },
                                animationSpec = tween(520)
                            ) + fadeIn(animationSpec = tween(260))
                        } else {
                            slideInHorizontally(
                                initialOffsetX = { fullWidth -> fullWidth },
                                animationSpec = tween(350)
                            ) + fadeIn(animationSpec = tween(350))
                        }
                    },
                    exitTransition = {
                        if (
                            initialState.destination.route == "onboarding" &&
                            targetState.destination.route == "home"
                        ) {
                            fadeOut(animationSpec = tween(220))
                        } else {
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> -fullWidth / 4 },
                                animationSpec = tween(350)
                            ) + fadeOut(animationSpec = tween(200))
                        }
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> -fullWidth / 4 },
                            animationSpec = tween(350)
                        ) + fadeIn(animationSpec = tween(350))
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(350)
                        ) + fadeOut(animationSpec = tween(200))
                    }
                ) {
                    composable("lock") {
                        LockScreen(
                            onUnlocked = {
                                sessionManager.unlock()
                                navController.navigate("home") {
                                    popUpTo("lock") { inclusive = true }
                                }
                            },
                            onWalletReset = {
                                navController.navigate("onboarding") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }
                    
                    composable("onboarding") {
                        MassaPayTheme(darkTheme = darkTheme) {
                            OnboardingFlowNew(
                                onComplete = {
                                    navController.navigate("home") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                },
                                onBack = { finish() }
                            )
                        }
                    }
                    
                    composable("home") {
                        val dashboardViewModel: com.massapay.android.ui.dashboard.DashboardViewModel = hiltViewModel()
                        val sendViewModel: SendViewModel = hiltViewModel()
                        var activeSheet by remember { mutableStateOf<HomeSheet?>(null) }
                        var selectedSheetNft by remember { mutableStateOf<NFT?>(null) }
                        val homeBackStackEntry = navController.currentBackStackEntry
                        val qrResult by homeBackStackEntry
                            ?.savedStateHandle
                            ?.getStateFlow("qr_result", "")
                            ?.collectAsState()
                            ?: remember { mutableStateOf("") }

                        LaunchedEffect(qrResult) {
                            if (qrResult.isNotBlank()) {
                                activeSheet = HomeSheet.SEND
                                sendViewModel.parseQrCode(qrResult)
                                homeBackStackEntry?.savedStateHandle?.set("qr_result", "")
                            }
                        }

                        DashboardScreen(
                            onSendClick = {
                                activeSheet = HomeSheet.SEND
                            },
                            onReceiveClick = {
                                activeSheet = HomeSheet.RECEIVE
                            },
                            onSettingsClick = {
                                activeSheet = HomeSheet.SETTINGS
                            },
                            onQrScanClick = {
                                navController.navigate("qr-scanner")
                            },
                            onNftClick = {
                                activeSheet = HomeSheet.NFT
                            },
                            onChartsClick = {
                                navController.navigate("charts")
                            },
                            onAccountsClick = {
                                navController.navigate("accounts")
                            },
                            onStakingClick = {
                                navController.navigate("staking")
                            },
                            onSwapClick = {
                                activeSheet = HomeSheet.SWAP
                            },
                            onPortfolioClick = {
                                navController.navigate("portfolio")
                            },
                            nftEnabled = advancedFeatures.nftEnabled,
                            swapEnabled = advancedFeatures.swapEnabled,
                            stakingEnabled = advancedFeatures.stakingEnabled,
                            viewModel = dashboardViewModel
                        )

                        activeSheet?.let { sheet ->
                            ModalBottomSheet(
                                onDismissRequest = {
                                    activeSheet = null
                                    selectedSheetNft = null
                                },
                                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                                modifier = Modifier.fillMaxHeight(0.94f),
                                containerColor = MaterialTheme.colorScheme.background,
                                tonalElevation = 0.dp,
                                dragHandle = { BottomSheetDefaults.DragHandle() }
                            ) {
                                when (sheet) {
                                    HomeSheet.SEND -> {
                                        SendScreen(
                                            onClose = { activeSheet = null },
                                            onScanQr = { navController.navigate("qr-scanner") },
                                            onTransactionSuccess = { dashboardViewModel.refreshData() },
                                            viewModel = sendViewModel
                                        )
                                    }

                                    HomeSheet.RECEIVE -> {
                                        ReceiveScreen(onClose = { activeSheet = null })
                                    }

                                    HomeSheet.SWAP -> {
                                        SwapScreen(onClose = { activeSheet = null })
                                    }

                                    HomeSheet.NFT -> {
                                        val nftViewModel: com.massapay.android.ui.nft.NFTGalleryViewModel = hiltViewModel()
                                        val nftUiState by nftViewModel.uiState.collectAsState()
                                        val selectedNft = selectedSheetNft

                                        if (selectedNft == null) {
                                            com.massapay.android.ui.nft.NFTGalleryScreen(
                                                onNFTClick = { nft -> selectedSheetNft = nft },
                                                onClose = { activeSheet = null },
                                                isDarkTheme = darkTheme,
                                                viewModel = nftViewModel
                                            )
                                        } else {
                                            com.massapay.android.ui.nft.NFTDetailScreen(
                                                nft = selectedNft,
                                                onClose = { selectedSheetNft = null },
                                                onTransfer = { toAddress ->
                                                    android.util.Log.d("NFT", "Transfer ${selectedNft.tokenId} to $toAddress")
                                                    nftViewModel.transferNFT(selectedNft, toAddress)
                                                },
                                                isDarkTheme = darkTheme,
                                                isTransferring = nftUiState.isTransferring,
                                                transferSuccess = nftUiState.transferSuccess,
                                                transferError = nftUiState.error,
                                                onDismissResult = { nftViewModel.resetTransferState() }
                                            )
                                        }
                                    }

                                    HomeSheet.SETTINGS -> {
                                        SettingsScreen(
                                            onBack = { activeSheet = null },
                                            onShowMnemonic = {},
                                            onResetWallet = {
                                                activeSheet = null
                                                navController.navigate("onboarding") {
                                                    popUpTo(0) { inclusive = true }
                                                }
                                            },
                                            onManageAccounts = {
                                                activeSheet = null
                                                navController.navigate("accounts")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    composable("portfolio") {
                        PortfolioScreen(
                            onClose = {
                                navController.popBackStack()
                            }
                        )
                    }
                    
                    composable("send") {
                        val qrResult = it.savedStateHandle.get<String>("qr_result")
                        val sendViewModel: SendViewModel = hiltViewModel()
                        
                        // Get dashboard ViewModel from previous back stack entry
                        val dashboardViewModel = navController.previousBackStackEntry?.let { entry ->
                            hiltViewModel<com.massapay.android.ui.dashboard.DashboardViewModel>(entry)
                        }
                        
                        // Process QR result if available
                        LaunchedEffect(qrResult) {
                            if (!qrResult.isNullOrEmpty()) {
                                sendViewModel.parseQrCode(qrResult)
                                it.savedStateHandle.remove<String>("qr_result")
                            }
                        }
                        
                        SendScreen(
                            onClose = {
                                navController.popBackStack()
                            },
                            onScanQr = {
                                navController.navigate("qr-scanner")
                            },
                            onTransactionSuccess = {
                                // Refresh dashboard data when transaction succeeds
                                dashboardViewModel?.refreshData()
                            },
                            viewModel = sendViewModel
                        )
                    }
                    
                    composable("receive") {
                        ReceiveScreen(
                            onClose = {
                                navController.popBackStack()
                            }
                        )
                    }
                    
                    composable("settings") {
                        SettingsScreen(
                            onBack = {
                                navController.popBackStack()
                            },
                            onShowMnemonic = {
                                // TODO: Implement mnemonic display with authentication
                            },
                            onResetWallet = {
                                // Navigate to onboarding and clear back stack
                                navController.navigate("onboarding") {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            onManageAccounts = {
                                navController.navigate("accounts")
                            }
                        )
                    }
                    
                    composable("accounts") {
                        com.massapay.android.ui.accounts.AccountsScreen(
                            onClose = { navController.popBackStack() },
                            isDarkTheme = darkTheme
                        )
                    }
                    
                    composable("charts") {
                        ChartsScreen(
                            onBackClick = {
                                navController.popBackStack()
                            }
                        )
                    }
                    
                    composable("swap") {
                        SwapScreen(
                            onClose = {
                                navController.popBackStack()
                            }
                        )
                    }
                    
                    composable("qr-scanner") {
                        QrScannerScreen(
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                            onQrCodeScanned = { qrData ->
                                // Save QR data and navigate back to send screen
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("qr_result", qrData)
                                navController.popBackStack()
                            }
                        )
                    }
                    
                    composable("nft") {
                        com.massapay.android.ui.nft.NFTGalleryScreen(
                            onNFTClick = { nft ->
                                // Navigate to NFT detail with serialized NFT
                                navController.currentBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("selected_nft", nft)
                                navController.navigate("nft-detail")
                            },
                            onClose = { navController.popBackStack() },
                            isDarkTheme = darkTheme
                        )
                    }
                    
                    composable("nft-detail") {
                        val nft = navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.get<com.massapay.android.core.model.NFT>("selected_nft")
                        
                        if (nft != null) {
                            val nftViewModel: com.massapay.android.ui.nft.NFTGalleryViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                            val nftUiState by nftViewModel.uiState.collectAsState()
                            
                            com.massapay.android.ui.nft.NFTDetailScreen(
                                nft = nft,
                                onClose = { navController.popBackStack() },
                                onTransfer = { toAddress ->
                                    android.util.Log.d("NFT", "Transfer ${nft.tokenId} to $toAddress")
                                    nftViewModel.transferNFT(nft, toAddress)
                                },
                                isDarkTheme = darkTheme,
                                isTransferring = nftUiState.isTransferring,
                                transferSuccess = nftUiState.transferSuccess,
                                transferError = nftUiState.error,
                                onDismissResult = { nftViewModel.resetTransferState() }
                            )
                        } else {
                            // Fallback if no NFT was passed
                            LaunchedEffect(Unit) {
                                navController.popBackStack()
                            }
                        }
                    }
                    
                    composable("staking") {
                        // Get QR content from saved state (after scanning)
                        val agentQRContent = navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.get<String>("agent_qr")
                        
                        com.massapay.android.ui.staking.StakingScreen(
                            onClose = { navController.popBackStack() },
                            isDarkTheme = darkTheme,
                            onScanAgentQR = { navController.navigate("agent-qr-scanner") },
                            agentQRContent = agentQRContent
                        )
                    }
                    
                    composable("agent-qr-scanner") {
                        AgentQRScannerScreen(
                            onClose = { navController.popBackStack() },
                            isDarkTheme = darkTheme,
                            onQRScanned = { qrContent ->
                                // Navigate back and pass QR content to StakingScreen
                                navController.previousBackStackEntry?.savedStateHandle?.set("agent_qr", qrContent)
                                navController.popBackStack()
                            }
                        )
                    }
                    
                }
                }
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Lock session when app goes to background
        sessionManager.onAppPaused()
    }
    
    override fun onResume() {
        super.onResume()
        // Session manager handles the locked state
        sessionManager.onAppResumed()
    }
    
    override fun onUserInteraction() {
        super.onUserInteraction()
        // Reset inactivity timer on any user interaction
        if (!sessionManager.isLocked.value) {
            sessionManager.resetInactivityTimer()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}

@Composable
fun AnimatedSplashScreen(onFinished: () -> Unit) {
    val tagline = "Connecting people"
    var charCount by remember { mutableStateOf(0) }

    // Blinking cursor for the typewriter effect
    val infinite = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(480),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    // Type the tagline out, then hold briefly so it reads well, then continue
    LaunchedEffect(Unit) {
        delay(100)
        for (i in 1..tagline.length) {
            charCount = i
            delay(42)
        }
        delay(550)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(
                    id = com.massapay.android.ui.R.drawable.brand_name
                ),
                contentDescription = "MassaConnect",
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(72.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tagline.take(charCount),
                    color = Color(0xFF2A2A2A),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "|",
                    color = Color(0xFF2A2A2A).copy(alpha = cursorAlpha),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
