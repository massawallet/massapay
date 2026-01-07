package com.massapay.android.ui.staking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.massapay.android.core.model.StakingInfo
import com.massapay.android.network.agentbridge.AgentConnectionState
import com.massapay.android.network.agentbridge.StakingRewards
import com.massapay.android.ui.agentbridge.AgentBridgeViewModel
import com.massapay.android.ui.agentbridge.AgentConnectionDialog

/**
 * Data class for staking operation result dialog
 */
data class StakingResultInfo(
    val isSuccess: Boolean,
    val title: String,
    val message: String,
    val operationType: StakingOperationType
)

enum class StakingOperationType {
    REGISTER_STAKING,
    REMOVE_STAKING,
    BUY_ROLLS,
    SELL_ROLLS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StakingScreen(
    onClose: () -> Unit,
    isDarkTheme: Boolean,
    onScanAgentQR: () -> Unit = {},
    agentQRContent: String? = null,
    viewModel: StakingViewModel = hiltViewModel(),
    agentBridgeViewModel: AgentBridgeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val agentUiState by agentBridgeViewModel.uiState.collectAsState()
    var showBuyDialog by remember { mutableStateOf(false) }
    var showSellDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showAgentDialog by remember { mutableStateOf(false) }
    var showRegisterStakingDialog by remember { mutableStateOf(false) }
    var showRemoveStakingConfirmDialog by remember { mutableStateOf<String?>(null) }
    var showStakingResultDialog by remember { mutableStateOf<StakingResultInfo?>(null) }
    var showRollsOperationResult by remember { mutableStateOf<StakingResultInfo?>(null) }
    
    // Determine if node is connected for showing remote staking section
    val isNodeConnected = agentUiState.nodeStatus?.connected == true
    val isAgentConnected = agentUiState.connectionState is AgentConnectionState.Connected
    
    // Track if we've already processed this QR content
    var processedQRContent by remember { mutableStateOf<String?>(null) }
    
    // Handle QR content from scanner - only connect if not already connected
    // and only process each QR content once
    LaunchedEffect(agentQRContent, agentUiState.connectionState) {
        agentQRContent?.let { qrContent ->
            if (qrContent.isNotEmpty() && 
                qrContent != processedQRContent &&
                agentUiState.connectionState is AgentConnectionState.Disconnected) {
                processedQRContent = qrContent
                agentBridgeViewModel.connectWithQrContent(qrContent)
            }
        }
    }
    
    // Fetch additional data when node is connected
    LaunchedEffect(isNodeConnected) {
        if (isNodeConnected) {
            agentBridgeViewModel.fetchStakingAddresses()
            agentUiState.walletAddress?.let { address ->
                agentBridgeViewModel.fetchRewards(address)
            }
        }
    }
    
    // Handle operation success message
    LaunchedEffect(agentUiState.operationSuccess) {
        agentUiState.operationSuccess?.let {
            kotlinx.coroutines.delay(3000)
            agentBridgeViewModel.clearOperationSuccess()
        }
    }
    
    // Handle operation result dialog (from startStakingAuto/removeStakingKey)
    LaunchedEffect(agentUiState.operationResult) {
        agentUiState.operationResult?.let { result ->
            showStakingResultDialog = StakingResultInfo(
                isSuccess = result.isSuccess,
                title = result.title,
                message = result.message,
                operationType = when (result.operationType) {
                    com.massapay.android.ui.agentbridge.StakingOpType.REGISTER_STAKING -> StakingOperationType.REGISTER_STAKING
                    com.massapay.android.ui.agentbridge.StakingOpType.REMOVE_STAKING -> StakingOperationType.REMOVE_STAKING
                    com.massapay.android.ui.agentbridge.StakingOpType.BUY_ROLLS -> StakingOperationType.BUY_ROLLS
                    com.massapay.android.ui.agentbridge.StakingOpType.SELL_ROLLS -> StakingOperationType.SELL_ROLLS
                }
            )
            agentBridgeViewModel.clearOperationResult()
        }
    }
    
    // Use MaterialTheme colors for consistent theming
    val backgroundColor = MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    val accentColor = Color(0xFF6366F1)
    
    // Icon container colors (black container with white icon in light mode)
    val iconContainerColor = if (isDarkTheme) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Black
    val iconTintColor = Color.White

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Staking",
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.ArrowBack, 
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Info",
                            tint = textColor
                        )
                    }
                    IconButton(onClick = { viewModel.loadStakingInfo() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor
                )
            )
        },
        containerColor = backgroundColor
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading && uiState.stakingInfo == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = accentColor
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Rolls Balance Card
                    RollsBalanceCard(
                        stakingInfo = uiState.stakingInfo,
                        isWalletRegisteredForStaking = agentUiState.stakingAddresses.contains(agentUiState.walletAddress),
                        isDarkTheme = isDarkTheme,
                        cardColor = cardColor,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        accentColor = accentColor,
                        onBuyClick = { showBuyDialog = true },
                        onSellClick = { showSellDialog = true },
                        canBuy = !uiState.isProcessing && viewModel.getMaxRollsToBuy() > 0,
                        canSell = !uiState.isProcessing && viewModel.getMaxRollsToSell() > 0
                    )
                    
                    // Connect to Node Card
                    AgentConnectionCard(
                        connectionState = agentUiState.connectionState,
                        nodeStatus = agentUiState.nodeStatus,
                        stakingInfo = agentUiState.stakingInfo,
                        isDarkTheme = isDarkTheme,
                        cardColor = cardColor,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        accentColor = accentColor,
                        iconContainerColor = iconContainerColor,
                        iconTintColor = iconTintColor,
                        onClick = { showAgentDialog = true },
                        onRefresh = {
                            agentUiState.walletAddress?.let { address ->
                                agentBridgeViewModel.fetchStakingInfo(address)
                            }
                            agentBridgeViewModel.fetchNodeStatus()
                        }
                    )
                    
                    // ========================================
                    // REMOTE STAKING SECTION (via Massa Agent)
                    // ========================================
                    AnimatedVisibility(
                        visible = isNodeConnected,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Staking Keys Card
                            StakingKeysCard(
                                stakingAddresses = agentUiState.stakingAddresses,
                                walletAddress = agentUiState.walletAddress,
                                isProcessing = agentUiState.isProcessingOperation,
                                isDarkTheme = isDarkTheme,
                                cardColor = cardColor,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                accentColor = accentColor,
                                onRegisterKey = { showRegisterStakingDialog = true },
                                onRemoveKey = { address ->
                                    // Show confirmation dialog instead of removing immediately
                                    showRemoveStakingConfirmDialog = address
                                }
                            )
                            
                            // Rewards Card
                            RewardsCard(
                                rewards = agentUiState.rewards,
                                isDarkTheme = isDarkTheme,
                                cardColor = cardColor,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                accentColor = accentColor,
                                onRefresh = {
                                    agentUiState.walletAddress?.let { address ->
                                        agentBridgeViewModel.fetchRewards(address)
                                    }
                                }
                            )
                        }
                    }
                    
                    // Info Cards
                    StakingInfoCard(
                        title = "What are Rolls?",
                        description = "Rolls are used for staking in Massa. 1 Roll = 100 MAS. You need to link your Rolls to a node to earn rewards.",
                        icon = Icons.Default.Help,
                        isDarkTheme = isDarkTheme,
                        cardColor = cardColor,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        iconContainerColor = iconContainerColor,
                        iconTintColor = iconTintColor
                    )
                    
                    StakingInfoCard(
                        title = "How to Earn Rewards",
                        description = "To earn staking rewards, you need to run a Massa node and register your staking key. Rolls alone don't generate rewards.",
                        icon = Icons.Default.EmojiEvents,
                        isDarkTheme = isDarkTheme,
                        cardColor = cardColor,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        iconContainerColor = iconContainerColor,
                        iconTintColor = iconTintColor
                    )
                    
                    StakingInfoCard(
                        title = "Selling Rolls",
                        description = "When you sell Rolls, your MAS will be available after ~3 cycles (about 3 hours). This is called 'deferred credits'.",
                        icon = Icons.Default.Schedule,
                        isDarkTheme = isDarkTheme,
                        cardColor = cardColor,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        iconContainerColor = iconContainerColor,
                        iconTintColor = iconTintColor
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            // Processing overlay
            if (uiState.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = accentColor)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Processing...",
                                color = textColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        
        // Success message - show as dialog
        uiState.successMessage?.let { message ->
            LaunchedEffect(message) {
                showRollsOperationResult = StakingResultInfo(
                    isSuccess = true,
                    title = "Success!",
                    message = message,
                    operationType = StakingOperationType.BUY_ROLLS
                )
                viewModel.clearMessages()
            }
        }
        
        // Error message - show as dialog
        uiState.error?.let { error ->
            LaunchedEffect(error) {
                showRollsOperationResult = StakingResultInfo(
                    isSuccess = false,
                    title = "Operation Failed",
                    message = error,
                    operationType = StakingOperationType.BUY_ROLLS
                )
                viewModel.clearMessages()
            }
        }
        
        // Buy Rolls Dialog
        if (showBuyDialog) {
            RollsDialog(
                title = "Buy Rolls",
                description = "1 Roll = 100 MAS",
                maxRolls = viewModel.getMaxRollsToBuy(),
                confirmText = "Buy",
                isDarkTheme = isDarkTheme,
                onConfirm = { count ->
                    viewModel.buyRolls(count)
                    showBuyDialog = false
                },
                onDismiss = { showBuyDialog = false }
            )
        }
        
        // Sell Rolls Dialog
        if (showSellDialog) {
            RollsDialog(
                title = "Sell Rolls",
                description = "You'll receive 100 MAS per Roll (after ~3 cycles)",
                maxRolls = viewModel.getMaxRollsToSell(),
                confirmText = "Sell",
                isDarkTheme = isDarkTheme,
                onConfirm = { count ->
                    viewModel.sellRolls(count)
                    showSellDialog = false
                },
                onDismiss = { showSellDialog = false }
            )
        }
        
        // Info Dialog
        if (showInfoDialog) {
            StakingHelpDialog(
                isDarkTheme = isDarkTheme,
                onDismiss = { showInfoDialog = false }
            )
        }
        
        // Agent Connection Dialog
        if (showAgentDialog) {
            AgentConnectionDialog(
                connectionState = agentUiState.connectionState,
                isDarkTheme = isDarkTheme,
                onScanQR = {
                    showAgentDialog = false
                    onScanAgentQR()
                },
                onDisconnect = {
                    agentBridgeViewModel.disconnect()
                    processedQRContent = null // Reset so user can scan new QR
                },
                onDismiss = { showAgentDialog = false }
            )
        }
        
        // Confirm Start Staking Dialog (auto uses wallet credentials)
        if (showRegisterStakingDialog) {
            StartStakingConfirmDialog(
                rollCount = uiState.stakingInfo?.finalRolls ?: agentUiState.stakingInfo?.finalRolls ?: 0,
                isDarkTheme = isDarkTheme,
                onConfirm = {
                    agentBridgeViewModel.startStakingAuto()
                    showRegisterStakingDialog = false
                },
                onDismiss = { showRegisterStakingDialog = false }
            )
        }
        
        // Agent operation success message
        agentUiState.operationSuccess?.let { message ->
            Snackbar(
                modifier = Modifier.padding(16.dp),
                containerColor = Color(0xFF4CAF50)
            ) {
                Text(message, color = Color.White)
            }
        }
        
        // Agent error - just clear it silently (errors shown in result dialogs)
        agentUiState.error?.let {
            LaunchedEffect(it) {
                kotlinx.coroutines.delay(1000)
                agentBridgeViewModel.clearError()
            }
        }
        
        // Processing overlay for agent operations
        if (agentUiState.isProcessingOperation) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = accentColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Sending to node...",
                            color = textColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        
        // Confirm Remove Staking Dialog
        showRemoveStakingConfirmDialog?.let { address ->
            RemoveStakingConfirmDialog(
                address = address,
                isCurrentWallet = address == agentUiState.walletAddress,
                isDarkTheme = isDarkTheme,
                onConfirm = {
                    agentBridgeViewModel.removeStakingKey(address)
                    showRemoveStakingConfirmDialog = null
                },
                onDismiss = { showRemoveStakingConfirmDialog = null }
            )
        }
        
        // Staking Result Dialog (success/error)
        showStakingResultDialog?.let { result ->
            StakingResultDialog(
                result = result,
                isDarkTheme = isDarkTheme,
                onDismiss = { showStakingResultDialog = null }
            )
        }
        
        // Rolls Operation Result Dialog (buy/sell success/error)
        showRollsOperationResult?.let { result ->
            StakingResultDialog(
                result = result,
                isDarkTheme = isDarkTheme,
                onDismiss = { showRollsOperationResult = null }
            )
        }
    }
}

