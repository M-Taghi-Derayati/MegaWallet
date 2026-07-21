package com.mtd.megawallet.ui.compose.screens.send

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.InterMedium
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.common_ui.theme.IranSansRegularMedium
import com.mtd.common_ui.theme.MegaWalletTheme
import com.mtd.domain.model.FeeOption
import com.mtd.domain.model.gassless.GaslessPreviewState

data class SmartFeeInfo(
    val amount: String,
    val amountUsd: String,
    val amountIrr: String,
    val description: String
)

data class CreditInfo(
    val available: String,
    val availableIrr: String,
    val fee: String,
    val feeIrr: String
)

// ============================================
// Smart Fee Section
// ============================================

@Composable
internal fun SmartFeeSection(
    fee: SmartFeeInfo,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "✨", fontSize = 18.sp)
            Text(
                text = "ارسال هوشمند",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF22C55E).copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "پیشنهادی",
                    color = Color(0xFF4ADE80),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AnimatedContent(
            targetState = isLoading,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
            },
            label = "smartLoading"
        ) { loading ->
            if (loading) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoadingPlaceholder(width = 112.dp, height = 20.dp)
                    LoadingPlaceholder(width = 80.dp, height = 16.dp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = fee.amount,
                        color = Color(0xFFA78BFA),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = fee.amountUsd,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                    Text(
                        text = "≈ ${fee.amountIrr}",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = fee.description,
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// ============================================
// Credit Fee Section
// ============================================

@Composable
internal fun CreditFeeSection(
    info: CreditInfo,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "پرداخت اعتباری",
                color = Color.White,
                fontSize = 15.sp,
                fontFamily = IranSansRegularMedium ,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        AnimatedContent(
            targetState = isLoading,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
            },
            label = "creditLoading"
        ) { loading ->
            if (loading) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LoadingPlaceholder(width = 200.dp, height = 48.dp)
                    LoadingPlaceholder(width = 200.dp, height = 48.dp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Available Credit
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "اعتبار باقی‌مانده",
                            color = Color(0xFFFBBF24).copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = info.available,
                                color = Color(0xFFFBBF24),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "≈ ${info.availableIrr}",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Fee
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "هزینه تراکنش",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = info.fee,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "≈ ${info.feeIrr}",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================
// Loading Placeholder
// ============================================

@Composable
private fun LoadingPlaceholder(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = alpha))
    )
}

private fun compactCryptoZeros(text: String): String {
    val parts = text.split(" ")
    if (parts.isEmpty()) return text
    var amountStr = parts[0]
    val symbol = parts.drop(1).joinToString(" ")

    // Only compact if there are 4 or more zeros after the decimal
    val regex = Regex("^(0\\.0{4,})(\\d+)$")
    val match = regex.find(amountStr)

    if (match != null) {
        val zerosPart = match.groupValues[1]
        val nonZerosPart = match.groupValues[2]

        val zeroCount = zerosPart.length - 2
        val subscriptZeros = zeroCount.toString().map { char ->
            when (char) {
                '0' -> '₀'; '1' -> '₁'; '2' -> '₂'; '3' -> '₃'; '4' -> '₄'
                '5' -> '₅'; '6' -> '₆'; '7' -> '₇'; '8' -> '₈'; '9' -> '₉'
                else -> char
            }
        }.joinToString("")

        // Take at most 4 digits from the non-zero part to prevent long overlapping fractions
        val trimmedNonZeros = nonZerosPart.take(4)
        amountStr = "0.0$subscriptZeros$trimmedNonZeros"
    }

    return if (symbol.isNotEmpty()) "$amountStr $symbol" else amountStr
}

@Composable
internal fun FeeSection(
    feeOptions: List<FeeOption>,
    selectedIndex: Int,
    onIndexSelected: (Int) -> Unit,
    useGasless: Boolean,
    gaslessPreviewState: GaslessPreviewState,
    hasInsufficientBalance: Boolean = false,
    isAmountTooSmall: Boolean = false,
    isLoadingFees: Boolean = false,
    primaryColor: Color = MaterialTheme.colorScheme.tertiary,
    secondaryColor: Color = MaterialTheme.colorScheme.onTertiary
) {
    val selectedOption = if (!useGasless && feeOptions.isNotEmpty()) feeOptions.getOrNull(selectedIndex) ?: feeOptions.first() else null
    val hasError = hasInsufficientBalance || isAmountTooSmall
    val gaslessPreview = gaslessPreviewState as? GaslessPreviewState.Ready
    val gaslessError = gaslessPreviewState as? GaslessPreviewState.Error
    val gaslessAmount = remember(gaslessPreview) {
        gaslessPreview?.gaslessPolicy?.let { policy ->
            listOfNotNull(
                policy.displayAmount?.takeIf { it.isNotBlank() },
                policy.displayToken?.takeIf { token ->
                    token.isNotBlank() && !(policy.displayAmount ?: "").contains(token)
                }
            ).joinToString(" ").ifBlank { null }
        }
    }
    val policyMessage = remember(gaslessPreview, gaslessError) {
        when {
            !gaslessPreview?.smartFee?.reasonFa.isNullOrBlank() -> gaslessPreview?.smartFee?.reasonFa
            gaslessPreview?.needsApprove == true &&
                !gaslessPreview.sponsorPolicy?.reasonFa.isNullOrBlank() -> gaslessPreview.sponsorPolicy?.reasonFa
            !gaslessPreview?.gaslessPolicy?.reasonFa.isNullOrBlank() -> gaslessPreview?.gaslessPolicy?.reasonFa
            else -> gaslessError?.message
        }
    }

    val isFetchingInitial = if (useGasless) {
        gaslessPreviewState is GaslessPreviewState.Loading
    } else {
        isLoadingFees && selectedOption == null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (hasError && !useGasless) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!useGasless && feeOptions.isNotEmpty()) {
                    onIndexSelected((selectedIndex + 1) % feeOptions.size)
                }
            }
            .padding(vertical = 6.dp, horizontal = if (hasError) 8.dp else 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // LEFT SIDE
        Column(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = isFetchingInitial,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "loadingFeeValues"
            ) { loading ->
                if (loading) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.height(20.dp).width(120.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.6f)))
                        Box(modifier = Modifier.height(16.dp).width(70.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.4f)))
                        Box(modifier = Modifier.height(14.dp).width(90.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f)))
                    }
                } else {
                    AnimatedContent(
                        targetState = useGasless to selectedOption,
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                        label = "FeeAmount"
                    ) { (gasless, option) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            // 1. Native Crypto Amount
                            Text(
                                text = if (gasless) compactCryptoZeros(gaslessAmount ?: "...") else compactCryptoZeros(option?.feeAmountDisplay ?: "..."),
                                color = if (gasless) Color(0xFF9C8FFF) else primaryColor,
                                fontFamily = IranSansRegular,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                          if (option != null) {
                                // 2. USD Equivalent
                                Text(
                                    text = option.feeAmountUsdDisplay,
                                    color = secondaryColor.copy(alpha = 1f),
                                    fontFamily = InterMedium,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // 3. IRR Equivalent
                                Text(
                                    text = "≈ ${option.feeAmountIrrDisplay}",
                                    color = secondaryColor.copy(alpha = 0.8f),
                                    fontFamily = IranSansRegular,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            AnimatedContent(targetState = hasError and !useGasless, label = "error") { isErr ->
                if (isErr) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isAmountTooSmall) "مقدار ارسالی کمتر از کارمزد است" else "موجودی کافی نیست",
                            color = MaterialTheme.colorScheme.error,
                            fontFamily = IranSansRegular,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(13.dp))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (useGasless) "هزینه نهایی گس‌لس" else "تخمین کارمزد",
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontFamily = IranSansRegular,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // RIGHT SIDE
        AnimatedVisibility(visible = !useGasless, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    AnimatedContent(targetState = isFetchingInitial, label = "LevelBlock") { loading ->
                        if (loading) {
                            Box(modifier = Modifier.height(18.dp).width(50.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.6f)))
                        } else {
                            AnimatedContent(targetState = selectedOption?.level, label = "Level") { level ->
                                Text(
                                    text = level ?: "نامشخص",
                                    color = primaryColor,
                                    fontFamily = IranSansRegular,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    AnimatedContent(targetState = isFetchingInitial, label = "TimeBlock") { loading ->
                         if (loading) {
                             Box(modifier = Modifier.height(14.dp).width(35.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.4f)))
                         } else {
                            AnimatedContent(targetState = selectedOption?.estimatedTime, label = "Time") { time ->
                                Text(
                                    text = time ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    fontFamily = IranSansRegular,
                                    fontSize = 12.sp
                                )
                            }
                         }
                    }
                }

                Spacer(Modifier.width(10.dp))

                VerticalFeeIndicator(selectedIndex = selectedIndex, totalOptions = if (feeOptions.isEmpty()) 1 else feeOptions.size, isLoading = isFetchingInitial)
            }
        }
    }
}


@Composable
private fun PolicyMetaChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = IranSansRegular,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class PolicyVisualStyle(
    val label: String,
    val icon: ImageVector,
    val container: Color
)


@Composable
private fun PolicyBadge(
    text: String,
    background: Color,
    content: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = content,
            fontFamily = IranSansRegular,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun VerticalFeeIndicator(selectedIndex: Int, totalOptions: Int, isLoading: Boolean = false) {
    val dotsCount = maxOf(3, totalOptions)
    Box(
        modifier = Modifier
            .width(26.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (i in (dotsCount - 1) downTo 0) {
                val isSelected = (i == selectedIndex) || (selectedIndex > 2 && i == 2)

                val size by animateDpAsState(if (isSelected) 18.dp else 5.dp, label = "dotSize")
                val color = when (i) {
                    2 -> Color(0xFFFF7043)
                    1 -> Color(0xFFFFCA28)
                    0 -> Color(0xFF29B6F6)
                    else -> Color.Gray
                }

                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSelected || isLoading,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(300))
                    ) {
                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) // just keep dot colored without icon
                        } else {
                            Icon(
                                imageVector = when(i){
                                    2 -> Icons.Default.Bolt
                                    1 -> Icons.Default.Check
                                    else -> Icons.Default.Schedule
                                },
                                contentDescription = null,
                                tint = Color.Black.copy(alpha = 0.7f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================
// Previews
// ============================================

@Preview(name = "SmartFeeSection - Dark")
@Composable
private fun SmartFeeSectionDarkPreview() {
    MegaWalletTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                SmartFeeSection(
                    fee = SmartFeeInfo("0.00012 ETH", "$0.32", "۲۰٬۰۰۰ تومان", "کارمزد بهینه برای ارسال سریع"),
                    isLoading = false
                )
            }
        }
    }
}

@Preview(name = "CreditFeeSection - Dark")
@Composable
private fun CreditFeeSectionDarkPreview() {
    MegaWalletTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                CreditFeeSection(
                    info = CreditInfo("۵۰٬۰۰۰ تومان", "۵۰٬۰۰۰ تومان", "۱٬۲۰۰ تومان", "۱٬۲۰۰ تومان"),
                    isLoading = false
                )
            }
        }
    }
}

@Preview(name = "FeeSection - Light")
@Composable
private fun FeeSectionLightPreview() {
    MegaWalletTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                FeeSection(
                    feeOptions = sampleFeeOptions,
                    selectedIndex = 1,
                    onIndexSelected = {},
                    useGasless = false,
                    gaslessPreviewState = GaslessPreviewState.Idle
                )
            }
        }
    }
}

