package com.massapay.android.ui.transaction

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun TransactionSuccessScreen(
    amount: String,
    recipient: String,
    onDismiss: () -> Unit
) {
    TransactionResultContent(
        icon = Icons.Filled.Check,
        iconColor = Color(0xFF4CAF50),
        backgroundColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
        title = "Transaction Sent!",
        message = "Your transaction of $amount MAS to\n${recipient.take(10)}...${recipient.takeLast(8)}\nhas been sent successfully",
        onDismiss = onDismiss
    )
}

@Composable
fun TransactionFailureScreen(
    errorMessage: String,
    onDismiss: () -> Unit
) {
    TransactionResultContent(
        icon = Icons.Filled.Close,
        iconColor = Color(0xFFF44336),
        backgroundColor = Color(0xFFF44336).copy(alpha = 0.1f),
        title = "Transaction Failed",
        message = errorMessage,
        onDismiss = onDismiss
    )
}

@Composable
private fun TransactionResultContent(
    icon: ImageVector,
    iconColor: Color,
    backgroundColor: Color,
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    // Animation states
    var animationStarted by remember { mutableStateOf(false) }
    
    // Scale animation for the circle
    val scale by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    // Alpha animation for the content
    val alpha by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(
            durationMillis = 600,
            delayMillis = 200
        ),
        label = "alpha"
    )
    
    // Start animation on composition
    LaunchedEffect(Unit) {
        delay(100)
        animationStarted = true
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Animated Circle with Icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .background(
                        color = backgroundColor,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            color = iconColor,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Title
            Text(
                text = title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer(alpha = alpha)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Message
            Text(
                text = message,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer(alpha = alpha)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Done button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .graphicsLayer(alpha = alpha),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = iconColor,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Done",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
