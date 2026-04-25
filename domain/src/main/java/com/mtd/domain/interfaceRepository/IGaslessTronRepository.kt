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
    suspend fun prepare(
        userAddress: String,
        startNonce: BigInteger? = null
    ): ResultResponse<GaslessPrepareData>

    suspend fun quote(request: GaslessQuoteRequest): ResultResponse<GaslessQuoteData>
    suspend fun submitRelay(
        payload: GaslessRelayPayload,
        idempotencyKey: String
    ): ResultResponse<GaslessQueuedTx>

    suspend fun getTxStatus(txId: String): ResultResponse<GaslessTxStatus>

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

    suspend fun quoteApprove(request: TronApproveQuoteRequest): ResultResponse<TronApproveQuoteResult>

    suspend fun sponsorApprove(request: TronSponsorApproveRequest): ResultResponse<TronSponsorApproveResult>
}
