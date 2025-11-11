package com.massapay.android.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@Composable
fun VerifyMnemonicScreen(
    mnemonic: List<String>,
    verifiedWords: Set<Int>,
    onVerifyWord: (Int, String) -> Unit
) {
    var currentWordIndex by remember {
        mutableStateOf(generateRandomIndices(mnemonic.size, 3)
            .first { !verifiedWords.contains(it) })
    }
    var userInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Verify Your Recovery Phrase",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Enter word #${currentWordIndex + 1}",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        OutlinedTextField(
            value = userInput,
            onValueChange = { userInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Word #${currentWordIndex + 1}") }
        )

        Button(
            onClick = {
                onVerifyWord(currentWordIndex, userInput)
                userInput = ""
                // Find next unverified word
                val remaining = generateRandomIndices(mnemonic.size, 3)
                    .filter { !verifiedWords.contains(it) }
                if (remaining.isNotEmpty()) {
                    currentWordIndex = remaining.first()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Verify")
        }

        // Progress indicator
        LinearProgressIndicator(
            progress = verifiedWords.size / 3f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SetPinScreen(
    onPinSet: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Set Your PIN",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Create a 6-digit PIN to secure your wallet",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        OutlinedTextField(
            value = pin,
            onValueChange = { 
                if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                    pin = it
                    if (it.length == 6) {
                        onPinSet(it)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Enter PIN") }
        )
    }
}

@Composable
fun ConfirmPinScreen(
    onPinConfirm: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Confirm Your PIN",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Re-enter your 6-digit PIN",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        OutlinedTextField(
            value = pin,
            onValueChange = { 
                if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                    pin = it
                    if (it.length == 6) {
                        onPinConfirm(it)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Confirm PIN") }
        )
    }
}

private fun generateRandomIndices(size: Int, count: Int): List<Int> {
    return (0 until size).shuffled(Random(System.currentTimeMillis())).take(count)
}