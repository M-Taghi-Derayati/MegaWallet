package com.mtd.domain.model

import java.math.BigDecimal
import java.math.BigInteger

/**
 * Phase 4 — Multi-route swap domain models (`/api/v1/swap`).
 *
 * Contract constraints encoded here:
 *  - `toAmount.{gross,net,min}`, `fees.*`, and `estimatedGas.native/costInToToken` are raw base units
 *    as [BigInteger]. **[SwapToAmount.net] is what the wallet actually receives — always display it**
 *    and never re-derive it as `gross − platformBps`.
 *  - A quote is valid for a **strict [SwapQuote.ttlMs]** (default 15s); re-quote on expiry.
 *  - `prepare` returns an **ordered** [SwapPrepareResult.transactions] list (optional approve, then swap)
 *    to sign + submit in order. Non-custodial: the client signs locally.
 */

/** `GET /api/v1/swap/providers` — active strategies + platform fee + quote TTL. */
data class SwapProviders(
    val providers: List<String>,
    val platformFeeBps: Int?,
    val platformFeeCollected: Boolean?,
    val quoteTtlMs: Long?
)

data class SwapToAmount(
    val gross: BigInteger,
    val net: BigInteger,
    val min: BigInteger
)

/**
 * کارمزدِ پلتفرم برای یک مسیر.
 *
 * [platformBps] فقط «نرخِ پیکربندی‌شده» است، نه سندِ این‌که چیزی برداشته شده — اپراتور می‌تواند آن را
 * در زمانِ اجرا عوض کند و بین دو استعلامِ پشت‌سرهم فرق کند، پس هرگز کش یا hard-code نمی‌شود و از
 * *همین پاسخ* خوانده می‌شود. تنها [collected] می‌گوید کارمزد واقعاً روی زنجیره برداشته شده؛ تا وقتی
 * `false` است `platformCommission` صفر و `toAmount.net == toAmount.gross` است.
 *
 * [uncollectedCommission] صرفاً اطلاعاتی است و **هرگز** نباید از مبلغِ دریافتی کم شود.
 */
data class SwapFees(
    val platformBps: Int?,
    val collected: Boolean,
    val platformCommission: BigInteger?,
    val uncollectedCommission: BigInteger?,
    val grossOutput: BigInteger?,
    val netOutput: BigInteger?
)

data class SwapEstimatedGas(
    val native: BigInteger?,
    val costInToToken: BigInteger?,
    val costUsd: Double?
)

/** An unsigned transaction template (approve or swap) to sign + broadcast locally. */
data class SwapTx(
    val to: String,
    val data: String,
    val value: String?     // hex-quantity string (e.g. "0x0"); not a raw base-unit amount
)

data class SwapRoute(
    val rank: Int?,
    val isBestReturn: Boolean,
    val provider: String?,
    val fromNetwork: String?,
    val toNetwork: String?,
    /** نامِ پلِ زیرین (`lifiIntents`، `near`، …) برای نمایش. */
    val tool: String?,
    /**
     * آدرسی که `tx`ِ **همین مسیر** خروجی را به آن می‌پردازد.
     *
     * وقتی با `userAddress` فرق دارد باید نمایش داده شود: یعنی پول به کیف‌پولِ دیگری می‌رود. در
     * `prepare` هم اگر آدرسِ مقصد فرستاده شود باید دقیقاً همین باشد، وگرنه calldata برای گیرندهٔ
     * دیگری ساخته شده است.
     */
    val toAddress: String?,
    val toAmount: SwapToAmount,
    val fees: SwapFees?,
    val estimatedGas: SwapEstimatedGas?,
    val allowanceTarget: String?,
    val tx: SwapTx?
) {
    /**
     * مسیرِ بین‌زنجیره‌ای (پل). درخواست و پاسخ دقیقاً همان `/quote` است؛ فرقش در **تسویه** است:
     * پل دو پا دارد و سرور هنوز پای مقصد را دنبال نمی‌کند، پس تأییدِ تراکنشِ مبدأ به‌هیچ‌وجه به
     * معنیِ رسیدنِ پول نیست.
     *
     * وقتی سرور شبکه‌ها را در پاسخ برنگردانده، مقایسه انجام نمی‌شود و `false` برمی‌گردد؛
     * تشخیصِ نهایی در آن حالت به شبکه‌های خودِ درخواست واگذار می‌شود.
     */
    val isBridge: Boolean
        get() {
            val from = fromNetwork ?: return false
            val to = toNetwork ?: return false
            return !from.equals(to, ignoreCase = true)
        }
}

/** `GET /api/v1/swap/quote` — ranked routes with a hard [ttlMs] expiry. */
data class SwapQuote(
    val requestId: String?,
    val routes: List<SwapRoute>,
    val bestRoute: SwapRoute?,
    val platformFeeBps: Int?,
    /** Whether the commission on [bestRoute] was really withheld. Per-response; never cache it. */
    val platformFeeCollected: Boolean?,
    val expiresAt: String?,
    val ttlMs: Long?,
    /** آدرسِ کیف‌پولی که سرور از آن استعلام گرفته (`request.userAddress`). */
    val quotedUserAddress: String?,
    /**
     * گیرندهٔ خروجی طبق **پاسخِ سرور** (`request.toAddress`) — نه چیزی که کلاینت فرستاده.
     *
     * سرور ممکن است آن را به `userAddress` پیش‌فرض کرده باشد. خواندنش از پاسخ یعنی آدرسی که به
     * کاربر نشان می‌دهیم همانی است که مسیر واقعاً برایش ساخته شده.
     */
    val quotedToAddress: String?
)

