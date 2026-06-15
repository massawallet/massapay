package com.massapay.android.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.massapay.android.core.model.Transaction
import com.massapay.android.core.model.TransactionStatus
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.massapay.android.ui.components.LucideIcons
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onQrScanClick: () -> Unit = {},
    onNftClick: () -> Unit = {},
    onChartsClick: () -> Unit = {},
    onAccountsClick: () -> Unit = {},
    onStakingClick: () -> Unit = {},
    onSwapClick: () -> Unit = {},
    onPortfolioClick: () -> Unit = {},
    nftEnabled: Boolean = false,
    swapEnabled: Boolean = false,
    stakingEnabled: Boolean = false,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var showStatsSheet by remember { mutableStateOf(false) }
    var showPortfolioSheet by remember { mutableStateOf(false) }

    // Parse account color from hex
    val accountColor = try {
        Color(android.graphics.Color.parseColor(uiState.activeAccountColor))
    } catch (e: Exception) {
        Color(0xFF2196F3)
    }

    // Use MaterialTheme colors for consistent theming
    val backgroundColor = MaterialTheme.colorScheme.background
    val cardBackground = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    val iconTint = MaterialTheme.colorScheme.onBackground

    // Accent colors - theme-aware for better visibility
    // Detect dark theme by checking if background is black (or very dark)
    val isDarkTheme = backgroundColor == Color.Black || backgroundColor == Color(0xFF000000)
    val web3Purple = if (isDarkTheme) Color(0xFF9B6BFF) else Color(0xFF6A1B9A)
    val web3Cyan = if (isDarkTheme) Color(0xFF4DD0E1) else Color(0xFF0097A7)
    val web3Red = if (isDarkTheme) Color(0xFFFF6B6B) else Color(0xFFD32F2F)
    val web3Green = if (isDarkTheme) Color(0xFF4CAF50) else Color(0xFF2E7D32)
    val accountPillColor = if (isDarkTheme) Color.White else Color.Black
    val accountPillContentColor = if (isDarkTheme) Color.Black else Color.White

    // Shimmer animation for loading state
    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by shimmerTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    val clipboardManager = LocalClipboardManager.current
    var showCopiedToast by remember { mutableStateOf(false) }

    // Auto-refresh when screen becomes visible (returning from Swap, Portfolio, etc.)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Show copied toast
    LaunchedEffect(showCopiedToast) {
        if (showCopiedToast) {
            kotlinx.coroutines.delay(1500)
            showCopiedToast = false
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = backgroundColor,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                "MassaConnect",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                ),
                                color = textPrimary,
                                maxLines = 1
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    "Connecting people",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                    ),
                                    color = textSecondary,
                                    maxLines = 1
                                )
                            }
                        }

                        IconButton(onClick = onQrScanClick) {
                            Icon(
                                imageVector = LucideIcons.Scan,
                                contentDescription = "Scan QR",
                                tint = iconTint
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(accountPillColor)
                            .clickable(onClick = onAccountsClick)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(accountColor)
                        )
                        Text(
                            text = uiState.activeAccountName,
                            style = MaterialTheme.typography.bodySmall,
                            color = accountPillContentColor,
                            maxLines = 1
                        )
                        uiState.activeWallet?.let { wallet ->
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = accountPillContentColor.copy(alpha = 0.42f)
                            )
                            Text(
                                text = "${wallet.take(6)}...${wallet.takeLast(4)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = accountPillContentColor.copy(alpha = 0.78f),
                                maxLines = 1
                            )
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(wallet))
                                        showCopiedToast = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (showCopiedToast) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    modifier = Modifier.size(12.dp),
                                    tint = if (showCopiedToast) Color(0xFF4CAF50) else accountPillContentColor.copy(alpha = 0.72f)
                                )
                            }
                        }
                        if (uiState.accountCount > 1) {
                            Text(
                                text = "(${uiState.accountCount})",
                                style = MaterialTheme.typography.bodySmall,
                                color = accountPillContentColor.copy(alpha = 0.62f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Switch Account",
                            tint = accountPillContentColor.copy(alpha = 0.82f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        },
        containerColor = backgroundColor,
        contentColor = Color.Transparent
    ) { padding ->
        // Pull to refresh indicator at top when loading
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 20.dp,
                    end = 20.dp,
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Balance Card - Web3 Style with Charts button
                item {
                    Web3BalanceCard(
                        balance = uiState.balance.toDoubleOrNull() ?: 0.0,
                        usdValue = uiState.usdValue.toDoubleOrNull() ?: 0.0,
                        totalPortfolioValue = uiState.totalPortfolioValue.toDouble(),
                        currentPrice = uiState.currentPrice,
                        priceChange24h = uiState.priceChange24h,
                        isLoading = uiState.isLoading,
                        isBalanceHidden = uiState.isBalanceHidden,
                        shimmerAlpha = shimmerAlpha,
                        cardBackground = cardBackground,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = web3Cyan,
                        onChartsClick = onChartsClick,
                        onPortfolioClick = { showPortfolioSheet = true },
                        onStatsClick = { showStatsSheet = true },
                        onToggleVisibility = viewModel::toggleBalanceVisibility,
                        massaStats = uiState.massaStats
                    )
                }

                // Core and advanced actions
                item {
                    DashboardActionPanel(
                        cardBackground = cardBackground,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        web3Purple = web3Purple,
                        web3Cyan = web3Cyan,
                        web3Green = web3Green,
                        swapEnabled = swapEnabled,
                        stakingEnabled = stakingEnabled,
                        nftEnabled = nftEnabled,
                        onSendClick = onSendClick,
                        onReceiveClick = onReceiveClick,
                        onSwapClick = onSwapClick,
                        onStakingClick = onStakingClick,
                        onNftClick = onNftClick
                    )
                }

                // Transactions Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recent Activity",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                            color = textPrimary
                        )
                        if (uiState.recentTransactions.isNotEmpty()) {
                            TextButton(onClick = { /* TODO */ }) {
                                Text("View All", color = web3Purple)
                            }
                        }
                    }
                }

                // Transactions List
                if (uiState.isLoading) {
                    items(3) {
                        Web3ShimmerTransaction(shimmerAlpha, cardBackground, textSecondary)
                    }
                } else if (uiState.recentTransactions.isEmpty()) {
                    item {
                        Web3EmptyState(web3Purple, cardBackground, textPrimary, textSecondary)
                    }
                } else {
                    itemsIndexed(
                        items = uiState.recentTransactions,
                        key = { index, tx -> "${tx.hash}_$index" }
                    ) { _, transaction ->
                        Web3TransactionItem(
                            transaction = transaction,
                            walletAddress = uiState.activeWallet ?: "",
                            accentColorReceive = web3Cyan,
                            accentColorSent = web3Red,
                            accentColorSuccess = web3Green,
                            cardBackground = cardBackground,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                    }
                }
            }

            MobileBankingBottomBar(
                selectedTab = selectedTab,
                isDarkTheme = isDarkTheme,
                onHomeClick = { selectedTab = 0 },
                onNftClick = if (nftEnabled) {
                    {
                        selectedTab = 1
                        onNftClick()
                    }
                } else null,
                onSendClick = {
                    selectedTab = 2
                    onSendClick()
                },
                onReceiveClick = {
                    selectedTab = 3
                    onReceiveClick()
                },
                onSettingsClick = {
                    selectedTab = 4
                    onSettingsClick()
                },
                nftEnabled = nftEnabled,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // ---- Portfolio summary bottom sheet (Material 3) ----
            if (showPortfolioSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showPortfolioSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    val totalValue = uiState.totalPortfolioValue.toDouble().let {
                        if (it > 0) it else (uiState.usdValue.toDoubleOrNull() ?: 0.0)
                    }
                    PortfolioSheet(
                        totalValue = totalValue,
                        masBalance = uiState.balance.toDoubleOrNull() ?: 0.0,
                        masUsdValue = uiState.usdValue.toDoubleOrNull() ?: 0.0,
                        masPrice = uiState.currentPrice,
                        onViewFull = {
                            showPortfolioSheet = false
                            onPortfolioClick()
                        }
                    )
                }
            }

            // ---- Massa market statistics bottom sheet (Material 3) ----
            if (showStatsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showStatsSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    uiState.massaStats?.let { stats ->
                        MassaStatsSheet(
                            stats = stats,
                            positiveColor = web3Green,
                            negativeColor = web3Red,
                            onClose = { showStatsSheet = false }
                        )
                    } ?: MassaStatsLoadingSheet(onClose = { showStatsSheet = false })
                }
            }
        }
    }
}