@Composable
private fun RollsBalanceCard(
    stakingInfo: StakingInfo?,
    isWalletRegisteredForStaking: Boolean = false,
    isDarkTheme: Boolean,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color,
    onBuyClick: () -> Unit,
    onSellClick: () -> Unit,
    canBuy: Boolean,
    canSell: Boolean
) {
    // Animated roll count
    val targetRolls = stakingInfo?.totalRolls?.toFloat() ?: 0f
    val animatedRolls by animateFloatAsState(
        targetValue = targetRolls,
        animationSpec = tween(
            durationMillis = 1000,
            easing = FastOutSlowInEasing
        ),
        label = "rollsAnimation"
    )
    
    // Animated MAS value
    val targetMasValue = stakingInfo?.rollsValueInMas?.toFloat() ?: 0f
    val animatedMasValue by animateFloatAsState(
        targetValue = targetMasValue,
        animationSpec = tween(
            durationMillis = 1000,
            easing = FastOutSlowInEasing
        ),
        label = "masValueAnimation"
    )
    
    // Icon rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "iconPulse")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )
    val iconGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconGlow"
    )
    
    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    val cardScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cardScale"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500),
        label = "cardAlpha"
    )
    
    LaunchedEffect(Unit) {
        visible = true
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
                alpha = cardAlpha
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1a1a2e),
                            Color(0xFF16213e)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header row with title and status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Staking",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    
                    // Status badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (targetRolls > 0) Color(0xFF4ADE80).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = if (targetRolls > 0) "Active" else "Inactive",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (targetRolls > 0) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Main balance section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side - Icon and Rolls
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Animated Roll icon - white bg with black icon
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(72.dp)
                        ) {
                            // Glow effect
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale * 1.1f
                                        scaleY = iconScale * 1.1f
                                    }
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = iconGlow),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            
                            // Main icon - white container with black icon
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                                    .background(
                                        color = Color.White,
                                        shape = RoundedCornerShape(18.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Token,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = Color.Black
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column {
                            // Animated Rolls count
                            Text(
                                text = "${animatedRolls.toInt()}",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Rolls",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    // Right side - MAS Value
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${String.format("%.0f", animatedMasValue)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF667eea)
                        )
                        Text(
                            text = "MAS",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Rolls section - Like Massa Explorer (Final / Candidate / Active)
                val finalRolls = stakingInfo?.finalRolls ?: 0
                val candidateRolls = stakingInfo?.candidateRolls ?: 0
                val activeRolls = stakingInfo?.activeRolls ?: 0
                val pendingRolls = maxOf(0, finalRolls - activeRolls)
                
                // Rolls label
                Text(
                    text = "Rolls",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Rolls row - Final / Candidate / Active
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Final Rolls
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.08f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Final",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$finalRolls",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4ADE80)
                            )
                        }
                    }
                    
                    // Candidate Rolls
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.08f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Candidate",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$candidateRolls",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF667eea)
                            )
                        }
                    }
                    
                    // Active Rolls
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.08f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$activeRolls",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (activeRolls > 0) Color(0xFF4ADE80) else Color(0xFFFF9800)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Balance Section
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF667eea).copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color(0xFF667eea)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Balance",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        Text(
                            text = "${String.format("%.4f", stakingInfo?.balance?.toDoubleOrNull() ?: 0.0)} MAS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                
                // Warning if there are pending rolls AND wallet is registered for staking
                // Only show this if we know the wallet is registered and rolls aren't active yet
                if (pendingRolls > 0 && isWalletRegisteredForStaking && activeRolls < finalRolls) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFF9800).copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFFFF9800)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$pendingRolls roll${if (pendingRolls > 1) "s" else ""} pending activation (~3 cycles)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFF9800)
                            )
                        }
                    }
                }
                
                // Deferred credits if any
                val deferredCredits = stakingInfo?.deferredCredits?.toDoubleOrNull() ?: 0.0
                if (deferredCredits > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFF9800).copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFFFF9800)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pending: ${String.format("%.2f", deferredCredits)} MAS",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFF9800)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Action Buttons - Modern style with gradient
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Buy Rolls Button - Gradient style
                    Button(
                        onClick = onBuyClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF667eea),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF667eea).copy(alpha = 0.4f),
                            disabledContentColor = Color.White.copy(alpha = 0.5f)
                        ),
                        enabled = canBuy
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buy Rolls", fontWeight = FontWeight.SemiBold)
                    }
                    
                    // Sell Rolls Button - Outlined style
                    OutlinedButton(
                        onClick = onSellClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.4f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (canSell) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f)
                        ),
                        enabled = canSell
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sell Rolls", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StakingStatItem(
    label: String,
    value: String,
    textColor: Color,
    secondaryTextColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = secondaryTextColor
        )
    }
}

