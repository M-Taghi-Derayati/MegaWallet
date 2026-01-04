package com.mtd.megawallet.ui.compose.screens.addexistingwallet.manualimport

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mtd.megawallet.ui.compose.components.SecretPhraseItem

/**
 * Grid showing entered seed phrase words.
 */
@Composable
fun SecretPhraseGrid(
    userInputs: List<String>,
    wordCount: Int,
    maxReachedIndex: Int,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // بهینه‌سازی: استفاده از remember برای cache کردن مقادیر ثابت
    val isDark = isSystemInDarkTheme() // باید در composition scope باشد
    val horizontalSpacing = remember { 32.dp }
    val verticalSpacing = remember { 13.dp }
    val horizontalPadding = remember { 13.dp }
    val placeholderText = remember { "........" }
    
    // بهینه‌سازی: cache کردن رنگ‌ها
    val itemColor = remember(isDark) {
        if (isDark) Color.Gray.copy(alpha = 0.5f) else Color.LightGray
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            // بهینه‌سازی: استفاده از contentPadding
            contentPadding = PaddingValues(0.dp),
            userScrollEnabled = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                count = wordCount,
                // بهینه‌سازی: استفاده از stable key
                key = { index -> index }
            ) { index ->
                // بهینه‌سازی: محاسبه word یکبار در composition
                val word = remember(userInputs, maxReachedIndex, index) {
                    if (index < maxReachedIndex) {
                        userInputs.getOrNull(index) ?: placeholderText
                    } else {
                        placeholderText
                    }
                }
                Box(
                    modifier = Modifier.clickable { onItemClick(index) },
                    contentAlignment = Alignment.Center
                ) {
                    SecretPhraseItem(
                        index = index, 
                        word = word, 
                        color = itemColor
                    )
                }
            }
        }
    }
}

