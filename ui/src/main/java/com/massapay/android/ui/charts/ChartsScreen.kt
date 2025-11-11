package com.massapay.android.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.massapay.android.ui.dashboard.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    onBackClick: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = isSystemInDarkTheme()
    
    // Use Material Theme colors for consistency
    val backgroundColor = MaterialTheme.colorScheme.background
    val cardBackground = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    
    // Theme-aware colors for positive/negative values
    val positiveColor = if (isDark) Color(0xFF4CAF50) else Color(0xFF2E7D32)
    val negativeColor = if (isDark) Color(0xFFFF6B6B) else Color(0xFFD32F2F)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Massa Statistics", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back")
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
            uiState.massaStats?.let { stats ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Price Header Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Current Price",
                                style = MaterialTheme.typography.bodyMedium,
                                color = textSecondary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    "$${String.format("%.6f", stats.price)}",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 36.sp
                                    ),
                                    color = textPrimary
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "Rank #${stats.rank}",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // 24h Change Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                "24 Hour Change",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = textPrimary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (stats.percentChange24h >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = if (stats.percentChange24h >= 0) positiveColor else negativeColor,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    "${if (stats.percentChange24h >= 0) "+" else ""}${String.format("%.2f", stats.percentChange24h)}%",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (stats.percentChange24h >= 0) positiveColor else negativeColor
                                )
                            }
                        }
                    }

                    // Period Changes Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Price Changes",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = textPrimary
                            )
                            Divider(color = textSecondary.copy(alpha = 0.2f))
                            
                            PeriodChangeRow("7 Days", stats.percentChange7d, textSecondary, positiveColor, negativeColor)
                            PeriodChangeRow("30 Days", stats.percentChange30d, textSecondary, positiveColor, negativeColor)
                        }
                    }

                    // Market Stats Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Market Statistics",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = textPrimary
                            )
                            Divider(color = textSecondary.copy(alpha = 0.2f))
                            
                            StatRow(
                                icon = Icons.Default.AccountBalance,
                                label = "Market Cap",
                                value = formatCurrency(stats.marketCap),
                                textPrimary = textPrimary,
                                textSecondary = textSecondary
                            )
                            StatRow(
                                icon = Icons.Default.TrendingUp,
                                label = "24h Volume",
                                value = formatCurrency(stats.volume24h),
                                textPrimary = textPrimary,
                                textSecondary = textSecondary
                            )
                            StatRow(
                                icon = Icons.Default.Token,
                                label = "Total Supply",
                                value = formatNumber(stats.totalSupply),
                                textPrimary = textPrimary,
                                textSecondary = textSecondary
                            )
                        }
                    }

                    // ATH Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "All-Time High",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = textPrimary
                                    )
                                    Text(
                                        "$${String.format("%.6f", stats.athPrice)}",
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = negativeColor.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        "${String.format("%.1f", stats.percentFromAth)}% from ATH",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = negativeColor
                                    )
                                }
                            }
                        }
                    }
                }
            } ?: run {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Loading statistics...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodChangeRow(
    period: String,
    change: Double,
    textSecondary: Color,
    positiveColor: Color,
    negativeColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            period,
            style = MaterialTheme.typography.bodyLarge,
            color = textSecondary
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (change >= 0) positiveColor.copy(alpha = 0.1f) else negativeColor.copy(alpha = 0.1f)
        ) {
            Text(
                "${if (change >= 0) "+" else ""}${String.format("%.2f", change)}%",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (change >= 0) positiveColor else negativeColor
            )
        }
    }
}

@Composable
private fun StatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    textPrimary: Color,
    textSecondary: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = textSecondary
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = textPrimary
        )
    }
}

private fun formatCurrency(value: Double): String {
    return when {
        value >= 1_000_000_000 -> "$${String.format("%.2f", value / 1_000_000_000)}B"
        value >= 1_000_000 -> "$${String.format("%.2f", value / 1_000_000)}M"
        value >= 1_000 -> "$${String.format("%.2f", value / 1_000)}K"
        else -> "$${String.format("%.2f", value)}"
    }
}

private fun formatNumber(value: Long): String {
    val doubleValue = value.toDouble()
    return when {
        doubleValue >= 1_000_000_000 -> "${String.format("%.2f", doubleValue / 1_000_000_000)}B"
        doubleValue >= 1_000_000 -> "${String.format("%.2f", doubleValue / 1_000_000)}M"
        doubleValue >= 1_000 -> "${String.format("%.2f", doubleValue / 1_000)}K"
        else -> String.format("%.0f", doubleValue)
    }
}
