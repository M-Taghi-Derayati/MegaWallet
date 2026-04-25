package com.mtd.domain.model

import java.math.BigInteger

enum class TransferMode {
    NORMAL,
    GASLESS
}

data class UnifiedTransferRequest(
    val networkId: String,
    val mode: TransferMode = TransferMode.NORMAL,
    val toAddress: String,
    val amount: BigInteger,
    val tokenAddress: String? = null,
    val feeAmount: BigInteger = BigInteger.ZERO,
    val utxoFeeRateInSatsPerByte: Long? = null,
    val deadlineEpochSeconds: Long? = null,
    val permit2Address: String? = null,
    val gasPrice: BigInteger? = null,
    val gasLimit: BigInteger? = null,
    val feeLimit: Long? = null,
    val contractFunction: String? = null,
    val contractParameter: String? = null,
    val data: String? = null
)

sealed class UnifiedGaslessSession {
    data class Evm(val value: EvmGaslessSession) : UnifiedGaslessSession()
    data class Tron(val value: TronGaslessSession) : UnifiedGaslessSession()
}

data class GaslessSubmission(
    val queueId: String,
    val stage: String?
)

data class GaslessFinalResult(
    val queueId: String,
    val status: GaslessTxStatus
)
