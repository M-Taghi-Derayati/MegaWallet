package com.mtd.data.repository

import com.mtd.core.model.NetworkName
import java.math.BigInteger

sealed class TransactionParams {
    data class Evm(
        val networkName: NetworkName,
        val to: String,
        val amount: BigInteger,
        val data: String? = null,
        val gasPrice: BigInteger,
       val gasLimit:BigInteger
    ) : TransactionParams()

    data class Utxo(
        val chainId: Long,
        val toAddress: String,
        val amountInSatoshi: Long,
        val feeRateInSatsPerByte: Long
    ) : TransactionParams()

    data class Tvm(
        val networkName: NetworkName,
        val toAddress: String,
        val amount: BigInteger,
        val contractAddress: String? = null, // If null, it's native TRX. If set, it's TRC20.
        val feeLimit: Long = 10000000 // Default 10 TRX
    ) : TransactionParams()
}