package com.mtd.megawallet.ui.compose.screens.addexistingwallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mtd.megawallet.ui.compose.components.BottomSecuritySection
import com.mtd.megawallet.ui.compose.components.InputManualSection
import com.mtd.megawallet.ui.compose.components.PrimaryButton
import com.mtd.megawallet.ui.compose.components.TopHeader

/**
 * Screen for importing private key.
 * Uses remember for caching string resources to improve performance.
 */
@Composable
fun PrivateKeyImport(
    isValid: Boolean,
    onClickClear: () -> Unit,
    onVerificationSuccess: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // String resources - Compose automatically optimizes these
    val title = stringResource(com.mtd.megawallet.R.string.import_wallet_title)
    val subtitle = stringResource(com.mtd.megawallet.R.string.import_wallet_subtitle_private_key)
    val securityNote = stringResource(com.mtd.megawallet.R.string.import_wallet_security_note)
    val addButtonText = stringResource(com.mtd.megawallet.R.string.import_wallet_add_button)
    val clearText = "پاک کردن"

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
                .padding(top = 150.dp),
            onClick = onClickClear,
            text = clearText,
            icon = Icons.Default.Close
        )

        // بخش پایینی
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            BottomSecuritySection(message = securityNote)

            PrimaryButton(
                text = addButtonText,
                onClick = { onVerificationSuccess("") }, // Actual key would be passed here
                enabled = isValid,
                modifier = Modifier.padding(horizontal = 10.dp),
                height =52.dp
            )
        }
    }
}