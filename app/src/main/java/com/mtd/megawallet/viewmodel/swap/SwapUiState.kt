package com.mtd.megawallet.viewmodel.swap

import com.mtd.core.utils.FiatConversion
import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.CurrencyRate
import com.mtd.domain.model.FiatCurrency
import com.mtd.domain.model.FeeOption
import com.mtd.domain.model.SwapFees
import com.mtd.domain.model.SwapQuote
import com.mtd.domain.model.SwapRoute
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.swap.SwapExecutionOutcome
import com.mtd.domain.model.swap.SwapExecutionProgress
import com.mtd.domain.model.swap.isCrossAddressFamily
import com.mtd.domain.model.swap.sameSwapAddress
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Locale

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
    val contractAddress: String?,
    /**
     * ⚠️ توکنی که در باندلِ کیوریت‌شده نیست (`SwapToken.id == null`) قابلِ تبدیل هست ولی کارمزدش
     * اسپانسر نمی‌شود. این پرچم تا لحظهٔ ساختِ تراکنش حمل می‌شود تا مسیرِ گس‌لس برایش پیشنهاد نشود.
     */
    val isSponsorable: Boolean = true,
    /**
     * نشانِ **منشأ** (`SwapToken.verified`) — نه ایمنی و نه ممیزی، و هرگز مانعِ تبدیل نیست.
     * `null` یعنی منبع چیزی اعلام نکرده.
     */
    val verified: Boolean? = null
) {
    /**
     * آیا این دقیقاً همان توکن روی همان شبکه است. با [id] مقایسه نمی‌شود: سمتِ پرداخت شناسه‌اش
     * از کاتالوگِ اپ می‌آید و سمتِ دریافت از فهرستِ سرور، پس دو رشتهٔ متفاوت برای یک توکن.
     */
    fun isSameTokenAs(other: SwapTokenOption): Boolean =
        networkId.equals(other.networkId, ignoreCase = true) &&
            (contractAddress ?: "").equals(other.contractAddress ?: "", ignoreCase = true)
}

/** وضعیتِ جست‌وجو در جهانِ قابل‌تبدیل. خالی‌بودنِ نتیجه با «هنوز نرسیده» یکی نیست. */
sealed interface SwapTokenSearchState {
    data object Idle : SwapTokenSearchState
    data object Loading : SwapTokenSearchState
    data class Failed(val message: String) : SwapTokenSearchState
}

