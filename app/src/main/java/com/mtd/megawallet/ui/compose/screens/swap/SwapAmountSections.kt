package com.mtd.megawallet.ui.compose.screens.swap

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.HexAssetShape
import com.mtd.common_ui.theme.InterBold
import com.mtd.common_ui.theme.InterMedium
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.megawallet.ui.compose.animations.constants.WalletScreenConstants
import com.mtd.megawallet.ui.compose.components.AmountDisplaySection
import com.mtd.megawallet.ui.compose.components.normalizeAmountForCalculation
import com.mtd.megawallet.viewmodel.swap.SwapQuoteState
import com.mtd.megawallet.viewmodel.swap.SwapUiState

/**
 * کارتِ پرداخت: توکنِ انتخاب‌شده، «همه»، و مبلغی که کاربر تایپ می‌کند.
 *
 * [intro] پیشرفتِ ۰..۱ ورودِ فاز است؛ اجزا با [segment] روی همین یک مقدار پلکانی می‌آیند تا چند
 * انیمیشنِ مستقل لازم نباشد هم‌زمان نگه داشته شوند.
 */
@Composable
fun SwapPayCardSection(
    state: SwapUiState,
    intro: Float,
    onUseMax: () -> Unit,
    onToggleFiat: () -> Unit,
    tokenSlot: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val token = state.payToken ?: return

    // همان کارتِ داراییِ فازِ مبلغِ ارسال: سطحِ گِرد ۲۰، نامِ فارسیِ ارز بالا، موجودی زیرش، و
    // «حداکثر» در انتها.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            tokenSlot()

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = segment(intro, 0.1f, 0.35f) }
            ) {
                Text(
                    text = token.option.faName ?: token.option.symbol,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 16.sp,
                    fontFamily = IranSansRegular
                )
                Text(
                    text = "${token.balanceDisplay} ${token.option.symbol}",
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontSize = 13.sp,
                    fontFamily = InterMedium
                )
            }

            SwapPillButton(
                text = "حداکثر",
                onClick = onUseMax,
                modifier = Modifier.graphicsLayer { alpha = segment(intro, 0.15f, 0.4f) }
            )
        }

        Spacer(Modifier.height(18.dp))

        // همان کارتِ مبلغِ صفحهٔ ارسال: شمارندهٔ اودومتری، معادلِ واحدِ دیگر، و هشدارِ موجودی.
        // معیارِ کمبود از [SwapUiState.exceedsBalance] می‌آید که در کوچک‌ترین واحد مقایسه می‌کند،
        // پس در حالتِ ورودیِ فیات هم درست است.
        state.payAsset?.let { asset ->
            AmountDisplaySection(
                asset = asset,
                amount = state.amountInput,
                calculationAmount = normalizeAmountForCalculation(state.amountInput),
                isFiatMode = state.isFiatInput,
                fiatCurrency = state.fiatCurrency,
                usdToIrrRate = state.usdToTomanRate,
                isOverBalance = state.exceedsBalance,
                onToggle = onToggleFiat,
                modifier = Modifier.graphicsLayer {
                    val a = segment(intro, 0.05f, 0.5f)
                    alpha = a
                    scaleX = 0.88f + 0.12f * a
                    scaleY = 0.88f + 0.12f * a
                }
            )
        }
    }
}

/**
 * کارتِ دریافت. سه حالت دارد و هر سه صریح‌اند: توکن انتخاب نشده، جفتِ بین‌شبکه‌ای (رد می‌شود)،
 * و مقدارِ استعلام‌شده.
 */
@Composable
fun SwapReceiveCardSection(
    state: SwapUiState,
    intro: Float,
    onOpenSheet: () -> Unit,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onOpenSheet() }
                .padding(16.dp)
                .graphicsLayer { alpha = segment(intro, 0.35f, 0.75f) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading()

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = state.receiveToken?.let { it.faName ?: it.name } ?: "دریافت",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 16.sp,
                    fontFamily = IranSansRegular
                )
                Text(
                    text = state.receiveToken?.let { "دریافت ${it.symbol} روی ${it.networkName}" }
                        ?: "یک ارز انتخاب کنید",
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontSize = 13.sp,
                    fontFamily = IranSansRegular
                )
            }

            SwapReceiveAmountText(state)
        }

        SwapProvenanceNotice(
            verified = state.receiveToken?.verified,
            modifier = Modifier.padding(top = 8.dp)
        )

        SwapQuoteStatusLine(
            state = state,
            modifier = Modifier
                .padding(top = 10.dp)
                .graphicsLayer { alpha = segment(intro, 0.45f, 0.85f) }
        )
    }
}

