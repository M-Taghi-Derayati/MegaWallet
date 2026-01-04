package com.mtd.megawallet.ui.compose.screens.addexistingwallet.manualimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mtd.common_ui.R
import com.mtd.core.model.Bip39Words
import com.mtd.megawallet.ui.compose.components.TopHeader

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualImportWordKeys(
    wordCount: Int = 12,
    initialWords: List<String> = emptyList(),
    onWordsChange: (List<String>) -> Unit = {},
    onVerificationSuccess: (List<String>) -> Unit
) {
    // --- State Management ---
    var currentIndex by remember { mutableIntStateOf(0) }
    var maxReachedIndex by remember { mutableIntStateOf(initialWords.count { it.isNotEmpty() }) }
    val userInputs = remember { 
        mutableStateListOf<String>().apply {
            addAll(List(wordCount) { i -> initialWords.getOrNull(i) ?: "" })
        }
    }

    val currentInput = userInputs[currentIndex].trim().lowercase()
    val isCurrentInputCorrect = remember(currentInput) {
        try {
            Bip39Words.English.contains(currentInput)
        } catch (e: Exception) {
            false
        }
    }

    // --- UI Layout ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
            TopHeader(  title = stringResource(com.mtd.megawallet.R.string.import_wallet_title),
                subtitle = stringResource(com.mtd.megawallet.R.string.import_wallet_subtitle_12_key))
            Spacer(modifier = Modifier.height(32.dp))
            InputSeedPhraseSection(
                currentIndex = currentIndex,
                userInputs = userInputs.toList(),
                wordCount = wordCount,
                onTextChange = { index, text -> userInputs[index] = text }
            )
        }

        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Spacer(modifier = Modifier.height(40.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "کلید های بازیابی شما",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily(Font(R.font.vazirmatn_medium, FontWeight.Light)),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(15.dp))

            SecretPhraseGrid(
                userInputs = userInputs.toList(),
                wordCount = wordCount,
                maxReachedIndex = maxReachedIndex,
                onItemClick = { clickedIndex ->
                    if (clickedIndex < maxReachedIndex) {
                        currentIndex = clickedIndex
                    }
                }
            )
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (isCurrentInputCorrect) {
                        if (currentIndex < wordCount - 1) {
                            // اگر کلمات بعدی قبلاً پر شده‌اند (حالت ویرایش)، به کلمه بعدی برو
                            // در غیر این صورت به بالاترین ایندکسی که رسیدیم برو
                            if (currentIndex < maxReachedIndex && maxReachedIndex < wordCount) {
                                currentIndex = maxReachedIndex
                            } else {
                                currentIndex++
                                if (currentIndex > maxReachedIndex) {
                                    maxReachedIndex = currentIndex
                                }
                            }
                        } else {
                            // اگر روی آخرین کلمه هستیم، نهایی کن
                            maxReachedIndex = wordCount 
                            onWordsChange(userInputs.toList())
                            onVerificationSuccess(userInputs.toList())
                        }
                    }
                },
                enabled = isCurrentInputCorrect,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = CircleShape
            ) {
                Text("ثبت",  fontFamily = FontFamily(Font(R.font.vazirmatn_medium, FontWeight.Light)))
            }
        }
    }
}


@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun AnimatedInputVerificationScreenPreview() {
    MaterialTheme {
        ManualImportWordKeys(onVerificationSuccess = {})
    }
}