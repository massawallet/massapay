package com.massapay.android.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifySeedScreen(
    seedWords: List<String>,
    onVerified: () -> Unit,
    onBack: () -> Unit
) {
    // Generate 3 random positions to verify
    val randomPositions = remember {
        seedWords.indices.shuffled().take(3).sorted()
    }

    var currentQuestionIndex by remember { mutableStateOf(0) }
    var selectedWords by remember { mutableStateOf(listOf<String>()) }
    var showError by remember { mutableStateOf(false) }
    var lastClickedWord by remember { mutableStateOf<String?>(null) }
    var isLastClickCorrect by remember { mutableStateOf<Boolean?>(null) }
    var shouldAdvance by remember { mutableStateOf(false) }
    var shouldComplete by remember { mutableStateOf(false) }
    
    // Handle advancing to next question with delay
    LaunchedEffect(shouldAdvance) {
        if (shouldAdvance) {
            kotlinx.coroutines.delay(300)
            if (currentQuestionIndex < 2) {
                currentQuestionIndex++
                lastClickedWord = null
                isLastClickCorrect = null
            } else {
                shouldComplete = true
            }
            shouldAdvance = false
        }
    }
    
    // Handle completion
    LaunchedEffect(shouldComplete) {
        if (shouldComplete) {
            onVerified()
        }
    }
    
    // Handle error feedback reset
    LaunchedEffect(lastClickedWord, isLastClickCorrect) {
        if (lastClickedWord != null && isLastClickCorrect == false) {
            kotlinx.coroutines.delay(800)
            lastClickedWord = null
            isLastClickCorrect = null
            showError = false
        }
    }

    val currentPosition = randomPositions.getOrNull(currentQuestionIndex)
    val correctWord = currentPosition?.let { seedWords[it] }

    // Generate options (correct word + 5 random wrong words)
    val options = remember(currentQuestionIndex) {
        val wrongWords = seedWords.filter { it != correctWord }.shuffled().take(5)
        (wrongWords + listOf(correctWord)).shuffled()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Progress
            LinearProgressIndicator(
                progress = (currentQuestionIndex + 1) / 3f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Header
            Text(
                text = "Verify Recovery Phrase",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select the correct word for each position",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (currentPosition != null) {
                // Question Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Question ${currentQuestionIndex + 1} of 3",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "What is word #${currentPosition + 1}?",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Options Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(options) { word ->
                        word?.let { w ->
                            WordOptionCard(
                                word = w,
                                isSelected = selectedWords.contains(w),
                                isLastClicked = lastClickedWord == w,
                                isCorrect = if (lastClickedWord == w) isLastClickCorrect else null,
                                onClick = {
                                    lastClickedWord = w
                                    if (w == correctWord) {
                                        isLastClickCorrect = true
                                        selectedWords = selectedWords + w
                                        showError = false
                                        shouldAdvance = true
                                    } else {
                                        isLastClickCorrect = false
                                        showError = true
                                    }
                                }
                            )
                        }
                    }
                }

                if (showError) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "❌ Incorrect word. Please try again.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (index < currentQuestionIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    if (index < 2) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Back Button
            TextButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    text = "Go Back",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun WordOptionCard(
    word: String,
    isSelected: Boolean,
    isLastClicked: Boolean,
    isCorrect: Boolean?,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isLastClicked && isCorrect == true -> MaterialTheme.colorScheme.primaryContainer
        isLastClicked && isCorrect == false -> MaterialTheme.colorScheme.errorContainer
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    
    val borderColor = when {
        isLastClicked && isCorrect == true -> MaterialTheme.colorScheme.primary
        isLastClicked && isCorrect == false -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = word,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}
