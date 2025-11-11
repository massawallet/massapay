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
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
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
import com.massapay.android.ui.charts.ChartsScreen
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
            val themeMode by themeManager.themeMode.collectAsState(initial = ThemeMode.DARK)
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
                    startDestination = startDestination
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
                            viewModel = dashboardViewModel
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
                            }
                        )
                    }
                    
                    composable("charts") {
                        ChartsScreen(
                            onBackClick = {
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
                        // NFT placeholder screen
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = "NFT",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(64.dp)
                                )
                                Text(
                                    "NFT Section",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    "Coming soon",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                                TextButton(onClick = { navController.popBackStack() }) {
                                    Text(
                                        "Back to Home",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
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