@Composable
private fun MassaStatsLoadingSheet(
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.72f)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Text(
                "Massa market stats",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Loading Massa statistics",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                )
            }
        }
        Button(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(bottom = 0.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (MaterialTheme.colorScheme.background == Color.Black) Color(0xFF202124) else Color.Black,
                contentColor = Color.White
            )
        ) {
            Text("Close")
        }
        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun Web3BalanceCard(
    balance: Double,
    usdValue: Double,
    totalPortfolioValue: Double,
    currentPrice: Double,
    priceChange24h: Double,
    isLoading: Boolean,
    isBalanceHidden: Boolean,
    shimmerAlpha: Float,
    cardBackground: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    onChartsClick: () -> Unit,
    onPortfolioClick: () -> Unit,
    onStatsClick: () -> Unit = {},
    onToggleVisibility: () -> Unit,
    massaStats: com.massapay.android.price.model.MassaStats?
) {
    // Use totalPortfolioValue if available, otherwise fall back to MAS-only value
    val displayUsdValue = if (totalPortfolioValue > 0) totalPortfolioValue else usdValue
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()

    // Button colors that work in both themes
    val iconButtonBg = if (isDarkTheme) {
        Color(0xFF1A1A1A)
    } else {
        Color(0xFFEEEEEE) // Light gray in light mode
    }
    val iconButtonTint = if (isDarkTheme) {
        Color.White
    } else {
        Color(0xFF333333) // Dark gray in light mode
    }

    val cardShape = RoundedCornerShape(28.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDarkTheme) 0.dp else 18.dp,
                shape = cardShape,
                clip = false,
                ambientColor = accentColor.copy(alpha = 0.20f),
                spotColor = accentColor.copy(alpha = 0.20f)
            ),
        colors = CardDefaults.cardColors(
            containerColor = cardBackground
        ),
        shape = cardShape,
        border = if (isDarkTheme) BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.08f),
                            Color.Transparent,
                            accentColor.copy(alpha = 0.04f)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Section - Title with Eye Button and View Charts Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Total Balance text with eye icon next to it
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Total Balance",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textSecondary
                        )

                        // Eye icon without container - respects dark/light theme
                        IconButton(
                            onClick = onToggleVisibility,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                if (isBalanceHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isBalanceHidden) "Show balance" else "Hide balance",
                                tint = textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Massa statistics button
                    IconButton(
                        onClick = onStatsClick,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = iconButtonBg,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.TrendingUp,
                            contentDescription = "Massa Statistics",
                            tint = iconButtonTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Balance in USD (Total Portfolio Value) - Animated Counter
                if (isLoading) {
                    com.massapay.android.ui.components.ShimmerBox(
                        width = 180.dp,
                        height = 48.dp
                    )
                } else {
                    if (isBalanceHidden) {
                        Text(
                            text = "â€¢â€¢â€¢â€¢â€¢â€¢",
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontSize = 42.sp,
                                letterSpacing = 8.sp
                            ),
                            color = textPrimary
                        )
                    } else {
                        com.massapay.android.ui.components.AnimatedCurrencyCounter(
                            targetValue = displayUsdValue,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontSize = 42.sp
                            ),
                            color = textPrimary
                        )
                    }
                }

                // MAS Balance with Portfolio button next to it - Animated
                if (isLoading) {
                    com.massapay.android.ui.components.ShimmerBox(
                        width = 120.dp,
                        height = 24.dp
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isBalanceHidden) {
                            Text(
                                text = "â€¢â€¢â€¢â€¢ MAS",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                    letterSpacing = 4.sp
                                ),
                                color = textPrimary
                            )
                        } else {
                            com.massapay.android.ui.components.AnimatedCryptoCounter(
                                targetValue = balance,
                                symbol = "MAS",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                ),
                                color = textPrimary,
                                decimals = 4
                            )
                        }

                        // Portfolio Button - next to MAS balance
                        Surface(
                            onClick = onPortfolioClick,
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = iconButtonBg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AccountBalanceWallet,
                                    contentDescription = "Portfolio",
                                    tint = iconButtonTint,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    "Portfolio",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = iconButtonTint
                                )
                            }
                        }
                    }
                }

                Divider(color = textSecondary.copy(alpha = 0.1f))

                // Massa Price Info (from CoinPaprika) - tap to open detailed stats sheet
                massaStats?.let { stats ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onStatsClick)
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Massa Price",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textSecondary
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.KeyboardArrowUp,
                                    contentDescription = "View details",
                                    tint = textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                "$${String.format("%.6f", stats.price)}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                ),
                                color = textPrimary
                            )
                            Text(
                                "Rank #${stats.rank}",
                                style = MaterialTheme.typography.bodySmall,
                                color = textSecondary
                            )
                        }

                        // 24h Change
                        val isPositive = stats.percentChange24h >= 0
                        val isDark = isSystemInDarkTheme()

                        val changeColor = when {
                            isPositive && isDark -> Color(0xFF4CAF50)
                            isPositive && !isDark -> Color(0xFF2E7D32)
                            !isPositive && isDark -> Color(0xFFFF6B6B)
                            else -> Color(0xFFD32F2F)
                        }

                        Surface(
                            color = changeColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isPositive) Icons.Outlined.TrendingUp else Icons.Outlined.TrendingDown,
                                    contentDescription = null,
                                    tint = changeColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "${if (isPositive) "+" else ""}${String.format("%.2f", stats.percentChange24h)}%",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    ),
                                    color = changeColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Web3ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color,
    cardBackground: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buttonScale"
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .height(80.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = cardBackground
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            color.copy(alpha = 0.1f),
                            color.copy(alpha = 0.05f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = color.copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = color,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    ),
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun DashboardActionPanel(
    cardBackground: Color,
    textPrimary: Color,
    textSecondary: Color,
    web3Purple: Color,
    web3Cyan: Color,
    web3Green: Color,
    swapEnabled: Boolean,
    stakingEnabled: Boolean,
    nftEnabled: Boolean,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onSwapClick: () -> Unit,
    onStakingClick: () -> Unit,
    onNftClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Wallet actions",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    ),
                    color = textPrimary
                )
                Text(
                    "Advanced tools stay hidden until enabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = textSecondary
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                icon = Icons.Outlined.ArrowUpward,
                label = "Send",
                onClick = onSendClick,
                color = web3Purple,
                cardBackground = cardBackground,
                textColor = textPrimary,
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                icon = Icons.Outlined.ArrowDownward,
                label = "Receive",
                onClick = onReceiveClick,
                color = web3Cyan,
                cardBackground = cardBackground,
                textColor = textPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        HiddenSectionsCard(
            cardBackground = cardBackground,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            nftEnabled = nftEnabled,
            swapEnabled = swapEnabled,
            stakingEnabled = stakingEnabled,
            onNftClick = onNftClick,
            onSwapClick = onSwapClick,
            onStakingClick = onStakingClick,
            nftColor = web3Cyan,
            swapColor = Color(0xFFFF9800),
            stakingColor = web3Green
        )
    }
}

