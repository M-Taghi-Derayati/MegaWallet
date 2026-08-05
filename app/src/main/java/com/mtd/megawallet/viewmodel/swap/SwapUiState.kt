package com.mtd.megawallet.viewmodel.swap

import com.mtd.core.utils.FiatConversion
import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.CurrencyRate
import com.mtd.domain.model.FiatCurrency
import com.mtd.domain.model.FeeOption
import com.mtd.domain.model.SwapFees
import com.mtd.domain.model.SwapQuote
import com.mtd.domain.model.SwapRoute
import com.mtd.domain.model.swap.SwapExecutionProgress
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * فازهای فلوی تبدیل. یک ستونِ واحد است که بینِ این فازها morph می‌شود، نه چند صفحهٔ جدا.
 */
enum class SwapPhase {
    PAY_TOKEN,
    AMOUNT,
    CONFIRM,
    EXECUTING,
    RESULT
}

/** یک توکن قابلِ انتخاب. آیکون‌ها از کانفیگ می‌آیند (`iconUrl`)، نه از drawableِ لوکال. */
data class SwapTokenOption(
    val id: String,
    val networkId: String,
    val networkName: String,
    val symbol: String,
    val name: String,
    val faName: String?,
    val iconUrl: String?,
    val networkIconUrl: String?,
    val decimals: Int,
    val contractAddress: String?
)

/** یک توکنِ قابلِ پرداخت، همراه با موجودیِ واقعیِ کاربر در **کوچک‌ترین واحد**. */
data class SwapPayToken(
    val option: SwapTokenOption,
    val balanceRaw: BigInteger,
    val balanceDisplay: String,
    val priceUsd: BigDecimal,
    val fiatDisplay: String
)

sealed interface SwapQuoteState {
    data object Idle : SwapQuoteState
    data object Loading : SwapQuoteState
    data class Ready(val quote: SwapQuote, val route: SwapRoute) : SwapQuoteState
    /** خطای واقعیِ سرور (مثلاً «مسیری برای این تبدیل پیدا نشد»)، نه پیام عمومی. */
    data class Failed(val message: String, val isRetryable: Boolean = true) : SwapQuoteState
}

sealed interface SwapPrepareState {
    data object Idle : SwapPrepareState
    data object Loading : SwapPrepareState
    data class Failed(val message: String) : SwapPrepareState
}

data class SwapFeeSelection(
    val options: List<FeeOption> = emptyList(),
    val selectedLevel: String? = null,
    /**
     * قیمتِ ارزِ بومیِ شبکه به دلار، برای نمایشِ فیاتِ کارمزد. صفر یعنی نامعلوم — آن‌وقت فقط
     * مقدارِ ارز نشان داده می‌شود، نه یک عددِ فیاتِ ساختگی.
     */
    val nativePriceUsd: BigDecimal = BigDecimal.ZERO
) {
    val selected: FeeOption?
        get() = options.firstOrNull { it.level == selectedLevel } ?: options.firstOrNull()

    /** هزینهٔ کارمزدِ انتخاب‌شده به دلار، یا `null` وقتی قیمتِ ارزِ بومی معلوم نیست. */
    val selectedFeeUsd: BigDecimal?
        get() {
            val coin = selected?.feeInCoin ?: return null
            selected?.feeInUsd?.let { return it }
            if (nativePriceUsd.signum() <= 0) return null
            return coin.multiply(nativePriceUsd)
        }
}

