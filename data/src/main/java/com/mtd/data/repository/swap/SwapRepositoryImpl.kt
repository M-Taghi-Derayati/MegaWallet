package com.mtd.data.repository.swap

import com.mtd.data.dto.SwapChainDto
import com.mtd.data.dto.SwapEstimatedGasDto
import com.mtd.data.dto.SwapFeesDto
import com.mtd.data.dto.SwapPrepareRequestDto
import com.mtd.data.dto.SwapProvidersDto
import com.mtd.data.dto.SwapQuoteResponseDto
import com.mtd.data.dto.SwapRouteDto
import com.mtd.data.dto.SwapToAmountDto
import com.mtd.data.dto.SwapTokenDto
import com.mtd.data.dto.SwapTxDto
import com.mtd.data.network.relayApiError
import com.mtd.data.service.SwapApiService
import com.mtd.data.utils.safeApiCall
import com.mtd.domain.interfaceRepository.ISwapRepository
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.SwapChain
import com.mtd.domain.model.SwapEstimatedGas
import com.mtd.domain.model.SwapFees
import com.mtd.domain.model.SwapPrepareResult
import com.mtd.domain.model.SwapProviders
import com.mtd.domain.model.SwapQuote
import com.mtd.domain.model.SwapQuoteRequest
import com.mtd.domain.model.SwapRoute
import com.mtd.domain.model.SwapToAmount
import com.mtd.domain.model.SwapToken
import com.mtd.domain.model.SwapTx
import com.mtd.domain.model.error.ApiError
import com.mtd.domain.model.error.ApiException
import com.mtd.domain.model.swap.sameSwapAddress
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4 — Multi-route swap (`/api/v1/swap`). Unwraps the relayer's `{ ok, … }` responses and maps
 * failures via [relayApiError] (422 ⇒ [com.mtd.domain.model.error.ApiError.SimulationReverted] /
 * [com.mtd.domain.model.error.ApiError.SwapNoRoutes]). The `x-idempotency-key` on `/prepare` is
 * injected by the IdempotencyInterceptor — not threaded through here.
 */
