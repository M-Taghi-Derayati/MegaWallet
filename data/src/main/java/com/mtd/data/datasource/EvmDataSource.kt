package com.mtd.data.datasource

import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.utils.AddressRegexUtils
import com.mtd.data.dto.EVMTokenTransferDto
import com.mtd.data.dto.EVMTransactionDto
import com.mtd.data.dto.NodeRealTransactionDto
import com.mtd.data.service.BSCscanApiService
import com.mtd.data.service.EVMApiService
import com.mtd.data.utils.AssetNormalizer.normalize
import com.mtd.domain.model.Asset
import com.mtd.domain.model.EvmTransaction
import com.mtd.domain.model.FeeData
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TokenTransferDetails
import com.mtd.domain.model.TransactionParams
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TransactionStatus
import com.mtd.domain.model.assets.AssetConfig
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkName.ARBITRUM
import com.mtd.domain.model.core.NetworkName.ARBSEPOLIA
import com.mtd.domain.model.core.NetworkName.BASE
import com.mtd.domain.model.core.NetworkName.BASESEPOLIA
import com.mtd.domain.model.core.NetworkName.BSC
import com.mtd.domain.model.core.NetworkName.BSCTESTNET
import com.mtd.domain.model.core.NetworkName.ETHEREUM
import com.mtd.domain.model.core.NetworkName.POLTESTNET
import com.mtd.domain.model.core.NetworkName.SEPOLIA
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.crypto.WalletUtils
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

