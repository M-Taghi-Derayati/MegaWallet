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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.InterMedium
import com.mtd.common_ui.theme.IranSansBoldMedium
import com.mtd.common_ui.theme.IranSansLightLight
import com.mtd.megawallet.ui.compose.components.AmountDisplaySection
import com.mtd.megawallet.ui.compose.components.SearchInputField
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            tokenSlot()

            Spacer(Modifier.width(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = segment(intro, 0.1f, 0.35f) }
            ) {
                Text(
                    text = token.option.symbol,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontFamily = InterMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${token.balanceDisplay} ${token.option.symbol}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = InterMedium
                )
            }

            SwapPillButton(
                text = "همه",
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
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onOpenSheet() }
                .padding(14.dp)
                .graphicsLayer { alpha = segment(intro, 0.35f, 0.75f) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading()

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = state.receiveToken?.let { it.faName ?: it.name } ?: "دریافت",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontFamily = IranSansBoldMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = state.receiveToken?.let { "دریافت ${it.symbol} روی ${it.networkName}" }
                        ?: "یک ارز انتخاب کنید",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = IranSansLightLight
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
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 16.sp,
        fontFamily = InterMedium
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
 * آدرسِ گیرندهٔ خروجی.
 *
 * دو حالتِ کاملاً متفاوت که عمداً یک شکل نیستند:
 *  - **هم‌خانواده** (تبدیلِ درون‌زنجیره‌ای یا پلِ EVM→EVM): آدرس اختیاری است و پیش‌فرض همان
 *    کیف‌پولِ خودِ کاربر. فقط یک سطرِ جمع‌وجور با «تغییر» نشان داده می‌شود تا فلوی رایج شلوغ نشود.
 *  - **بین‌خانوادگی** (TRON ↔ EVM): آدرس **اجباری** است و ورودی از همان اول باز است. آدرسِ
 *    اتریومی روی ترون (و برعکس) اصلاً آدرس نیست، پس چیزی برای پیش‌فرض‌گرفتن از سمتِ مبدأ وجود
 *    ندارد و کیف‌پولِ خودِ کاربر روی زنجیرهٔ مقصد جای آن را می‌گیرد.
 */
@Composable
fun SwapDestinationSection(
    state: SwapUiState,
    onAddressChange: (String) -> Unit,
    onToggleEditor: () -> Unit,
    onResetToOwnWallet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val receive = state.receiveToken ?: return
    val required = state.requiresDestinationAddress
    val expanded = required || state.destinationEditing

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "دریافت‌کننده",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp,
                    fontFamily = IranSansBoldMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when {
                        required && state.usableDestinationAddress == null ->
                            "آدرس ${receive.networkName} را وارد کنید"
                        state.destinationIsOwnWallet -> "کیف پول شما روی ${receive.networkName}"
                        state.usableDestinationAddress != null -> "آدرس دلخواه روی ${receive.networkName}"
                        else -> "کیف پول شما"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = IranSansLightLight
                )
            }

            if (!required) {
                SwapPillButton(
                    text = if (state.destinationEditing) "بستن" else "تغییر",
                    onClick = onToggleEditor
                )
            } else if (state.destinationOwnAddress != null && !state.destinationIsOwnWallet) {
                SwapPillButton(text = "کیف پول خودم", onClick = onResetToOwnWallet)
            }
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            SearchInputField(
                value = state.destinationInput,
                label = "آدرس مقصد",
                placeholder = "آدرس روی ${receive.networkName}",
                onValueChange = onAddressChange
            )
        } else {
            state.usableDestinationAddress?.let { address ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = address,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontFamily = InterMedium
                )
            }
        }

        state.destinationError?.let { error ->
            Spacer(Modifier.height(6.dp))
            SwapNotice(text = error, isError = true)
        }

        // ⚠️ پول به کیف‌پولی غیر از کیف‌پولِ خودِ کاربر می‌رود. پل‌زدن به آدرسی که هیچ کلیدی روی
        // آن زنجیره ندارد یعنی سوختنِ دارایی، و هیچ‌کس آن را برنمی‌گرداند.
        if (state.destinationError == null && state.recipientIsElsewhere) {
            Spacer(Modifier.height(6.dp))
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
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontFamily = IranSansLightLight,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun SwapPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 12.sp,
            fontFamily = IranSansBoldMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** جای خالیِ آیکونِ دریافت، تا وقتی ارزی انتخاب نشده. */
@Composable
fun SwapReceivePlaceholder(size: Dp = 38.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size * 0.5f)
        )
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontFamily = IranSansLightLight,
            modifier = Modifier.weight(1f)
        )
        SwapUiState.SLIPPAGE_CHOICES.forEach { bps ->
            val selected = bps == selectedBps
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
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
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 12.sp,
                    fontFamily = InterMedium
                )
            }
        }
    }
}
