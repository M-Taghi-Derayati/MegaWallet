package com.mtd.megawallet.ui.compose.screens.send

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import com.mtd.common_ui.R
import com.mtd.core.utils.BalanceFormatter
import com.mtd.domain.model.AssetItem
import com.mtd.megawallet.ui.compose.animations.constants.WalletScreenConstants
import com.mtd.megawallet.ui.compose.components.PrimaryButton
import com.mtd.megawallet.ui.compose.screens.wallet.getLocalIconResId
import com.mtd.megawallet.ui.compose.screens.wallet.getNetworkIconResId
import com.mtd.megawallet.ui.compose.screens.wallet.getPlaceholderIconResId
import java.math.BigDecimal
import java.math.RoundingMode
import com.mtd.common_ui.theme.InterBold
import com.mtd.common_ui.theme.InterMedium
import com.mtd.common_ui.theme.InterRegularMedium
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.common_ui.theme.MegaWalletTheme

/**
 * فاز ورود مبلغ: نمایش مبلغ بزرگ، کارت اطلاعات دارایی، کیبورد عددی و دکمهٔ ادامه.
 */
@Composable
internal fun AmountInputPhase(
    asset: AssetItem,
    amountText: String,
    isUsdMode: Boolean,
    isExiting: Boolean,
    hasValidAddress: Boolean,
    onAmountChanged: (String) -> Unit,
    onToggleMode: () -> Unit,
    onUseMax: () -> Unit,
    onContinue: () -> Unit
) {
    // Determine if entered amount exceeds available balance
    val calculationAmount = when {
        amountText.isBlank() || amountText == "0" -> "0"
        amountText == "." -> "0"
        amountText.endsWith(".") -> amountText + "0"
        else -> amountText
    }
    val isOverBalance = remember(calculationAmount, isUsdMode, asset) {
        try {
            val bdVal = BigDecimal(calculationAmount)
            if (bdVal <= BigDecimal.ZERO) false
            else {
                // Determine the exact max string that is displayed in UI based on mode
                val maxStr = if (isUsdMode) asset.balanceUsdt.replace("$", "").replace(",", "").trim()
                             else asset.balance.replace(",", "").trim()

                // If they typed exactly what is max formatted, trust it's fine!
                if (calculationAmount == maxStr) false
                else if (isUsdMode) bdVal > asset.balanceRaw.multiply(asset.priceUsdRaw)
                else bdVal > asset.balanceRaw
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
                    isUsdMode = isUsdMode,
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
                            "del" -> if (amountText.length <= 1) "0" else amountText.dropLast(1)
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
private fun AmountDisplaySection(
    asset: AssetItem,
    amount: String,
    calculationAmount: String,
    isUsdMode: Boolean,
    isOverBalance: Boolean,
    onToggle: () -> Unit
) {
    val price = asset.priceUsdRaw
    val equivalent = remember(calculationAmount, isUsdMode) {
        try {
            val bdVal = BigDecimal(calculationAmount)
            if (isUsdMode) {
                val cryptoVal = bdVal.divide(price, 8, RoundingMode.HALF_UP)
                "${BalanceFormatter.formatBalance(cryptoVal, asset.decimals)} ${asset.symbol}"
            } else {
                val usdVal = bdVal.multiply(price)
                "$${BalanceFormatter.formatUsdValue(usdVal, false)}"
            }
        } catch (e: Exception) { "—" }
    }

    val amountColor = if (isOverBalance) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    val subColor = if (isOverBalance) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onTertiary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Main Amount Row with Slot-Machine Digit Animation ---
        // AnimatedContent keyed on the raw `amount` string:
        // Adding digits  -> new number rolls UP from below
        // Removing digits -> new number rolls DOWN from above
        AnimatedContent(
            targetState = amount,
            transitionSpec = {
                val isGrowing = targetState.length >= initialState.length
                if (isGrowing) {
                    (
                        slideInVertically(
                            animationSpec = spring(dampingRatio = 0.62f, stiffness = 900f),
                            initialOffsetY = { it / 2 }
                        ) + fadeIn(tween(110))
                    ).togetherWith(
                        slideOutVertically(
                            animationSpec = tween(90, easing = FastOutSlowInEasing),
                            targetOffsetY = { -(it / 3) }
                        ) + fadeOut(tween(80))
                    )
                } else {
                    (
                        slideInVertically(
                            animationSpec = spring(dampingRatio = 0.62f, stiffness = 900f),
                            initialOffsetY = { -(it / 2) }
                        ) + fadeIn(tween(110))
                    ).togetherWith(
                        slideOutVertically(
                            animationSpec = tween(90, easing = FastOutSlowInEasing),
                            targetOffsetY = { it / 3 }
                        ) + fadeOut(tween(80))
                    )
                }
            },
            label = "DigitChange",
            contentAlignment = Alignment.Center
        ) { displayAmount ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayAmount,
                    style = TextStyle(
                        fontSize = 52.sp,
                        color = amountColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterBold
                    ),
                    maxLines = 1,
                    softWrap = false
                )

                AnimatedVisibility(
                    visible = isUsdMode,
                    enter = fadeIn(tween(180)) + slideInVertically { it / 2 },
                    exit = fadeOut(tween(150)) + slideOutVertically { it / 2 }
                ) {
                    Text(
                        text = "$",
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
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Equivalent / Swap Row ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isUsdMode) {
                val iconRes = getLocalIconResId(asset.symbol).let { if (it == 0) R.drawable.ic_wallet else it }
                Image(
                    painter = painterResource(id = iconRes),
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
private fun AssetInfoCard(
    asset: AssetItem,
    onUseMax: () -> Unit
) {
    val localIconResId = remember(asset.symbol) {
        getLocalIconResId(asset.symbol)
    }
    val localIconNetworkResId = remember(asset.networkId) {
        getNetworkIconResId(asset.networkId)
    }
    val imageLoader = LocalContext.current.imageLoader
    val placeholderResId = remember { getPlaceholderIconResId() }
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
                if (localIconResId != 0) {
                    Box(
                        modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = localIconResId),
                            contentDescription = "${asset.name} icon",
                            modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE),
                            contentScale = ContentScale.Fit,
                            colorFilter = null
                        )
                    }
                }
                else {
                    Box(
                        modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = asset.iconUrl,
                            contentDescription = "${asset.name} icon",
                            modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE),
                            contentScale = ContentScale.Fit,
                            placeholder = painterResource(id = placeholderResId),
                            error = painterResource(id = placeholderResId),
                            fallback = painterResource(id = placeholderResId),
                            imageLoader = imageLoader
                        )
                    }
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

                        Image(
                            painter = painterResource(id = localIconNetworkResId),
                            contentDescription = "${asset.networkName} network icon",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(WalletScreenConstants.ASSET_ICON_NETWORK_PADDING),
                            contentScale = ContentScale.Fit,
                            colorFilter = null
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

@Composable
private fun NumericKeypad(onKeyPress: (String) -> Unit) {
    val keys = listOf(
        listOf("3", "2", "1"),
        listOf("6", "5", "4"),
        listOf("9", "8", "7"),
        listOf("del", "0", ".")
    )

    Column(modifier = Modifier.fillMaxWidth()) {
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
                        if (key == "del") {
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
                    isUsdMode = false,
                    isOverBalance = false,
                    onToggle = {}
                )
            }
        }
    }
}
