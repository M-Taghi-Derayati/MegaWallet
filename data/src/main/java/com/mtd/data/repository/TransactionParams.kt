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
}