@Preview(name = "FeeSection - Dark")
@Composable
private fun FeeSectionDarkPreview() {
    MegaWalletTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                FeeSection(
                    feeOptions = sampleFeeOptions,
                    selectedIndex = 1,
                    onIndexSelected = {},
                    useGasless = false,
                    gaslessPreviewState = GaslessPreviewState.Idle
                )
            }
        }
    }
}

private val sampleFeeOptions = listOf(
    FeeOption(
        level = "کند",
        feeAmountDisplay = "0.00004 ETH",
        feeAmountUsdDisplay = "$0.10",
        feeAmountIrrDisplay = "۱۰٬۰۰۰ تومان",
        estimatedTime = "~۵ دقیقه",
        feeInSmallestUnit = java.math.BigDecimal("40000000000000")
    ),
    FeeOption(
        level = "عادی",
        feeAmountDisplay = "0.00007 ETH",
        feeAmountUsdDisplay = "$0.18",
        feeAmountIrrDisplay = "۱۸٬۰۰۰ تومان",
        estimatedTime = "~۲ دقیقه",
        feeInSmallestUnit = java.math.BigDecimal("70000000000000")
    ),
    FeeOption(
        level = "سریع",
        feeAmountDisplay = "0.00012 ETH",
        feeAmountUsdDisplay = "$0.31",
        feeAmountIrrDisplay = "۳۱٬۰۰۰ تومان",
        estimatedTime = "~۳۰ ثانیه",
        feeInSmallestUnit = java.math.BigDecimal("120000000000000")
    )
)
