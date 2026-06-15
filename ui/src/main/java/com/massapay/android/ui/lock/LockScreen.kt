package com.massapay.android.ui.lock

import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.massapay.android.ui.R
import kotlin.math.PI
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    onWalletReset: () -> Unit = {},
    viewModel: LockViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current as FragmentActivity
    var pin by remember { mutableStateOf("") }
    var showBiometricSheet by remember { mutableStateOf(false) }
    var showPinSheet by remember { mutableStateOf(false) }

    val isDarkTheme = MaterialTheme.colorScheme.background.luminanceCompat() < 0.5f
    val textColor = MaterialTheme.colorScheme.onBackground
    val darkControlColor = Color(0xFF202124)
    val buttonContainerColor = if (isDarkTheme) darkControlColor else Color.Black
    val buttonContentColor = Color.White
    val linkColor = if (isDarkTheme) Color.White else Color.Black
    val navigationBarColor = MaterialTheme.colorScheme.background
    val view = LocalView.current
    val gradientColors = if (isDarkTheme) {
        listOf(Color.Black, Color(0xFF101114), MaterialTheme.colorScheme.background)
    } else {
        listOf(Color.Black, Color(0xFF4A4A4A), MaterialTheme.colorScheme.background)
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = navigationBarColor.toArgb()
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    val biometricPrompt = remember {
        BiometricPrompt(
            context,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.onBiometricSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    viewModel.onBiometricError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    viewModel.onBiometricError("Authentication failed")
                }
            }
        )
    }

    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock MassaConnect")
            .setSubtitle("Use your biometric credential")
            .setNegativeButtonText("Use PIN")
            .build()
    }

    LaunchedEffect(uiState.showBiometricPrompt) {
        if (uiState.showBiometricPrompt) {
            biometricPrompt.authenticate(promptInfo)
            viewModel.resetBiometricPrompt()
        }
    }

    LaunchedEffect(uiState.isUnlocked) {
        if (uiState.isUnlocked) onUnlocked()
    }

    LaunchedEffect(uiState.walletReset) {
        if (uiState.walletReset) onWalletReset()
    }

    LaunchedEffect(uiState.error) {
        if (uiState.error != null) pin = ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.38f)
                .background(Brush.verticalGradient(gradientColors))
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 28.dp, vertical = 22.dp)
        ) {
            val logoTop = maxHeight * 0.31f
            val greetingTop = maxHeight * 0.47f
            val noticeTop = maxHeight * 0.61f
            val actionTop = (maxHeight * 0.83f).coerceAtMost(maxHeight - 112.dp)

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MassaConnect",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "v1.5.0",
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 6.dp, end = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.People,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Connecting people",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Image(
                painter = painterResource(R.drawable.brand_logo),
                contentDescription = "MassaConnect logo",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = logoTop)
                    .size(82.dp),
                contentScale = ContentScale.Fit
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = greetingTop)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Hola, ${uiState.walletName}!",
                    color = textColor,
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Black
                )
                TextButton(onClick = { viewModel.showForgotPasswordDialog(true) }) {
                    Text(
                        text = "Forgot your PIN?",
                        color = linkColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            NoticeBox(
                text = uiState.error ?: "Your session expired for security. Authenticate again to enter.",
                isError = uiState.error != null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = noticeTop)
            )

            AnimatedVisibility(
                visible = uiState.biometricAvailable,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = actionTop)
                    .fillMaxWidth()
            ) {
                Column {
                    Button(
                        onClick = { showBiometricSheet = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonContainerColor,
                            contentColor = buttonContentColor
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp)
                    ) {
                        if (uiState.isLoading && !uiState.showBiometricPrompt) {
                            UnlockLoadingDots(color = buttonContentColor)
                        } else {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(26.dp))
                            Spacer(modifier = Modifier.size(12.dp))
                            Text("Unlock with biometrics", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = {
                            showPinSheet = true
                            viewModel.clearError()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use PIN", color = linkColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            AnimatedVisibility(
                visible = !uiState.biometricAvailable,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = actionTop)
                    .fillMaxWidth()
            ) {
                Button(
                    onClick = { showPinSheet = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonContainerColor,
                        contentColor = buttonContentColor
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp)
                ) {
                    Text("Unlock with PIN", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showBiometricSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBiometricSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            BiometricUnlockSheet(
                isLoading = uiState.isLoading,
                buttonContainerColor = buttonContainerColor,
                buttonContentColor = buttonContentColor,
                linkColor = linkColor,
                onUnlock = {
                    showBiometricSheet = false
                    viewModel.authenticateWithBiometric()
                },
                onUsePin = {
                    showBiometricSheet = false
                    showPinSheet = true
                    viewModel.clearError()
                }
            )
        }
    }

    if (showPinSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showPinSheet = false
                pin = ""
                viewModel.clearError()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            PinUnlockSheet(
                pin = pin,
                error = uiState.error,
                isDarkTheme = isDarkTheme,
                linkColor = linkColor,
                biometricAvailable = uiState.biometricAvailable,
                onNumberClick = { number ->
                    if (pin.length < 6) {
                        if (pin.isEmpty()) viewModel.clearError()
                        pin += number
                        if (pin.length == 6) {
                            viewModel.verifyPin(pin)
                        }
                    }
                },
                onDeleteClick = {
                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                },
                onUseBiometric = {
                    showPinSheet = false
                    pin = ""
                    viewModel.clearError()
                    showBiometricSheet = true
                }
            )
        }
    }

    if (uiState.showForgotPasswordDialog) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.showForgotPasswordDialog(false) },
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = { it != SheetValue.PartiallyExpanded }
            ),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ForgotPinSheet(
                onReset = {
                    viewModel.resetWallet()
                    viewModel.showForgotPasswordDialog(false)
                },
                onCancel = { viewModel.showForgotPasswordDialog(false) }
            )
        }
    }
}

