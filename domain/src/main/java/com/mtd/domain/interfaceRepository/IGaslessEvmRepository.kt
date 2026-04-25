package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.EvmPrepareData
import com.mtd.domain.model.EvmQueuedTx
import com.mtd.domain.model.EvmQuoteData
import com.mtd.domain.model.EvmQuoteRequest
import com.mtd.domain.model.EvmRelayPayload
import com.mtd.domain.model.EvmSponsorApproveRequest
import com.mtd.domain.model.EvmSponsorApproveResult
import com.mtd.domain.model.EvmTxStatus
import com.mtd.domain.model.GaslessEligibilityResult
import com.mtd.domain.model.GaslessServiceType
import com.mtd.domain.model.GaslessSupportedToken
import com.mtd.domain.model.ResultResponse
import java.math.BigInteger

interface IGaslessEvmRepository {
    suspend fun prepare(
        userAddress: String,
        startNonce: BigInteger? = null
    ): ResultResponse<EvmPrepareData>

    suspend fun quote(request: EvmQuoteRequest): ResultResponse<EvmQuoteData>
    suspend fun submitRelay(
        payload: EvmRelayPayload,
        idempotencyKey: String
    ): ResultResponse<EvmQueuedTx>

    suspend fun getTxStatus(txId: String): ResultResponse<EvmTxStatus>
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

    suspend fun getSupportedTokens(): ResultResponse<List<GaslessSupportedToken>>

    suspend fun checkEligibility(
        service: GaslessServiceType,
        userAddress: String,
        tokenAddress: String
    ): ResultResponse<GaslessEligibilityResult>

    suspend fun sponsorApprove(request: EvmSponsorApproveRequest): ResultResponse<EvmSponsorApproveResult>
}
