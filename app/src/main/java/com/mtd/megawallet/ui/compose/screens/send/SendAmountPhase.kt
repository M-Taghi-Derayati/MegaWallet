package com.mtd.megawallet.ui.compose.screens.send

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.R
import com.mtd.domain.model.AssetItem
import com.mtd.megawallet.ui.compose.animations.constants.WalletScreenConstants
import com.mtd.megawallet.ui.compose.components.AmountDisplaySection
import com.mtd.megawallet.ui.compose.components.KEYPAD_DELETE_KEY
import com.mtd.megawallet.ui.compose.components.NumericKeypad
import com.mtd.megawallet.ui.compose.components.PrimaryButton
import com.mtd.megawallet.ui.compose.components.normalizeAmountForCalculation
import com.mtd.common_ui.theme.AssetIcon
import com.mtd.common_ui.theme.NetworkIcon
import java.math.BigDecimal
import com.mtd.common_ui.theme.InterMedium
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.common_ui.theme.MegaWalletTheme
import com.mtd.core.utils.FiatConversion
import com.mtd.domain.model.CurrencyRate
import com.mtd.domain.model.FiatCurrency

/**
 * فاز ورود مبلغ: نمایش مبلغ بزرگ، کارت اطلاعات دارایی، کیبورد عددی و دکمهٔ ادامه.
 */
@Composable
internal fun AmountInputPhase(
    asset: AssetItem,
    amountText: String,
    isFiatMode: Boolean,
    fiatCurrency: FiatCurrency,
    usdToIrrRate: CurrencyRate?,
    isMaxAmount: Boolean,
    isExiting: Boolean,
    hasValidAddress: Boolean,
    onAmountChanged: (String) -> Unit,
    onToggleMode: () -> Unit,
    onUseMax: () -> Unit,
    onContinue: () -> Unit
) {
    // Determine if entered amount exceeds available balance
    val calculationAmount = normalizeAmountForCalculation(amountText)
    // TASK-56 — the over-balance check is done in the unit the user is TYPING in.
    //
    // It used to compare a typed fiat amount against `balanceRaw * priceUsdRaw`, i.e. against the
    // balance in USD. With تومان selected that compared تومان to dollars and flagged almost every
    // valid amount as over-balance. Both sides are now converted to the same unit first.
    val isOverBalance = remember(calculationAmount, isFiatMode, fiatCurrency, usdToIrrRate, isMaxAmount, asset) {
        try {
            val bdVal = BigDecimal(calculationAmount)
            // MAX is the balance by construction. Comparing its *rounded display text* back against the
            // balance is what used to need a string-equality escape hatch here.
            if (isMaxAmount) false
            else if (bdVal <= BigDecimal.ZERO) false
            else if (!isFiatMode) bdVal > asset.balanceRaw
            else {
                val balanceUsd = asset.balanceRaw.multiply(asset.priceUsdRaw)
                val balanceInFiat = when (fiatCurrency) {
                    FiatCurrency.USD -> balanceUsd
                    FiatCurrency.TOMAN -> FiatConversion.usdToToman(balanceUsd, usdToIrrRate)
                }
                // Rate unknown ⇒ we cannot judge; do not paint a valid amount red.
                if (balanceInFiat == null) false else bdVal > balanceInFiat
            }
        } catch (e: Exception) { false }
    }
    val canContinue = !isOverBalance && amountText != "0" && amountText.isNotBlank() && hasValidAddress

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Amount Display (Flexible Space)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = !isExiting,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 3 }
            ) {
                AmountDisplaySection(
                    asset = asset,
                    amount = amountText,
                    calculationAmount = calculationAmount,
                    isFiatMode = isFiatMode,
                    fiatCurrency = fiatCurrency,
                    usdToIrrRate = usdToIrrRate,
                    isOverBalance = isOverBalance,
                    onToggle = onToggleMode
                )
            }
        }

        // Bottom Section (Fixed Space)
        AnimatedVisibility(
            visible = !isExiting,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 3 }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AssetInfoCard(
                    asset = asset,
                    onUseMax = onUseMax
                )

                Spacer(modifier = Modifier.height(24.dp))

                NumericKeypad(
                    onKeyPress = { key ->
                        val newAmount = when (key) {
                            KEYPAD_DELETE_KEY -> if (amountText.length <= 1) "0" else amountText.dropLast(1)
                            "." -> if (amountText.contains(".")) amountText else if (amountText == "0") "0." else "$amountText."
                            else -> if (amountText == "0") key else amountText + key
                        }
                        onAmountChanged(newAmount)
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))



                PrimaryButton("ادامه", onContinue,canContinue,false, Modifier)

            }
        }
    }
}

@Composable
private fun AssetInfoCard(
    asset: AssetItem,
    onUseMax: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_SIZE)
            ) {
                // آیکون اصلی ارز
                Box(
                    modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE),
                    contentAlignment = Alignment.Center
                ) {
                    AssetIcon(
                        iconUrl = asset.iconUrl,
                        symbol = asset.symbol,
                        contentDescription = "${asset.name} icon",
                        modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE)
                    )
                }

                // بج شبکه (پایین سمت راست)
                if (asset.networkName.isNotEmpty()) {
                    val isDark = isSystemInDarkTheme()
                    Box(
                        modifier = Modifier
                            .size(WalletScreenConstants.ASSET_ICON_NETWORK_SIZE_LARGE)
                            .align(Alignment.BottomEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pls),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            tint = if (isDark) Color.Black else Color.White
                        )

                        NetworkIcon(
                            iconUrl = asset.networkIconUrl,
                            contentDescription = "${asset.networkName} network icon",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(WalletScreenConstants.ASSET_ICON_NETWORK_PADDING)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = asset.faName?:"",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 16.sp,
                    fontFamily = IranSansRegular
                )
                Text(
                    text = "${asset.balance} ${asset.symbol}",
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontSize = 13.sp,
                    fontFamily = InterMedium
                )
            }

            Surface(
                onClick = onUseMax,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "حداکثر",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 14.sp,
                    fontFamily = IranSansRegular
                )
            }
        }
    }
}

// ============================================
// Previews
// ============================================

@Preview(name = "AssetInfoCard - Light")
@Composable
private fun AssetInfoCardLightPreview() {
    MegaWalletTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                AssetInfoCard(asset = sampleConfirmAsset, onUseMax = {})
            }
        }
    }
}