/**
 * Inputs for a swap quote. `amountRaw` is the raw base-unit input amount.
 *
 * [userAddress] is **required** by the server: routes are quoted *for a specific wallet* and the
 * returned `tx.data` is built for that address. A placeholder would produce a transaction that
 * pays out to someone else, so there is no default — the caller must resolve a real address or
 * not ask for a quote at all.
 */
data class SwapQuoteRequest(
    val fromNetwork: String,
    val toNetwork: String,
    val fromToken: String,
    val toToken: String,
    val amountRaw: BigInteger,
    val userAddress: String,
    /**
     * آدرسی که خروجی به آن پرداخت می‌شود؛ جدا از [userAddress] که مبدأ است.
     *
     * برای تبدیلِ درون‌زنجیره‌ای و پلِ EVM→EVM اختیاری است و سرور آن را [userAddress] می‌گیرد. برای
     * TRON↔EVM **اجباری** است: قالبِ دو طرف فرق دارد (`0x…` در برابر base58ِ `T…`)، پس آدرسِ مبدأ
     * روی زنجیرهٔ مقصد اصلاً آدرس نیست و چیزی برای پیش‌فرض‌گرفتن وجود ندارد. پل‌زدن به آدرسی که
     * هیچ کلیدی نمی‌تواند خرجش کند یعنی سوختنِ دارایی، پس این مقدار حدس زده نمی‌شود.
     */
    val toAddress: String? = null,
    val slippage: Double? = null
)

/** `POST /api/v1/swap/prepare` — ordered, non-custodial transaction bundle to sign in order. */
data class SwapPrepareResult(
    val requestId: String?,
    val transactions: List<SwapTx>
)

/**
 * `GET /api/v1/swap/chains` — جایی که تبدیل/پل واقعاً کار می‌کند.
 *
 * هر شبکه‌ای که در این فهرست نیست نقدینگی ندارد (همهٔ تست‌نت‌ها این‌طورند) و استعلامش همیشه با
 * ۴۲۲ برمی‌گردد. پس ورودیِ تبدیل برای آن شبکه اصلاً نمایش داده نمی‌شود — نه این‌که خطای خام
 * نشان داده شود. این محدودیت **فقط** برای تبدیل است؛ تست‌نت‌ها در بقیهٔ اپ دست‌نخورده می‌مانند.
 */
data class SwapChain(
    val networkId: String,
    val chainId: Long?,
    val name: String?,
    val key: String?,
    val tokenCount: Int?
)

/**
 * یک توکن از جهانِ قابل‌تبدیل (`GET /api/v1/swap/tokens`) — نه از باندلِ امضاشده.
 *
 * باندل ~۲۷ دارایی **کیوریت‌شده** دارد (همان‌هایی که کارمزدشان اسپانسر می‌شود)؛ این فهرست ~۱۱۳۳۳
 * توکن است. یعنی نبودنِ یک توکن در باندل هیچ ربطی به قابلِ‌تبدیل‌بودنش ندارد.
 */
data class SwapToken(
    /** `null` یعنی کیوریت‌نشده. تنها سیگنالِ معتبر برای «اسپانسر نکن». */
    val id: String?,
    val symbol: String,
    val name: String,
    val decimals: Int,
    val networkId: String,
    /** `null` فقط برای ارزِ بومیِ شبکه. */
    val contractAddress: String?,
    val iconUrl: String?,
    /** `null` عمدی است (قیمتِ نامعتبرِ upstream حذف شده) — صفر نیست. */
    val priceUsd: BigDecimal?,
    val imported: Boolean,
    /**
     * **نشانِ منشأ، نه نشانِ ایمنی.** `true` یعنی «فهرستی که به آن اتکا می‌کنیم این قرارداد را روی
     * این شبکه منتشر کرده» — نه ممیزی، نه تأیید، نه توصیهٔ سرمایه‌گذاری، و هرگز نباید این‌طور
     * نمایش داده شود.
     *
     * `null` با `false` یکی نیست: `null` یعنی سرور چیزی نگفته، پس نه نشان داده می‌شود نه هشدار.
     * `false` یعنی هیچ فهرستی منتشرش نکرده — که برای یک توکنِ واقعیِ تازه هم دقیقاً همین‌طور است،
     * پس فقط هشدارِ خنثی می‌گیرد و هیچ‌وقت پنهان یا مسدود نمی‌شود.
     */
    val verified: Boolean?,
    /** رتبهٔ ارزشِ بازار؛ `0`/`null` یعنی بی‌رتبه. ترتیبِ فهرست خودش رتبه‌بندی‌شده است. */
    val marketCapRank: Int?
) {
    /**
     * ⚠️ قاعدهٔ سخت: توکنِ کیوریت‌نشده هرگز نباید مسیرِ گس‌لس/اسپانسر بگیرد. قابلِ تبدیل و انتقال
     * هست، ولی کارمزدش را ما نمی‌دهیم — و پیشنهادِ اشتباهِ آن یعنی تراکنشی که سمتِ سرور رد می‌شود.
     */
    val isSponsorable: Boolean get() = id != null && !imported
}
