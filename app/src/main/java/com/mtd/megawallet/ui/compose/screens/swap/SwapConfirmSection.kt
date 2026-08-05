package com.mtd.megawallet.ui.compose.screens.swap

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.InterMedium
import com.mtd.common_ui.theme.IranSansBoldMedium
import com.mtd.common_ui.theme.IranSansLightLight
import com.mtd.core.utils.FiatConversion
import com.mtd.megawallet.viewmodel.swap.SwapPrepareState
import com.mtd.megawallet.viewmodel.swap.SwapUiState

/**
 * فازِ تأیید.
 *
 * چهار عددی که طبق سیاستِ محصول باید **قبل از** تأیید دیده شوند: مبلغِ پرداخت، مبلغِ دریافت،
 * **حداقلِ دریافتی** (تنها عددی که تضمین شده)، و کارمزدِ پلتفرم + لغزش. اعتبارِ استعلام هم کنارِ
 * عنوان شمارش معکوس می‌شود.
 */
@Composable
fun SwapConfirmSection(
    state: SwapUiState,
    walletName: String,
    intro: Float,
    quoteFraction: Float,
    quoteSecondsRemaining: Int,
    onSlippageSelected: (Int) -> Unit,
    onFeeLevelSelected: (String) -> Unit,
    payCoin: @Composable () -> Unit,
    receiveCoin: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val pay = state.payToken ?: return
    val receive = state.receiveToken ?: return
    val route = state.readyRoute

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            payCoin()
            Box(Modifier.offset(x = (-14).dp)) { receiveCoin() }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "تبدیل ${pay.option.symbol} به ${receive.symbol}",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 22.sp,
            fontFamily = IranSansBoldMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer {
                val a = segment(intro, 0f, 0.4f)
                alpha = a
                translationY = (1f - a) * 14f
            }
        )

        Spacer(Modifier.height(6.dp))

        SwapQuoteTimer(
            fraction = quoteFraction,
            secondsRemaining = quoteSecondsRemaining,
            modifier = Modifier.graphicsLayer { alpha = segment(intro, 0.1f, 0.45f) }
        )

        Spacer(Modifier.height(18.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.graphicsLayer { alpha = segment(intro, 0.2f, 0.6f) }
        ) {
            SwapDetailRow(
                label = "پرداخت ${pay.option.symbol}",
                value = SwapFormat.amount(state.amountRaw, pay.option.decimals),
                iconUrl = pay.option.iconUrl
            )
            SwapDetailRow(
                label = "دریافت ${receive.symbol}",
                value = route?.toAmount?.net
                    ?.let { SwapFormat.amount(it, receive.decimals) }
                    ?: FiatConversion.UNKNOWN_PLACEHOLDER,
                iconUrl = receive.iconUrl
            )
            SwapDetailRow(
                label = "شبکه",
                value = pay.option.networkName,
                iconUrl = pay.option.networkIconUrl
            )
            SwapDetailRow(label = "کیف پول", value = walletName, iconUrl = null)
        }

        Spacer(Modifier.height(14.dp))

        SwapExpandableDetails(
            state = state,
            onSlippageSelected = onSlippageSelected,
            modifier = Modifier.graphicsLayer { alpha = segment(intro, 0.3f, 0.7f) }
        )

        Spacer(Modifier.height(14.dp))

        SwapFeeRow(
            state = state,
            onFeeLevelSelected = onFeeLevelSelected,
            modifier = Modifier.graphicsLayer { alpha = segment(intro, 0.4f, 0.8f) }
        )

        val prepare = state.prepareState
        if (prepare is SwapPrepareState.Failed) {
            Spacer(Modifier.height(12.dp))
            SwapNotice(text = prepare.message, isError = true)
        }
    }
}