@Composable
private fun HiddenSectionsCard(
    cardBackground: Color,
    textPrimary: Color,
    textSecondary: Color,
    nftEnabled: Boolean,
    swapEnabled: Boolean,
    stakingEnabled: Boolean,
    onNftClick: () -> Unit,
    onSwapClick: () -> Unit,
    onStakingClick: () -> Unit,
    nftColor: Color,
    swapColor: Color,
    stakingColor: Color
) {
    val hasEnabledTools = nftEnabled || swapEnabled || stakingEnabled
    if (!hasEnabledTools) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cardBackground,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Advanced section",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        color = textPrimary
                    )
                    Text(
                        "Enabled tools appear here",
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                ) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = textSecondary,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(20.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (nftEnabled) {
                    HiddenSectionButton(
                        icon = Icons.Outlined.Image,
                        label = "NFT",
                        color = nftColor,
                        onClick = onNftClick,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (swapEnabled) {
                    HiddenSectionButton(
                        icon = Icons.Outlined.SwapHoriz,
                        label = "Swap",
                        color = swapColor,
                        onClick = onSwapClick,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (stakingEnabled) {
                    HiddenSectionButton(
                        icon = Icons.Outlined.Layers,
                        label = "Staking",
                        color = stakingColor,
                        onClick = onStakingClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HiddenSectionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = color
    val bgColor = color.copy(alpha = 0.14f)

    Surface(
        onClick = onClick,
        modifier = modifier.height(76.dp),
        shape = RoundedCornerShape(18.dp),
        color = bgColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                ),
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color,
    cardBackground: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val actualColor = if (enabled) color else color.copy(alpha = 0.4f)
    val actualTextColor = if (enabled) textColor else textColor.copy(alpha = 0.4f)

    // Press animation
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "cardScale"
    )

    val cardShape = RoundedCornerShape(20.dp)
    Card(
        onClick = { if (enabled) onClick() },
        modifier = modifier
            .height(74.dp)
            .scale(scale)
            .shadow(
                elevation = if (enabled && !isPressed) 10.dp else 0.dp,
                shape = cardShape,
                clip = false,
                ambientColor = actualColor.copy(alpha = 0.28f),
                spotColor = actualColor.copy(alpha = 0.28f)
            )
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        }
                    )
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = cardBackground
        ),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            actualColor.copy(alpha = 0.12f),
                            actualColor.copy(alpha = 0.03f)
                        )
                    )
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = actualColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = actualColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                ),
                color = actualTextColor
            )
            if (!enabled) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "Soon",
                    style = MaterialTheme.typography.labelSmall,
                    color = actualColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun Web3TransactionItem(
    transaction: Transaction,
    walletAddress: String,
    accentColorReceive: Color,
    accentColorSent: Color,
    accentColorSuccess: Color,
    cardBackground: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isDark = isSystemInDarkTheme()
    val txShape = RoundedCornerShape(20.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 0.dp else 8.dp,
                shape = txShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.10f)
            )
            .clickable {
                // Open Massa explorer in browser
                val url = "https://explorer.massa.net/mainnet/operation/${transaction.hash}"
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                context.startActivity(intent)
            },
        colors = CardDefaults.cardColors(
            containerColor = cardBackground
        ),
        border = if (isDark) BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)) else null,
        shape = txShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Container
            val isSent = transaction.from.equals(walletAddress, ignoreCase = true)
            val iconColor = if (isSent) accentColorSent else accentColorReceive

            Surface(
                color = iconColor.copy(alpha = 0.15f),
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = if (isSent) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Transaction Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (isSent) "Sent" else "Received",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    ),
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    java.text.SimpleDateFormat("MMM dd, yyyy â€¢ HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(transaction.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = textSecondary
                )
            }

            // Amount
            Column(
                horizontalAlignment = Alignment.End
            ) {
                val amountValue = transaction.amount.toDoubleOrNull() ?: 0.0
                Text(
                    "${if (isSent) "-" else "+"}${String.format("%.4f", amountValue)} MAS",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    ),
                    color = if (isSent) accentColorSent else accentColorReceive
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Status indicator with external link
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusColor = when (transaction.status) {
                        TransactionStatus.CONFIRMED -> accentColorSuccess
                        TransactionStatus.PENDING -> Color(0xFFFFC107)
                        TransactionStatus.FAILED -> accentColorSent
                    }
                    Surface(
                        color = statusColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            transaction.status.name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.OpenInNew,
                        contentDescription = "View in explorer",
                        tint = textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Web3ShimmerTransaction(
    shimmerAlpha: Float,
    cardBackground: Color,
    shimmerColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = cardBackground
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        shimmerColor.copy(alpha = shimmerAlpha * 0.2f),
                        CircleShape
                    )
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(16.dp)
                        .background(
                            shimmerColor.copy(alpha = shimmerAlpha * 0.2f),
                            RoundedCornerShape(4.dp)
                        )
                )
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(12.dp)
                        .background(
                            shimmerColor.copy(alpha = shimmerAlpha * 0.1f),
                            RoundedCornerShape(4.dp)
                        )
                )
            }
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(16.dp)
                    .background(
                        shimmerColor.copy(alpha = shimmerAlpha * 0.2f),
                        RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}

