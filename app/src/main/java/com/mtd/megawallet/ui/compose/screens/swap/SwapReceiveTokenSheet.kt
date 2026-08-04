package com.mtd.megawallet.ui.compose.screens.swap

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import com.mtd.common_ui.theme.InterMedium
import com.mtd.common_ui.theme.IranSansBoldMedium
import com.mtd.common_ui.theme.IranSansLightLight
import com.mtd.megawallet.ui.compose.components.HintState
import com.mtd.megawallet.ui.compose.components.SearchInputField
import com.mtd.megawallet.viewmodel.swap.SwapTokenOption
import kotlinx.coroutines.launch

/**
 * انتخابِ ارزِ دریافت.
 *
 * فهرست از کاتالوگِ داده‌محورِ اپ می‌آید (`assets.json` + باندلِ امضاشده)؛ `/api/v1/swap` سرویسی
 * برای فهرستِ توکنِ قابل‌تبدیل ندارد. توکن‌های شبکه‌های دیگر پنهان نمی‌شوند — انتخابشان صریحاً رد
 * می‌شود تا کاربر بفهمد چرا نمی‌شود، نه اینکه ارز را اصلاً پیدا نکند.
 */
@Composable
fun SwapReceiveTokenSheet(
    visible: Boolean,
    tokens: List<SwapTokenOption>,
    query: String,
    payNetworkId: String?,
    onQueryChange: (String) -> Unit,
    onTokenSelected: (SwapTokenOption) -> Unit,
    onDismiss: () -> Unit
) {
    val motion = LocalSwapMotion.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motion.fade(SwapMotion.FADE_FAST)),
        exit = fadeOut(motion.fade(SwapMotion.FADE_FAST))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(motion.sheet()) { it },
        exit = slideOutVertically(motion.sheetExit()) { it }
    ) {
        SwapSheetSurface(onDismiss = onDismiss) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "دریافت",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
                    fontFamily = IranSansBoldMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "بستن",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            SearchInputField(
                value = query,
                label = "جست‌وجو",
                placeholder = "جست‌وجوی ارز",
                onValueChange = onQueryChange,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(10.dp))

            if (tokens.isEmpty()) {
                HintState("ارزی با این نام پیدا نشد", Modifier.padding(horizontal = 20.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(tokens, key = { it.id }) { token ->
                        SwapReceiveTokenRow(
                            token = token,
                            crossNetwork = payNetworkId != null && token.networkId != payNetworkId,
                            onClick = { onTokenSelected(token) }
                        )
                    }
                }
            }
        }
    }
}

/** شیتِ کشیدنی: کشیدن رو به پایین آن را می‌بندد. */
@Composable
private fun SwapSheetSurface(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val motion = LocalSwapMotion.current
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }

    LaunchedEffect(Unit) { dragOffset.snapTo(0f) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .graphicsLayer { translationY = dragOffset.value }
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (dragOffset.value > size.height * DISMISS_FRACTION) {
                                    onDismiss()
                                    dragOffset.snapTo(0f)
                                } else {
                                    dragOffset.animateTo(0f, motion.sheetExit())
                                }
                            }
                        }
                    ) { _, dragAmount ->
                        scope.launch {
                            dragOffset.snapTo((dragOffset.value + dragAmount).coerceAtLeast(0f))
                        }
                    }
                }
        ) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SwapReceiveTokenRow(
    token: SwapTokenOption,
    crossNetwork: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(vertical = 10.dp)
            // شبکهٔ متفاوت قابل انتخاب هست ولی کم‌رنگ: دلیلِ رد شدن روی کارتِ دریافت نوشته می‌شود.
            .graphicsLayer { alpha = if (crossNetwork) 0.45f else 1f },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            SwapTokenLogo(iconUrl = token.iconUrl, contentDescription = token.name, size = 40.dp)
            SwapNetworkBadge(iconUrl = token.networkIconUrl)
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = token.faName ?: token.name,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontFamily = IranSansBoldMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${token.symbol} · ${token.networkName}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontFamily = InterMedium
            )
        }

        if (crossNetwork) {
            Text(
                text = "شبکهٔ دیگر",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = IranSansLightLight
            )
        }
    }
}

private const val DISMISS_FRACTION = 0.28f
