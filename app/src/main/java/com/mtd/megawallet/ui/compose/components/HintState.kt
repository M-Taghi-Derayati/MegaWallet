package com.mtd.megawallet.ui.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.common_ui.theme.MegaWalletTheme

/** پیامِ جای‌گزینِ لیست: بارگذاری، خالی‌بودن، یا خطا. */
@Composable
internal fun HintState(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onTertiary,
            fontFamily = IranSansRegular,
            fontSize = 14.sp
        )
    }
}

// ============================================
// Previews
// ============================================

@Preview(name = "HintState error - Dark")
@Composable
private fun HintStateDarkPreview() {
    MegaWalletTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.size(240.dp).padding(16.dp)) {
                HintState(text = "آدرس وارد شده معتبر نیست", isError = true)
            }
        }
    }
}