@Composable
private fun Web3EmptyState(
    accentColor: Color,
    cardBackground: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = cardBackground
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = accentColor.copy(alpha = 0.15f),
                shape = CircleShape,
                modifier = Modifier.size(80.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Receipt,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Text(
                "No transactions yet",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                color = textPrimary
            )
            Text(
                "Your transaction history will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ModernNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val iconTint = if (isDark) Color.White else Color.Black
    val iconTintSecondary = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) iconTint else iconTintSecondary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) iconTint else iconTintSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
fun BalanceCard(
    balance: String,
    usdValue: String,
    showUsdValue: Boolean,
    onToggleUsdDisplay: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Total Balance",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = "$balance MAS",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.clickable { onToggleUsdDisplay() }
                )

                if (showUsdValue) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "â‰ˆ $${formatNumber(usdValue)} USD",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Price metrics row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PriceMetric(
                        label = "Price",
                        value = "$0.0342",
                        change = "+2.4%",
                        isPositive = true
                    )

                    Divider(
                        modifier = Modifier
                            .height(40.dp)
                            .width(1.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                    )

                    PriceMetric(
                        label = "24h Change",
                        value = "+0.0008",
                        change = "+2.4%",
                        isPositive = true
                    )

                    Divider(
                        modifier = Modifier
                            .height(40.dp)
                            .width(1.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                    )

                    PriceMetric(
                        label = "Market Cap",
                        value = "$34.2M",
                        change = "Rank #450",
                        isPositive = null
                    )
                }
            }
        }
    }
}

