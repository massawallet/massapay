package com.massapay.android

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
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
import com.massapay.android.core.preferences.ThemeManager
import com.massapay.android.core.preferences.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    
    @Inject
    lateinit var secureStorage: SecureStorage
    
    @Inject
    lateinit var themeManager: ThemeManager
    
    @Inject
    lateinit var sessionManager: SessionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        enableEdgeToEdge()
        
        setContent {
            val themeMode by themeManager.themeMode.collectAsState(initial = ThemeMode.LIGHT)
            val isSystemInDarkTheme = isSystemInDarkTheme()
            var showSplash by remember { mutableStateOf(true) }
            val isLocked by sessionManager.isLocked.collectAsState()
            
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme
            }
            
            // Hide splash after brief delay
            LaunchedEffect(Unit) {
                delay(1500) // Show splash for 1.5 seconds
                showSplash = false
            }
            
            // Set window background color immediately based on theme
            SideEffect {
                window.decorView.setBackgroundColor(
                    if (darkTheme) android.graphics.Color.BLACK 
                    else android.graphics.Color.WHITE
                )
            }
            
            if (showSplash) {
                AnimatedSplashScreen(darkTheme = darkTheme)
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
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(350)
                        ) + fadeIn(animationSpec = tween(350))
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> -fullWidth / 4 },
                            animationSpec = tween(350)
                        ) + fadeOut(animationSpec = tween(200))
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
                        // Force light theme for onboarding (both system bars and content)
                        SideEffect {
                            val window = (view.context as FragmentActivity).window
                            val insetsController = WindowCompat.getInsetsController(window, view)
                            window.statusBarColor = Color(0xFFFFFFFF).toArgb()
                            window.navigationBarColor = Color(0xFFFFFFFF).toArgb()
                            insetsController.isAppearanceLightStatusBars = true
                            insetsController.isAppearanceLightNavigationBars = true
                        }
                        MassaPayTheme(darkTheme = false) {
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
                        DashboardScreen(
                            onSendClick = {
                                navController.navigate("send")
                            },
                            onReceiveClick = {
                                navController.navigate("receive")
                            },
                            onSettingsClick = {
                                navController.navigate("settings")
                            },
                            onQrScanClick = {
                                navController.navigate("qr-scanner")
                            },
                            onNftClick = {
                                navController.navigate("nft")
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
                                navController.navigate("swap")
                            },
                            onDAppBrowserClick = {
                                navController.navigate("dapp-browser")
                            },
                            onPortfolioClick = {
                                navController.navigate("portfolio")
                            },
                            viewModel = dashboardViewModel
                        )
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
                    
                    composable("dapp-browser") {
                        com.massapay.android.ui.dapp.DAppBrowserScreen(
                            onClose = { navController.popBackStack() },
                            isDarkTheme = darkTheme,
                            onWalletConnectClick = {
                                navController.navigate("walletconnect")
                            }
                        )
                    }
                    
                    composable("walletconnect") {
                        val wcViewModel: com.massapay.android.ui.walletconnect.WalletConnectViewModel = hiltViewModel()
                        
                        // Handle WC URI from scanner
                        val wcUri = it.savedStateHandle.get<String>("wc_uri")
                        LaunchedEffect(wcUri) {
                            if (!wcUri.isNullOrEmpty()) {
                                wcViewModel.connectWithUri(wcUri)
                                it.savedStateHandle.remove<String>("wc_uri")
                            }
                        }
                        
                        com.massapay.android.ui.walletconnect.WalletConnectScreen(
                            onClose = { navController.popBackStack() },
                            onScanQR = {
                                navController.navigate("wc-scanner")
                            },
                            isDarkTheme = darkTheme,
                            viewModel = wcViewModel
                        )
                    }
                    
                    composable("wc-scanner") {
                        QrScannerScreen(
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                            onQrCodeScanned = { qrData ->
                                // If it's a WalletConnect URI, pass it back
                                if (qrData.startsWith("wc:")) {
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("wc_uri", qrData)
                                }
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
}

@Composable
fun AnimatedSplashScreen(darkTheme: Boolean) {
    // Simple white splash screen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        // Empty - just white background
    }
}