data class SwapUiState(
    val phase: SwapPhase = SwapPhase.PAY_TOKEN,

    // ── انتخاب توکن پرداخت ──
    val isLoadingPayTokens: Boolean = true,
    val payQuery: String = "",
    val payTokens: List<SwapPayToken> = emptyList(),
    val payToken: SwapPayToken? = null,
    /**
     * همان دارایی به شکلِ `AssetItem`. کارتِ مبلغ کامپوننتِ مشترک با ارسال است و با این نوع کار
     * می‌کند؛ نگه‌داشتنش اینجا از ساختِ دوبارهٔ یک دارایی از روی [payToken] جلوگیری می‌کند.
     */
    val payAsset: AssetItem? = null,

    // ── مبلغ ──
    val amountInput: String = "0",
    /** ورودیِ مبلغ در واحدِ فیاتِ انتخاب‌شده است یا در واحدِ خودِ توکن. */
    val isFiatInput: Boolean = false,

    // ── انتخاب توکن دریافت ──
    val receiveSheetVisible: Boolean = false,
    val receiveQuery: String = "",
    val receiveTokens: List<SwapTokenOption> = emptyList(),
    val receiveToken: SwapTokenOption? = null,

    // ── استعلام ──
    val slippageBps: Int = DEFAULT_SLIPPAGE_BPS,
    val quoteState: SwapQuoteState = SwapQuoteState.Idle,
    /**
     * پایانِ اعتبارِ استعلام بر مبنای `SystemClock.elapsedRealtime()`.
     *
     * خودِ شمارشِ معکوس عمداً در state نیست: هر تیک، کلِ stateFlow را invalidate می‌کرد. UI از این
     * مقدار تیک خودش را می‌سازد.
     */
    val quoteExpiresAtElapsed: Long? = null,
    val quoteTtlMs: Long = DEFAULT_QUOTE_TTL_MS,

    // ── اجرا ──
    val fee: SwapFeeSelection = SwapFeeSelection(),
    val prepareState: SwapPrepareState = SwapPrepareState.Idle,
    val execution: SwapExecutionProgress? = null,
    val executionMessage: String? = null,

    // ── نمایش ──
    val fiatCurrency: FiatCurrency = FiatCurrency.DEFAULT,
    val usdToTomanRate: CurrencyRate? = null,
    val providers: List<String> = emptyList()
) {

    /**
     * جفتِ بین‌شبکه‌ای. `SwapQuoteRequest` دو شبکه را جدا می‌گیرد، ولی ریلِ اجرا
     * (`TransactionParams.Evm`) فقط یک `networkId` دارد؛ پس این حالت در UI صریحاً رد می‌شود.
     */
    val isCrossNetwork: Boolean
        get() {
            val from = payToken?.option?.networkId ?: return false
            val to = receiveToken?.networkId ?: return false
            return from != to
        }

    /** مبلغِ پرداخت در کوچک‌ترین واحدِ توکنِ پرداخت. تنها مبلغی که به سرور می‌رود. */
    val amountRaw: BigInteger
        get() {
            val token = payToken ?: return BigInteger.ZERO
            val typed = amountInput.toBigDecimalOrZero()
            if (typed.signum() <= 0) return BigInteger.ZERO

            val crypto = if (isFiatInput) {
                val usd = when (fiatCurrency) {
                    FiatCurrency.USD -> typed
                    FiatCurrency.TOMAN ->
                        FiatConversion.tomanToUsd(typed, usdToTomanRate) ?: return BigInteger.ZERO
                }
                val price = token.priceUsd
                if (price.signum() <= 0) return BigInteger.ZERO
                usd.divide(price, token.option.decimals + 2, RoundingMode.DOWN)
            } else {
                typed
            }

            return crypto
                .movePointRight(token.option.decimals)
                .setScale(0, RoundingMode.DOWN)
                .toBigInteger()
        }

    val exceedsBalance: Boolean
        get() = payToken != null && amountRaw > payToken.balanceRaw

    val readyRoute: SwapRoute?
        get() = (quoteState as? SwapQuoteState.Ready)?.route

    /** مبلغِ **تضمین‌شده**: چیزی که کاربر در بدترین حالتِ لغزش دریافت می‌کند. */
    val minimumReceivedRaw: BigInteger?
        get() = readyRoute?.toAmount?.min

    /**
     * کارمزدِ پلتفرم، فقط وقتی واقعاً روی زنجیره برداشته شده — وگرنه `null` و ردیفِ کارمزد اصلاً
     * نمایش داده نمی‌شود.
     *
     * نرخ از همین مسیرِ استعلام‌شده خوانده می‌شود و هیچ‌جا نگه داشته نمی‌شود: هم نرخ و هم کلیدِ
     * برداشت در پنلِ اپراتور تغییر می‌کنند و می‌توانند بین دو استعلامِ پشت‌سرهم فرق کنند. نمایشِ
     * `platformBps` به‌تنهایی یعنی گفتنِ «۰٫۵٪ گرفتیم» در حالی که چیزی برداشته نشده.
     */
    val collectedPlatformFee: SwapFees?
        get() = readyRoute?.fees?.takeIf { it.collected }

    val canRequestQuote: Boolean
        get() = payToken != null &&
            receiveToken != null &&
            !isCrossNetwork &&
            amountRaw.signum() > 0 &&
            !exceedsBalance

    val canContinueFromAmount: Boolean
        get() = canRequestQuote && quoteState is SwapQuoteState.Ready

    val canConfirm: Boolean
        get() = canContinueFromAmount &&
            prepareState !is SwapPrepareState.Loading &&
            fee.selected?.gasPrice != null

    val visibleReceiveTokens: List<SwapTokenOption>
        get() = receiveTokens.filter { it.matches(receiveQuery) }

    companion object {
        /** ۰٫۵٪ — همان پیش‌فرضی که سرور برای مسیریابی استفاده می‌کند. */
        const val DEFAULT_SLIPPAGE_BPS = 50
        val SLIPPAGE_CHOICES = listOf(10, 50, 100, 300)
        const val DEFAULT_QUOTE_TTL_MS = 15_000L
    }
}

