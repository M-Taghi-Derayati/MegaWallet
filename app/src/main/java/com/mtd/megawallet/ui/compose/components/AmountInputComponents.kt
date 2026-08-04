package com.mtd.megawallet.ui.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.InterBold
import com.mtd.common_ui.theme.InterMedium
import com.mtd.common_ui.theme.InterRegularMedium
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.common_ui.theme.MegaWalletTheme
import com.mtd.core.utils.BalanceFormatter
import com.mtd.core.utils.FiatConversion
import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.CurrencyRate
import com.mtd.domain.model.FiatCurrency
import com.mtd.megawallet.ui.compose.animations.constants.WalletScreenConstants
import com.mtd.megawallet.ui.compose.screens.send.sampleConfirmAsset
import java.math.BigDecimal
import java.math.RoundingMode

/** کلیدِ حذف که [NumericKeypad] منتشر می‌کند. */
internal const val KEYPAD_DELETE_KEY = "del"

/**
 * رشتهٔ تایپ‌شده را به عددی قابلِ محاسبه تبدیل می‌کند.
 *
 * حالت‌های نیمه‌تمامِ تایپ («.» تنها، یا عددی که به نقطه ختم شده) عدد نیستند ولی باید بدونِ
 * پرش به صفر ادامهٔ محاسبه بدهند.
 */
internal fun normalizeAmountForCalculation(amountText: String): String = when {
    amountText.isBlank() || amountText == "0" -> "0"
    amountText == "." -> "0"
    amountText.endsWith(".") -> amountText + "0"
    else -> amountText
}

/**
 * نمایشِ مبلغِ در حالِ تایپ: شمارندهٔ اودومتری، معادلِ آن در واحدِ دیگر، و هشدارِ کمبودِ موجودی.
 *
 * ضربه روی کلِ بخش واحدِ ورودی را عوض می‌کند (ارز ⇄ فیات).
 */
@Composable
internal fun AmountDisplaySection(
    asset: AssetItem,
    amount: String,
    calculationAmount: String,
    isFiatMode: Boolean,
    fiatCurrency: FiatCurrency,
    usdToIrrRate: CurrencyRate?,
    isOverBalance: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val price = asset.priceUsdRaw
    // TASK-56 — the "other side" of the amount box, in the selected currency rather than always USD.
    val equivalent = remember(calculationAmount, isFiatMode, fiatCurrency, usdToIrrRate) {
        try {
            val bdVal = BigDecimal(calculationAmount)
            if (isFiatMode) {
                val usdVal = when (fiatCurrency) {
                    FiatCurrency.USD -> bdVal
                    FiatCurrency.TOMAN -> FiatConversion.tomanToUsd(bdVal, usdToIrrRate)
                }
                if (usdVal == null || price <= BigDecimal.ZERO) {
                    FiatConversion.UNKNOWN_PLACEHOLDER
                } else {
                    val cryptoVal = usdVal.divide(price, 8, RoundingMode.DOWN)
                    "${BalanceFormatter.formatBalance(cryptoVal, asset.decimals)} ${asset.symbol}"
                }
            } else {
                BalanceFormatter.formatFiatValue(
                    usdAmount = bdVal.multiply(price),
                    currency = fiatCurrency,
                    rate = usdToIrrRate
                )
            }
        } catch (e: Exception) { FiatConversion.UNKNOWN_PLACEHOLDER }
    }

    val amountColor = if (isOverBalance) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    val subColor = if (isOverBalance) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onTertiary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Main Amount Row with per-digit Rolling Counter (odometer) ---
        // شمارندهٔ اودومترِ مشترک: رشتهٔ تایپ‌شده را عیناً نشان می‌دهد و فقط ارقامِ تغییرکرده را در فازِ draw
        // می‌غلتاند (بدونِ recomposition per-frame). همان موتوری که موجودیِ کل استفاده می‌کند.
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.Center
        ) {
            RollingCounter(
                text = amount,
                style = TextStyle(
                    fontSize = 52.sp,
                    color = amountColor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterBold
                )
            )

            AnimatedVisibility(
                visible = isFiatMode,
                enter = fadeIn(tween(180)) + slideInVertically { it / 2 },
                exit = fadeOut(tween(150)) + slideOutVertically { it / 2 }
            ) {
                Text(
                    text = BalanceFormatter.fiatSymbol(fiatCurrency),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = WalletScreenConstants.CURRENCY_SYMBOL_FONT_SIZE,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = InterRegularMedium
                    ),
                    modifier = Modifier.padding(top = WalletScreenConstants.CURRENCY_SYMBOL_PADDING_TOP)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Equivalent / Swap Row ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isFiatMode) {
                AssetIcon(
                    iconUrl = asset.iconUrl,
                    symbol = asset.symbol,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = equivalent,
                color = subColor,
                fontSize = 16.sp,
                fontFamily = InterMedium
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.SwapVert,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = subColor
            )
        }

        // --- Insufficient Balance Error ---
        AnimatedVisibility(
            visible = isOverBalance,
            enter = fadeIn(tween(200)) + slideInVertically { -it / 2 },
            exit = fadeOut(tween(150)) + slideOutVertically { -it / 2 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "موجودی کافی نیست",
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = IranSansRegular,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
internal fun NumericKeypad(
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = listOf(
        listOf("3", "2", "1"),
        listOf("6", "5", "4"),
        listOf("9", "8", "7"),
        listOf(KEYPAD_DELETE_KEY, "0", ".")
    )

    Column(modifier = modifier.fillMaxWidth()) {
        keys.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clickable(indication = null, interactionSource = null){onKeyPress(key)},
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == KEYPAD_DELETE_KEY) {
                            Icon(
                                imageVector = Icons.Default.Backspace,
                                contentDescription = "Delete",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        } else {
                            Text(
                                text = key,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontSize = 24.sp,
                                fontFamily = InterBold
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ============================================
// Previews
// ============================================

@Preview(name = "NumericKeypad - Dark")
@Composable
private fun NumericKeypadDarkPreview() {
    MegaWalletTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                NumericKeypad(onKeyPress = {})
            }
        }
    }
}

@Preview(name = "AmountDisplaySection - Dark")
@Composable
private fun AmountDisplaySectionDarkPreview() {
    MegaWalletTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                AmountDisplaySection(
                    asset = sampleConfirmAsset,
                    amount = "0.25",
                    calculationAmount = "0.25",
                    isFiatMode = false,
                    fiatCurrency = FiatCurrency.USD,
                    usdToIrrRate = null,
                    isOverBalance = false,
                    onToggle = {}
                )
            }
        }
    }
}