@Composable
private fun RollStatWithIcon(
    icon: ImageVector,
    label: String,
    value: String,
    iconTint: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = iconTint
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = secondaryTextColor
        )
    }
}

@Composable
private fun StakingInfoCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDarkTheme: Boolean,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    iconContainerColor: Color,
    iconTintColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDarkTheme) 0.dp else 4.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon with styled container
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = iconContainerColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = iconTintColor
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
            }
        }
    }
}

@Composable
private fun RollsDialog(
    title: String,
    description: String,
    maxRolls: Int,
    confirmText: String,
    isDarkTheme: Boolean,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var rollCount by remember { mutableStateOf(1) }
    
    val dialogColor = if (isDarkTheme) Color(0xFF121212) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogColor,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(title, fontWeight = FontWeight.Bold, color = textColor)
        },
        text = {
            Column {
                Text(
                    description,
                    color = textColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Roll count selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (rollCount > 1) rollCount-- },
                        enabled = rollCount > 1
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Decrease",
                            tint = if (rollCount > 1) textColor else textColor.copy(alpha = 0.3f)
                        )
                    }
                    
                    Text(
                        text = "$rollCount",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    
                    IconButton(
                        onClick = { if (rollCount < maxRolls) rollCount++ },
                        enabled = rollCount < maxRolls
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Increase",
                            tint = if (rollCount < maxRolls) textColor else textColor.copy(alpha = 0.3f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Max: $maxRolls rolls",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Cost/receive info
                val amount = rollCount * 100.0
                Text(
                    text = if (confirmText == "Buy") "Cost: $amount MAS + fee" else "You'll receive: $amount MAS",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6366F1),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(rollCount) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDarkTheme) Color.White else Color.Black,
                    contentColor = if (isDarkTheme) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(confirmText, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textColor.copy(alpha = 0.7f))
            }
        }
    )
}

