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
import com.mtd.data.utils.AssetNormalizer.normalize
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
import kotlinx.coroutines.withTimeout
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


    object Web3jFactory {
        private val cache = mutableMapOf<String, Web3j>()

        fun getOrCreate(rpcUrl: String, okHttpClient: OkHttpClient): Web3j {
            return cache.getOrPut(rpcUrl) {
                Web3j.build(HttpService(rpcUrl, okHttpClient, false))
            }
        }
    }


    @Synchronized
    private fun getOrUpdateWeb3j(): Web3j {
        if (currentWeb3j == null) {
            val index = currentRpcIndex.get() % network.RpcUrls.size
            val rpcUrl = network.RpcUrls[index]
            Timber.i("Initializing Web3j with RPC: $rpcUrl")
            currentWeb3j = Web3j.build(HttpService(rpcUrl, okHttpClient, false))
        }
        return currentWeb3j!!
    }

    private suspend fun <T> executeWithFailover(block: suspend (Web3j) -> T): T {
        var lastException: Exception? = null

        // همیشه لیست را از ابتدا (اولویت بالا) به انتها تست می‌کنیم
        val rpcList = network.RpcUrls

        for (url in rpcList) {
            try {
                return withTimeout(6000) { // تایم‌اوت ۶ ثانیه‌ای برای هر RPC
                    val web3j = Web3jFactory.getOrCreate(url, okHttpClient)
                    // اجرای عملیات اصلی
                    block(web3j)
                }
            } catch (e: Exception) {
                Timber.e("RPC Failed: $url | Error: ${e.message}")
                lastException = e
                // در صورت خطا، حلقه ادامه پیدا کرده و به سراغ URL بعدی می‌رود
                continue
            }
        }

        throw lastException ?: Exception("All RPC nodes are unreachable for ${network.name}")
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

    override suspend fun getBalanceAssets(address: String): ResultResponse<List<Asset>> {
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

                                 response.result.toString()

                            }
                            Asset(assetConfig.name, assetConfig.symbol, assetConfig.decimals, assetConfig.contractAddress, normalize(balance,assetConfig.decimals,network.name))
                        }
                    }
                }
                ResultResponse.Success(assetDeferreds.awaitAll())
            } catch (e: Exception) {
                ResultResponse.Error(e)
            }
        }
    }

    /**
     * پیاده‌سازی نهایی و تصحیح شده با رعایت دقیق محدودیت‌های RPC رایگان (drpc.org)
     * محدودیت: حداکثر 3 درخواست در کل هر بچ.
     * استراتژی:
     * 1. تمام درخواست‌ها (کیف‌پول‌ها ضربدر دارایی‌ها) لیست می‌شوند.
     * 2. به دسته‌های 3 تایی تقسیم می‌شوند (طبق پیام خطا).
     * 3. دسته‌ها به صورت موازی ارسال می‌شوند تا سرعت حفظ شود.
     * نتیجه: اگر 18 درخواست باشد، 6 درخواست HTTP ارسال می‌شود که تنها راه قانونی است.
     */
    override suspend fun getBalancesForMultipleAddresses(addresses: List<String>): ResultResponse<Map<String, List<Asset>>> {
        return withContext(Dispatchers.IO) {
            executeWithFailover { web3j ->
                try {
                    val supportedAssets = assetRegistry.getAssetsForNetwork(network.id)
                    if (supportedAssets.isEmpty()) return@executeWithFailover ResultResponse.Success(
                        emptyMap()
                    )

                    // 1. آماده‌سازی تمام درخواست‌ها به صورت تخت
                    val allRequests =
                        mutableListOf<Triple<String, com.mtd.core.assets.AssetConfig, org.web3j.protocol.core.Request<*, *>>>()

                    addresses.forEach { address ->
                        supportedAssets.forEach { assetConfig ->
                            if (assetConfig.contractAddress == null) {
                                // Native Coin
                                val request =
                                    web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST)
                                allRequests.add(Triple(address, assetConfig, request))
                            } else {
                                // ERC-20 Token
                                val function =
                                    Function("balanceOf", listOf(Address(address)), emptyList())
                                val encodedFunction = FunctionEncoder.encode(function)
                                val transaction = Transaction.createEthCallTransaction(
                                    address,
                                    assetConfig.contractAddress,
                                    encodedFunction
                                )
                                val request =
                                    web3j.ethCall(transaction, DefaultBlockParameterName.LATEST)
                                allRequests.add(Triple(address, assetConfig, request))
                            }
                        }
                    }

                    if (allRequests.isEmpty()) return@executeWithFailover ResultResponse.Success(
                        emptyMap()
                    )

                    // 2. اعمال محدودیت اکید: Max 3 Requests per Batch
                    val REQUESTS_LIMIT_PER_BATCH = 3
                    val requestChunks = allRequests.chunked(REQUESTS_LIMIT_PER_BATCH)

                    // 3. ارسال موازی دسته‌ها
                    val chunkDeferreds = requestChunks.map { chunk ->
                        async {
                            try {
                                val batch = web3j.newBatch()
                                chunk.forEach { (_, _, request) ->
                                    batch.add(request)
                                }

                                val batchResponse = batch.sendAsync().await()
                                val responses = batchResponse.responses

                                val chunkResults = mutableListOf<Pair<String, Asset>>()

                                responses.forEachIndexed { index, response ->
                                    if (index < chunk.size) {
                                        val (address, assetConfig, _) = chunk[index]
                                        val balance = normalize(response.result.toString(),assetConfig.decimals,network.name)
                                        val asset = Asset(
                                            assetConfig.name,
                                            assetConfig.symbol,
                                            assetConfig.decimals,
                                            assetConfig.contractAddress,
                                            balance
                                        )
                                        chunkResults.add(address to asset)
                                    }
                                }
                                chunkResults
                            } catch (e: Exception) {
                                Timber.e(e, "Error processing chunk")
                                emptyList<Pair<String, Asset>>()
                            }
                        }
                    }

                    // 4. تجمیع نتایج
                    val allChunkResults = chunkDeferreds.awaitAll().flatten()

                    val finalResults = mutableMapOf<String, MutableList<Asset>>()
                    allChunkResults.forEach { (address, asset) ->
                        finalResults.getOrPut(address) { mutableListOf() }.add(asset)
                    }

                    ResultResponse.Success(finalResults)

                } catch (e: Exception) {
                    Timber.e(e, "Error in batched balance fetching")
                    ResultResponse.Error(e)
                }
            }
        }
    }


    override suspend fun getBalance(address: String): ResultResponse<BigDecimal> {
        return ResultResponse.Success(BigDecimal.ZERO)
    }

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
                FeeData(level = "عادی 🐢", gasPrice =  normal, gasLimit =  gasLimit, feeInSmallestUnit =  (normal * gasLimit).toBigDecimal(), feeInCoin = normalize ((normal * gasLimit),asset?.decimals?:18,network.name), feeInUsd =  null, estimatedTime =  "~ 30s"),
                FeeData(level = "سریع 🚀", gasPrice =  fast, gasLimit =  gasLimit, feeInSmallestUnit =  (fast * gasLimit).toBigDecimal(), feeInCoin =  normalize ((fast * gasLimit),asset?.decimals?:18,network.name), feeInUsd =  null, estimatedTime =  "~ 15s"),
                FeeData(level = "درلحظه 🔥", gasPrice =  urgent, gasLimit =  gasLimit, feeInSmallestUnit = ( urgent * gasLimit).toBigDecimal(), feeInCoin =   normalize ((urgent * gasLimit),asset?.decimals?:18,network.name), feeInUsd =  null, estimatedTime =  "< 10s"),
            ))
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override fun getWeb3jInstance(): Web3j = getOrUpdateWeb3j()


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