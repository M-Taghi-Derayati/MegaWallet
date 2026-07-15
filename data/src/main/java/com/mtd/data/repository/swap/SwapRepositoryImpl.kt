package com.mtd.data.repository.swap

import com.mtd.data.dto.SwapEstimatedGasDto
import com.mtd.data.dto.SwapFeesDto
import com.mtd.data.dto.SwapPrepareRequestDto
import com.mtd.data.dto.SwapProvidersDto
import com.mtd.data.dto.SwapQuoteResponseDto
import com.mtd.data.dto.SwapRouteDto
import com.mtd.data.dto.SwapToAmountDto
import com.mtd.data.dto.SwapTxDto
import com.mtd.data.network.relayApiError
import com.mtd.data.service.SwapApiService
import com.mtd.data.utils.safeApiCall
import com.mtd.domain.interfaceRepository.ISwapRepository
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.SwapEstimatedGas
import com.mtd.domain.model.SwapFees
import com.mtd.domain.model.SwapPrepareResult
import com.mtd.domain.model.SwapProviders
import com.mtd.domain.model.SwapQuote
import com.mtd.domain.model.SwapQuoteRequest
import com.mtd.domain.model.SwapRoute
import com.mtd.domain.model.SwapToAmount
import com.mtd.domain.model.SwapTx
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

    override suspend fun getProviders(): ResultResponse<SwapProviders> = safeApiCall {
        val response = swapApiService.getProviders()
        val body = response.body()
        if (!response.isSuccessful || body == null) throw relayApiError(response, "swap providers failed (${response.code()})")
        body.toDomain()
    }

    override suspend fun getQuote(request: SwapQuoteRequest): ResultResponse<SwapQuote> = safeApiCall {
        val response = swapApiService.getQuote(
            fromNetwork = request.fromNetwork,
            toNetwork = request.toNetwork,
            fromToken = request.fromToken,
            toToken = request.toToken,
            amountRaw = request.amountRaw.toString(),
            slippage = request.slippage,
            userAddress = request.userAddress
        )
        val body = response.body()
        if (!response.isSuccessful || body == null) throw relayApiError(response, "swap quote failed (${response.code()})")
        body.toDomain()
    }

    override suspend fun prepare(
        request: SwapQuoteRequest,
        routeProvider: String?
    ): ResultResponse<SwapPrepareResult> = safeApiCall {
        val response = swapApiService.prepare(
            SwapPrepareRequestDto(
                fromNetwork = request.fromNetwork,
                toNetwork = request.toNetwork,
                fromToken = request.fromToken,
                toToken = request.toToken,
                amountRaw = request.amountRaw,
                slippage = request.slippage,
                userAddress = request.userAddress,
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
        quoteTtlMs = quoteTtlMs
    )

    private fun SwapQuoteResponseDto.toDomain() = SwapQuote(
        requestId = requestId,
        routes = routes.orEmpty().map { it.toDomain() },
        bestRoute = bestRoute?.toDomain(),
        platformFeeBps = platformFeeBps,
        expiresAt = expiresAt,
        ttlMs = ttlMs
    )

    private fun SwapRouteDto.toDomain() = SwapRoute(
        rank = rank,
        isBestReturn = isBestReturn ?: false,
        provider = provider,
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

    private fun SwapFeesDto.toDomain() = SwapFees(
        platformBps = platformBps,
        platformCommission = platformCommission,
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
}
