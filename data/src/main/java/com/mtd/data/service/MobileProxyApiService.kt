package com.mtd.data.service

import com.mtd.data.dto.BalancesDto
import com.mtd.data.dto.BalancesRequestDto
import com.mtd.data.dto.BatchBalanceRequestDto
import com.mtd.data.dto.BatchNetworkBalanceDto
import com.mtd.data.dto.BroadcastDto
import com.mtd.data.dto.BroadcastRequestDto
import com.mtd.data.dto.FeeOptionsDto
import com.mtd.data.dto.HeldTokensRequestDto
import com.mtd.data.dto.HistoryRequestDto
import com.mtd.data.dto.HistoryResponseDto
import com.mtd.data.dto.MonitoringSubscribeRequestDto
import com.mtd.data.dto.MonitoringSubscribeResponseDto
import com.mtd.data.dto.NetworksDto
import com.mtd.data.dto.PrepareTxDto
import com.mtd.data.dto.PrepareContractCallRequestDto
import com.mtd.data.dto.PrepareTxRequestDto
import com.mtd.data.dto.ProxyEnvelope
import com.mtd.data.dto.TokenListDto
import com.mtd.data.dto.TokenPricesDto
import com.mtd.data.dto.TokenPricesRequestDto
import com.mtd.data.dto.TokenResolveDto
import com.mtd.data.dto.TxDetailDto
import com.mtd.data.dto.TxStatusDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Mobile Blockchain Proxy — `/api/mobile/v1` (§1.7). Polymorphic over EVM/TVM/UTXO; every
 * per-network route returns the BM-33 [ProxyEnvelope]. `POST /history` is intentionally absent
 * here (different shape + cursor) and is added in Phase 2.
 */
interface MobileProxyApiService {

    @GET("api/mobile/v1/networks")
    suspend fun networks(): Response<ProxyEnvelope<NetworksDto>>

    @POST("api/mobile/v1/networks/{networkId}/balances")
    suspend fun balances(
        @Path("networkId") networkId: String,
        @Body body: BalancesRequestDto
    ): Response<ProxyEnvelope<BalancesDto>>

    @POST("api/mobile/v1/balances/batch")
    suspend fun batchBalances(
        @Body body: BatchBalanceRequestDto
    ): Response<ProxyEnvelope<Map<String, BatchNetworkBalanceDto>>>

    // NOT BM-33-enveloped — a page spans many networks.
    @POST("api/mobile/v1/history")
    suspend fun history(
        @Body body: HistoryRequestDto
    ): Response<HistoryResponseDto>

    // Batch monitoring enrollment (TASK-32). NOT BM-33-enveloped — one call spans many networks;
    // durable + idempotent; max 25 pairs/call (chunk beyond). Auth: Bearer JWT (proxy:write-ish).
    @POST("api/mobile/v1/monitoring/subscribe")
    suspend fun monitoringSubscribe(
        @Body body: MonitoringSubscribeRequestDto
    ): Response<MonitoringSubscribeResponseDto>

    // §5 — کشفِ توکن. باندلِ امضاشده فقط مجموعهٔ curated را می‌آورد؛ بقیهٔ جهانِ توکن‌ها سمتِ سرور
    // است و کلاینت به‌درخواست کشفشان می‌کند. توکنی که `id: null` برمی‌گردد در کاتالوگِ ما نیست و
    // فقط با `contractAddress` قابلِ استفاده است — و هرگز از مسیرِ gasless (فهرستِ جداگانه و curated).

