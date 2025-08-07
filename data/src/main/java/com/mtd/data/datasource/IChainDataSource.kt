package com.mtd.data.datasource

import com.mtd.data.repository.TransactionParams
import com.mtd.domain.model.Asset
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionRecord
import java.math.BigInteger

interface IChainDataSource {
    suspend fun getTransactionHistory(address: String): ResultResponse<List<TransactionRecord>>
    suspend fun sendTransaction(params: TransactionParams, privateKeyHex: String): ResultResponse<String>
    suspend fun getBalanceEVM(address: String): ResultResponse<List<Asset>>
    suspend fun getBalance(address: String): ResultResponse<BigInteger>
    suspend fun estimateFee(): ResultResponse<BigInteger>
}