@Composable
private fun StakingHelpDialog(
    isDarkTheme: Boolean,
    onDismiss: () -> Unit
) {
    val dialogColor = if (isDarkTheme) Color(0xFF121212) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogColor,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text("About Staking", fontWeight = FontWeight.Bold, color = textColor)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Massa uses a Proof-of-Stake consensus mechanism with 'Rolls'.",
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Text(
                    "• 1 Roll = 100 MAS",
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Text(
                    "• Rolls are used to participate in block production",
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Text(
                    "• To earn rewards, you need to run a node and register your staking key",
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Text(
                    "• Selling rolls has a delay of ~3 cycles before MAS is available",
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "For more information, visit docs.massa.net",
                    color = Color(0xFF6366F1),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it", color = Color(0xFF6366F1), fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
/**
 * Card for connecting to Massa Agent Desktop - shows node info when connected
 */
@Composable
private fun AgentConnectionCard(
    connectionState: AgentConnectionState,
    nodeStatus: com.massapay.android.network.agentbridge.AgentNodeStatus?,
    stakingInfo: com.massapay.android.network.agentbridge.AgentStakingInfo?,
    isDarkTheme: Boolean,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color,
    iconContainerColor: Color,
    iconTintColor: Color,
    onClick: () -> Unit,
    onRefresh: () -> Unit
) {
    val isConnected = connectionState is AgentConnectionState.Connected
    val isNodeConnected = nodeStatus?.connected == true
    val statusColor = when {
        isNodeConnected -> Color(0xFF4CAF50)
        isConnected -> Color(0xFFFFA726)
        else -> secondaryTextColor
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row - clickable to open connection dialog
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isNodeConnected -> Color(0xFF4CAF50)
                                isConnected -> Color(0xFFFFA726)
                                else -> iconContainerColor
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when {
                            isNodeConnected -> Icons.Default.Computer
                            isConnected -> Icons.Default.Sync
                            else -> Icons.Default.Link
                        },
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = iconTintColor
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Text content
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Massa Agent",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (connectionState) {
                                is AgentConnectionState.Connected -> {
                                    if (isNodeConnected) "Node connected • Cycle ${nodeStatus?.currentCycle ?: "-"}"
                                    else "Agent connected • Waiting for node"
                                }
                                is AgentConnectionState.Connecting -> "Connecting..."
                                is AgentConnectionState.Error -> connectionState.message
                                else -> "Tap to connect to your PC"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (connectionState is AgentConnectionState.Error) Color(0xFFEF4444) else secondaryTextColor
                        )
                    }
                }
                
                // Refresh or Arrow icon
                if (isConnected) {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = accentColor
                        )
                    }
                } else {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = secondaryTextColor
                    )
                }
            }
            
            // Show node details when connected
            if (isConnected && nodeStatus != null) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Divider(color = secondaryTextColor.copy(alpha = 0.2f))
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Node info grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    NodeInfoItem(
                        label = "Version",
                        value = nodeStatus.version ?: "-",
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor
                    )
                    NodeInfoItem(
                        label = "Cycle",
                        value = nodeStatus.currentCycle?.toString() ?: "-",
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor
                    )
                    NodeInfoItem(
                        label = "Peers",
                        value = nodeStatus.connectedPeers?.toString() ?: "-",
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor
                    )
                }
                
                // Show staking info from node if available
                if (stakingInfo != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        NodeInfoItem(
                            label = "Rolls (Node)",
                            value = stakingInfo.finalRolls.toString(),
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor
                        )
                        NodeInfoItem(
                            label = "Candidate",
                            value = stakingInfo.candidateRolls.toString(),
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor
                        )
                        NodeInfoItem(
                            label = "Active",
                            value = stakingInfo.activeRolls.toString(),
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeInfoItem(
    label: String,
    value: String,
    textColor: Color,
    secondaryTextColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = secondaryTextColor
        )
    }
}