@Composable
private fun SwapReceiveAmountText(state: SwapUiState) {
    val receive = state.receiveToken
    val net = state.readyRoute?.toAmount?.net

    val text = when {
        receive == null -> "0"
        state.quoteState is SwapQuoteState.Loading -> "…"
        net != null -> SwapFormat.amount(net, receive.decimals)
        else -> "0"
    }

    Text(
        text = text,
        color = MaterialTheme.colorScheme.tertiary,
        fontSize = 16.sp,
        fontFamily = InterBold
    )
}

/**
 * وضعیتِ استعلام، همیشه با دلیلِ واقعی.
 *
 * «حداقل دریافتی» مقدارِ تضمین‌شده است و باید قبل از تأیید دیده شود — نه مقدارِ خوش‌بینانه.
 */
@Composable
fun SwapQuoteStatusLine(
    state: SwapUiState,
    modifier: Modifier = Modifier
) {
    val receive = state.receiveToken

    when (val quote = state.quoteState) {
        is SwapQuoteState.Failed -> SwapNotice(
            text = quote.message,
            isError = true,
            modifier = modifier
        )

        is SwapQuoteState.Ready -> if (receive != null) {
            val min = quote.route.toAmount.min
            val minText =
                "حداقل دریافتی: ${SwapFormat.amountWithSymbol(min, receive.decimals, receive.symbol)}"
            SwapNotice(
                // مسیرِ بین‌زنجیره‌ای باید قبل از تأیید معلوم باشد، نه بعد از ارسال: زمانِ رسیدنش
                // با تبدیلِ درون‌زنجیره‌ای فرق دارد.
                text = if (state.isBridge) "$minText · ${state.bridgeLabel()}" else minText,
                isError = false,
                modifier = modifier
            )
        }

        SwapQuoteState.Idle, SwapQuoteState.Loading -> Spacer(modifier.height(0.dp))
    }
}

/**
 * گیرندهٔ خروجی — یک سطرِ **قابلِ فشردن** که شیتِ انتخاب را باز می‌کند، نه یک فیلدِ آدرسِ خام.
 *
 * فیلدِ آدرس فرضِ اشتباهی داشت: که کاربر آدرسی برای وارد کردن دارد. در بیشترِ تبدیل‌ها مقصد یکی از
 * کیف‌پول‌های خودِ اوست و آن‌ها نام دارند؛ [SwapDestinationSheet] همان را یک ضربه می‌کند و آدرسِ
 * دستی را به یک گزینهٔ صریح تبدیل می‌کند.
 *
 * دو حالت همچنان فرق دارند و عمداً یک متن ندارند:
 *  - **هم‌خانواده** (تبدیلِ درون‌زنجیره‌ای یا پلِ EVM→EVM): پیش‌فرض کیف‌پولِ خودِ کاربر است و سطر
 *    فقط تأیید می‌کند که کجا می‌رود.
 *  - **بین‌خانوادگی** (TRON ↔ EVM): آدرس **اجباری** است و تا وقتی انتخاب نشده، سطر خودش می‌گوید
 *    چه چیزی کم است. آدرسِ اتریومی روی ترون (و برعکس) اصلاً آدرس نیست، پس چیزی برای
 *    پیش‌فرض‌گرفتن از سمتِ مبدأ وجود ندارد.
 */
@Composable
fun SwapDestinationSection(
    state: SwapUiState,
    /**
     * نامی که خودِ کاربر روی این گیرنده گذاشته — نامِ کیف‌پول یا مدخلِ دفترچه. `null` یعنی این
     * آدرس در هیچ فهرستی نیست.
     */
    destinationName: String?,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val receive = state.receiveToken ?: return
    val required = state.requiresDestinationAddress
    val missing = required && state.usableDestinationAddress == null

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onOpenPicker() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "دارایی به کجا برسد؟",
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontSize = 13.sp,
                    fontFamily = IranSansRegular
                )
                Spacer(Modifier.height(2.dp))
                // نامِ واقعی، نه یک برچسبِ ثابت: کیف‌پولِ کاربر اسمی دارد که خودش گذاشته و همان
                // چیزی است که در بقیهٔ اپ می‌بیند؛ «کیف پول من» برای کسی که کیفش را «کیف اتر»
                // نامیده اصلاً همان کیف به نظر نمی‌رسد.
                Text(
                    text = when {
                        missing -> "انتخاب کنید"
                        destinationName != null -> destinationName
                        state.usableDestinationAddress != null -> "آدرس ثبت‌نشده"
                        else -> "انتخاب کنید"
                    },
                    color = if (missing) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    fontSize = 16.sp,
                    fontFamily = IranSansBold
                )
                state.usableDestinationAddress?.let { address ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${shortenAddress(address)} · ${receive.networkName}",
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontSize = 12.sp,
                        fontFamily = InterMedium
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // «تغییر» صریح می‌ماند: سطرِ قابلِ فشردن به‌تنهایی برای کسی که با این الگو آشنا نیست
            // کافی نیست، و اینجا جایی نیست که کاربر باید حدس بزند.
            SwapPillButton(text = if (missing) "انتخاب" else "تغییر", onClick = onOpenPicker)
        }

        state.destinationError?.let { error ->
            Spacer(Modifier.height(8.dp))
            SwapNotice(text = error, isError = true)
        }

        // ⚠️ پول به کیف‌پولی غیر از کیف‌پولِ خودِ کاربر می‌رود. پل‌زدن به آدرسی که هیچ کلیدی روی
        // آن زنجیره ندارد یعنی سوختنِ دارایی، و هیچ‌کس آن را برنمی‌گرداند.
        if (state.destinationError == null && state.recipientIsElsewhere) {
            Spacer(Modifier.height(8.dp))
            SwapNotice(
                text = "این دارایی به کیف پول شما واریز نمی‌شود. آدرس مقصد را دقیق بررسی کنید؛ " +
                    "ارسال به آدرس اشتباه برگشت‌پذیر نیست.",
                isError = true
            )
        }
    }
}

