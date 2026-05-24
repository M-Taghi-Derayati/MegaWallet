package com.mtd.data.datasource

import com.mtd.core.network.BlockchainNetwork
import com.mtd.domain.model.Asset
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionParams
import com.mtd.domain.model.TransactionRecord
import org.web3j.protocol.Web3j
import java.math.BigDecimal

class ProxyChainDataSource(
    private val network: BlockchainNetwork
) : IChainDataSource {

    override suspend fun getTransactionHistory(address: String): ResultResponse<List<TransactionRecord>> {
        return unsupported("getTransactionHistory")
    }

    override suspend fun sendTransaction(
        params: TransactionParams,
        privateKeyHex: String
    ): ResultResponse<String> {
        return unsupported("sendTransaction")
    }

    override suspend fun getBalanceAssets(address: String): ResultResponse<List<Asset>> {
        return unsupported("getBalanceAssets")
    }

    override suspend fun getBalance(address: String): ResultResponse<BigDecimal> {
        return unsupported("getBalance")
    }

    override suspend fun getFeeOptions(
        fromAddress: String?,
        toAddress: String?,
        asset: Asset?
    ): ResultResponse<List<IChainDataSource.FeeData>> {
        return unsupported("getFeeOptions")
    }

    override suspend fun getBalancesForMultipleAddresses(
        addresses: List<String>
    ): ResultResponse<Map<String, List<Asset>>> {
        return unsupported("getBalancesForMultipleAddresses")
    }

    override suspend fun getTransactionFeeDetails(
        txId: String
    ): ResultResponse<IChainDataSource.TransactionFeeDetails> {
        return unsupported("getTransactionFeeDetails")
    }

    override fun getWeb3jInstance(): Web3j {
        throw UnsupportedOperationException(
            "Proxy chain data source does not expose Web3j for network: ${network.id}"
        )
    }

    private fun <T> unsupported(operation: String): ResultResponse<T> {
        return ResultResponse.Error(
            UnsupportedOperationException(
                "Proxy chain data source operation $operation is not implemented yet for network: ${network.id}"
            )
        )
    }
}