/**
 * Remote Staking Operations Card - Buy/Sell rolls via Massa Agent
 */
@Composable
private fun RemoteStakingOperationsCard(
    stakingInfo: com.massapay.android.network.agentbridge.AgentStakingInfo?,
    isProcessing: Boolean,
    isDarkTheme: Boolean,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color,
    onBuyRolls: () -> Unit,
    onSellRolls: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF667eea),
                            Color(0xFF764ba2)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Node Staking",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Text(
                            "LIVE",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Balance info from node
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stakingInfo?.finalRolls?.toString() ?: "0",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Final Rolls",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(50.dp)
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stakingInfo?.candidateRolls?.toString() ?: "0",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Candidate",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(50.dp)
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format("%.1f", stakingInfo?.balance?.toDoubleOrNull() ?: 0.0),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Balance",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onBuyRolls,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF667eea)
                        ),
                        enabled = !isProcessing
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buy Rolls", fontWeight = FontWeight.SemiBold)
                    }
                    
                    OutlinedButton(
                        onClick = onSellRolls,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.5f)),
                        enabled = !isProcessing && (stakingInfo?.finalRolls ?: 0) > 0
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sell Rolls", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Staking Addresses Card - Register/Remove staking addresses
 */
@Composable
private fun StakingKeysCard(
    stakingAddresses: List<String>,
    walletAddress: String?,
    isProcessing: Boolean,
    isDarkTheme: Boolean,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color,
    onRegisterKey: () -> Unit,
    onRemoveKey: (String) -> Unit
) {
    // Check if current wallet is already registered
    val isWalletRegistered = walletAddress != null && stakingAddresses.contains(walletAddress)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isWalletRegistered) Color(0xFF10B981).copy(alpha = 0.15f) else accentColor.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isWalletRegistered) Icons.Default.CheckCircle else Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = if (isWalletRegistered) Color(0xFF10B981) else accentColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Staking Addresses",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor
                        )
                        Text(
                            if (isWalletRegistered) "Your wallet is active" else "${stakingAddresses.size} registered",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isWalletRegistered) Color(0xFF10B981) else secondaryTextColor
                        )
                    }
                }
                
                // Only show Register button if wallet is NOT already registered
                if (!isWalletRegistered) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = accentColor.copy(alpha = 0.15f),
                        onClick = onRegisterKey
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = accentColor,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Register address",
                                    modifier = Modifier.size(22.dp),
                                    tint = accentColor
                                )
                            }
                        }
                    }
                }
            }
            
            if (stakingAddresses.isEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDarkTheme) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = secondaryTextColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No addresses registered",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Register your wallet to start staking and earning rewards",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryTextColor.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                
                // List of staking addresses
                stakingAddresses.forEach { address ->
                    val isCurrentWallet = address == walletAddress
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrentWallet) 
                            accentColor.copy(alpha = 0.1f) 
                        else 
                            if (isDarkTheme) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${address.take(10)}...${address.takeLast(8)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor
                                )
                                if (isCurrentWallet) {
                                    Text(
                                        text = "Your wallet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accentColor
                                    )
                                }
                            }
                            
                            IconButton(
                                onClick = { onRemoveKey(address) },
                                enabled = !isProcessing
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove",
                                    tint = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Rewards Card - Display staking rewards
 */
@Composable
private fun RewardsCard(
    rewards: StakingRewards?,
    isDarkTheme: Boolean,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF4ADE80).copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = Color(0xFF4ADE80)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Staking Rewards",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    )
                }
                
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = accentColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Total rewards
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = if (isDarkTheme) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Total Earned",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor
                        )
                        Text(
                            text = if (rewards != null) {
                                val total = rewards.totalRewards.toDoubleOrNull() ?: 0.0
                                String.format("%.4f MAS", total)
                            } else {
                                "-- MAS"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4ADE80)
                        )
                    }
                    
                    Icon(
                        Icons.Default.TrendingUp,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = Color(0xFF4ADE80).copy(alpha = 0.5f)
                    )
                }
            }
            
            // Recent cycles
            if (rewards != null && rewards.cycleRewards.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "Recent Cycles",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = secondaryTextColor
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                rewards.cycleRewards.take(5).forEach { cycle ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Cycle ${cycle.cycle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryTextColor
                        )
                        val rewardAmount = cycle.reward.toDoubleOrNull() ?: 0.0
                        Text(
                            "+${String.format("%.4f", rewardAmount)} MAS",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4ADE80)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    "No rewards data available yet. Make sure you have rolls registered for staking.",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Dialog to confirm starting staking (auto uses wallet credentials)
 */
@Composable
private fun StartStakingConfirmDialog(
    rollCount: Int,
    isDarkTheme: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dialogColor = if (isDarkTheme) Color(0xFF121212) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val accentColor = Color(0xFF6366F1)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogColor,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = accentColor
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Start Staking",
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        },
        text = {
            Column {
                Text(
                    "You are about to start staking with your $rollCount roll(s).",
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "What happens next:",
                                fontWeight = FontWeight.Medium,
                                color = textColor
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "• Your wallet will be registered on the node\n" +
                            "• Rolls will become active in ~2 hours\n" +
                            "• You'll earn MAS for each block created",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.8f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Your wallet credentials are used securely",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Staking", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textColor.copy(alpha = 0.7f))
            }
        }
    )
}

