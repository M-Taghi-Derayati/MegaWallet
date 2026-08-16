package com.mtd.megawallet.ui.compose.screens.send

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.InterBold
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.common_ui.theme.MegaWalletTheme
import com.mtd.domain.model.AssetItem
import com.mtd.common_ui.theme.AssetIcon
import com.mtd.common_ui.theme.NetworkIcon
import com.mtd.megawallet.ui.compose.components.ConfirmDetailCard
import com.mtd.megawallet.ui.compose.components.ConfirmDetailRow

/**
 * آواتار گیرنده در صفحهٔ تأیید تراکنش (آیکون ارز + نشانِ تأیید).
 * فقط به داده‌های immutable وابسته است تا recomposition پایدار بماند.
 */
@Composable
internal fun RecipientAvatar(
    asset: AssetItem,
    recipientName: String?,
    modifier: Modifier = Modifier,
    showBadge: Boolean = true
) {
    Box(modifier = modifier.size(64.dp)) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            AssetIcon(
                iconUrl = asset.iconUrl,
                symbol = asset.symbol,
                contentDescription = null,
                modifier = Modifier.size(56.dp)
            )
        }
        if (showBadge) {
            Box(
                modifier = Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF1C8A3C)).align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(12.dp), tint = Color.White)
            }
        }
    }
}

/**
 * کارتِ جزئیات تراکنش (مقدار ارسالی، ارزش کل، کیف‌پول مبدأ، شبکه).
 * تمام ورودی‌ها immutable هستند؛ رنگ‌های انیمیشنی از بیرون پاس داده می‌شوند.
 *
 * قاب و ردیف‌ها از [ConfirmDetailCard]/[ConfirmDetailRow] می‌آیند تا صفحهٔ تأییدِ تبدیل هم دقیقاً
 * همین‌ها را بردارد، نه یک کپیِ کمی متفاوت.
 */
@Composable
internal fun TransactionDetailCard(
    asset: AssetItem,
    displayCrypto: String,
    displayUsd: String,
    displayIrr: String,
    walletName: String,
    isAmountTooSmall: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    ConfirmDetailCard(modifier = modifier) {
        ConfirmDetailRow(
            label = "ارسال ${asset.faName}",
            valueLeft = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssetIcon(
                        iconUrl = asset.iconUrl,
                        symbol = asset.symbol,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = displayCrypto,
                        color = if (isAmountTooSmall) MaterialTheme.colorScheme.error else primaryColor,
                        fontFamily = InterBold,
                        fontSize = 15.sp
                    )
                }
            }
        )
        ConfirmDetailRow(
            label = "ارزش کل",
            valueLeft = {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = displayUsd,
                        color = primaryColor,
                        fontFamily = InterBold,
                        fontSize = 15.sp
                    )
                    if (displayIrr.isNotBlank()) {
                        Text(
                            text = "≈ $displayIrr",
                            color = secondaryColor,
                            fontFamily = IranSansRegular,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        )
        ConfirmDetailRow(
            label = "از کیف‌ پول",
            value = walletName
        )
        ConfirmDetailRow(
            label = "شبکه",
            valueLeft = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NetworkIcon(asset.networkIconUrl, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = asset.networkFaName ?: "نامشخص",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = IranSansRegular,
                        fontSize = 15.sp
                    )
                }
            }
        )
    }
}

/**
 * پوششِ ورود مرحله‌ای (staggered) برای هر بخشِ صفحه.
 */
@Composable
internal fun StaggeredSection(visible: Boolean, delayMs: Int = 0, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible, enter = slideInVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) { it / 4 } + fadeIn(tween(250)), exit = fadeOut(tween(150))) { content() }
}

// ============================================
// Previews
// ============================================

@Preview(name = "ConfirmDetailRow - Light")
@Composable
private fun ConfirmDetailRowLightPreview() {
    MegaWalletTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                ConfirmDetailRow(label = "از کیف‌ پول", value = "کیف پول من")
            }
        }
    }
}

@Preview(name = "ConfirmDetailRow - Dark")
@Composable
private fun ConfirmDetailRowDarkPreview() {
    MegaWalletTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                ConfirmDetailRow(label = "از کیف‌ پول", value = "کیف پول من")
            }
        }
    }
}

@Preview(name = "TransactionDetailCard - Light")
@Composable
private fun TransactionDetailCardLightPreview() {
    MegaWalletTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                TransactionDetailCard(
                    asset = sampleConfirmAsset,
                    displayCrypto = "0.0125 ETH",
                    displayUsd = "$32.10",
                    displayIrr = "۲٬۱۰۰٬۰۰۰ تومان",
                    walletName = "کیف پول من",
                    isAmountTooSmall = false,
                    primaryColor = MaterialTheme.colorScheme.tertiary,
                    secondaryColor = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Preview(name = "TransactionDetailCard - Dark")
@Composable
private fun TransactionDetailCardDarkPreview() {
    MegaWalletTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                TransactionDetailCard(
                    asset = sampleConfirmAsset,
                    displayCrypto = "0.0125 ETH",
                    displayUsd = "$32.10",
                    displayIrr = "۲٬۱۰۰٬۰۰۰ تومان",
                    walletName = "کیف پول من",
                    isAmountTooSmall = false,
                    primaryColor = MaterialTheme.colorScheme.tertiary,
                    secondaryColor = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** دادهٔ نمونه برای پیش‌نمایش‌های صفحهٔ تأیید. */
internal val sampleConfirmAsset = AssetItem(
    id = "ETH-SEPOLIA",
    networkId = "ethereum-sepolia",
    name = "Ethereum",
    faName = "اتریوم",
    symbol = "ETH",
    networkName = "on Sepolia",
    networkFaName = "اتریوم سپولیا",
    iconUrl = null,
    balance = "1.5 ETH",
    balanceUsdt = "$3,000.00",
    isNativeToken = true
)
