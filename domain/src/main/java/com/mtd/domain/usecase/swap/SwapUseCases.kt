package com.mtd.domain.usecase.swap

import com.mtd.domain.interfaceRepository.ISwapRepository
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.SwapPrepareResult
import com.mtd.domain.model.SwapProviders
import com.mtd.domain.model.SwapQuote
import com.mtd.domain.model.SwapQuoteRequest
import javax.inject.Inject

class GetSwapProvidersUseCase @Inject constructor(
    private val swapRepository: ISwapRepository
) {
    suspend operator fun invoke(): ResultResponse<SwapProviders> = swapRepository.getProviders()
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
     * @param routeProvider مسیرِ انتخاب‌شده در UI؛ `null` یعنی سرور بهترین مسیر را بردارد. باید با
     *   همان [request]ی صدا زده شود که quote گرفته شده، وگرنه شبیه‌سازیِ سمتِ سرور روی ورودیِ دیگری
     *   انجام می‌شود.
     */
    suspend operator fun invoke(
        request: SwapQuoteRequest,
        routeProvider: String? = null
    ): ResultResponse<SwapPrepareResult> = swapRepository.prepare(request, routeProvider)
}