/**
 * Dialog to register staking key
 */
@Composable
private fun RegisterStakingKeyDialog(
    isDarkTheme: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var secretKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    
    val dialogColor = if (isDarkTheme) Color(0xFF121212) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogColor,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Key,
                    contentDescription = null,
                    tint = Color(0xFF6366F1)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Register Staking Key",
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        },
        text = {
            Column {
                Text(
                    "Enter your wallet's secret key to register it for staking on the node.",
                    color = textColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFA726).copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFFA726),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Your secret key will be sent securely to your node",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFA726)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = secretKey,
                    onValueChange = { secretKey = it },
                    label = { Text("Secret Key") },
                    placeholder = { Text("S1...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showKey) 
                        androidx.compose.ui.text.input.VisualTransformation.None 
                    else 
                        androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showKey) "Hide" else "Show"
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = textColor.copy(alpha = 0.3f)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(secretKey) },
                enabled = secretKey.isNotBlank() && secretKey.startsWith("S"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Register", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textColor.copy(alpha = 0.7f))
            }
        }
    )
}

/**
 * Confirmation dialog before removing staking address
 */
@Composable
private fun RemoveStakingConfirmDialog(
    address: String,
    isCurrentWallet: Boolean,
    isDarkTheme: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dialogColor = if (isDarkTheme) Color(0xFF121212) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val warningColor = Color(0xFFEF4444)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogColor,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = warningColor.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = warningColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        title = {
            Text(
                "Stop Staking?",
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column {
                Text(
                    "You are about to stop staking for this address:",
                    color = textColor.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "${address.take(12)}...${address.takeLast(10)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = textColor
                        )
                        if (isCurrentWallet) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF6366F1).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    "  Your wallet  ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF6366F1),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = warningColor.copy(alpha = 0.1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = warningColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "What will happen:",
                                fontWeight = FontWeight.Medium,
                                color = textColor
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "• This address will stop generating rewards\n" +
                            "• Your rolls will remain intact\n" +
                            "• You can re-register anytime",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = warningColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop Staking", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textColor.copy(alpha = 0.7f))
            }
        }
    )
}

