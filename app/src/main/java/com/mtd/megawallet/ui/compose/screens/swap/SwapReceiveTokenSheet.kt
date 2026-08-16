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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
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
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.domain.model.AssetItem
import com.mtd.megawallet.ui.compose.components.HintState
import com.mtd.megawallet.ui.compose.components.SearchInputField
import com.mtd.megawallet.ui.compose.components.ShimmerAssetList
import com.mtd.megawallet.ui.compose.components.TokenList
import com.mtd.megawallet.viewmodel.swap.SwapImportState
import com.mtd.megawallet.viewmodel.swap.SwapTokenSearchState
import com.mtd.megawallet.viewmodel.swap.SwapUiState
import kotlinx.coroutines.launch

/**
 * انتخابِ ارزِ دریافت.
 *
 * فهرست **یک ردیف به ازای هر توکن** دارد، نه به ازای هر جفتِ توکن-شبکه؛ انتخابِ شبکه در شیتِ
 * بعدی انجام می‌شود. ردیف‌ها همان [TokenList] صفحهٔ ارسال‌اند تا دو صفحه یک شکل بمانند.
 *
 * نمای پیش‌فرض (قبل از تایپ‌کردن) **فقط** مجموعهٔ رتبه‌بندی‌شدهٔ سرور است. جهانِ کاملِ ~۱۱ هزارتایی
 * تنها با جست‌وجو ظاهر می‌شود، چون آرایهٔ خامِ aggregator هیچ سیگنالِ کیفیتی ندارد — روی ترون سرِ
 * آن سه بدلِ تتر است که جلوتر از قراردادِ واقعی می‌نشینند. ریختنِ آن در نمای پیش‌فرض یعنی
 * راه‌بردنِ کاربر مستقیم وسطِ همان‌ها.
 *
 * فهرست به موجودیِ کاربر محدود نمی‌شود: سمتِ دریافت معمولاً توکنی است که کاربر اصلاً ندارد.
 */
@Composable
fun SwapReceiveTokenSheet(
    state: SwapUiState,
    onQueryChange: (String) -> Unit,
    onAssetSelected: (AssetItem) -> Unit,
    onImportQueryChange: (String) -> Unit,
    onImportSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    val motion = LocalSwapMotion.current
    val visible = state.receiveSheetVisible

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
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 20.sp,
                    fontFamily = IranSansBold,
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
                        tint = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            SearchInputField(
                value = state.receiveQuery,
                label = "جست‌وجو",
                placeholder = "نام، نماد یا آدرس قرارداد",
                onValueChange = onQueryChange,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            SwapImportRow(
                state = state,
                onImportQueryChange = onImportQueryChange,
                onImportSubmit = onImportSubmit
            )

            Spacer(Modifier.height(10.dp))

            val searchState = state.receiveSearchState
            val searching = state.receiveQuery.isNotBlank()

            // آینهٔ سرور خاموش بود (۵۰۳) و فهرست از باندل ساخته شده — همان چیزی که کاربر می‌بیند
            // فقط بخشِ کوچکی از جهانِ قابلِ تبدیل است، و باید بداند.
            if (!searching && state.receiveListFellBack && state.receiveTokens.isNotEmpty()) {
                Text(
                    text = "فهرست کامل ارزها در دسترس نیست؛ فعلاً فقط ارزهای اصلی نمایش داده می‌شوند.",
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontSize = 12.sp,
                    fontFamily = IranSansRegular,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            when {
                // جهانِ قابلِ تبدیل از سرور می‌آید و چند ثانیه طول می‌کشد؛ اسکلتِ ردیف‌ها ارتفاعِ
                // فهرست را از قبل می‌گیرد تا شیت موقعِ رسیدنِ داده یک‌باره جهش نکند.
                searchState is SwapTokenSearchState.Loading && state.receiveTokens.isEmpty() ->
                    ShimmerAssetList(modifier = Modifier.padding(horizontal = 20.dp))

                searchState is SwapTokenSearchState.Failed && state.receiveTokens.isEmpty() ->
                    HintState(searchState.message, Modifier.padding(horizontal = 20.dp))

                state.receiveTokens.isEmpty() ->
                    HintState(
                        if (searching) {
                            "ارزی با این نام پیدا نشد"
                        } else {
                            "فهرست ارزها در دسترس نیست"
                        },
                        Modifier.padding(horizontal = 20.dp)
                    )

                else -> Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp)) {
                    TokenList(
                        fiatCurrency = state.fiatCurrency,
                        assets = state.receiveTokens,
                        selectedAssetId = state.receiveToken?.id,
                        onTokenClick = onAssetSelected
                    )
                }
            }

            // جست‌وجوی ناموفق وقتی فهرستِ محلی چیزی دارد، فهرست را پاک نمی‌کند — فقط گفته
            // می‌شود که جست‌وجوی سراسری کار نکرده (۵۰۳ ⇒ عقب‌نشینی به فهرستِ باندل).
            if (searchState is SwapTokenSearchState.Failed && state.receiveTokens.isNotEmpty()) {
                Text(
                    text = searchState.message,
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontSize = 12.sp,
                    fontFamily = IranSansRegular,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * «افزودن با آدرس قرارداد».
 *
 * آدرس دقیقاً همان‌طور که کاربر چسبانده فرستاده می‌شود — حروفِ base58 ترون حساس‌اند و
 * کوچک‌کردنشان جست‌وجوی روی‌زنجیره را برای توکنِ خارج از فهرست خراب می‌کند.
 */
@Composable
private fun SwapImportRow(
    state: SwapUiState,
    onImportQueryChange: (String) -> Unit,
    onImportSubmit: () -> Unit
) {
    val importing = state.importState is SwapImportState.Loading

    Column(Modifier.padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                SearchInputField(
                    value = state.importQuery,
                    label = "افزودن",
                    placeholder = "آدرس قرارداد توکن",
                    onValueChange = onImportQueryChange
                )
            }

            Spacer(Modifier.width(8.dp))

            val enabled = state.importQuery.isNotBlank() && !importing
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                    .clickable(
                        enabled = enabled,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onImportSubmit() },
                contentAlignment = Alignment.Center
            ) {
                if (importing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onTertiary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "افزودن توکن",
                        tint = if (enabled) {
                            MaterialTheme.colorScheme.background
                        } else {
                            MaterialTheme.colorScheme.onTertiary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        (state.importState as? SwapImportState.Failed)?.let { failed ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = failed.message,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                fontFamily = IranSansRegular
            )
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
                    .background(MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.4f))
            )
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

private const val DISMISS_FRACTION = 0.28f
