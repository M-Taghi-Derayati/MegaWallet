package com.mtd.megawallet.ui.compose.screens.addexistingwallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mtd.megawallet.ui.compose.components.BottomSecuritySection
import com.mtd.megawallet.ui.compose.components.InputManualSection
import com.mtd.megawallet.ui.compose.components.PrimaryButton
import com.mtd.megawallet.ui.compose.components.TopHeader

/**
 * Screen for auto-importing seed phrase from clipboard.
 * Uses remember for caching string resources to improve performance.
 */
@Composable
fun AutoImportWordKeys(
    words: List<String>,
    isValid: Boolean,
    onClickManual: () -> Unit,
    onVerificationSuccess: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    // String resources - Compose automatically optimizes these
    val title = stringResource(com.mtd.megawallet.R.string.import_wallet_title)
    val subtitle = stringResource(com.mtd.megawallet.R.string.import_wallet_subtitle_12_key)
    val securityNote = stringResource(com.mtd.megawallet.R.string.import_wallet_security_note)
    val addButtonText = stringResource(com.mtd.megawallet.R.string.import_wallet_add_button)
    val manualImportText = "وارد کردن دستی"

    Box(
        modifier = modifier
            .padding(vertical = 25.dp, horizontal = 20.dp)
            .fillMaxSize()
    ) {
        TopHeader(
            title = title,
            subtitle = subtitle
        )

        InputManualSection(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 230.dp),
            onClick = onClickManual,
            text = manualImportText,
            icon = Icons.Default.Edit
        )

        // بخش پایینی
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            BottomSecuritySection(message = securityNote)

            PrimaryButton(
                text = addButtonText,
                onClick = { onVerificationSuccess(words) },
                enabled = isValid,
                modifier = Modifier.padding(horizontal = 10.dp),
                height = 52.dp
            )
        }
    }
}