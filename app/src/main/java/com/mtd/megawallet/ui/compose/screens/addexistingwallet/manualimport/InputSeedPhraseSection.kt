package com.mtd.megawallet.ui.compose.screens.addexistingwallet.manualimport

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mtd.core.model.Bip39Words

/**
 * Section for inputting seed phrase words with auto-complete.
 */
@Composable
fun InputSeedPhraseSection(
    currentIndex: Int,
    userInputs: List<String>,
    wordCount: Int,
    onTextChange: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
    isTestMode: Boolean = false,
    validationStateMap: Map<Int, ValidationState> = emptyMap(),
    hideIndex: Boolean = false
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            val rangeStart = (currentIndex - 2).coerceAtLeast(0)
            val rangeEnd = (currentIndex + 2).coerceAtMost(wordCount - 1)

            for (i in rangeStart..rangeEnd) {
                key(i) {
                    val currentInput = userInputs.getOrNull(i) ?: ""
                    val suggestions = remember(currentInput) {
                        if (currentInput.length >= 2) {
                            try {
                                Bip39Words.English.filter {
                                    it.startsWith(
                                        currentInput,
                                        ignoreCase = true
                                    ) && it != currentInput
                                }.take(3)
                            } catch (e: Exception) {
                                emptyList()
                            }
                        } else {
                            emptyList()
                        }
                    }



                    val validationState = validationStateMap[i] ?: ValidationState.None

                    AnimatedInputCard(
                        index = i,
                        currentIndex = currentIndex,
                        text = currentInput,
                        onTextChange = { newText ->
                            if (i == currentIndex && !isTestMode) {
                                onTextChange(i, newText)
                            }
                        },
                        onClick = { /* Handle click if needed */ },
                        validationState = validationState,
                        isReadOnly = isTestMode,
                        hideIndex = hideIndex
                    )

                    // Show auto-complete dropdown for current input
                    if (i == currentIndex && suggestions.isNotEmpty() && !isTestMode) {
                        AutoCompleteDropdown(
                            suggestions = suggestions,
                            onSuggestionClick = { word ->
                                onTextChange(i, word)
                            }
                        )
                    }
                }
            }
        }
    }
}