@Composable
fun PriceMetric(
    label: String,
    value: String,
    change: String,
    isPositive: Boolean?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = change,
            style = MaterialTheme.typography.bodySmall,
            color = when (isPositive) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            }
        )
    }
}

@Composable
fun TransactionItem(transaction: Transaction, activeWallet: String?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Transaction icon with background
            Surface(
                shape = MaterialTheme.shapes.small,
                color = when {
                    transaction.from == (activeWallet ?: "") -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
            ) {
                Icon(
                    imageVector = when {
                        transaction.from == (activeWallet ?: "") -> Icons.Default.ArrowUpward
                        else -> Icons.Default.ArrowDownward
                    },
                    contentDescription = null,
                    tint = when {
                        transaction.from == (activeWallet ?: "") -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            // Transaction details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        transaction.from == (activeWallet ?: "") -> "Sent to ${transaction.to.take(8)}..."
                        else -> "Received from ${transaction.from.take(8)}..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "${transaction.amount} ${transaction.token.symbol}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        transaction.from == (activeWallet ?: "") -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }

            // Status indicator
            if (transaction.status == TransactionStatus.PENDING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (transaction.status == TransactionStatus.CONFIRMED) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Confirmed",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorDialog(
    error: String,
    onDismiss: () -> Unit
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
                .fillMaxHeight(0.45f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Text(
                text = "Error",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (MaterialTheme.colorScheme.background == Color.Black) Color(0xFF202124) else Color.Black,
                    contentColor = Color.White
                )
            ) {
                Text("Retry")
            }
        }
    }
}

private fun formatNumber(value: String): String {
    return try {
        NumberFormat.getInstance().format(value.toBigDecimal())
    } catch (e: Exception) {
        value
    }
}

@Composable
fun ModernBalanceCard(
    balance: String,
    usdValue: String,
    isLoading: Boolean,
    shimmerAlpha: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Total Balance",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Surface(
                        modifier = Modifier
                            .width(150.dp)
                            .height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = shimmerAlpha * 0.2f)
                    ) {}
                } else {
                    Text(
                        "$balance MAS",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Surface(
                        modifier = Modifier
                            .width(100.dp)
                            .height(20.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = shimmerAlpha * 0.2f)
                    ) {}
                } else {
                    Text(
                        "â‰ˆ $$usdValue USD",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun ModernPriceMetricsCard(priceInfo: Any?) {
    // TODO: Implement when priceInfo is added to DashboardState
    /*
    if (priceInfo == null) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PriceMetricItem(
                icon = Icons.Outlined.AttachMoney,
                label = "Price",
                value = "$${String.format("%.2f", priceInfo.price)}",
                modifier = Modifier.weight(1f)
            )

            PriceMetricItem(
                icon = Icons.Outlined.TrendingUp,
                label = "24h Change",
                value = "${String.format("%.2f", priceInfo.change24h)}%",
                valueColor = if (priceInfo.change24h >= 0)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )

            PriceMetricItem(
                icon = Icons.Outlined.ShowChart,
                label = "Market Cap",
                value = formatMarketCap(priceInfo.marketCap),
                modifier = Modifier.weight(1f)
            )
        }
    }
    */
}

@Composable
fun PriceMetricItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            ),
            color = valueColor
        )
    }
}

