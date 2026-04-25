package com.mtd.domain.model

import com.mtd.domain.model.core.NetworkName
import java.math.BigInteger



data class TronGaslessTransferRequest(
    val networkId: String,
    val tokenAddress: String,
    val targetAddress: String,
    val amount: BigInteger,
    val feeAmount: BigInteger = BigInteger.ZERO,
    val deadlineEpochSeconds: Long? = null
)


data class TronApproveRequirement(
    val tokenAddress: String,
    val spenderAddress: String,
    val minimumAmount: BigInteger,
    val currentAllowance: BigInteger
)

data class TronGaslessSession(
    val request: TronGaslessTransferRequest,
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

    val approveRequirement: TronApproveRequirement
        get() = TronApproveRequirement(
            tokenAddress = request.tokenAddress,
            spenderAddress = relayerContract,
            minimumAmount = request.amount,
            currentAllowance = allowance
        )
}
