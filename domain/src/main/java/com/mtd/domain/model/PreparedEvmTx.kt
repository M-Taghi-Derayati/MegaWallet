package com.mtd.domain.model

import java.math.BigInteger

/**
 * Decoded form of the proxy `/prepare` EVM `transaction` block (hex quantities already parsed to
 * [BigInteger]). [type] 2 = EIP-1559, 0 = legacy.
 */
data class PreparedEvmTx(
    val to: String,
    val value: BigInteger,
    val data: String,
    val nonce: BigInteger,
    val gasLimit: BigInteger,
    val chainId: Long,
    val type: Int,
    val gasPrice: BigInteger? = null,
    val maxFeePerGas: BigInteger? = null,
    val maxPriorityFeePerGas: BigInteger? = null
)