@Singleton
class SwapRepositoryImpl @Inject constructor(
    private val swapApiService: SwapApiService
) : ISwapRepository {

    /** `/swap/chains` تقریباً هرگز عوض نمی‌شود؛ نگه‌داشتنش جلوی یک درخواست در هر resume را می‌گیرد. */
    private val chainsLock = Mutex()
    private var cachedChains: List<SwapChain>? = null
    private var cachedChainsAtMs: Long = 0L

    override suspend fun getProviders(): ResultResponse<SwapProviders> = safeApiCall {
        val response = swapApiService.getProviders()
        val body = response.body()
        if (!response.isSuccessful || body == null) throw relayApiError(response, "swap providers failed (${response.code()})")
        body.toDomain()
    }

    override suspend fun getChains(forceRefresh: Boolean): ResultResponse<List<SwapChain>> = safeApiCall {
        chainsLock.withLock {
            val cached = cachedChains
            val isFresh = System.currentTimeMillis() - cachedChainsAtMs < CHAINS_CACHE_TTL_MS
            if (!forceRefresh && cached != null && isFresh) return@withLock cached

            val response = swapApiService.getChains()
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                // شکستِ تازه‌سازی نباید فهرستِ سالمِ قبلی را دور بریزد، وگرنه یک قطعیِ لحظه‌ای
                // ورودیِ تبدیل را روی همهٔ شبکه‌ها می‌بندد.
                cached?.let { return@withLock it }
                throw relayApiError(response, "swap chains failed (${response.code()})")
            }

            val chains = body.chains.orEmpty().mapNotNull { it.toDomainOrNull() }
            cachedChains = chains
            cachedChainsAtMs = System.currentTimeMillis()
            chains
        }
    }

    override suspend fun getTokens(
        networkId: String,
        query: String?,
        limit: Int?
    ): ResultResponse<List<SwapToken>> = safeApiCall {
        val response = swapApiService.getTokens(
            networkId = networkId,
            query = query?.trim()?.takeIf { it.isNotEmpty() },
            limit = (limit ?: DEFAULT_TOKEN_LIMIT).coerceIn(1, MAX_TOKEN_LIMIT)
        )
        val body = response.body()
        if (!response.isSuccessful || body == null) throw relayApiError(response, "swap tokens failed (${response.code()})")
        // ترتیبِ سرور رتبه‌بندی‌شده است (تطبیقِ دقیقِ نماد اول) — مرتب‌سازیِ دوباره یعنی
        // بالا آمدنِ توکنِ بدلی که فقط نامش شبیه است.
        body.tokens.orEmpty().mapNotNull { it.toDomainOrNull(networkId) }
    }

    override suspend fun findToken(
        networkId: String,
        contractAddress: String
    ): ResultResponse<SwapToken> = safeApiCall {
        // آدرس دقیقاً همان‌طور که کاربر چسبانده فرستاده می‌شود: base58 ترون حساس به حروف است و
        // کوچک‌کردنش خواندنِ روی‌زنجیره را برای توکنِ خارج از فهرست غیرممکن می‌کند.
        val response = swapApiService.findToken(networkId, contractAddress.trim())
        val body = response.body()
        if (!response.isSuccessful || body == null) throw relayApiError(response, "swap token lookup failed (${response.code()})")

        (body.token ?: body.tokens?.firstOrNull())?.toDomainOrNull(networkId)
            ?: throw ApiException(
                apiError = ApiError.AssetNotFound,
                httpStatus = response.code(),
                reasonFa = "این توکن روی این شبکه قابل تبدیل نیست."
            )
    }

    override suspend fun getQuote(request: SwapQuoteRequest): ResultResponse<SwapQuote> = safeApiCall {
        val response = swapApiService.getQuote(
            fromNetwork = request.fromNetwork,
            toNetwork = request.toNetwork,
            fromToken = request.fromToken,
            toToken = request.toToken,
            amountRaw = request.amountRaw.toString(),
            userAddress = request.userAddress,
            // آدرس دقیقاً همان‌طور که آمده می‌رود؛ base58 ترون به حروف حساس است.
            toAddress = request.toAddress?.trim()?.takeIf { it.isNotEmpty() },
            slippage = request.slippage
        )
        val body = response.body()
        if (!response.isSuccessful || body == null) throw relayApiError(response, "swap quote failed (${response.code()})")
        body.toDomain()
    }

    override suspend fun prepare(
        request: SwapQuoteRequest,
        routeProvider: String?,
        routeToAddress: String?
    ): ResultResponse<SwapPrepareResult> = safeApiCall {
        val requested = request.toAddress?.trim()?.takeIf { it.isNotEmpty() }
        val onRoute = routeToAddress?.trim()?.takeIf { it.isNotEmpty() }

        // ناسازگاری یعنی calldata برای گیرندهٔ دیگری ساخته شده. سرور هم ۴۰۰ می‌دهد، ولی این‌جا
        // متوقف می‌شود چون رسیدنِ چنین درخواستی به شبکه یعنی استیتِ کلاینت با مسیرِ انتخاب‌شده
        // یکی نیست — و پولِ کاربر سرِ همان اختلاف به کیف‌پولِ اشتباه می‌رود.
        if (requested != null && onRoute != null && !sameSwapAddress(requested, onRoute)) {
            throw ApiException(
                apiError = ApiError.ValidationError,
                reasonFa = "آدرس مقصد با مسیر انتخاب‌شده هم‌خوان نیست؛ برای جلوگیری از ارسال به آدرس اشتباه، تبدیل انجام نشد."
            )
        }

        val response = swapApiService.prepare(
            SwapPrepareRequestDto(
                fromNetwork = request.fromNetwork,
                toNetwork = request.toNetwork,
                fromToken = request.fromToken,
                toToken = request.toToken,
                amountRaw = request.amountRaw,
                slippage = request.slippage,
                userAddress = request.userAddress,
                // مسیر حرفِ آخر را می‌زند: تراکنش برای همان گیرنده ساخته شده است.
                toAddress = onRoute ?: requested,
                provider = routeProvider
            )
        )
        val body = response.body()
        if (!response.isSuccessful || body == null) throw relayApiError(response, "swap prepare failed (${response.code()})")
        SwapPrepareResult(
            requestId = body.requestId,
            transactions = body.transactions.orEmpty().mapNotNull { it.toDomainOrNull() }
        )
    }

    private fun SwapProvidersDto.toDomain() = SwapProviders(
        providers = providers.orEmpty(),
        platformFeeBps = platformFeeBps,
        platformFeeCollected = platformFeeCollected,
        quoteTtlMs = quoteTtlMs
    )

    private fun SwapQuoteResponseDto.toDomain() = SwapQuote(
        requestId = requestId,
        routes = routes.orEmpty().map { it.toDomain() },
        bestRoute = bestRoute?.toDomain(),
        platformFeeBps = platformFeeBps,
        platformFeeCollected = platformFeeCollected,
        expiresAt = expiresAt,
        ttlMs = ttlMs,
        quotedUserAddress = request?.userAddress?.takeIf { it.isNotBlank() },
        quotedToAddress = request?.toAddress?.takeIf { it.isNotBlank() }
    )

    private fun SwapRouteDto.toDomain() = SwapRoute(
        rank = rank,
        isBestReturn = isBestReturn ?: false,
        provider = provider,
        fromNetwork = fromNetwork,
        toNetwork = toNetwork,
        tool = tool,
        toAddress = toAddress?.takeIf { it.isNotBlank() },
        toAmount = (toAmount ?: SwapToAmountDto()).toDomain(),
        fees = fees?.toDomain(),
        estimatedGas = estimatedGas?.toDomain(),
        allowanceTarget = allowanceTarget,
        tx = tx?.toDomainOrNull()
    )

    private fun SwapToAmountDto.toDomain() = SwapToAmount(
        gross = gross ?: BigInteger.ZERO,
        net = net ?: BigInteger.ZERO,
        min = min ?: BigInteger.ZERO
    )

    // `collected` absent ⇒ false: an older/partial payload must never be read as "we charged you".
    private fun SwapFeesDto.toDomain() = SwapFees(
        platformBps = platformBps,
        collected = collected ?: false,
        platformCommission = platformCommission,
        uncollectedCommission = uncollectedCommission,
        grossOutput = grossOutput,
        netOutput = netOutput
    )

    private fun SwapEstimatedGasDto.toDomain() = SwapEstimatedGas(
        native = native,
        costInToToken = costInToToken,
        costUsd = costUsd
    )

    private fun SwapTxDto.toDomainOrNull(): SwapTx? {
        val target = to?.takeIf { it.isNotBlank() } ?: return null
        return SwapTx(to = target, data = data.orEmpty(), value = value)
    }

    private fun SwapChainDto.toDomainOrNull(): SwapChain? {
        val id = networkId?.takeIf { it.isNotBlank() } ?: return null
        return SwapChain(networkId = id, chainId = chainId, name = name, key = key, tokenCount = tokenCount)
    }

    /**
     * ورودیِ ناقص کنار گذاشته می‌شود. [decimals] هرگز پیش‌فرض نمی‌گیرد: حدسِ ۱۸ روی توکنی که
     * ۶ رقم دارد مبلغ را ۱۰¹² برابر غلط می‌سازد — همان اشکالی که Base USDC داشت.
     */
    private fun SwapTokenDto.toDomainOrNull(requestedNetworkId: String): SwapToken? {
        val ticker = symbol?.takeIf { it.isNotBlank() } ?: return null
        val places = decimals?.takeIf { it >= 0 } ?: return null
        return SwapToken(
            id = id?.takeIf { it.isNotBlank() },
            symbol = ticker,
            name = name?.takeIf { it.isNotBlank() } ?: ticker,
            decimals = places,
            networkId = networkId?.takeIf { it.isNotBlank() } ?: requestedNetworkId,
            contractAddress = contractAddress?.takeIf { it.isNotBlank() },
            iconUrl = iconUrl?.takeIf { it.isNotBlank() },
            // قیمتِ نامفهوم به صفر تبدیل نمی‌شود؛ صفر یعنی «بی‌ارزش» و مجموعِ سبد را می‌خورد.
            priceUsd = priceUsd?.let { runCatching { BigDecimal(it.trim()) }.getOrNull() },
            imported = imported ?: (id == null),
            // نبودِ فیلد به `false` تبدیل نمی‌شود: «سرور چیزی نگفت» با «هیچ فهرستی منتشرش نکرده»
            // یکی نیست، و دومی است که هشدار می‌گیرد.
            verified = verified,
            marketCapRank = marketCapRank?.takeIf { it > 0 }
        )
    }

    private companion object {
        const val DEFAULT_TOKEN_LIMIT = 50
        const val MAX_TOKEN_LIMIT = 200
        const val CHAINS_CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    }
}