class EvmDataSource(
    private val network: BlockchainNetwork,
    private val retrofitBuilder: Retrofit.Builder,
    private val assetRegistry: AssetRegistry,
    private val okHttpClient: OkHttpClient
) : IChainDataSource {

    companion object {
        private const val RPC_FAILOVER_TIMEOUT_MS = 15_000L
    }

    object Web3jFactory {
        // TASK-20: ConcurrentHashMap (was a plain mutableMapOf) — data sources are hit from concurrent
        // coroutines, so a non-thread-safe map here could corrupt/throw on parallel getOrCreate.
        private val cache = java.util.concurrent.ConcurrentHashMap<String, Web3j>()

        fun getOrCreate(rpcUrl: String, okHttpClient: OkHttpClient): Web3j {
            return cache.getOrPut(rpcUrl) {
                Web3j.build(HttpService(rpcUrl, okHttpClient, false))
            }
        }
    }


    private suspend fun <T> executeWithFailover(block: suspend (Web3j) -> T): T {
        var lastException: Exception? = null

        // همیشه لیست را از ابتدا (اولویت بالا) به انتها تست می‌کنیم
        val rpcList = network.RpcUrlsEvm

        for (url in rpcList) {
            try {
                return withTimeout(RPC_FAILOVER_TIMEOUT_MS) { // تایم‌اوت ۶ ثانیه‌ای برای هر RPC
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
                    ETHEREUM, SEPOLIA,ARBITRUM, ARBSEPOLIA, BASE, BASESEPOLIA, POLTESTNET -> fetchEVMTransactions(explorer, address)
                    BSC, BSCTESTNET -> fetchBscScanTransactions(explorer, address)
                    else -> null
                }
                if (result is ResultResponse.Success) return result
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch history from explorer: $explorer")
            }
        }
        return ResultResponse.Error(Exception("All explorers failed"))
    }

    private suspend fun fetchEVMTransactions(baseUrl: String, address: String): ResultResponse<List<TransactionRecord>> {
         val api = retrofitBuilder.baseUrl(baseUrl).build().create(EVMApiService::class.java)
         return coroutineScope {
            val nativeTxsDeferred = async(Dispatchers.IO) { api.getTransactions(address) }
            val tokenTxsDeferred = async(Dispatchers.IO) { api.getTokenTransfers(address) }

            val nativeTxsResponse = nativeTxsDeferred.await()
            val tokenTxsResponse = tokenTxsDeferred.await()
            val allRecords = mutableListOf<TransactionRecord>()

            if (nativeTxsResponse.isSuccessful) {
                nativeTxsResponse.body()?.items?.forEach { allRecords.add(it.toDomainModel(address, network.name)) }
            }
            if (tokenTxsResponse.isSuccessful) {
                tokenTxsResponse.body()?.items?.forEach { allRecords.add(it.toDomainModel(address, network.name)) }
            }
            
            if (!nativeTxsResponse.isSuccessful && !tokenTxsResponse.isSuccessful) {
                 return@coroutineScope ResultResponse.Error(Exception("Both APIs failed"))
            }
            ResultResponse.Success(allRecords.sortedByDescending { it.timestamp })
        }
    }

    private suspend fun fetchBscScanTransactions(baseUrl: String, address: String): ResultResponse<List<TransactionRecord>> {
        val api = retrofitBuilder.baseUrl(baseUrl).build().create(BSCscanApiService::class.java)
        return coroutineScope {
            val nativeTxsDeferred = async(Dispatchers.IO) { api.getAssetTransferByAddress(address, "transaction") }
            val tokenTxsDeferred = async(Dispatchers.IO) { api.getAssetTransferByAddress(address, "20") }

            val nativeTxsResponse = nativeTxsDeferred.await()
            val tokenTxsResponse = tokenTxsDeferred.await()
            val allRecords = mutableListOf<TransactionRecord>()

            if (nativeTxsResponse.isSuccessful) {
                nativeTxsResponse.body()?.data?.list?.forEach { dto ->
                    allRecords.add(mapNodeRealToDomain(dto, address))
                }
            }
            if (tokenTxsResponse.isSuccessful) {
                tokenTxsResponse.body()?.data?.list?.forEach { dto ->
                    allRecords.add(mapNodeRealToDomain(dto, address))
                }
            }

            if (!nativeTxsResponse.isSuccessful && !tokenTxsResponse.isSuccessful) {
                return@coroutineScope ResultResponse.Error(Exception("NodeReal API failed"))
            }
            ResultResponse.Success(allRecords.sortedByDescending { it.timestamp })
        }
    }

    private fun mapNodeRealToDomain(dto: NodeRealTransactionDto, address: String): TransactionRecord {
        val amount = try {
            if (dto.value.startsWith("0x")) BigInteger(dto.value.substring(2), 16)
            else BigInteger(dto.value)
        } catch (e: Exception) {
            BigInteger.ZERO
        }

        return EvmTransaction(
            hash = dto.hash,
            timestamp = dto.blockTimeStamp,
            fee = (dto.gasUsed ?: BigInteger.ZERO) * (dto.gasPrice ?: BigInteger.ZERO),
            status = if (dto.receiptsStatus == 1) TransactionStatus.CONFIRMED else TransactionStatus.FAILED,
            fromAddress = dto.from,
            toAddress = dto.to,
            amount = amount,
            isOutgoing = dto.from.equals(address, ignoreCase = true),
            networkName = network.name,
            contractAddress = if (dto.category == "20") dto.contractAddress else null,
            tokenTransferDetails = if (dto.category == "20") {
                TokenTransferDetails(
                    from = dto.from,
                    to = dto.to,
                    amount = amount,
                    tokenSymbol = dto.asset,
                    tokenDecimals = dto.decimal?.toIntOrNull() ?: 18,
                    contractAddress = dto.contractAddress
                )
            } else null
        )
    }



    private suspend fun getNonce(address: String): BigInteger {
        return executeWithFailover { web3j ->
            web3j.ethGetTransactionCount(address, DefaultBlockParameterName.PENDING).sendAsync().await().transactionCount
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
        val normalizedTo = params.to.trim()
        val isValidByRegex = AddressRegexUtils.matchesAddress(network.regex, normalizedTo)
        if (!isValidByRegex && !WalletUtils.isValidAddress(normalizedTo)) {
            return ResultResponse.Error(IllegalArgumentException("Invalid recipient address"))
        }
        if (params.gasPrice <= BigInteger.ZERO || params.gasLimit <= BigInteger.ZERO) {
            return ResultResponse.Error(IllegalArgumentException("Invalid gas values"))
        }
        if (params.amount < BigInteger.ZERO) {
            return ResultResponse.Error(IllegalArgumentException("Amount cannot be negative"))
        }
        if (params.amount == BigInteger.ZERO && params.data.isNullOrBlank()) {
            return ResultResponse.Error(IllegalArgumentException("Either amount or data must be provided"))
        }
        return try {
            val normalizedKey = privateKeyHex.removePrefix("0x")
            val credentials = Credentials.create(normalizedKey)
            val nonce = getNonce(credentials.address)
            val rawTransaction = RawTransaction.createTransaction(
                nonce, params.gasPrice, params.gasLimit, normalizedTo, params.amount, params.data ?: ""
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
                            Asset(assetConfig.name, assetConfig.symbol, assetConfig.decimals, assetConfig.contractAddress, normalize(balance,assetConfig.decimals,network.networkType))
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
                        mutableListOf<Triple<String, AssetConfig, org.web3j.protocol.core.Request<*, *>>>()

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
                                        val balance = normalize(response.result.toString(),assetConfig.decimals,network.networkType)
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

    private suspend fun estimateGasLimit(
        from: String,
        to: String,
        value: BigInteger,
        data: String? = null,
        fallbackGasLimit: BigInteger = BigInteger("150000")
    ): BigInteger {
        if (from.isBlank() || to.isBlank()) return fallbackGasLimit

        return executeWithFailover { web3j ->
            val transaction = Transaction(
                from,
                null, // nonce
                null, // gasPrice
                null, // gasLimit
                to,
                value,
                data
            )
            try {
                val estimate = web3j.ethEstimateGas(transaction).sendAsync().await()
                if (estimate.hasError()) fallbackGasLimit
                else (estimate.amountUsed.toBigDecimal() * BigDecimal("1.2")).toBigInteger()
            } catch (e: Exception) { fallbackGasLimit }
        }
    }

    private suspend fun getPriorityFee(): BigInteger {
        return try {
            executeWithFailover { web3j ->
                web3j.ethMaxPriorityFeePerGas().sendAsync().await().maxPriorityFeePerGas
            }
        } catch (e: Exception) {
            BigInteger.valueOf(1_500_000_000L) // Safe fallback: 1.5 Gwei
        }
    }

    override suspend fun getFeeOptions(
        fromAddress: String?,
        toAddress: String?,
        asset: Asset?,
        amount: BigInteger?
    ): ResultResponse<List<FeeData>> {
        return try {
            val networkGasPrice = getGasPrice()
            val networkPriorityFee = getPriorityFee()
            val transferAmount = amount?.takeIf { it > BigInteger.ZERO } ?: BigInteger.ONE
            val recipientAddress = toAddress ?: ""

            val gasLimit: BigInteger = if (asset?.contractAddress == null) {
                estimateGasLimit(
                    from = fromAddress ?: "",
                    to = recipientAddress,
                    value = transferAmount,
                    fallbackGasLimit = BigInteger.valueOf(21_000L)
                )
            } else {
                val function = Function("transfer", listOf(Address(recipientAddress), Uint256(transferAmount)), emptyList())
                estimateGasLimit(
                    from = fromAddress ?: "",
                    to = asset.contractAddress!!,
                    value = BigInteger.ZERO,
                    data = FunctionEncoder.encode(function)
                )
            }
            // ضرایب منطقی برای لایه ۲ (باعث می‌شود قیمت خیلی فضایی نشود اما تایید تراکنش تضمین شود)
            val normalMaxFee = networkGasPrice.multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100))
                .add(networkPriorityFee)

            val fast = networkGasPrice.multiply(BigInteger.valueOf(150)).divide(BigInteger.valueOf(100))
                .add(networkPriorityFee.multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100)))

            val urgent = networkGasPrice.multiply(BigInteger.valueOf(200)).divide(BigInteger.valueOf(100))
                .add(networkPriorityFee.multiply(BigInteger.valueOf(2)))

// محاسبه هزینه L1 با ضریب اطمینان ۱۰ درصدی برای پوشش نوسانات لحظه‌ای
            val rawL1Fee = if (isL2StackOptimism()) {
                val txTarget = asset?.contractAddress ?: recipientAddress
                val txValue = if (asset?.contractAddress == null) transferAmount else BigInteger.ZERO
                val txData = if (asset?.contractAddress == null) "" else {
                    FunctionEncoder.encode(Function("transfer", listOf(Address(recipientAddress), Uint256(transferAmount)), emptyList()))
                }
                getL1DataFee(txTarget, txValue, txData)
            } else {
                BigInteger.ZERO
            }

// اعمال ضریب اطمینان ۱.۱ (۱۰ درصد اضافه) برای هزینه L1
            val l1DataFeeWithBuffer = rawL1Fee.toBigDecimal().multiply(BigDecimal("1.10")).toBigInteger()

            ResultResponse.Success(listOf(
                FeeData(
                    level = "عادی",
                    gasPrice = normalMaxFee,
                    gasLimit = gasLimit,
                    // هزینه کل = (L2 Gas * Limit) + L1 Data Fee
                    feeInSmallestUnit = (normalMaxFee * gasLimit + l1DataFeeWithBuffer).toBigDecimal(),
                    feeInCoin = normalize((normalMaxFee * gasLimit + l1DataFeeWithBuffer), network.decimals, network.networkType),
                    feeInUsd = null,
                    estimatedTime = "~ 30 ثانیه"
                ),
                FeeData(
                    level = "سریع",
                    gasPrice = fast,
                    gasLimit = gasLimit,
                    feeInSmallestUnit = (fast * gasLimit + l1DataFeeWithBuffer).toBigDecimal(),
                    feeInCoin = normalize((fast * gasLimit + l1DataFeeWithBuffer), network.decimals, network.networkType),
                    feeInUsd = null,
                    estimatedTime = "~ 15 ثانیه"
                ),
                FeeData(
                    level = "در لحظه",
                    gasPrice = urgent,
                    gasLimit = gasLimit,
                    feeInSmallestUnit = (urgent * gasLimit + l1DataFeeWithBuffer).toBigDecimal(),
                    feeInCoin = normalize((urgent * gasLimit + l1DataFeeWithBuffer), network.decimals, network.networkType),
                    feeInUsd = null,
                    estimatedTime = "< 10 ثانیه"
                ),
            ))
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    private fun isL2StackOptimism(): Boolean {
        return network.name == BASE || network.name == BASESEPOLIA
    }

    private suspend fun getL1DataFee(to: String, value: BigInteger, data: String): BigInteger {
        return executeWithFailover { web3j ->
            try {
                // Create a dummy raw transaction to get its length/RLP
                val dummyRawTx = RawTransaction.createTransaction(
                    BigInteger.ZERO, // nonce
                    BigInteger.valueOf(1_000_000_000L), // gasPrice
                    BigInteger.valueOf(21_000L), // gasLimit
                    to,
                    value,
                    data
                )
                val encodedTx = TransactionEncoder.encode(dummyRawTx)

                val function = Function(
                    "getL1CalldataGas",
                    listOf(DynamicBytes(encodedTx)),
                    listOf(object : TypeReference<Uint256>() {})
                )

                val encodedFunction = FunctionEncoder.encode(function)
                val response = web3j.ethCall(
                    Transaction.createEthCallTransaction(null, "0x420000000000000000000000000000000000000F", encodedFunction),
                    DefaultBlockParameterName.LATEST
                ).sendAsync().await()

                if (response.hasError()) {
                    Timber.w("L1 Fee Oracle Error: ${response.error.message}")
                    BigInteger.ZERO
                } else {
                    try {
                        val hexResult = response.result?.removePrefix("0x")
                        if (!hexResult.isNullOrBlank()) {
                            hexResult.toBigIntegerOrNull(16) ?: BigInteger.ZERO
                        } else {
                            BigInteger.ZERO
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Manual decode failed")
                        BigInteger.ZERO
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get L1 Data Fee")
                BigInteger.ZERO
            }
        }
    }


    private fun EVMTransactionDto.toDomainModel(userAddress: String, networkName: NetworkName): EvmTransaction {
        val feeValue = (this.gasUsed ?: BigInteger.ZERO) * (this.gasPrice ?: BigInteger.ZERO)
        val timestampValue = try { Instant.parse(this.timestamp).epochSecond } catch (e: Exception) { 0L }
        return EvmTransaction(
            hash = this.hash, 
            timestamp = timestampValue, 
            fee = feeValue, 
            status = if (this.status.equals("ok", true)) TransactionStatus.CONFIRMED else TransactionStatus.FAILED,
            fromAddress = this.from.hash, 
            toAddress = this.to?.hash ?: "Contract Creation", 
            amount = this.value,
            nonce = this.nonce,
            gasUsed = this.gasUsed,
            gasPrice = this.gasPrice,
            isOutgoing = this.from.hash.equals(userAddress, true),
            networkName = networkName
        )
    }

    private fun EVMTokenTransferDto.toDomainModel(userAddress: String, networkName: NetworkName): EvmTransaction {
        val timestampValue = try { Instant.parse(this.timestamp).epochSecond } catch (e: Exception) { 0L }
        return EvmTransaction(
            hash = this.txHash,
            timestamp = timestampValue,
            fee = BigInteger.ZERO,
            status = TransactionStatus.CONFIRMED,
            fromAddress = this.fromAddress.hash,
            toAddress = this.toAddress.hash,
            amount = this.total.value?.toBigIntegerOrNull() ?: BigInteger.ZERO,
            isOutgoing = this.fromAddress.hash.equals(userAddress, true),
            contractAddress = this.token.address,
            networkName = networkName,
            tokenTransferDetails = TokenTransferDetails(
                from = this.fromAddress.hash,
                to = this.toAddress.hash,
                amount = this.total.value?.toBigIntegerOrNull() ?: BigInteger.ZERO,
                tokenSymbol = this.token.symbol ,
                tokenDecimals = this.token.decimals?.toIntOrNull() ?: 18,
                contractAddress = this.token.address
            )
        )
    }
}