@Composable
private fun BiometricUnlockSheet(
    isLoading: Boolean,
    buttonContainerColor: Color,
    buttonContentColor: Color,
    linkColor: Color,
    onUnlock: () -> Unit,
    onUsePin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.46f)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Icon(
            Icons.Default.Fingerprint,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Unlock with biometrics",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Confirm your identity to access your wallet.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onUnlock,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonContainerColor,
                contentColor = buttonContentColor
            )
        ) {
            if (isLoading) {
                UnlockLoadingDots(color = buttonContentColor)
            } else {
                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.size(10.dp))
                Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        TextButton(
            onClick = onUsePin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Use PIN", color = linkColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun UnlockLoadingDots(
    color: Color,
    modifier: Modifier = Modifier,
    dotCount: Int = 6
) {
    val transition = rememberInfiniteTransition(label = "unlockDots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing)
        ),
        label = "unlockDotsPhase"
    )

    Row(
        modifier = modifier.height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(dotCount) { index ->
            val wave = ((sin((phase * 2f * PI) - (index * 0.62f)) + 1f) / 2f).toFloat()
            Box(
                modifier = Modifier
                    .offset(y = (-7f * wave).dp)
                    .scale(0.78f + (0.24f * wave))
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.58f + (0.42f * wave)))
            )
        }
    }
}

@Composable
private fun PinUnlockSheet(
    pin: String,
    error: String?,
    isDarkTheme: Boolean,
    linkColor: Color,
    biometricAvailable: Boolean,
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onUseBiometric: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.86f)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = error ?: "Enter your PIN",
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(6) { index ->
                PinDot(
                    isFilled = index < pin.length,
                    isError = error != null,
                    isDarkTheme = isDarkTheme
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        NumberPad(
            onNumberClick = onNumberClick,
            onDeleteClick = onDeleteClick,
            isDarkTheme = isDarkTheme
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (biometricAvailable) {
            TextButton(
                onClick = onUseBiometric,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Use biometrics", color = linkColor, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun NoticeBox(
    text: String,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminanceCompat() < 0.5f
    val container = if (isDarkTheme) Color(0xFF202124) else Color.White
    val content = if (isDarkTheme) Color.White else Color.Black

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .border(
                width = 1.dp,
                color = if (isDarkTheme) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isError) Icons.Default.Error else Icons.Outlined.Info,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = text,
            color = content,
            fontSize = 16.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ForgotPinSheet(
    onReset: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.86f)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Forgot your PIN?",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "Resetting the wallet removes local encrypted data from this device. Continue only if your seed phrase or private key is backed up.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
        WarningItem("Your wallet profile and local keys will be deleted.")
        WarningItem("Your PIN and biometric preference will be cleared.")
        WarningItem("You must import your seed phrase or private key again.")
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onReset,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Reset Wallet", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = CircleShape
        ) {
            Text("Cancel", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun WarningItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error)
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun PinDot(
    isFilled: Boolean,
    isError: Boolean,
    isDarkTheme: Boolean
) {
    val dotColor = when {
        isError -> MaterialTheme.colorScheme.error
        isDarkTheme -> Color.White
        else -> Color(0xFF1F2937)
    }

    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(if (isFilled) dotColor else Color.Transparent)
            .border(2.dp, dotColor.copy(alpha = if (isFilled) 1f else 0.35f), CircleShape)
    )
}

@Composable
private fun NumberPad(
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    isDarkTheme: Boolean
) {
    val keyColor = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.045f)
    val keyText = MaterialTheme.colorScheme.onBackground

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9")
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { number ->
                    NumberButton(
                        text = number,
                        onClick = { onNumberClick(number) },
                        modifier = Modifier.weight(1f),
                        containerColor = keyColor,
                        contentColor = keyText
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.weight(1f))
            NumberButton(
                text = "0",
                onClick = { onNumberClick("0") },
                modifier = Modifier.weight(1f),
                containerColor = keyColor,
                contentColor = keyText
            )
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clip(CircleShape)
                    .background(keyColor)
            ) {
                Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = keyText)
            }
        }
    }
}

@Composable
private fun NumberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    containerColor: Color,
    contentColor: Color
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text = text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun Color.luminanceCompat(): Float {
    return red * 0.299f + green * 0.587f + blue * 0.114f
}
