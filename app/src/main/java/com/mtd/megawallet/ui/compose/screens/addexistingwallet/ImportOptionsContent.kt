package com.mtd.megawallet.ui.compose.screens.addexistingwallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mtd.common_ui.R
import com.mtd.megawallet.ui.compose.components.WalletOptionItem

/**
 * Content showing import options (seed phrase or private key).
 */
@Composable
fun ImportOptionsContent(
    onImportSeed: () -> Unit,
    onImportPrivateKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "انتخاب روش وارد کردن",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.tertiary,
            fontFamily = FontFamily(Font(R.font.vazirmatn_bold, FontWeight.Bold))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "نحوه وارد کردن کیف پول خود را انتخاب کنید.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiary,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily(Font(R.font.vazirmatn_medium, FontWeight.Light))
        )
        Spacer(modifier = Modifier.height(32.dp))
        Column {
            WalletOptionItem(
                icon = Icons.Default.TextFields,
                iconColor = Color(0xFF22C55E),
                title = "عبارت بازیابی",
                subtitle = "وارد کردن کیف پول با عبارت ۱۲/۲۴ کلمه‌ای بازیابی",
                onClick = onImportSeed
            )
            Spacer(modifier = Modifier.height(16.dp))
            WalletOptionItem(
                icon = Icons.Default.Key,
                iconColor = Color(0xFF22C55E),
                title = "کلید خصوصی",
                subtitle = "وارد کردن کیف پول با کلید خصوصی ۶۴ کاراکتری",
                onClick = onImportPrivateKey
            )
        }
    }
}

