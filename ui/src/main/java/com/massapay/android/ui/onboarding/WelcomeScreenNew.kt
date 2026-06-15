package com.massapay.android.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.massapay.android.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreenNew(
    onCreateWallet: () -> Unit,
    onImportWallet: () -> Unit
) {
    var showTermsSheet by remember { mutableStateOf(false) }
    var acceptedTerms by remember { mutableStateOf(false) }

    val isDarkTheme = MaterialTheme.colorScheme.background.luminanceCompat() < 0.5f
    val textColor = MaterialTheme.colorScheme.onBackground
    val mutedText = textColor.copy(alpha = 0.62f)
    val buttonContainerColor = if (isDarkTheme) Color.White else Color.Black
    val buttonContentColor = if (isDarkTheme) Color.Black else Color.White
    val outlineButtonColor = buttonContainerColor
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.42f)
                .background(Brush.verticalGradient(gradientColors))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MassaConnect",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "v1.5.0",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier.padding(top = 4.dp),
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Image(
                painter = painterResource(R.drawable.brand_logo),
                contentDescription = "MassaConnect logo",
                modifier = Modifier.size(92.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Welcome",
                color = textColor,
                fontSize = 34.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Create or import your wallet to start using MassaConnect.",
                color = mutedText,
                fontSize = 16.sp,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = CircleShape,
                color = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
            ) {
                Text(
                    text = "Powered by Massa Network",
                    color = if (isDarkTheme) Color.White else Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    if (acceptedTerms) onCreateWallet() else showTermsSheet = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonContainerColor,
                    contentColor = buttonContentColor
                ),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 22.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.size(12.dp))
                Text("Create Wallet", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = {
                    if (acceptedTerms) onImportWallet() else showTermsSheet = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = outlineButtonColor),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, outlineButtonColor),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 22.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(23.dp))
                Spacer(modifier = Modifier.size(12.dp))
                Text("Import Wallet", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = acceptedTerms,
                    onCheckedChange = { acceptedTerms = it },
                    colors = CheckboxDefaults.colors(checkedColor = buttonContainerColor)
                )
                Text(
                    text = "I accept the",
                    color = mutedText,
                    fontSize = 14.sp
                )
                TextButton(onClick = { showTermsSheet = true }) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = outlineButtonColor
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Terms", color = outlineButtonColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showTermsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTermsSheet = false },
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = { it != SheetValue.PartiallyExpanded }
            ),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            TermsSheetContent(
                onAccept = {
                    acceptedTerms = true
                    showTermsSheet = false
                },
                onCancel = { showTermsSheet = false }
            )
        }
    }
}

@Composable
private fun TermsSheetContent(
    onAccept: () -> Unit,
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
            text = "Terms & Conditions",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Before creating or importing a wallet, confirm that you understand how self-custody works.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
        TermsBullet("You are responsible for keeping your recovery phrase safe.")
        TermsBullet("Never share your seed phrase, private key, PIN, or biometric access.")
        TermsBullet("Blockchain transactions are irreversible.")
        TermsBullet("MassaConnect is non-custodial: only this device stores your keys.")
        Spacer(modifier = Modifier.weight(1f, fill = false))
        Button(
            onClick = onAccept,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (MaterialTheme.colorScheme.background.luminanceCompat() < 0.5f) Color.White else Color.Black,
                contentColor = if (MaterialTheme.colorScheme.background.luminanceCompat() < 0.5f) Color.Black else Color.White
            )
        ) {
            Text("Accept and Continue", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel", textAlign = TextAlign.Center)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TermsBullet(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f))
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(if (MaterialTheme.colorScheme.background.luminanceCompat() < 0.5f) Color.White else Color.Black)
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

private fun Color.luminanceCompat(): Float {
    return red * 0.299f + green * 0.587f + blue * 0.114f
}
