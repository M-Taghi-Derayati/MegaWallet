package com.mtd.megawallet.ui.compose.screens.swap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.mtd.domain.model.swap.SwapExecutionOutcome
import com.mtd.domain.model.swap.SwapExecutionProgress
import com.mtd.domain.model.swap.SwapLegKind
import com.mtd.domain.model.swap.SwapLegStatus
import com.mtd.megawallet.viewmodel.swap.SwapUiState

/**
 * فازِ اجرا: کدام پا در پرواز است.
 *
 * تأییدِ allowance و خودِ تبدیل دو تراکنشِ جدا روی زنجیره‌اند و ترتیبی اجرا می‌شوند؛ کاربر باید
 * ببیند کدام‌یک در جریان است، وگرنه یک انتظارِ چنددقیقه‌ای شبیهِ هنگ کردن به نظر می‌رسد.
 */
@Composable
fun SwapExecutionSection(
    state: SwapUiState,
    progress: SwapExecutionProgress,
    modifier: Modifier = Modifier
) {
    val pay = state.payToken
    val receive = state.receiveToken

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "در حال انجام تبدیل",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontFamily = IranSansBoldMedium,
            fontWeight = FontWeight.Bold
        )

        if (pay != null && receive != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${pay.option.symbol} ← ${receive.symbol}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontFamily = InterMedium
            )
        }

        Spacer(Modifier.height(26.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            progress.legs.forEachIndexed { index, leg ->
                SwapLegRow(
                    title = when (leg.kind) {
                        SwapLegKind.APPROVE -> "تأیید دسترسی به ${pay?.option?.symbol ?: "توکن"}"
                        SwapLegKind.SWAP -> "انجام تبدیل"
                    },
                    subtitle = when (leg.status) {
                        SwapLegStatus.PENDING -> "در نوبت"
                        SwapLegStatus.SUBMITTING -> "در حال امضا و ارسال…"
                        SwapLegStatus.CONFIRMING -> "ارسال شد؛ منتظر تأیید شبکه…"
                        SwapLegStatus.CONFIRMED -> "تأیید شد"
                        SwapLegStatus.UNCONFIRMED -> "هنوز تأیید نشده"
                        SwapLegStatus.FAILED -> "ناموفق"
                    },
                    status = leg.status,
                    isActive = progress.activeIndex == index
                )
            }
        }
    }
}

@Composable
private fun SwapLegRow(
    title: String,
    subtitle: String,
    status: SwapLegStatus,
    isActive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SwapLegIndicator(status = status, isActive = isActive)

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontFamily = IranSansBoldMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = when (status) {
                    SwapLegStatus.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 12.sp,
                fontFamily = IranSansLightLight
            )
        }
    }
}

@Composable
private fun SwapLegIndicator(status: SwapLegStatus, isActive: Boolean) {
    val motion = LocalSwapMotion.current
    val color = when (status) {
        SwapLegStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
        SwapLegStatus.FAILED -> MaterialTheme.colorScheme.error
        SwapLegStatus.UNCONFIRMED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
        when (status) {
            SwapLegStatus.CONFIRMED -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )

            SwapLegStatus.FAILED -> Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )

            SwapLegStatus.UNCONFIRMED -> Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )

            else -> {
                // نبضِ پای در جریان. transition بی‌قید ساخته می‌شود تا فراخوانیِ composable شرطی
                // نشود؛ با انیمیشنِ خاموش فقط مقدارِ ثابت خوانده می‌شود.
                val transition = rememberInfiniteTransition(label = "swapLegPulse")
                val pulse by transition.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(720, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "swapLegPulseAlpha"
                )

                val alpha = when {
                    !isActive -> 0.35f
                    motion.animated -> pulse
                    else -> 1f
                }

                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = alpha))
                )
            }
        }
    }
}

/**
 * نتیجه.
 *
 * سه پایانِ متمایز — موفق، «ارسال شد ولی تأیید نشد»، و ناموفق. حالتِ میانی عمداً «ناموفق» نمایش
 * داده نمی‌شود: تراکنش روی زنجیره هست و ممکن است بعداً تأیید شود.
 */
@Composable
fun SwapResultSection(
    state: SwapUiState,
    outcome: SwapExecutionOutcome,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = LocalSwapMotion.current
    val pop = remember { Animatable(if (motion.animated) 0.7f else 1f) }

    LaunchedEffect(outcome) {
        if (motion.animated) {
            pop.snapTo(0.7f)
            pop.animateTo(1f, motion.emphasis())
        }
    }

    /**
     * ⚠️ برای پل، «تأیید شد» فقط دربارهٔ **پای مبدأ** است. سرور پای مقصد را دنبال نمی‌کند، پس
     * تیکِ سبزِ «انجام شد» ادعایی است که هیچ‌کس تأییدش نکرده — و کاربر را می‌فرستد سراغِ کیف‌پولی
     * که هنوز خالی است.
     */
    val bridgePending = state.isBridgeSettlementPending
    val isSuccess = outcome is SwapExecutionOutcome.Completed && !bridgePending
    val accent = when {
        bridgePending -> MaterialTheme.colorScheme.onSurfaceVariant
        outcome is SwapExecutionOutcome.Completed -> MaterialTheme.colorScheme.primary
        outcome is SwapExecutionOutcome.Stalled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .clip(CircleShape)
                .border(2.dp, accent.copy(alpha = 0.35f), CircleShape)
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    bridgePending -> Icons.Default.Warning
                    outcome is SwapExecutionOutcome.Completed -> Icons.Default.Check
                    outcome is SwapExecutionOutcome.Stalled -> Icons.Default.Warning
                    else -> Icons.Default.Close
                },
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = when {
                bridgePending -> "در حال انتقال بین شبکه‌ها"
                outcome is SwapExecutionOutcome.Completed -> "تبدیل انجام شد"
                outcome is SwapExecutionOutcome.Stalled -> "ارسال شد، در انتظار تأیید"
                else -> "تبدیل ناموفق بود"
            },
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontFamily = IranSansBoldMedium,
            fontWeight = FontWeight.Bold
        )

        val receive = state.receiveToken
        if (isSuccess && receive != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "حداقل دریافتی شما: " + (
                    state.minimumReceivedRaw
                        ?.let { SwapFormat.amountWithSymbol(it, receive.decimals, receive.symbol) }
                        ?: ""
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontFamily = IranSansLightLight
            )
        }

        state.executionMessage?.let { message ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontFamily = IranSansLightLight
            )
        }

        Spacer(Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.onBackground)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDone() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "بستن",
                color = MaterialTheme.colorScheme.background,
                fontSize = 16.sp,
                fontFamily = IranSansBoldMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
