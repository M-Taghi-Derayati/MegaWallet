package com.mtd.data.datasource

import com.mtd.core.model.NetworkName.BSCTESTNET
import com.mtd.core.model.NetworkName.POLTESTNET
import com.mtd.core.model.NetworkName.SEPOLIA
import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.registry.AssetRegistry
import com.mtd.data.datasource.IChainDataSource.FeeData
import com.mtd.data.dto.BlockscoutTokenTransferDto
import com.mtd.data.dto.BlockscoutTransactionDto
import com.mtd.data.repository.TransactionParams
import com.mtd.data.service.BSCscanApiService
import com.mtd.data.service.BlockscoutApiService
import com.mtd.domain.model.Asset
import com.mtd.domain.model.EvmTransaction
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import retrofit2.Retrofit
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class EvmDataSource(
    private val network: BlockchainNetwork,
    private val retrofitBuilder: Retrofit.Builder,
    private val assetRegistry: AssetRegistry,
    private val okHttpClient: OkHttpClient
) : IChainDataSource {

    private val currentRpcIndex = AtomicInteger(0)
    private var currentWeb3j: Web3j? = null

    @Synchronized
    private fun getOrUpdateWeb3j(): Web3j {
        if (currentWeb3j == null) {
            val rpcUrl = getNextRpcUrl()
            Timber.i("Initializing Web3j with RPC: $rpcUrl")
            currentWeb3j = Web3j.build(HttpService(rpcUrl, okHttpClient, false))
        }
        return currentWeb3j!!
    }

    @Synchronized
    private fun rotateRpc() {
        val nextIndex = (currentRpcIndex.incrementAndGet()) % network.defaultRpcUrls.size
        currentRpcIndex.set(nextIndex)
        val rpcUrl = network.defaultRpcUrls[nextIndex]
        Timber.w("RPC connection failed. Rotating to next RPC: $rpcUrl")
        currentWeb3j = Web3j.build(HttpService(rpcUrl, okHttpClient, false))
    }

    private fun getNextRpcUrl(): String {
        val index = currentRpcIndex.get() % network.defaultRpcUrls.size
        return network.defaultRpcUrls[index]
    }

    private suspend fun <T> executeWithFailover(block: suspend (Web3j) -> T): T {
        var lastException: Exception? = null
        val maxAttempts = network.defaultRpcUrls.size.coerceAtLeast(1)
        
        for (i in 0 until maxAttempts) {
            try {
                val web3j = getOrUpdateWeb3j()
                return block(web3j)
            } catch (e: Exception) {
                Timber.e(e, "Error executing Web3j request on attempt ${i + 1}/$maxAttempts")
                lastException = e
                rotateRpc()
            }
        }
        throw lastException ?: Exception("All RPCs failed")
    }

    override suspend fun getTransactionHistory(address: String): ResultResponse<List<TransactionRecord>> {
        for (explorer in network.explorers) {
            try {
                val result = when (network.name) {
                    SEPOLIA, POLTESTNET -> fetchBlockscoutTransactions(explorer, address)
                    BSCTESTNET -> fetchBscScanTransactions(explorer, address)
                    else -> null
                }
                if (result is ResultResponse.Success) return result
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch history from explorer: $explorer")
            }
        }
        return ResultResponse.Error(Exception("All explorers failed"))
    }

    private suspend fun fetchBlockscoutTransactions(baseUrl: String, address: String): ResultResponse<List<TransactionRecord>> {
         val api = retrofitBuilder.baseUrl(baseUrl).build().create(BlockscoutApiService::class.java)
         return coroutineScope {
            val nativeTxsDeferred = async(Dispatchers.IO) { api.getTransactions(address) }
            val tokenTxsDeferred = async(Dispatchers.IO) { api.getTokenTransfers(address) }

            val nativeTxsResponse = nativeTxsDeferred.await()
            val tokenTxsResponse = tokenTxsDeferred.await()
            val allRecords = mutableListOf<TransactionRecord>()

            if (nativeTxsResponse.isSuccessful) {
                nativeTxsResponse.body()?.items?.forEach { allRecords.add(it.toDomainModel(address)) }
            }
            if (tokenTxsResponse.isSuccessful) {
                tokenTxsResponse.body()?.items?.forEach { allRecords.add(it.toDomainModel(address)) }
            }
            
            if (!nativeTxsResponse.isSuccessful && !tokenTxsResponse.isSuccessful) {
                 return@coroutineScope ResultResponse.Error(Exception("Both APIs failed"))
            }
            ResultResponse.Success(allRecords.sortedByDescending { it.timestamp })
        }
    }

    private suspend fun fetchBscScanTransactions(baseUrl: String, address: String): ResultResponse<List<TransactionRecord>> {
        val api = retrofitBuilder.baseUrl(baseUrl).build().create(BSCscanApiService::class.java)
        val response = api.getTransactions(address = address)
        if (response.isSuccessful && response.body()?.status == "1") {
            val records = response.body()!!.result.map { dto ->
                EvmTransaction(
                    hash = dto.hash,
                    fromAddress = dto.from,
                    toAddress = dto.to,
                    amount = dto.value,
                    fee = (dto.gasUsed.toBigIntegerOrNull() ?: BigInteger.ZERO) * (dto.gasPrice.toBigIntegerOrNull() ?: BigInteger.ZERO),
                    timestamp = dto.timeStamp.toLongOrNull() ?: 0L,
                    isOutgoing = dto.from.equals(address, ignoreCase = true),
                    status = if (dto.isError == "0") TransactionStatus.CONFIRMED else TransactionStatus.FAILED
                )
            }
            return ResultResponse.Success(records)
        }
        return ResultResponse.Error(Exception("BSCScan API failed"))
    }

    private suspend fun getNonce(address: String): BigInteger {
        return executeWithFailover { web3j ->
            web3j.ethGetTransactionCount(address, DefaultBlockParameterName.LATEST).sendAsync().await().transactionCount
        }
    }

    private suspend fun sendRawTransaction(signedTxHex: String): String {
        return executeWithFailover { web3j ->
            val receipt = web3j.ethSendRawTransaction(signedTxHex).sendAsync().await()
            if (receipt.hasError()) throw RuntimeException(receipt.error.message)
            receipt.transactionHash
        }
    }

    override suspend fun sendTransaction(params: TransactionParams, privateKeyHex: String): ResultResponse<String> {
        if (params !is TransactionParams.Evm) return ResultResponse.Error(IllegalArgumentException("Invalid params"))
        return try {
            val credentials = Credentials.create(privateKeyHex)
            val nonce = getNonce(credentials.address)
            val rawTransaction = RawTransaction.createTransaction(
                nonce, params.gasPrice, params.gasLimit, params.to, params.amount, params.data ?: ""
            )
            val signedTx = TransactionEncoder.signMessage(rawTransaction, network.chainId!!, credentials)
            ResultResponse.Success(sendRawTransaction(Numeric.toHexString(signedTx)))
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override suspend fun getBalanceEVM(address: String): ResultResponse<List<Asset>> {
        return withContext(Dispatchers.IO) {
            try {
                val supportedAssets = assetRegistry.getAssetsForNetwork(network.id)
                if (supportedAssets.isEmpty()) return@withContext ResultResponse.Success(emptyList())

                val assetDeferreds = supportedAssets.map { assetConfig ->
                    async {
                        executeWithFailover { web3j ->
                             val balance = if (assetConfig.contractAddress == null) {
                                web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).sendAsync().await().balance
                            } else {
                                val function = Function("balanceOf", listOf(Address(address)), emptyList())
                                val response = web3j.ethCall(
                                    Transaction.createEthCallTransaction(address, assetConfig.contractAddress, FunctionEncoder.encode(function)),
                                    DefaultBlockParameterName.LATEST
                                ).sendAsync().await()
                                if (response.hasError() || response.value == "0x") BigInteger.ZERO 
                                else try { BigInteger(response.value.substring(2), 16) } catch (e: Exception) { BigInteger.ZERO }
                            }
                            Asset(assetConfig.name, assetConfig.symbol, assetConfig.decimals, assetConfig.contractAddress, balance)
                        }
                    }
                }
                ResultResponse.Success(assetDeferreds.awaitAll())
            } catch (e: Exception) {
                ResultResponse.Error(e)
            }
        }
    }

    override suspend fun getBalance(address: String): ResultResponse<BigInteger> { TODO("Not yet implemented") }

    private suspend fun getGasPrice(): BigInteger {
        return executeWithFailover { web3j -> web3j.ethGasPrice().sendAsync().await().gasPrice }
    }

    private suspend fun estimateGasLimit(from: String, to: String, data: String? = null): BigInteger {
        return executeWithFailover { web3j ->
            val transaction = Transaction.createEthCallTransaction(from, to, data ?: "")
            try {
                val estimate = web3j.ethEstimateGas(transaction).sendAsync().await()
                if (estimate.hasError()) BigInteger("150000") 
                else (estimate.amountUsed.toBigDecimal() * BigDecimal("1.2")).toBigInteger()
            } catch (e: Exception) { BigInteger("150000") }
        }
    }

    override suspend fun getFeeOptions(fromAddress: String?, toAddress: String?, asset: Asset?): ResultResponse<List<FeeData>> {
        return try {
            val baseGasPrice = getGasPrice().coerceAtLeast(BigInteger.ONE)
            val priorityFee = BigInteger("2000000000") // Simplified for now
            val maxFeePerGas = baseGasPrice.add(priorityFee)

            val gasLimit: BigInteger = if (asset?.contractAddress == null) {
                BigInteger.valueOf(21_000L)
            } else {
                val function = Function("transfer", listOf(Address(toAddress), Uint256(BigInteger.ONE)), emptyList())
                estimateGasLimit(fromAddress?:"", asset.contractAddress!!, FunctionEncoder.encode(function))
            }

            val normal = maxFeePerGas
            val fast = maxFeePerGas.add(priorityFee.divide(BigInteger.valueOf(2)))
            val urgent = maxFeePerGas.add(priorityFee.multiply(BigInteger.valueOf(2)))

            ResultResponse.Success(listOf(
                FeeData(level = "عادی 🐢", gasPrice =  normal, gasLimit =  gasLimit, feeInSmallestUnit =  normal * gasLimit, feeInEth =  (normal * gasLimit).toEth(), feeInUsd =  null, estimatedTime =  "~ 30s"),
                FeeData(level = "سریع 🚀", gasPrice =  fast, gasLimit =  gasLimit, feeInSmallestUnit =  fast * gasLimit, feeInEth =  (fast * gasLimit).toEth(), feeInUsd =  null, estimatedTime =  "~ 15s"),
                FeeData(level = "درلحظه 🔥", gasPrice =  urgent, gasLimit =  gasLimit, feeInSmallestUnit =  urgent * gasLimit, feeInEth =  (urgent * gasLimit).toEth(), feeInUsd =  null, estimatedTime =  "< 10s"),
            ))
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override fun getWeb3jInstance(): Web3j = getOrUpdateWeb3j()

    fun BigInteger.toEth(): BigDecimal = this.toBigDecimal().divide(BigDecimal.TEN.pow(18))

    private fun BlockscoutTransactionDto.toDomainModel(userAddress: String): EvmTransaction {
        val fee = (this.gasUsed?.toBigIntegerOrNull() ?: BigInteger.ZERO) * (this.gasPrice?.toBigIntegerOrNull() ?: BigInteger.ZERO)
        val timestamp = try { Instant.parse(this.timestamp).epochSecond } catch (e: Exception) { 0L }
        return EvmTransaction(
            this.hash, timestamp, fee, 
            if (this.status.equals("ok", true)) TransactionStatus.CONFIRMED else TransactionStatus.FAILED,
            this.from.hash, this.to?.hash ?: "Contract Creation", this.value, 
            this.from.hash.equals(userAddress, true)
        )
    }

    private fun BlockscoutTokenTransferDto.toDomainModel(userAddress: String): EvmTransaction {
        val timestamp = try { Instant.parse(this.timestamp).epochSecond } catch (e: Exception) { 0L }
        return EvmTransaction(
            this.txHash, timestamp, BigInteger.ZERO, TransactionStatus.CONFIRMED,
            this.from.hash, this.to.hash, this.total.value.toBigIntegerOrNull() ?: BigInteger.ZERO,
            this.from.hash.equals(userAddress, true), this.token.address
        )
    }
}