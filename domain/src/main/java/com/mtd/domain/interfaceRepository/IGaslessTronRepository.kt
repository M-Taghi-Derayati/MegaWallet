package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.GaslessPrepareData
import com.mtd.domain.model.GaslessQueuedTx
import com.mtd.domain.model.GaslessQuoteData
import com.mtd.domain.model.GaslessQuoteRequest
import com.mtd.domain.model.GaslessRelayPayload
import com.mtd.domain.model.GaslessEligibilityResult
import com.mtd.domain.model.GaslessServiceType
import com.mtd.domain.model.GaslessSupportedToken
import com.mtd.domain.model.GaslessTxStatus
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TronApproveQuoteRequest
import com.mtd.domain.model.TronApproveQuoteResult
import com.mtd.domain.model.TronSponsorApproveRequest
import com.mtd.domain.model.TronSponsorApproveResult
import java.math.BigInteger

interface IGaslessTronRepository {
    // Phase 3 (routing convergence): relayer calls carry the data-driven `relayPrefix`
    // (defaults to the TVM family path "tron"; coordinator resolves it from networkId).
    suspend fun prepare(
        userAddress: String,
        startNonce: BigInteger? = null,
        relayPrefix: String = "tron"
    ): ResultResponse<GaslessPrepareData>

    suspend fun quote(request: GaslessQuoteRequest, relayPrefix: String = "tron"): ResultResponse<GaslessQuoteData>
    suspend fun submitRelay(
        payload: GaslessRelayPayload,
        idempotencyKey: String,
        relayPrefix: String = "tron"
    ): ResultResponse<GaslessQueuedTx>

    suspend fun getTxStatus(txId: String, relayPrefix: String = "tron"): ResultResponse<GaslessTxStatus>

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

    suspend fun getSupportedTokens(relayPrefix: String = "tron"): ResultResponse<List<GaslessSupportedToken>>

    suspend fun checkEligibility(
        service: GaslessServiceType,
        userAddress: String,
        tokenAddress: String,
        relayPrefix: String = "tron"
    ): ResultResponse<GaslessEligibilityResult>

    suspend fun quoteApprove(
        request: TronApproveQuoteRequest,
        relayPrefix: String = "tron"
    ): ResultResponse<TronApproveQuoteResult>

    suspend fun sponsorApprove(
        request: TronSponsorApproveRequest,
        relayPrefix: String = "tron"
    ): ResultResponse<TronSponsorApproveResult>
}
