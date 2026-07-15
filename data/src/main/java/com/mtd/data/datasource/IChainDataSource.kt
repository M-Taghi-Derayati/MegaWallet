package com.mtd.data.datasource

import com.mtd.data.dto.BatchBalanceWalletDto
import com.mtd.data.dto.HistoryAddressDto
import com.mtd.domain.model.Asset
import com.mtd.domain.model.HistoryPage
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionParams
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.error.ApiError
import com.mtd.domain.model.error.ApiException
import java.math.BigDecimal
import java.math.BigInteger

interface IChainDataSource {
    suspend fun getTransactionHistory(address: String): ResultResponse<List<TransactionRecord>>
    suspend fun sendTransaction(params: TransactionParams, privateKeyHex: String): ResultResponse<String>
    suspend fun getBalanceAssets(address: String): ResultResponse<List<Asset>>
    suspend fun getBalance(address: String): ResultResponse<BigDecimal>
    suspend fun getFeeOptions(
        fromAddress: String? = null,
        toAddress: String? = null,
        asset: Asset? = null,
        amount: BigInteger? = null
    ): ResultResponse<List<FeeData>>

    suspend fun getBalancesForMultipleAddresses(addresses: List<String>): ResultResponse<Map<String, List<Asset>>>

    suspend fun getTransactionFeeDetails(txId: String): ResultResponse<TransactionFeeDetails> {
        return ResultResponse.Error(UnsupportedOperationException("Not implemented for this chain"))
    }

    /** Unified multi-network history. PROXY-only; DIRECT sources return UnsupportedOperation. */
    suspend fun getHistory(
        addresses: List<HistoryAddressDto>,
        cursor: String? = null,
        limit: Int? = null
    ): ResultResponse<HistoryPage> {
        return ResultResponse.Error(
            ApiException(ApiError.UnsupportedOperation, reasonFa = "Unified history is only available in PROXY mode")
        )
    }

    /** Cross-network batch balances. PROXY-only; DIRECT sources return UnsupportedOperation. */
    suspend fun getBatchBalances(
        wallets: List<BatchBalanceWalletDto>
    ): ResultResponse<Map<String, ResultResponse<List<Asset>>>> {
        return ResultResponse.Error(
            ApiException(ApiError.UnsupportedOperation, reasonFa = "Batch balances is only available in PROXY mode")
        )
    }

    // یک data class برای داده‌های خام کارمزد
    data class FeeData(
        val level: String, // "Normal", "Fast", "Urgent"
        val feeInSmallestUnit: BigDecimal,
        val estimatedTime: String,
        // فیلدهای مخصوص EVM
        val gasPrice: BigInteger? = null,
        val gasLimit: BigInteger? = null,
        val feeInCoin: BigDecimal?=null,
        val feeInUsd: BigDecimal?=null,
        val feeRateInSatsPerByte: Long? = null,
    )

    data class TransactionFeeDetails(
        val fee: BigInteger,
        val energyUsed: Long? = null,
        val bandwidthUsed: Long? = null,
        val energyFee: BigInteger? = null,
        val networkFee: BigInteger? = null // bandwidth fee
    )
}
