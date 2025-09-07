package com.mtd.data.datasource

import com.mtd.data.repository.TransactionParams
import com.mtd.domain.model.Asset
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionRecord
import org.web3j.protocol.Web3j
import java.math.BigDecimal
import java.math.BigInteger

interface IChainDataSource {
    suspend fun getTransactionHistory(address: String): ResultResponse<List<TransactionRecord>>
    suspend fun sendTransaction(params: TransactionParams, privateKeyHex: String): ResultResponse<String>
    suspend fun getBalanceEVM(address: String): ResultResponse<List<Asset>>
    suspend fun getBalance(address: String): ResultResponse<BigInteger>
    suspend fun getFeeOptions(  fromAddress: String?=null, toAddress: String?=null, asset: Asset?=null): ResultResponse<List<FeeData>>

    /**
     * نمونه Web3j مدیریت شده توسط این DataSource را برمی‌گرداند.
     * این متد فقط باید توسط DataSource های نوع EVM پیاده‌سازی شود.
     * @return یک نمونه از Web3j.
     * @throws UnsupportedOperationException اگر DataSource از نوع EVM نباشد.
     */
    fun getWeb3jInstance(): Web3j

    // یک data class برای داده‌های خام کارمزد
    data class FeeData(
        val level: String, // "Normal", "Fast", "Urgent"
        val feeInSmallestUnit: BigInteger,
        val estimatedTime: String,
        // فیلدهای مخصوص EVM
        val gasPrice: BigInteger? = null,
        val gasLimit: BigInteger? = null,
        val feeInEth: BigDecimal?=null,
        val feeInUsd: BigDecimal?=null,
        val feeRateInSatsPerByte: Long? = null,
    )
}