/** وضعیتِ «افزودن با چسباندن آدرس». */
sealed interface SwapImportState {
    data object Idle : SwapImportState
    data object Loading : SwapImportState
    data class Failed(val message: String) : SwapImportState
}

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
    /**
     * یک ردیف به ازای هر **توکن** (نه هر جفتِ توکن-شبکه)؛ سرگروه‌ها `groupAssets` دارند و انتخابِ
     * شبکه در شیتِ بعدی انجام می‌شود. همان نوعی که فهرستِ صفحهٔ ارسال مصرف می‌کند.
     */
    val receiveTokens: List<AssetItem> = emptyList(),
    val receiveSearchState: SwapTokenSearchState = SwapTokenSearchState.Idle,
    val receiveToken: SwapTokenOption? = null,
    /**
     * فهرستِ پیش‌فرض از آینهٔ سرور نیامد (۵۰۳ یا قطعی) و به دارایی‌های کاربر + باندل عقب‌نشینی شد.
     * فقط برای گفتنِ همین به کاربر است؛ خودِ فهرست همچنان قابل استفاده است.
     */
    val receiveListFellBack: Boolean = false,

    // ── افزودن با آدرس ──
    val importQuery: String = "",
    val importState: SwapImportState = SwapImportState.Idle,

    // ── آدرسِ مقصد (گیرندهٔ خروجی) ──
    /**
     * خانوادهٔ آدرسِ دو سر. وقتی فرق دارند (TRON ↔ EVM) آدرسِ مقصد **اجباری** است، چون آدرسِ
     * فرستنده روی زنجیرهٔ مقصد اصلاً آدرس نیست.
     */
    val payNetworkType: NetworkType? = null,
    val receiveNetworkType: NetworkType? = null,
    /** متنِ قابلِ ویرایشِ آدرسِ مقصد. پیش‌فرض آدرسِ خودِ کاربر روی شبکهٔ مقصد است. */
    val destinationInput: String = "",
    /** آدرسِ خودِ کیف‌پول روی شبکهٔ مقصد، اگر داشته باشد. مبنای «به کیف‌پول خودم». */
    val destinationOwnAddress: String? = null,
    /** خطای اعتبارسنجیِ **سمتِ کلاینت**. `null` یعنی آدرس برای شبکهٔ مقصد معتبر است. */
    val destinationError: String? = null,
    val destinationEditing: Boolean = false,
    /** آدرسِ مبدأ که استعلام با آن گرفته شد؛ برای تشخیصِ «گیرنده کسِ دیگری است». */
    val senderAddress: String? = null,

    /**
     * شبکه‌هایی که تبدیل/پل رویشان کار می‌کند (`GET /api/v1/swap/chains`).
     *
     * [swapChainsLoaded] عمداً جداست: مجموعهٔ خالی وقتی هنوز پاسخی نیامده یعنی «نمی‌دانیم»، نه
     * «هیچ‌جا کار نمی‌کند». گِیت‌کردن بر مبنای ندانستن، فهرستِ خالی به کاربر نشان می‌دهد.
     *
     * این محدودیت فقط ورودیِ تبدیل را می‌بندد؛ تست‌نت‌ها در بقیهٔ اپ دست‌نخورده‌اند.
     */
    val swappableNetworkIds: Set<String> = emptySet(),
    val swapChainsLoaded: Boolean = false,

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
     * پل: مبدأ و مقصد روی دو شبکهٔ متفاوت.
     *
     * درخواست و پاسخِ `/quote` دقیقاً همان تبدیلِ درون‌زنجیره‌ای است و ریلِ اجرا هم مشکلی ندارد،
     * چون تراکنشِ امضاشده فقط روی **شبکهٔ مبدأ** ارسال می‌شود. فرقِ واقعی در تسویه است و در
     * [isBridgeSettlementPending] توضیح داده شده.
     */
    val isBridge: Boolean
        get() {
            // اولویت با چیزی است که خودِ مسیر اعلام کرده؛ اگر سرور شبکه‌ها را برنگرداند، انتخابِ
            // کاربر همان اطلاعات را دارد.
            readyRoute?.let { route ->
                if (route.fromNetwork != null && route.toNetwork != null) return route.isBridge
            }
            val from = payToken?.option?.networkId ?: return false
            val to = receiveToken?.networkId ?: return false
            return !from.equals(to, ignoreCase = true)
        }

    /** نامِ پلِ زیرین برای نمایش (`lifiIntents`، `near`، …). */
    val bridgeTool: String?
        get() = readyRoute?.tool?.takeIf { isBridge && it.isNotBlank() }

    // ── آدرسِ مقصد ────────────────────────────────────────────────────────────

    /**
     * آیا برای این جفت، آدرسِ مقصد **اجباری** است.
     *
     * فقط وقتی دو سر دو قالبِ آدرسِ متفاوت دارند: آدرسِ EVM هگز است و آدرسِ ترون base58، پس
     * پیش‌فرض‌گرفتنِ آدرسِ فرستنده روی زنجیرهٔ مقصد یعنی فرستادنِ پول به جایی که هیچ کلیدی
     * نمی‌تواند خرجش کند. برای EVM→EVM همان رفتارِ قبلی برقرار است و چیزی لازم نیست.
     */
    val requiresDestinationAddress: Boolean
        get() = isCrossAddressFamily(payNetworkType, receiveNetworkType)

    /** آدرسِ مقصدی که می‌شود به سرور فرستاد: خالی نیست و اعتبارسنجیِ محلی را رد کرده. */
    val usableDestinationAddress: String?
        get() = destinationInput.trim()
            .takeIf { it.isNotEmpty() && destinationError == null }

    /** آیا مقصد همان کیف‌پولِ خودِ کاربر است (پیش‌فرضِ امن). */
    val destinationIsOwnWallet: Boolean
        get() = sameSwapAddress(usableDestinationAddress, destinationOwnAddress)

    /**
     * گیرنده‌ای که **سرور** اعلام کرده مسیر به آن پرداخت می‌کند — نه چیزی که کلاینت فرستاده.
     * اولویت با خودِ مسیر است، چون `tx` مالِ همان است.
     */
    val quotedRecipient: String?
        get() = readyRoute?.toAddress
            ?: (quoteState as? SwapQuoteState.Ready)?.quote?.quotedToAddress

    /**
     * ⚠️ خروجی به کیف‌پولِ دیگری می‌رود. باید **قبل از** امضا دیده شود؛ این تنها موقعیتی است که
     * کاربر دارایی‌اش را جایی می‌فرستد که خودش انتخابش کرده و ممکن است اشتباه باشد.
     */
    val recipientIsElsewhere: Boolean
        get() {
            val recipient = quotedRecipient ?: usableDestinationAddress ?: return false
            val self = destinationOwnAddress
            // مبدأ و مقصد وقتی هم‌خانواده‌اند با آدرسِ فرستنده مقایسه می‌شوند؛ در حالتِ بین‌خانوادگی
            // آدرسِ فرستنده اصلاً قابلِ مقایسه نیست و ملاک، آدرسِ خودِ کاربر روی شبکهٔ مقصد است.
            if (self != null) return !sameSwapAddress(recipient, self)
            return !sameSwapAddress(recipient, senderAddress)
        }

    val destinationNetworkName: String?
        get() = receiveToken?.networkName?.takeIf { it.isNotBlank() }

    /**
     * آیا شبکه‌ای که کاربر انتخاب کرده اصلاً تبدیل‌پذیر است. تا وقتی [swapChainsLoaded] نشده
     * `true` برمی‌گرداند — نبودِ پاسخ دلیلِ بستنِ فلو نیست.
     */
    fun isNetworkSwappable(networkId: String): Boolean =
        !swapChainsLoaded || swappableNetworkIds.contains(networkId.lowercase(Locale.US))

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

    /**
     * جفتِ بین‌خانوادگی بدونِ آدرسِ مقصدِ معتبر اصلاً استعلام نمی‌شود: سرور آن را با ۴۰۰ِ نام‌دار
     * رد می‌کند، و مهم‌تر این‌که آدرسِ مقصد باید **قبل از** استعلام جمع شود — کشِ ۱۵ ثانیه‌ای
     * سرور به ازای هر مقصد جداست.
     */
    val destinationReady: Boolean
        get() = !requiresDestinationAddress || usableDestinationAddress != null

    val canRequestQuote: Boolean
        get() = payToken != null &&
            receiveToken != null &&
            amountRaw.signum() > 0 &&
            !exceedsBalance &&
            destinationReady

    val canContinueFromAmount: Boolean
        get() = canRequestQuote && quoteState is SwapQuoteState.Ready

    val canConfirm: Boolean
        get() = canContinueFromAmount &&
            prepareState !is SwapPrepareState.Loading &&
            fee.selected?.gasPrice != null

    /**
     * ⚠️ پل در **دو پا** تسویه می‌شود: پای مبدأ در چند ثانیه تأیید می‌شود، پای مقصد چند دقیقه بعد
     * می‌نشیند. سرور هنوز پای مقصد را دنبال نمی‌کند، پس تأییدِ تراکنشِ مبدأ **به معنیِ رسیدنِ پول
     * نیست** و نباید «انجام شد» نمایش داده شود.
     */
    val isBridgeSettlementPending: Boolean
        get() = isBridge && execution?.outcome is SwapExecutionOutcome.Completed

    companion object {
        /** ۰٫۵٪ — همان پیش‌فرضی که سرور برای مسیریابی استفاده می‌کند. */
        const val DEFAULT_SLIPPAGE_BPS = 50
        val SLIPPAGE_CHOICES = listOf(10, 50, 100, 300)
        const val DEFAULT_QUOTE_TTL_MS = 15_000L
    }
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

    /**
     * انتخاب از فهرستِ دریافت. ورودی `AssetItem` است چون همان کامپوننتِ مشترکِ ارسال آن را
     * منتشر می‌کند؛ سرگروه‌ها قبل از رسیدن به این‌جا در شیتِ شبکه باز شده‌اند.
     */
    data class ReceiveAssetSelected(val asset: AssetItem) : SwapEvent

    data class ImportQueryChanged(val query: String) : SwapEvent
    data object ImportSubmitted : SwapEvent

    /** آدرسِ گیرندهٔ خروجی. متن دست‌نخورده می‌رسد: base58 ترون به حروف حساس است. */
    data class DestinationAddressChanged(val address: String) : SwapEvent
    data object DestinationEditorToggled : SwapEvent
    /** برگشت به آدرسِ خودِ کیف‌پول روی شبکهٔ مقصد. */
    data object DestinationResetToOwnWallet : SwapEvent

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