@Composable
private fun SwapDetailRow(
    label: String,
    value: String,
    iconUrl: String?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontFamily = IranSansLightLight,
            modifier = Modifier.weight(1f)
        )
        if (iconUrl != null) {
            SwapTokenLogo(iconUrl = iconUrl, contentDescription = null, size = 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 15.sp,
            fontFamily = InterMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/** «جزئیات بیشتر»: کارمزدِ پلتفرم، لغزش، حداقل دریافتی و ارائه‌دهندهٔ مسیر. */
@Composable
private fun SwapExpandableDetails(
    state: SwapUiState,
    onSlippageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val motion = LocalSwapMotion.current
    val receive = state.receiveToken

    Column(modifier) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = !expanded }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (expanded) "بستن جزئیات" else "مشاهدهٔ جزئیات",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontFamily = IranSansLightLight
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(motion.fade()) + expandVertically(motion.morph()),
            exit = fadeOut(motion.fade(SwapMotion.FADE_FAST)) +
                shrinkVertically(motion.morph(SwapMotion.MORPH_EXIT))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (receive != null) {
                    SwapDetailRow(
                        label = "حداقل دریافتی",
                        value = state.minimumReceivedRaw
                            ?.let { SwapFormat.amountWithSymbol(it, receive.decimals, receive.symbol) }
                            ?: FiatConversion.UNKNOWN_PLACEHOLDER,
                        iconUrl = null
                    )
                }
                // فقط وقتی کارمزد واقعاً برداشته شده. تا وقتی `collected=false` است سرور چیزی
                // کم نمی‌کند و `net == gross`؛ نشان‌دادنِ نرخ در آن حالت یعنی ادعای هزینه‌ای که
                // وجود ندارد.
                state.collectedPlatformFee?.let { fees ->
                    SwapDetailRow(
                        label = "کارمزد پلتفرم",
                        value = fees.platformBps
                            ?.let { SwapFormat.percentFromBps(it) }
                            ?: FiatConversion.UNKNOWN_PLACEHOLDER,
                        iconUrl = null
                    )
                }
                state.readyRoute?.provider?.let { provider ->
                    SwapDetailRow(label = "مسیر", value = provider, iconUrl = null)
                }
                SwapSlippageRow(
                    selectedBps = state.slippageBps,
                    onSelect = onSlippageSelected
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun SwapFeeRow(
    state: SwapUiState,
    onFeeLevelSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = state.fee.selected

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = state.fee.selectedFeeUsd
                    ?.let { SwapFormat.usd(it, state.fiatCurrency, state.usdToTomanRate) }
                    ?: selected?.feeAmountDisplay
                    ?: FiatConversion.UNKNOWN_PLACEHOLDER,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                fontFamily = InterMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "برآورد کارمزد شبکه",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontFamily = IranSansLightLight
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.fee.options.forEach { option ->
                val isSelected = option.level == selected?.level
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onFeeLevelSelected(option.level) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = option.level,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 12.sp,
                        fontFamily = IranSansLightLight
                    )
                }
            }
        }
    }
}

/** دکمهٔ تأیید + هشدارِ برگشت‌ناپذیری. غیرفعال بودنش همیشه دلیلِ نوشته‌شده دارد. */
@Composable
fun SwapConfirmCta(
    state: SwapUiState,
    intro: Float,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = state.canConfirm && state.prepareState !is SwapPrepareState.Loading

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "پیش از تأیید بررسی کنید. تراکنش پس از ارسال برگشت‌پذیر نیست.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontFamily = IranSansLightLight,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .graphicsLayer {
                    val a = segment(intro, 0.5f, 0.9f)
                    alpha = if (enabled) a else a * 0.5f
                    translationY = (1f - a) * 18f
                }
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.onBackground)
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onConfirm() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (state.prepareState is SwapPrepareState.Loading) {
                    "در حال آماده‌سازی…"
                } else {
                    "تأیید تبدیل"
                },
                color = MaterialTheme.colorScheme.background,
                fontSize = 16.sp,
                fontFamily = IranSansBoldMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
