package com.mtd.domain.usecase.swap

import com.mtd.domain.interfaceRepository.ISwapRepository
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.SwapChain
import com.mtd.domain.model.SwapPrepareResult
import com.mtd.domain.model.SwapProviders
import com.mtd.domain.model.SwapQuote
import com.mtd.domain.model.SwapQuoteRequest
import com.mtd.domain.model.SwapRoute
import com.mtd.domain.model.SwapToken
import javax.inject.Inject

class GetSwapProvidersUseCase @Inject constructor(
    private val swapRepository: ISwapRepository
) {
    suspend operator fun invoke(): ResultResponse<SwapProviders> = swapRepository.getProviders()
}

class GetSwapChainsUseCase @Inject constructor(
    private val swapRepository: ISwapRepository
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): ResultResponse<List<SwapChain>> =
        swapRepository.getChains(forceRefresh)
}

class GetSwapTokensUseCase @Inject constructor(
    private val swapRepository: ISwapRepository
) {
    /** [query] `null`/خالی ⇒ فهرستِ پیش‌فرض به ترتیبِ محبوبیت. ترتیبِ خروجی دست‌نخورده می‌ماند. */
    suspend operator fun invoke(
        networkId: String,
        query: String? = null,
        limit: Int? = null
    ): ResultResponse<List<SwapToken>> = swapRepository.getTokens(networkId, query, limit)
}

/** جست‌وجوی دقیقِ آدرسِ قرارداد برای «افزودن با چسباندن آدرس». */
class FindSwapTokenUseCase @Inject constructor(
    private val swapRepository: ISwapRepository
) {
    suspend operator fun invoke(
        networkId: String,
        contractAddress: String
    ): ResultResponse<SwapToken> = swapRepository.findToken(networkId, contractAddress)
}

class GetSwapQuoteUseCase @Inject constructor(
    private val swapRepository: ISwapRepository
) {
    suspend operator fun invoke(request: SwapQuoteRequest): ResultResponse<SwapQuote> =
        swapRepository.getQuote(request)
}

class PrepareSwapUseCase @Inject constructor(
    private val swapRepository: ISwapRepository
) {
    /**
     * @param route مسیرِ انتخاب‌شده در UI؛ `null` یعنی سرور بهترین مسیر را بردارد. باید با همان
     *   [request]ی صدا زده شود که quote گرفته شده، وگرنه شبیه‌سازیِ سمتِ سرور روی ورودیِ دیگری
     *   انجام می‌شود. گیرندهٔ خروجی هم از خودِ مسیر برداشته می‌شود، نه از حدسِ لایهٔ بالا.
     */
    suspend operator fun invoke(
        request: SwapQuoteRequest,
        route: SwapRoute?
    ): ResultResponse<SwapPrepareResult> =
        swapRepository.prepare(request, route?.provider, route?.toAddress)
}