@Composable
fun ModernTransactionItem(
    transaction: Transaction,
    walletAddress: String
) {
    // Determine if outgoing based on the current wallet address
    val isOutgoing = transaction.from.equals(walletAddress, ignoreCase = true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (isOutgoing)
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isOutgoing) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                        contentDescription = null,
                        tint = if (isOutgoing)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isOutgoing) "Sent" else "Received",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    java.text.SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                        .format(java.util.Date(transaction.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${if (isOutgoing) "-" else "+"}${transaction.amount} ${transaction.token.symbol}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    ),
                    color = if (isOutgoing)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                StatusBadge(status = transaction.status)
            }
        }
    }
}

@Composable
fun StatusBadge(status: TransactionStatus) {
    val (text, color) = when (status) {
        TransactionStatus.PENDING -> "Pending" to MaterialTheme.colorScheme.tertiary
        TransactionStatus.CONFIRMED -> "Confirmed" to MaterialTheme.colorScheme.primary
        TransactionStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
fun ShimmerTransactionItem() {
    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by shimmerTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.2f)
            ) {}

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    modifier = Modifier
                        .width(100.dp)
                        .height(20.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.2f)
                ) {}
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .width(150.dp)
                        .height(16.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.2f)
                ) {}
            }
        }
    }
}

@Composable
fun EmptyTransactionsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No Transactions Yet",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Your transaction history will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatMarketCap(value: Double): String {
    return when {
        value >= 1_000_000_000 -> String.format("$%.2fB", value / 1_000_000_000)
        value >= 1_000_000 -> String.format("$%.2fM", value / 1_000_000)
        value >= 1_000 -> String.format("$%.2fK", value / 1_000)
        else -> String.format("$%.2f", value)
    }
}

