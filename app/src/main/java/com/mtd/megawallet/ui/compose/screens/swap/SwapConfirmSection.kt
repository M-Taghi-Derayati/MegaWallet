package com.mtd.megawallet.ui.compose.screens.swap

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.InterBold
import com.mtd.common_ui.theme.InterMedium
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.core.utils.FiatConversion
import com.mtd.megawallet.ui.compose.components.BottomSecuritySection
import com.mtd.megawallet.ui.compose.components.ConfirmDetailCard
import com.mtd.megawallet.ui.compose.components.ConfirmDetailRow
import com.mtd.megawallet.ui.compose.components.ConfirmSliderButton
import com.mtd.megawallet.ui.compose.components.FeeLevelIndicator
import com.mtd.megawallet.viewmodel.swap.SwapPrepareState
import com.mtd.megawallet.viewmodel.swap.SwapUiState

/**
 * فازِ تأیید — همان قابِ صفحهٔ تأییدِ ارسال، با محتوای تبدیل.
 *
 * چهار عددی که طبق سیاستِ محصول باید **قبل از** تأیید دیده شوند: مبلغِ پرداخت، مبلغِ دریافت،
 * **حداقلِ دریافتی** (تنها عددی که تضمین شده)، و کارمزدِ پلتفرم + لغزش. اعتبارِ استعلام هم زیرِ
 * عنوان شمارش معکوس می‌شود.
 *
 * چیزهایی که ارسال ندارد و این‌جا می‌مانند — تایمرِ استعلام، لغزش، مسیر/ارائه‌دهنده، و جزئیاتِ پل —
 * فقط لباسِ ارسال را می‌پوشند: همان کارت، همان ردیف، همان پالت.
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

        // سرصفحه: جفتِ آیکون به‌جای آواتارِ گیرنده، در همان جایگاه و اندازه.
        //
        // دیگر روی هم نمی‌افتند: حالا هر آیکون بجِ شبکه‌اش را هم دارد و در تبدیلِ بین‌شبکه‌ای
        // همان بج تنها چیزی است که مبدأ را از مقصد جدا می‌کند؛ پوشاندنش یعنی حذفِ اطلاعاتی که
        // کاربر باید قبل از امضا ببیند.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            payCoin()
            receiveCoin()
        }

        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier.graphicsLayer {
                val a = segment(intro, 0f, 0.4f)
                alpha = a
                translationY = (1f - a) * 14f
            }
        ) {
            Text(
                text = "تأیید تبدیل",
                color = MaterialTheme.colorScheme.onTertiary,
                fontFamily = IranSansRegular,
                fontSize = 15.sp
            )
            Text(
                text = "${pay.option.symbol} ← ${receive.symbol}",
                color = MaterialTheme.colorScheme.tertiary,
                fontFamily = InterBold,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(8.dp))

        SwapQuoteTimer(
            fraction = quoteFraction,
            secondsRemaining = quoteSecondsRemaining,
            modifier = Modifier.graphicsLayer { alpha = segment(intro, 0.1f, 0.45f) }
        )

        Spacer(Modifier.height(20.dp))

        ConfirmDetailCard(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = segment(intro, 0.2f, 0.6f) }
        ) {
            ConfirmDetailRow(
                label = "پرداخت ${pay.option.faName ?: pay.option.symbol}",
                valueLeft = {
                    SwapAmountValue(
                        iconUrl = pay.option.iconUrl,
                        symbol = pay.option.symbol,
                        text = SwapFormat.amount(state.amountRaw, pay.option.decimals)
                    )
                }
            )
            ConfirmDetailRow(
                label = "دریافت ${receive.faName ?: receive.symbol}",
                valueLeft = {
                    SwapAmountValue(
                        iconUrl = receive.iconUrl,
                        symbol = receive.symbol,
                        // مقدارِ نیامده صفر نیست: تا وقتی مسیر نرسیده placeholder نشان داده می‌شود.
                        text = route?.toAmount?.net
                            ?.let { SwapFormat.amount(it, receive.decimals) }
                            ?: FiatConversion.UNKNOWN_PLACEHOLDER
                    )
                }
            )
            ConfirmDetailRow(
                label = if (state.isBridge) "شبکهٔ مبدأ" else "شبکه",
                valueLeft = {
                    SwapNetworkValue(
                        iconUrl = pay.option.networkIconUrl,
                        text = pay.option.networkName
                    )
                }
            )
            if (state.isBridge) {
                ConfirmDetailRow(
                    label = "شبکهٔ مقصد",
                    valueLeft = {
                        SwapNetworkValue(
                            iconUrl = receive.networkIconUrl,
                            text = receive.networkName
                        )
                    }
                )
                state.bridgeTool?.let { tool ->
                    ConfirmDetailRow(label = "پل", value = tool)
                }
            }
            ConfirmDetailRow(label = "از کیف‌ پول", value = walletName)

            // گیرنده فقط وقتی نمایش داده می‌شود که همان کیف‌پولِ کاربر **نباشد** — و آن وقت کاملِ
            // آدرس، در سطرِ خودش. کوتاه‌کردن به «0x1234…abcd» دقیقاً همان چیزی است که آدرسِ جعلی
            // پشتش پنهان می‌شود؛ کاربر باید بتواند کاراکتربه‌کاراکتر مقایسه کند.
            state.quotedRecipient?.takeIf { state.recipientIsElsewhere }?.let { recipient ->
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = "دریافت‌کننده",
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontFamily = IranSansRegular,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = recipient,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = InterMedium,
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (state.recipientIsElsewhere) {
            Spacer(Modifier.height(12.dp))
            SwapNotice(
                text = "دارایی به کیف پول شما واریز نمی‌شود؛ به آدرسی می‌رود که بالا نوشته شده. " +
                    "پس از ارسال، برگشت‌پذیر نیست.",
                isError = true
            )
        }

        SwapProvenanceNotice(
            verified = receive.verified,
            modifier = Modifier.padding(top = 10.dp)
        )

        // ⚠️ پل در دو پا تسویه می‌شود و سرور پای مقصد را دنبال نمی‌کند. کاربر باید **قبل از**
        // امضا بداند که پول بلافاصله آن‌طرف نیست، نه این‌که بعد از تأیید سرگردان شود.
        if (state.isBridge) {
            Spacer(Modifier.height(12.dp))
            SwapNotice(
                text = "این یک انتقال بین‌شبکه‌ای است: تراکنش روی ${pay.option.networkName} " +
                    "ثبت می‌شود و دارایی شما چند دقیقه بعد روی ${receive.networkName} می‌نشیند.",
                isError = false
            )
        }

        Spacer(Modifier.height(14.dp))

        SwapExpandableDetails(
            state = state,
            onSlippageSelected = onSlippageSelected,
            modifier = Modifier.graphicsLayer { alpha = segment(intro, 0.3f, 0.7f) }
        )

        Spacer(Modifier.height(12.dp))

        SwapFeeSection(
            state = state,
            onFeeLevelSelected = onFeeLevelSelected,
            modifier = Modifier.graphicsLayer { alpha = segment(intro, 0.4f, 0.8f) }
        )

        val prepare = state.prepareState
        if (prepare is SwapPrepareState.Failed) {
            Spacer(Modifier.height(12.dp))
            SwapNotice(text = prepare.message, isError = true)
        }

        Spacer(Modifier.height(16.dp))
    }
}

/** مقدارِ یک ارز در کارتِ جزئیات: آیکون + عدد، دقیقاً مثلِ کارتِ تراکنشِ ارسال. */
@Composable
private fun SwapAmountValue(
    iconUrl: String?,
    symbol: String,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SwapAssetLogo(
            iconUrl = iconUrl,
            symbol = symbol,
            contentDescription = null,
            size = 20.dp
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.tertiary,
            fontFamily = InterBold,
            fontSize = 15.sp
        )
    }
}

