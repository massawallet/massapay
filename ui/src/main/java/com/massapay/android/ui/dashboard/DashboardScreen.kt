package com.massapay.android.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.massapay.android.core.model.Transaction
import com.massapay.android.core.model.TransactionStatus
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
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Massa Pay",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        color = textPrimary
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    titleContentColor = textPrimary
                ),
                actions = {
                    IconButton(onClick = onQrScanClick) {
                        Icon(
                            imageVector = LucideIcons.Scan,
                            contentDescription = "Scan QR",
                            tint = iconTint
                        )
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlatNavItem(
                    icon = LucideIcons.House,
                    label = "Home",
                    isSelected = selectedTab == 0,
                    isDarkTheme = isDarkTheme,
                    onClick = { selectedTab = 0 }
                )
                FlatNavItem(
                    icon = LucideIcons.Image,
                    label = "NFT",
                    isSelected = selectedTab == 1,
                    isDarkTheme = isDarkTheme,
                    onClick = { 
                        selectedTab = 1
                        onNftClick()
                    }
                )
                FlatNavItem(
                    icon = LucideIcons.SendHorizontal,
                    label = "Send",
                    isSelected = selectedTab == 2,
                    isDarkTheme = isDarkTheme,
                    onClick = { 
                        selectedTab = 2
                        onSendClick()
                    }
                )
                FlatNavItem(
                    icon = LucideIcons.QrCode,
                    label = "Receive",
                    isSelected = selectedTab == 3,
                    isDarkTheme = isDarkTheme,
                    onClick = { 
                        selectedTab = 3
                        onReceiveClick()
                    }
                )
                FlatNavItem(
                    icon = LucideIcons.Settings,
                    label = "Settings",
                    isSelected = selectedTab == 4,
                    isDarkTheme = isDarkTheme,
                    onClick = { 
                        selectedTab = 4
                        onSettingsClick()
                    }
                )
            }
        },
        containerColor = backgroundColor,
        contentColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Balance Card - Web3 Style with Charts button
                item {
                    Web3BalanceCard(
                        balance = uiState.balance.toDoubleOrNull() ?: 0.0,
                        usdValue = uiState.usdValue.toDoubleOrNull() ?: 0.0,
                        currentPrice = uiState.currentPrice,
                        priceChange24h = uiState.priceChange24h,
                        isLoading = uiState.isLoading,
                        shimmerAlpha = shimmerAlpha,
                        cardBackground = cardBackground,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = web3Cyan,
                        onChartsClick = onChartsClick,
                        massaStats = uiState.massaStats
                    )
                }

                // Send & Receive Buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Web3ActionButton(
                            icon = Icons.Outlined.ArrowUpward,
                            label = "Send",
                            onClick = onSendClick,
                            color = web3Purple,
                            cardBackground = cardBackground,
                            textColor = textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Web3ActionButton(
                            icon = Icons.Outlined.ArrowDownward,
                            label = "Receive",
                            onClick = onReceiveClick,
                            color = web3Cyan,
                            cardBackground = cardBackground,
                            textColor = textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
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
                    items(
                        items = uiState.recentTransactions,
                        key = { it.hash }
                    ) { transaction ->
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
        }
    }
}

@Composable
private fun Web3BalanceCard(
    balance: Double,
    usdValue: Double,
    currentPrice: Double,
    priceChange24h: Double,
    isLoading: Boolean,
    shimmerAlpha: Float,
    cardBackground: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    onChartsClick: () -> Unit,
    massaStats: com.massapay.android.price.model.MassaStats?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = cardBackground
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Section - Title and View Charts Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Total Balance",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSecondary
                    )
                    
                    // View Charts Button
                    IconButton(
                        onClick = onChartsClick,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.TrendingUp,
                            contentDescription = "View Charts",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                
                // Balance in USD
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(48.dp)
                            .background(
                                textSecondary.copy(alpha = shimmerAlpha * 0.2f),
                                RoundedCornerShape(8.dp)
                            )
                    )
                } else {
                    Text(
                        "$${String.format("%.2f", usdValue)}",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = 42.sp
                        ),
                        color = textPrimary
                    )
                }
                
                // MAS Balance
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(24.dp)
                            .background(
                                textSecondary.copy(alpha = shimmerAlpha * 0.2f),
                                RoundedCornerShape(8.dp)
                            )
                    )
                } else {
                    Text(
                        "${String.format("%.4f", balance)} MAS",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        ),
                        color = textPrimary
                    )
                }
                
                Divider(color = textSecondary.copy(alpha = 0.1f))
                
                // Massa Price Info (from CoinPaprika)
                massaStats?.let { stats ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Massa Price",
                                style = MaterialTheme.typography.bodySmall,
                                color = textSecondary
                            )
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
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // Open Massa explorer in browser
                val url = "https://explorer.massa.net/mainnet/operation/${transaction.hash}"
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                context.startActivity(intent)
            },
        colors = CardDefaults.cardColors(
            containerColor = cardBackground
        ),
        shape = RoundedCornerShape(16.dp)
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
                    java.text.SimpleDateFormat("MMM dd, yyyy • HH:mm", java.util.Locale.getDefault())
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
        shape = RoundedCornerShape(16.dp)
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
                        text = "≈ $${formatNumber(usdValue)} USD",
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

@Composable
fun ErrorDialog(
    error: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Error") },
        text = { Text(error) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Retry")
            }
        }
    )
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
                        "≈ $$usdValue USD",
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
