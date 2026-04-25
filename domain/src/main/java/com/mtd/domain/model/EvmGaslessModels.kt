package com.mtd.domain.model

import com.mtd.domain.model.core.NetworkName
import java.math.BigInteger

enum class GaslessCoordinatorState {
    INIT,
    PREPARING,
    CHECKING_ALLOWANCE,
    NEEDS_APPROVE,
    AWAITING_APPROVE_CONFIRMATION,
    SIGNING_GASLESS,
    SUBMITTING_RELAY,
    QUEUED,
    PROCESSING,
    SUCCESS,
    FAILED
}

typealias EvmPrepareData = GaslessPrepareData
typealias EvmQuoteRequest = GaslessQuoteRequest
typealias EvmQuoteData = GaslessQuoteData
typealias EvmRelayParams = GaslessRelayParams
typealias EvmRelayPayload = GaslessRelayPayload
typealias EvmQueuedTx = GaslessQueuedTx
typealias EvmTxStatus = GaslessTxStatus

data class EvmGaslessTransferRequest(
    val networkId: String,
    val tokenAddress: String,
    val targetAddress: String,
    val amount: BigInteger,
    val permit2Address: String,
    val feeAmount: BigInteger = BigInteger.ZERO,
    val deadlineEpochSeconds: Long? = null
)

data class EvmApproveRequirement(
    val tokenAddress: String,
    val spenderAddress: String,
    val minimumAmount: BigInteger,
    val currentAllowance: BigInteger
)

data class EvmGaslessSession(
    val request: EvmGaslessTransferRequest,
    val networkName: NetworkName,
    val userAddress: String,
    val chainId: Long,
    val relayerContract: String,
    val treasuryAddress: String,
    val nonce: BigInteger,
    val allowance: BigInteger,
    val prepareToken: String,
    val idempotencyKey: String
) {
    val needsApprove: Boolean
        get() = allowance < request.amount

    val approveRequirement: EvmApproveRequirement
        get() = EvmApproveRequirement(
            tokenAddress = request.tokenAddress,
            spenderAddress = request.permit2Address,
            minimumAmount = request.amount,
            currentAllowance = allowance
        )
}