@Composable
private fun FlatNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isDarkTheme) Color.White else Color.Black,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected)
                    androidx.compose.ui.text.font.FontWeight.SemiBold
                else
                    androidx.compose.ui.text.font.FontWeight.Normal
            ),
            color = if (isDarkTheme) Color.White else Color.Black,
            maxLines = 1,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun MobileBankingBottomBar(
    selectedTab: Int,
    isDarkTheme: Boolean,
    onHomeClick: () -> Unit,
    onNftClick: (() -> Unit)?,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onSettingsClick: () -> Unit,
    nftEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val barShape = RoundedCornerShape(percent = 50)
    val barColor = if (isDarkTheme) {
        Color(0xFF202124).copy(alpha = 0.92f)
    } else {
        Color.Black.copy(alpha = 0.90f)
    }
    val borderColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.White.copy(alpha = 0.16f)
    }
    val shadowColor = if (isDarkTheme) {
        Color.Black.copy(alpha = 0.45f)
    } else {
        Color.Black.copy(alpha = 0.16f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = barShape,
                    clip = false,
                    ambientColor = shadowColor,
                    spotColor = shadowColor
                ),
            shape = barShape,
            color = barColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BankingNavItem(
                    icon = LucideIcons.House,
                    label = "Inicio",
                    selected = selectedTab == 0,
                    isDarkTheme = isDarkTheme,
                    onClick = onHomeClick
                )
                if (nftEnabled && onNftClick != null) {
                    BankingNavItem(
                        icon = LucideIcons.Image,
                        label = "NFT",
                        selected = selectedTab == 1,
                        isDarkTheme = isDarkTheme,
                        onClick = onNftClick
                    )
                }
                BankingNavItem(
                    icon = LucideIcons.SendHorizontal,
                    label = "Send",
                    selected = selectedTab == 2,
                    isDarkTheme = isDarkTheme,
                    onClick = onSendClick
                )
                BankingNavItem(
                    icon = LucideIcons.ArrowDownToLine,
                    label = "Recibe",
                    selected = selectedTab == 3,
                    isDarkTheme = isDarkTheme,
                    onClick = onReceiveClick
                )
                BankingNavItem(
                    icon = LucideIcons.Settings,
                    label = "Settings",
                    selected = selectedTab == 4,
                    isDarkTheme = isDarkTheme,
                    onClick = onSettingsClick
                )
            }
        }
    }
}