private fun SwapTokenOption.matches(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return symbol.contains(q, ignoreCase = true) ||
        name.contains(q, ignoreCase = true) ||
        faName?.contains(q, ignoreCase = true) == true ||
        networkName.contains(q, ignoreCase = true)
}

private fun String.toBigDecimalOrZero(): BigDecimal =
    runCatching { BigDecimal(trim().trimEnd('.').ifBlank { "0" }) }.getOrDefault(BigDecimal.ZERO)

/** کاربر روی این‌ها اثر می‌گذارد؛ ViewModel تنها مصرف‌کننده است. */
sealed interface SwapEvent {
    data class PayQueryChanged(val query: String) : SwapEvent

    /**
     * فهرستِ پرداخت از همان `AssetItem`های صفحهٔ اصلی ساخته می‌شود (کامپوننتِ مشترک با ارسال)،
     * پس انتخاب هم با همان نوع می‌آید و ViewModel آن را به [SwapPayToken] نگاشت می‌کند.
     */
    data class PayAssetSelected(val asset: AssetItem) : SwapEvent
    data object BackToPaySelect : SwapEvent

    data class AmountKeyPressed(val key: String) : SwapEvent
    data object ToggleFiatInput : SwapEvent
    data object UseMax : SwapEvent

    data object OpenReceiveSheet : SwapEvent
    data object DismissReceiveSheet : SwapEvent
    data class ReceiveQueryChanged(val query: String) : SwapEvent
    data class ReceiveTokenSelected(val token: SwapTokenOption) : SwapEvent

    data class SlippageChanged(val bps: Int) : SwapEvent
    data class FeeLevelSelected(val level: String) : SwapEvent
    data object RequoteRequested : SwapEvent

    data object ContinuePressed : SwapEvent
    data object BackFromConfirm : SwapEvent
    data object ConfirmPressed : SwapEvent
    data object ResultDismissed : SwapEvent
}

/** باید با کلیدی که صفحه‌کلیدِ مشترکِ ارسال منتشر می‌کند یکی بماند. */
const val SWAP_KEY_DELETE = "del"