    /**
     * **فهرستِ پیش‌فرضِ** یک شبکه با یک فراخوانی: کاتالوگِ امضاشده + رتبهٔ ارزشِ بازارِ همان زنجیره
     * + توکن‌های واقعاً داشتهٔ [address]، یکتاشده و مرتب‌شده.
     *
     * ترتیبِ آرایه حفظ شود — verified اول، بعد رتبه. جایگزینِ فهرستی است که خودمان از باندل +
     * held سرِ هم می‌کردیم.
     */
    @GET("api/mobile/v1/networks/{networkId}/tokens")
    suspend fun defaultTokens(
        @Path("networkId") networkId: String,
        @Query("address") address: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<ProxyEnvelope<TokenListDto>>

    /** توکن‌هایی که این آدرس قبلاً با آن‌ها تراکنش داشته — از تاریخچهٔ ایندکس‌شده، بدونِ تماسِ زنجیره‌ای. */
    @POST("api/mobile/v1/networks/{networkId}/tokens/held")
    suspend fun heldTokens(
        @Path("networkId") networkId: String,
        @Body body: HeldTokensRequestDto
    ): Response<ProxyEnvelope<TokenListDto>>

    /** جست‌وجو در کلِ جهانِ توکن‌ها با نماد / نام / آدرسِ دقیقِ قرارداد. */
    @GET("api/mobile/v1/networks/{networkId}/tokens/search")
    suspend fun searchTokens(
        @Path("networkId") networkId: String,
        @Query("q") query: String,
        @Query("limit") limit: Int? = null
    ): Response<ProxyEnvelope<TokenListDto>>

    /**
     * همان جست‌وجو، ولی روی همهٔ زنجیره‌ها. هر عنصرِ نتیجه `networkId` خودش را همراه دارد —
     * برخلافِ مسیرِ per-network، این‌جا فراخوان شبکه را از قبل نمی‌داند.
     */
    @GET("api/mobile/v1/tokens/search")
    suspend fun searchTokensAllNetworks(
        @Query("q") query: String,
        @Query("limit") limit: Int? = null
    ): Response<ProxyEnvelope<TokenListDto>>

    /**
     * یافتنِ یک آدرسِ قرارداد روی هر زنجیره‌ای که می‌شناسد.
     *
     * می‌تواند **چند** نتیجه بدهد: همان آدرس روی چند زنجیرهٔ EVM مستقر می‌شود و این حالت نادر
     * نیست. انتخاب با کاربر است، نه اولین عنصر.
     */
    @GET("api/mobile/v1/tokens/resolve/{address}")
    suspend fun resolveToken(
        @Path("address") address: String
    ): Response<ProxyEnvelope<TokenListDto>>

    /**
     * همان resolve وقتی شبکه از قبل معلوم است: catalog → فهرستِ آینه‌ای → `eth_call` روی خودِ
     * قرارداد. یعنی هر ERC-20ِ واقعی import می‌شود، نه فقط فهرست‌شده‌ها.
     *
     * `404 ASSET_NOT_FOUND` یعنی در آن آدرس توکنِ خواندنی نیست — import باید رد شود، نه اینکه
     * `decimals` حدس زده شود. [walletAddress] در همین فراخوانی برای مانیتورینگ ثبت می‌شود.
     *
     * ⚠️ آدرس عیناً پاس داده می‌شود؛ `@Path` را encode نکنید و lowercase هم نکنید — base58ِ ترون
     * حساس به حروف است و شکلِ lowercase آن قابلِ بازسازی نیست.
     */
    @GET("api/mobile/v1/networks/{networkId}/tokens/resolve/{contractAddress}")
    suspend fun resolveTokenOnNetwork(
        @Path("networkId") networkId: String,
        @Path("contractAddress") contractAddress: String,
        @Query("address") walletAddress: String? = null
    ): Response<ProxyEnvelope<TokenResolveDto>>

    /**
     * قیمت بر اساسِ **آدرسِ قرارداد** — تنها مسیری که به توکنِ افزودهٔ کاربر (و به کلِ ترون) قیمت
     * می‌دهد؛ `GET /api/v1/prices` با نماد کار می‌کند و فقط ارزهای اصلی را می‌شناسد.
     *
     * پاسخِ ناقص عادی است: `missing`ِ ناخالی شکستِ درخواست نیست.
     */
    @POST("api/mobile/v1/tokens/prices")
    suspend fun tokenPrices(
        @Body body: TokenPricesRequestDto
    ): Response<ProxyEnvelope<TokenPricesDto>>

    @GET("api/mobile/v1/networks/{networkId}/fees/options")
    suspend fun feeOptions(
        @Path("networkId") networkId: String,
        // Optional transaction context — enables context-aware fee estimation (EVM L1+L2, TRON energy).
        // All omitted when null → backend falls back to its blind/default estimate (backward compatible).
        @Query("sender") sender: String? = null,
        @Query("recipient") recipient: String? = null,
        @Query("tokenAddress") tokenAddress: String? = null,
        @Query("amount") amount: String? = null,
        @Query("contractData") contractData: String? = null,
        @Query("vbytes") vbytes: Int? = null
    ): Response<ProxyEnvelope<FeeOptionsDto>>

    @POST("api/mobile/v1/networks/{networkId}/transactions/prepare")
    suspend fun prepareTransaction(
        @Path("networkId") networkId: String,
        @Body body: PrepareTxRequestDto
    ): Response<ProxyEnvelope<PrepareTxDto>>

    @POST("api/mobile/v1/networks/{networkId}/transactions/prepare-contract-call")
    suspend fun prepareContractCall(
        @Path("networkId") networkId: String,
        @Body body: PrepareContractCallRequestDto
    ): Response<ProxyEnvelope<PrepareTxDto>>

    @POST("api/mobile/v1/networks/{networkId}/transactions/broadcast")
    suspend fun broadcastTransaction(
        @Path("networkId") networkId: String,
        @Body body: BroadcastRequestDto
    ): Response<ProxyEnvelope<BroadcastDto>>

    @GET("api/mobile/v1/networks/{networkId}/transactions/{txId}/status")
    suspend fun transactionStatus(
        @Path("networkId") networkId: String,
        @Path("txId") txId: String
    ): Response<ProxyEnvelope<TxStatusDto>>

    // On-demand full fee/energy/gas — proxy of gettransactioninfobyid (TRON) /
    // eth_getTransactionReceipt (EVM). Call LAZILY on tx-open only (never per list row); settled
    // results are cached server-side. PENDING → feeRaw:null.
    @GET("api/mobile/v1/networks/{networkId}/transactions/{txId}/detail")
    suspend fun transactionDetail(
        @Path("networkId") networkId: String,
        @Path("txId") txId: String
    ): Response<ProxyEnvelope<TxDetailDto>>
}
