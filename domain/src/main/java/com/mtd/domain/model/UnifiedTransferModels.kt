package com.mtd.domain.model

import java.math.BigInteger

enum class TransferMode {
    NORMAL,
    GASLESS
}

data class UnifiedTransferRequest(
    val networkId: String,
    val assetId: String,
    val mode: TransferMode = TransferMode.NORMAL,
    val toAddress: String,
    val amount: BigInteger,
    val tokenAddress: String? = null,
    val feeAmount: BigInteger = BigInteger.ZERO,
    // User's choice of who funds the relayer fee for gasless sends — forwarded to /quote.
    val feeFundingSource: GaslessFeeFundingSource = GaslessFeeFundingSource.WALLET,
    val utxoFeeRateInSatsPerByte: Long? = null,
    val deadlineEpochSeconds: Long? = null,
    val permit2Address: String? = null,
    val gasPrice: BigInteger? = null,
    val gasLimit: BigInteger? = null,
    val feeLimit: Long? = null,
    val contractFunction: String? = null,
    val contractParameter: String? = null,
    val data: String? = null,
    val feeLevel: String? = null
)

sealed class UnifiedGaslessSession {
    data class Evm(val value: EvmGaslessSession) : UnifiedGaslessSession()
    data class Tron(val value: TronGaslessSession) : UnifiedGaslessSession()
}

data class GaslessSubmission(
    val queueId: String,
    val stage: String?,
    /**
     * true when the relayer replayed a prior submission for the same idempotency key (no new on-chain
     * tx). UI uses this to suppress a duplicate "submitted" toast on an idempotent replay.
     */
    val idempotent: Boolean = false
)

data class GaslessFinalResult(
    val queueId: String,
    val status: GaslessTxStatus
)

data class GaslessDisplayPreview(
    val displayPolicy: GaslessDisplayPolicyBundle?,
    val gaslessFeeAmount: BigInteger,
    val needsApprove: Boolean,
    val smartFee: GaslessSmartFee? = null,
    // Fee-funding fields surfaced from the quote (Sprint 2 parsing → Sprint 3 end-to-end flow) so the
    // UI can later show "gas credit applied" vs "wallet pays" vs "sponsor pays" without re-quoting.
    val feeFundingSource: GaslessFeeFundingSource? = null,
    val gasCreditApplied: Boolean? = null,
    val totalFee: BigInteger? = null,
    val finalFee: BigInteger? = null
)