/** همتای [SwapAmountValue] برای نامِ شبکه. */
@Composable
private fun SwapNetworkValue(
    iconUrl: String?,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SwapNetworkLogo(iconUrl = iconUrl, contentDescription = null, size = 20.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.tertiary,
            fontFamily = IranSansRegular,
            fontSize = 15.sp
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
        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            thickness = 0.5.dp
        )

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
                color = MaterialTheme.colorScheme.onTertiary,
                fontFamily = IranSansRegular,
                fontSize = 13.sp
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
                    ConfirmDetailRow(
                        label = "حداقل دریافتی",
                        value = state.minimumReceivedRaw
                            ?.let { SwapFormat.amountWithSymbol(it, receive.decimals, receive.symbol) }
                            ?: FiatConversion.UNKNOWN_PLACEHOLDER
                    )
                }
                // فقط وقتی کارمزد واقعاً برداشته شده. تا وقتی `collected=false` است سرور چیزی
                // کم نمی‌کند و `net == gross`؛ نشان‌دادنِ نرخ در آن حالت یعنی ادعای هزینه‌ای که
                // وجود ندارد.
                state.collectedPlatformFee?.let { fees ->
                    ConfirmDetailRow(
                        label = "کارمزد پلتفرم",
                        value = fees.platformBps
                            ?.let { SwapFormat.percentFromBps(it) }
                            ?: FiatConversion.UNKNOWN_PLACEHOLDER
                    )
                }
                state.readyRoute?.provider?.let { provider ->
                    ConfirmDetailRow(label = "مسیر", value = provider)
                }
                SwapSlippageRow(
                    selectedBps = state.slippageBps,
                    onSelect = onSlippageSelected
                )
                Spacer(Modifier.height(2.dp))
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            thickness = 0.5.dp
        )
    }
}

