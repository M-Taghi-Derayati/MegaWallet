package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.EvmSponsorApproveResult
import com.mtd.domain.model.EvmSponsorMode
import com.mtd.domain.model.EvmApproveQuoteResult
import com.mtd.domain.model.GaslessDisplayPreview
import com.mtd.domain.model.GaslessEligibilityResult
import com.mtd.domain.model.GaslessFinalResult
import com.mtd.domain.model.GaslessServiceType
import com.mtd.domain.model.GaslessSubmission
import com.mtd.domain.model.GaslessSupportedToken
import com.mtd.domain.model.PendingGaslessTx
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionParams
import com.mtd.domain.model.TronApproveQuoteResult
import com.mtd.domain.model.TronSponsorApproveResult
import com.mtd.domain.model.TronSponsorMode
import com.mtd.domain.model.UnifiedGaslessSession
import com.mtd.domain.model.UnifiedTransferRequest
import java.math.BigInteger

interface IUnifiedTransferCoordinator {
    suspend fun sendNormal(request: UnifiedTransferRequest): ResultResponse<String>

    suspend fun sendPreparedTransaction(params: TransactionParams): ResultResponse<String>

    suspend fun getSupportedGaslessTokens(
        networkId: String
    ): ResultResponse<List<GaslessSupportedToken>>

    suspend fun checkGaslessEligibility(
        networkId: String,
        tokenAddress: String,
        service: GaslessServiceType
    ): ResultResponse<GaslessEligibilityResult>

    suspend fun prepareGasless(
        request: UnifiedTransferRequest
    ): ResultResponse<UnifiedGaslessSession>

    suspend fun previewGaslessDisplayPolicy(
        request: UnifiedTransferRequest
    ): ResultResponse<GaslessDisplayPreview>

    fun buildApproveTransaction(
        session: UnifiedGaslessSession,
        gasPrice: BigInteger? = null,
        gasLimit: BigInteger? = null,
        tronFeeLimit: Long = 15_000_000L,
        approveAmount: BigInteger? = null
    ): ResultResponse<TransactionParams>

    suspend fun requestTronSponsorForApprove(
        session: UnifiedGaslessSession,
        mode: TronSponsorMode = TronSponsorMode.GIFT
    ): ResultResponse<TronSponsorApproveResult>

    suspend fun quoteTronApproveRequirement(
        session: UnifiedGaslessSession
    ): ResultResponse<TronApproveQuoteResult>

    suspend fun quoteEvmApproveRequirement(
        session: UnifiedGaslessSession
    ): ResultResponse<EvmApproveQuoteResult>

    suspend fun requestEvmSponsorForApprove(
        session: UnifiedGaslessSession,
        mode: EvmSponsorMode = EvmSponsorMode.GIFT
    ): ResultResponse<EvmSponsorApproveResult>

    suspend fun submitGasless(
        session: UnifiedGaslessSession
    ): ResultResponse<GaslessSubmission>

    suspend fun pollGaslessUntilFinal(
        session: UnifiedGaslessSession,
        queueId: String,
        pollIntervalMs: Long = 4_000L,
        timeoutMs: Long = 5 * 60_000L
    ): ResultResponse<GaslessFinalResult>

    fun getPendingGaslessTransactions(): List<PendingGaslessTx>

    fun clearPendingGaslessTransactions()
}
