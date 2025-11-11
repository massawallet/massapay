package com.massapay.android.ui.onboarding.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun VerifyMnemonicScreen(
    mnemonic: List<String>,
    verifiedWords: Set<Int>,
    onVerifyWord: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Verify Your Recovery Phrase",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Please verify the following words from your recovery phrase",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        // We'll verify 3 random words (indexes 2, 8, and 14 for example)
        val wordsToVerify = listOf(2, 8, 14)
        
        wordsToVerify.forEach { index ->
            if (index < mnemonic.size) {
                WordVerificationCard(
                    wordIndex = index + 1,
                    isVerified = verifiedWords.contains(index),
                    onWordSubmit = { word -> onVerifyWord(index, word) }
                )
            }
        }

        Text(
            text = "Enter the correct words to continue",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WordVerificationCard(
    wordIndex: Int,
    isVerified: Boolean,
    onWordSubmit: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isVerified) 
                MaterialTheme.colorScheme.secondaryContainer 
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Word #$wordIndex",
                style = MaterialTheme.typography.titleMedium
            )
            
            if (!isVerified) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Enter word #$wordIndex") },
                    singleLine = true,
                    enabled = !isVerified
                )
                
                Button(
                    onClick = { onWordSubmit(input.trim()) },
                    modifier = Modifier.align(Alignment.End),
                    enabled = input.isNotBlank()
                ) {
                    Text("Verify")
                }
            } else {
                Text(
                    text = "✓ Verified",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}