/**
 * هشدارِ خنثی برای توکنی که در هیچ فهرستِ معتبری ثبت نشده.
 *
 * ⚠️ این «ناامن» نیست و هرگز نباید مانعِ تبدیل شود: یک توکنِ واقعیِ تازه یا کم‌رونق هم دقیقاً
 * همین‌طور به نظر می‌رسد. فقط دعوت به بررسیِ آدرسِ قرارداد است.
 */
@Composable
fun SwapProvenanceNotice(
    verified: Boolean?,
    modifier: Modifier = Modifier
) {
    if (verified != false) return
    SwapNotice(
        text = "این توکن در فهرست‌های معتبر ثبت نشده — قبل از سواپ آدرس کانترکت را بررسی کنید.",
        isError = false,
        modifier = modifier
    )
}

/** برچسبِ پل، با نامِ پلِ زیرین وقتی سرور اعلامش کرده. */
internal fun SwapUiState.bridgeLabel(): String {
    val destination = destinationNetworkName ?: "شبکهٔ مقصد"
    val tool = bridgeTool
    return if (tool != null) "انتقال به $destination از طریق $tool" else "انتقال به $destination"
}

@Composable
fun SwapNotice(
    text: String,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onTertiary
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontFamily = IranSansRegular,
        modifier = modifier.fillMaxWidth()
    )
}

/** قرصِ عملِ فرعی — همان «حداکثر»ِ کارتِ داراییِ صفحهٔ ارسال. */
@Composable
fun SwapPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.tertiary,
            fontSize = 14.sp,
            fontFamily = IranSansRegular
        )
    }
}

/**
 * جای خالیِ آیکونِ دریافت، تا وقتی ارزی انتخاب نشده.
 *
 * قابش همان شش‌ضلعیِ [HexAssetShape] است چون دقیقاً همان‌جا لحظه‌ای بعد یک آیکونِ ارز می‌نشیند؛
 * دایره‌بودنش یعنی جابه‌جاییِ فرم درست وسطِ انتخابِ کاربر. فضایی که می‌گیرد هم به‌اندازهٔ
 * آیکون **به‌علاوهٔ بجِ شبکه** است تا ردیف موقعِ انتخاب جابه‌جا نشود.
 */
@Composable
fun SwapReceivePlaceholder(size: Dp = WalletScreenConstants.ASSET_ICON_MAIN_SIZE) {
    Box(
        modifier = Modifier.size(
            size * (WalletScreenConstants.ASSET_ICON_SIZE / WalletScreenConstants.ASSET_ICON_MAIN_SIZE)
        )
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(HexAssetShape)
                .border(1.dp, MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.4f), HexAssetShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}

/** ردیفِ انتخابِ لغزش. مقدارها bps هستند؛ درصد فقط نمایش است. */
@Composable
fun SwapSlippageRow(
    selectedBps: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "لغزش مجاز",
            color = MaterialTheme.colorScheme.onTertiary,
            fontSize = 14.sp,
            fontFamily = IranSansRegular,
            modifier = Modifier.weight(1f)
        )
        SwapUiState.SLIPPAGE_CHOICES.forEach { bps ->
            val selected = bps == selectedBps
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(bps) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = SwapFormat.percentFromBps(bps),
                    color = if (selected) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onTertiary
                    },
                    fontSize = 12.sp,
                    fontFamily = InterMedium
                )
            }
        }
    }
}