/**
 * Result dialog showing success or error for staking operations
 */
@Composable
private fun StakingResultDialog(
    result: StakingResultInfo,
    isDarkTheme: Boolean,
    onDismiss: () -> Unit
) {
    val dialogColor = if (isDarkTheme) Color(0xFF121212) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val statusColor = if (result.isSuccess) Color(0xFF4CAF50) else Color(0xFFEF4444)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogColor,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = statusColor.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        },
        title = {
            Text(
                result.title,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    result.message,
                    color = textColor.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                
                if (result.isSuccess) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = statusColor.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                when (result.operationType) {
                                    StakingOperationType.REGISTER_STAKING -> Icons.Default.PlayArrow
                                    StakingOperationType.REMOVE_STAKING -> Icons.Default.Stop
                                    StakingOperationType.BUY_ROLLS -> Icons.Default.Add
                                    StakingOperationType.SELL_ROLLS -> Icons.Default.Remove
                                },
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                when (result.operationType) {
                                    StakingOperationType.REGISTER_STAKING -> "Now earning rewards!"
                                    StakingOperationType.REMOVE_STAKING -> "Staking stopped"
                                    StakingOperationType.BUY_ROLLS -> "Rolls added to wallet"
                                    StakingOperationType.SELL_ROLLS -> "Rolls sold successfully"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = statusColor
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (result.isSuccess) statusColor else Color(0xFF6366F1)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (result.isSuccess) "Great!" else "OK",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}