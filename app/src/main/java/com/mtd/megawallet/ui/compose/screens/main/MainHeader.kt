package com.mtd.megawallet.ui.compose.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.R
import com.mtd.common_ui.theme.IranSansBoldBold
import com.mtd.common_ui.theme.MegaWalletTheme
import com.mtd.domain.model.BlockchainConnectionMode
import com.mtd.domain.model.FiatCurrency
import com.mtd.megawallet.ui.compose.animations.constants.MainScreenConstants

/**
 * Header صفحه اصلی - شبیه عکس اول
 * شامل: emoji در دایره صورتی، نام کیف، و سه آیکون در سمت راست
 */
@Composable
internal fun MainHeader(
    walletName: String,
    walletColor: Color,
    onWalletClick: () -> Unit,
    onScanClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMoreOptionsClick: () -> Unit,
    onCurrencyToggle: () -> Unit,
    connectionMode: BlockchainConnectionMode,
    fiatCurrency: FiatCurrency
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(), // اضافه کردن padding برای statusbar
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxWidth()
                .padding(
                    horizontal = MainScreenConstants.HEADER_PADDING_HORIZONTAL,
                    vertical = MainScreenConstants.HEADER_PADDING_VERTICAL
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // سمت راست: دایره رنگی با emoji و نام کیف
            Row(
                modifier = Modifier.clickable { onWalletClick() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MainScreenConstants.WALLET_NAME_SPACING)
            ) {

                Box(
                    modifier = Modifier
                        .size(MainScreenConstants.WALLET_AVATAR_SIZE)
                        .clip(CircleShape)
                        .background(walletColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_wallet),
                        contentDescription = "Wallet",
                        modifier = Modifier.size(MainScreenConstants.WALLET_ICON_SIZE)
                    )
                }

                Text(
                    text = walletName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontFamily = IranSansBoldBold,
                    fontSize = MainScreenConstants.WALLET_NAME_FONT_SIZE
                )

            }

            // سمت چپ: سه آیکون (سه نقطه، سرچ، و یک آیکون دیگر)
            Row(
                horizontalArrangement = Arrangement.spacedBy(MainScreenConstants.HEADER_SPACING),
                verticalAlignment = Alignment.CenterVertically
            ) {

                // آیکون سه نقطه
                IconButton(
                    onClick = onMoreOptionsClick,
                    modifier = Modifier.size(MainScreenConstants.HEADER_ICON_SIZE)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(MainScreenConstants.HEADER_ICON_ICON_SIZE)
                    )
                }
                // آیکون سرچ — تغییر حالت اتصال (PROXY سبز / DIRECT خاکستری)
                val searchTint = when (connectionMode) {
                    BlockchainConnectionMode.PROXY -> Color(0xFF2E7D32) // green = relayer/proxy active
                    BlockchainConnectionMode.DIRECT -> MaterialTheme.colorScheme.tertiary
                }
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier.size(MainScreenConstants.HEADER_ICON_SIZE)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = "Toggle connection mode (${connectionMode.name})",
                        tint = searchTint,
                        modifier = Modifier.size(MainScreenConstants.HEADER_ICON_ICON_SIZE)
                    )
                }
                // TASK-56 — تغییر واحد نمایش بین دلار و تومان.
                //
                // The control shows the currency that is ACTIVE, not the one a tap would switch to: the
                // header is the only indicator of which unit the balances on screen are in, so it has to
                // answer "what am I looking at?" before "what does this button do?".
                val currencyLabel = when (fiatCurrency) {
                    FiatCurrency.USD -> "$"
                    FiatCurrency.TOMAN -> "تومان"
                }
                val currencyDescription = when (fiatCurrency) {
                    FiatCurrency.USD -> "واحد نمایش: دلار — برای تغییر به تومان بزنید"
                    FiatCurrency.TOMAN -> "واحد نمایش: تومان — برای تغییر به دلار بزنید"
                }
                IconButton(
                    onClick = onCurrencyToggle,
                    modifier = Modifier
                        .size(MainScreenConstants.HEADER_ICON_SIZE)
                        .semantics { contentDescription = currencyDescription }
                ) {
                    Text(
                        text = currencyLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        // تومان در فارسی؛ «$» با فونت لاتین بهتر می‌نشیند
                        fontFamily = if (fiatCurrency == FiatCurrency.TOMAN) IranSansBoldBold else null,
                        fontSize = if (fiatCurrency == FiatCurrency.TOMAN) 12.sp else 18.sp,
                        maxLines = 1
                    )
                }



            }



        }
    }
}

// ============================================
// Previews
// ============================================

@Preview(name = "MainHeader - Light")
@Composable
private fun MainHeaderLightPreview() {
    MegaWalletTheme(darkTheme = false) {
        MainHeader(
            walletName = "کیف پول من",
            walletColor = Color(0xFFEC4899),
            onWalletClick = {},
            onScanClick = {},
            onSearchClick = {},
            onMoreOptionsClick = {},
            onCurrencyToggle = {},
            connectionMode = BlockchainConnectionMode.DIRECT,
            fiatCurrency = FiatCurrency.USD
        )
    }
}

@Preview(name = "MainHeader - Dark (Proxy)")
@Composable
private fun MainHeaderDarkPreview() {
    MegaWalletTheme(darkTheme = true) {
        MainHeader(
            walletName = "کیف پول من",
            walletColor = Color(0xFF6C63FF),
            onWalletClick = {},
            onScanClick = {},
            onSearchClick = {},
            onMoreOptionsClick = {},
            onCurrencyToggle = {},
            connectionMode = BlockchainConnectionMode.PROXY,
            fiatCurrency = FiatCurrency.TOMAN
        )
    }
}