/**
 * بلوکِ کارمزدِ شبکه — همان چیدمانِ صفحهٔ ارسال: سمتِ راست مقدار و معادل‌ها، سمتِ چپ سطح و زمانِ
 * تخمینی کنارِ نشانگرِ عمودی. ضربه روی بلوک سطحِ بعدی را انتخاب می‌کند.
 *
 * برخلافِ ارسال، این‌جا سطح‌ها از استعلامِ همان مسیر می‌آیند؛ فهرستِ خالی یعنی «هنوز نیامده»، پس
 * اسکلتِ بارگذاری نشان داده می‌شود نه عددِ صفر.
 */
@Composable
private fun SwapFeeSection(
    state: SwapUiState,
    onFeeLevelSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = state.fee.options
    val selected = state.fee.selected
    val selectedIndex = options.indexOfFirst { it.level == selected?.level }.coerceAtLeast(0)
    val isLoading = options.isEmpty()

    val feeFiat = state.fee.selectedFeeUsd
        ?.let { SwapFormat.usd(it, state.fiatCurrency, state.usdToTomanRate) }
        ?: selected?.feeAmountUsdDisplay

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (options.isNotEmpty()) {
                    onFeeLevelSelected(options[(selectedIndex + 1) % options.size].level)
                }
            }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = isLoading,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "swapFeeValues"
            ) { loading ->
                if (loading) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FeeSkeleton(width = 120.dp, height = 20.dp, alpha = 0.6f)
                        FeeSkeleton(width = 70.dp, height = 16.dp, alpha = 0.4f)
                        FeeSkeleton(width = 90.dp, height = 14.dp, alpha = 0.3f)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = selected?.feeAmountDisplay ?: FiatConversion.UNKNOWN_PLACEHOLDER,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontFamily = IranSansRegular,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = feeFiat ?: FiatConversion.UNKNOWN_PLACEHOLDER,
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontFamily = InterMedium,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        selected?.feeAmountIrrDisplay?.takeIf { it.isNotBlank() }?.let { irr ->
                            Text(
                                text = "≈ $irr",
                                color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.8f),
                                fontFamily = IranSansRegular,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = "برآورد کارمزد شبکه",
                color = MaterialTheme.colorScheme.onTertiary,
                fontFamily = IranSansRegular,
                fontSize = 13.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.End) {
                AnimatedContent(targetState = isLoading, label = "swapLevelBlock") { loading ->
                    if (loading) {
                        FeeSkeleton(width = 50.dp, height = 18.dp, alpha = 0.6f)
                    } else {
                        Text(
                            text = selected?.level ?: "نامشخص",
                            color = MaterialTheme.colorScheme.tertiary,
                            fontFamily = IranSansRegular,
                            fontSize = 15.sp
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                AnimatedContent(targetState = isLoading, label = "swapTimeBlock") { loading ->
                    if (loading) {
                        FeeSkeleton(width = 35.dp, height = 14.dp, alpha = 0.4f)
                    } else {
                        Text(
                            text = selected?.estimatedTime ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontFamily = IranSansRegular,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            FeeLevelIndicator(
                selectedIndex = selectedIndex,
                totalOptions = if (options.isEmpty()) 1 else options.size,
                isLoading = isLoading
            )
        }
    }
}

@Composable
private fun FeeSkeleton(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    alpha: Float
) {
    Box(
        modifier = Modifier
            .height(height)
            .width(width)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}

/**
 * نوارِ پایینِ فازِ تأیید: هشدارِ برگشت‌ناپذیری + همان اسلایدرِ صفحهٔ ارسال.
 *
 * دکمهٔ ساده جای خود را به [ConfirmSliderButton] داده چون هر دو فلو یک کارِ برگشت‌ناپذیر انجام
 * می‌دهند و نباید با یک ضربهٔ اتفاقی شروع شوند. غیرفعال بودنش همچنان از `canConfirm` می‌آید و
 * شکستِ آماده‌سازی اسلایدر را به حالتِ اولش برمی‌گرداند.
 */
@Composable
fun SwapConfirmCta(
    state: SwapUiState,
    intro: Float,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val preparing = state.prepareState is SwapPrepareState.Loading
    val enabled = state.canConfirm && !preparing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .graphicsLayer {
                val a = segment(intro, 0.5f, 0.9f)
                alpha = a
                translationY = (1f - a) * 18f
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BottomSecuritySection(
            message = "قبل از تأیید، جزئیات را بررسی کنید. تراکنش‌ های بلاکچین برگشت ‌پذیر نیستند"
        )

        ConfirmSliderButton(
            enabled = enabled,
            text = if (preparing) "در حال آماده‌سازی..." else "برای تایید بکشید",
            isError = state.prepareState is SwapPrepareState.Failed,
            onConfirmed = onConfirm
        )
    }
}
