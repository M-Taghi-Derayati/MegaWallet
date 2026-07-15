package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.EvmPrepareData
import com.mtd.domain.model.EvmQueuedTx
import com.mtd.domain.model.EvmQuoteData
import com.mtd.domain.model.EvmQuoteRequest
import com.mtd.domain.model.EvmRelayPayload
import com.mtd.domain.model.EvmApproveQuoteRequest
import com.mtd.domain.model.EvmApproveQuoteResult
import com.mtd.domain.model.EvmSponsorApproveRequest
import com.mtd.domain.model.EvmSponsorApproveResult
import com.mtd.domain.model.EvmTxStatus
import com.mtd.domain.model.GaslessEligibilityResult
import com.mtd.domain.model.GaslessServiceType
import com.mtd.domain.model.GaslessSupportedToken
import com.mtd.domain.model.ResultResponse
import java.math.BigInteger

interface IGaslessEvmRepository {
    // Phase 3 (routing convergence): every relayer call carries the data-driven
    // `relayPrefix` (the `/api/{relayPrefix}` segment). It defaults to the EVM family
    // path "evm" so existing callers/tests are byte-identical; the coordinator resolves
    // the real per-network prefix (e.g. "bsc") from networkId via IGaslessRouteResolver.
    suspend fun prepare(
        userAddress: String,
        startNonce: BigInteger? = null,
        relayPrefix: String = "evm"
    ): ResultResponse<EvmPrepareData>

    suspend fun quote(request: EvmQuoteRequest, relayPrefix: String = "evm"): ResultResponse<EvmQuoteData>
    suspend fun submitRelay(
        payload: EvmRelayPayload,
        idempotencyKey: String,
        relayPrefix: String = "evm"
    ): ResultResponse<EvmQueuedTx>

    suspend fun getTxStatus(txId: String, relayPrefix: String = "evm"): ResultResponse<EvmTxStatus>
    suspend fun getAllowance(
        networkId: String,
        tokenAddress: String,
        ownerAddress: String,
        spenderAddress: String
    ): ResultResponse<BigInteger>

    suspend fun getRelayerTreasury(
        networkId: String,
        relayerContractAddress: String
    ): ResultResponse<String>

    suspend fun getSupportedTokens(relayPrefix: String = "evm"): ResultResponse<List<GaslessSupportedToken>>

    suspend fun checkEligibility(
        service: GaslessServiceType,
        userAddress: String,
        tokenAddress: String,
        relayPrefix: String = "evm"
    ): ResultResponse<GaslessEligibilityResult>

    suspend fun sponsorApprove(
        request: EvmSponsorApproveRequest,
        relayPrefix: String = "evm"
    ): ResultResponse<EvmSponsorApproveResult>

    suspend fun quoteApprove(
        request: EvmApproveQuoteRequest,
        relayPrefix: String = "evm"
    ): ResultResponse<EvmApproveQuoteResult>
}