@Composable
private fun RowScope.BankingNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    isDarkTheme: Boolean,
    onClick: () -> Unit
) {
    val selectedColor = Color.White
    val unselectedColor = Color.White.copy(alpha = 0.66f)
    val iconColor = if (selected) selectedColor else unselectedColor
    val labelColor = if (selected) selectedColor else unselectedColor
    val circleColor = if (isDarkTheme) {
        Color.White.copy(alpha = if (selected) 0.18f else 0.08f)
    } else {
        Color.White.copy(alpha = if (selected) 0.20f else 0.08f)
    }
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bankingNavScale"
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (selected) circleColor else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) {
                    androidx.compose.ui.text.font.FontWeight.SemiBold
                } else {
                    androidx.compose.ui.text.font.FontWeight.Medium
                }
            ),
            color = labelColor,
            maxLines = 1,
            textAlign = TextAlign.Center,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ModernNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    onClick: () -> Unit
) {
    // Consistent colors - black container with white icon when selected (light mode)
    val selectedBg = if (isDarkTheme) Color.White else Color.Black
    val selectedContent = if (isDarkTheme) Color.Black else Color.White
    val unselectedColor = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)

    // Animated scale on selection
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "navScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .background(if (isSelected) selectedBg else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) selectedContent else unselectedColor,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected)
                    androidx.compose.ui.text.font.FontWeight.Bold
                else
                    androidx.compose.ui.text.font.FontWeight.Normal
            ),
            color = if (isSelected) selectedContent else unselectedColor,
            maxLines = 1,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun PortfolioSheet(
    totalValue: Double,
    masBalance: Double,
    masUsdValue: Double,
    masPrice: Double,
    onViewFull: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val subtle = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
    val isDark = isSystemInDarkTheme()
    val heroStart = if (isDark) Color(0xFF202124) else Color.Black
    val heroEnd = if (isDark) Color(0xFF111315) else Color(0xFF303030)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
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
                shape = CircleShape,
                color = primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, primary.copy(alpha = 0.18f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Portfolio",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    ),
                    color = onSurface
                )
                Text(
                    "Your Massa position at a glance",
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurface.copy(alpha = 0.58f)
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                color = heroStart,
                shadowElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    heroStart,
                                    heroEnd
                                ),
                                start = Offset.Zero,
                                end = Offset.Infinite
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                            ) {
                                Icon(
                                    Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .size(24.dp)
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    "MAS",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                        Text(
                            "Total Portfolio Value",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.68f)
                        )
                        Text(
                            "$${String.format("%,.2f", totalValue)}",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Black
                            ),
                            color = Color.White
                        )
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Token,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.82f),
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    "${String.format("%.4f", masBalance)} MAS available",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                    ),
                                    color = Color.White.copy(alpha = 0.88f)
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PortfolioMetricCard(
                    icon = Icons.Outlined.Paid,
                    label = "USD value",
                    value = "$${String.format("%,.2f", masUsdValue)}",
                    modifier = Modifier.weight(1f),
                    containerColor = subtle
                )
                PortfolioMetricCard(
                    icon = Icons.Outlined.ShowChart,
                    label = "MAS price",
                    value = "$${String.format("%.6f", masPrice)}",
                    modifier = Modifier.weight(1f),
                    containerColor = subtle
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = subtle
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = primary.copy(alpha = 0.12f)
                        ) {
                            Icon(
                                Icons.Outlined.Insights,
                                contentDescription = null,
                                tint = primary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(18.dp)
                            )
                        }
                        Text(
                            "Account summary",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                            color = onSurface
                        )
                    }
                    SheetStatRow("Balance MAS", "${String.format("%.4f", masBalance)} MAS")
                    SheetStatRow("USD value", "$${String.format("%,.2f", masUsdValue)}")
                    SheetStatRow("MAS price", "$${String.format("%.6f", masPrice)}")
                }
            }
        }
        Button(
            onClick = onViewFull,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(bottom = 0.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (MaterialTheme.colorScheme.background == Color.Black) Color(0xFF202124) else Color.Black,
                contentColor = Color.White
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("View full portfolio", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Icon(
                    Icons.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun PortfolioMetricCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    containerColor: Color,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(18.dp)
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                color = valueColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MassaStatsSheet(
    stats: com.massapay.android.price.model.MassaStats,
    positiveColor: Color,
    negativeColor: Color,
    onClose: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val pctColor = { v: Double -> if (v >= 0) positiveColor else negativeColor }
    val pctText = { v: Double -> "${if (v >= 0) "+" else ""}${String.format("%.2f", v)}%" }
    val subtle = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val isDark = isSystemInDarkTheme()
    val heroStart = if (isDark) Color(0xFF202124) else Color.Black
    val heroEnd = if (isDark) Color(0xFF111315) else Color(0xFF303030)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
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
                shape = CircleShape,
                color = primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, primary.copy(alpha = 0.18f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Massa Market Stats",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    ),
                    color = onSurface
                )
                Text(
                    "Network market pulse and price movement",
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurface.copy(alpha = 0.58f)
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                color = heroStart,
                shadowElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    heroStart,
                                    heroEnd
                                ),
                                start = Offset.Zero,
                                end = Offset.Infinite
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                            ) {
                                Icon(
                                    Icons.Outlined.ShowChart,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .size(24.dp)
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = pctColor(stats.percentChange24h).copy(alpha = 0.20f),
                                border = BorderStroke(1.dp, pctColor(stats.percentChange24h).copy(alpha = 0.34f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (stats.percentChange24h >= 0) Icons.Outlined.TrendingUp else Icons.Outlined.TrendingDown,
                                        contentDescription = null,
                                        tint = pctColor(stats.percentChange24h),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "24h ${pctText(stats.percentChange24h)}",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        ),
                                        color = pctColor(stats.percentChange24h)
                                    )
                                }
                            }
                        }
                        Text(
                            "Massa price",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.68f)
                        )
                        Text(
                            "$${String.format("%.6f", stats.price)}",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Black
                            ),
                            color = Color.White
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                            ) {
                                Text(
                                    "Rank #${stats.rank}",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    ),
                                    color = Color.White.copy(alpha = 0.88f)
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                            ) {
                                Text(
                                    "Powered by Massa",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    ),
                                    color = Color.White.copy(alpha = 0.88f)
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PortfolioMetricCard(
                    icon = Icons.Outlined.ShowChart,
                    label = "7d",
                    value = pctText(stats.percentChange7d),
                    valueColor = pctColor(stats.percentChange7d),
                    modifier = Modifier.weight(1f),
                    containerColor = subtle
                )
                PortfolioMetricCard(
                    icon = Icons.Outlined.Timeline,
                    label = "30d",
                    value = pctText(stats.percentChange30d),
                    valueColor = pctColor(stats.percentChange30d),
                    modifier = Modifier.weight(1f),
                    containerColor = subtle
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = subtle
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = primary.copy(alpha = 0.12f)
                        ) {
                            Icon(
                                Icons.Outlined.Analytics,
                                contentDescription = null,
                                tint = primary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(18.dp)
                            )
                        }
                        Text(
                            "Market details",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                            color = onSurface
                        )
                    }
                    SheetStatRow("Vol 24h", formatMarketCap(stats.volume24h))
                    SheetStatRow("Market cap", formatMarketCap(stats.marketCap))
                    SheetStatRow("ATH", "$${String.format("%.6f", stats.athPrice)}")
                    SheetStatRow("From ATH", pctText(stats.percentFromAth), pctColor(stats.percentFromAth))
                    if (stats.totalSupply > 0) {
                        SheetStatRow("Total supply", String.format("%,d MAS", stats.totalSupply))
                    }
                }
            }
        }
        Button(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (MaterialTheme.colorScheme.background == Color.Black) Color(0xFF202124) else Color.Black,
                contentColor = Color.White
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Close", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun SheetStatRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            ),
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor
        )
    }
}
