package com.mtd.data.datasource

import com.mtd.core.assets.AssetConfig
import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.network.tron.TronUtils
import com.mtd.core.network.tron.TronUtils.Base58.decodeTronAddressToEthFormat
import com.mtd.core.registry.AssetRegistry
import com.mtd.data.datasource.IChainDataSource.FeeData
import com.mtd.data.repository.TransactionParams
import com.mtd.data.service.AccountRequest
import com.mtd.data.service.CreateTxRequest
import com.mtd.data.service.TriggerConstantRequest
import com.mtd.data.service.TronExplorerService
import com.mtd.data.service.TronNativeService
import com.mtd.data.utils.AssetNormalizer.normalize
import com.mtd.domain.model.Asset
import com.mtd.domain.model.EvmTransaction
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService
import retrofit2.Retrofit
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger

class TronDataSource(
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

    private fun getNextRpcUrl(): String {
        val index = currentRpcIndex.get() % network.RpcUrls.size
        return network.RpcUrls[index]
    }

    private suspend fun <T> executeWithFailover(block: suspend (Web3j) -> T): T {
        var lastException: Exception? = null
        val maxAttempts = network.RpcUrls.size.coerceAtLeast(1)

        for (i in 0 until maxAttempts) {
            try {
                val web3j = getOrUpdateWeb3j()
                return block(web3j)
            } catch (e: Exception) {
                Timber.e(e, "Error executing Web3j request on attempt ${i + 1}/$maxAttempts")
                lastException = e
                //rotateRpc()
            }
        }
        throw lastException ?: Exception("All RPCs failed")
    }

    override suspend fun getBalance(address: String): ResultResponse<BigDecimal> {
        return ResultResponse.Success(BigDecimal.ZERO)
    }

    override suspend fun getBalanceAssets(address: String): ResultResponse<List<Asset>> {
        return withContext(Dispatchers.IO) {
            try {
                val supportedAssets = assetRegistry.getAssetsForNetwork(network.id)
                if (supportedAssets.isEmpty()) return@withContext ResultResponse.Success(emptyList())
                val tronAddress=decodeTronAddressToEthFormat(address)
                val assetDeferreds = supportedAssets.map { assetConfig ->
                    async {
                        executeWithFailover { web3j ->
                            val balance = if (assetConfig.contractAddress == null) {
                                web3j.ethGetBalance(tronAddress, DefaultBlockParameterName.LATEST).sendAsync().await().balance.toBigDecimal().movePointLeft(assetConfig.decimals)
                            } else {
                                val function =
                                    Function("balanceOf", listOf(Address(tronAddress)), emptyList())
                                val response = web3j.ethCall(
                                    Transaction.createEthCallTransaction(tronAddress,  decodeTronAddressToEthFormat(assetConfig.contractAddress?:"") , org.web3j.abi.FunctionEncoder.encode(function)),
                                    DefaultBlockParameterName.LATEST
                                ).sendAsync().await()
                               response.result.toString()
                            }
                            Asset(assetConfig.name, assetConfig.symbol, assetConfig.decimals, assetConfig.contractAddress,  normalize (balance,assetConfig.decimals,network.name))
                        }
                    }
                }
                ResultResponse.Success(assetDeferreds.awaitAll())
            } catch (e: Exception) {
                ResultResponse.Error(e)
            }
        }
    }

    override suspend fun getTransactionHistory(address: String): ResultResponse<List<TransactionRecord>> {
        return try {
            val records = mutableListOf<TransactionRecord>()
            val api = retrofitBuilder.baseUrl(network.explorers[0]).build().create(TronExplorerService::class.java)
            // ۱. دریافت تراکنش‌های بومی (TRX)
            val normalTxs = api.getTrxHistory(address, limit = 20, start = 0) //TODO این سرویس فقط 20 تراکنش اخر و میده باید بهبود پیدا کنه
            normalTxs.data.forEach { tx ->
                records.add(
                    EvmTransaction(
                        hash = tx.hash,
                        timestamp = tx.timestamp,
                        fee = tx.cost.net_fee + tx.cost.energy_fee,
                        status = if (tx.confirmed && tx.result == "SUCCESS") TransactionStatus.CONFIRMED else TransactionStatus.FAILED,
                        fromAddress = tx.ownerAddress,
                        toAddress = tx.toAddress,
                        amount = tx.amount,
                        isOutgoing = tx.ownerAddress.equals(address, ignoreCase = true),
                        contractAddress = "" // برای TRX خالی است
                    )
                )
            }

            // ۲. دریافت تراکنش‌های توکن (TRC20 - مثل USDT)
            val tokenTxs = api.getTokenHistory(address, limit = 20, start = 0)
            tokenTxs.trc20_transfer.forEach { tx ->
                records.add(
                    EvmTransaction(
                        hash = tx.transaction_id,
                        timestamp = tx.block_ts,
                        fee = BigInteger.ZERO, // Tronscan در این لایه هزینه را به صورت مستقیم نمی‌دهد
                        status = TransactionStatus.CONFIRMED,
                        fromAddress = tx.from,
                        toAddress = tx.to,
                        amount = tx.value,
                        isOutgoing = tx.from.equals(address, ignoreCase = true),
                        contractAddress = tx.token_id
                    )
                )
            }

            // ۳. مرتب‌سازی بر اساس زمان (جدیدترین به قدیمی‌ترین)
            val sortedRecords = records.sortedByDescending { it.timestamp }

            ResultResponse.Success(sortedRecords)
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    } //TODO اینجا هزینه پرداختی هر تراکنش نیست باید https://apilist.tronscan.org/api/transaction-info?hash={transaction_id} رو استفاده کنیم وقتی جزئیات رو خواست ببینه

    override suspend fun sendTransaction(
        params: TransactionParams,
        privateKeyHex: String
    ): ResultResponse<String> {
        return ResultResponse.Success("")
    }

    override suspend fun getFeeOptions(
        fromAddress: String?,
        toAddress: String?,
        asset: Asset?
    ): ResultResponse<List<FeeData>> {
        return try {
            if (fromAddress == null || toAddress == null) throw Exception("Addresses required")
            val api = retrofitBuilder.baseUrl(network.RpcUrls[2]).build().create(TronNativeService::class.java)
            val params=  api.getChainParameters().chainParameter.associate { it.key to (it.value ?: 0L) }
            val energyFeeSun = params["getEnergyFee"] ?: 420L
            val bandwidthFeeSun = params["getTransactionFee"] ?: 1000L
            val createAccountFeeSun = params["getCreateNewAccountFeeInSystem"] ?: 1100000L
            var totalFeeInSun: Long

            if (asset?.contractAddress == null) {
                // --- محاسبه برای TRX ---
                // الف) محاسبه حجم تراکنش برای پهنای باند
                val tempTx = api.createTransaction(CreateTxRequest(fromAddress, toAddress, 1000000L))
                val txSize = (tempTx.raw_data_hex.length / 2) + 69 // حجم + امضا

                // ب) چک کردن نیاز به فعال‌سازی حساب مقصد
                val isNewAccount =  try {
                    val response = api.getAccount(AccountRequest(address = toAddress))
                    response.address == null
                } catch (e: Exception) {
                    e.message
                    true
                }
                val activationCost = if (isNewAccount) createAccountFeeSun else 0L

                totalFeeInSun = (txSize * bandwidthFeeSun) + activationCost
            } else {
                // --- محاسبه برای توکن (USDT) ---
                val amount = asset.balance.toBigInteger() ?: BigInteger.ONE
                val parameter = encodeTransferParams(toAddress, amount)
                // الف) تخمین انرژی مصرفی
                val request = TriggerConstantRequest(
                    owner_address = fromAddress,
                    contract_address = asset.contractAddress!!,
                    function_selector = "transfer(address,uint256)",
                    parameter = parameter,
                    visible = true
                )


                val energyUsed = api.triggerConstantContract(request).energy_used ?: 65000L

                // ب) تخمین پهنای باند (تراکنش‌های قرارداد هوشمند حدود ۳۵۰ واحد مصرف می‌کنند)
                val bandwidthUsage = 350L

                totalFeeInSun = (energyUsed * energyFeeSun) + (bandwidthUsage * bandwidthFeeSun)
            }

            // تبدیل به واحد TRX برای نمایش
            val feeInTrx = totalFeeInSun.toBigDecimal().divide(BigDecimal(1_000_000))

            ResultResponse.Success(listOf(
                FeeData(
                    level = "Standard",
                    feeInSmallestUnit = totalFeeInSun.toBigDecimal(),
                    feeInCoin = feeInTrx,
                    estimatedTime = " ~ 1 min"
                )
            ))
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }


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
                                    web3j.ethGetBalance(decodeTronAddressToEthFormat(address), DefaultBlockParameterName.LATEST)
                                allRequests.add(Triple(decodeTronAddressToEthFormat(address), assetConfig, request))
                            } else {
                                // ERC-20 Token
                                val function =
                                    Function("balanceOf", listOf(Address(decodeTronAddressToEthFormat(address))), emptyList())
                                val encodedFunction = org.web3j.abi.FunctionEncoder.encode(function)
                                val transaction = Transaction.createEthCallTransaction(
                                    decodeTronAddressToEthFormat(address),
                                    decodeTronAddressToEthFormat(assetConfig.contractAddress?:"") ,
                                    encodedFunction
                                )
                                val request =
                                    web3j.ethCall(transaction, DefaultBlockParameterName.LATEST)
                                allRequests.add(Triple(decodeTronAddressToEthFormat(address), assetConfig, request))
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
                                        val balance =  normalize (response.result.toString(),assetConfig.decimals,network.name)
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

    override fun getWeb3jInstance(): Web3j {
        TODO("Not yet implemented")
    }

    private fun encodeTransferParams(toAddress: String, amount: BigInteger): String {
        // تبدیل آدرس به Hex (بدون پیشوند 41) و پد کردن تا 64 کاراکتر
        val addressHex = TronUtils.toHex(toAddress).substring(2).padStart(64, '0')
        val amountHex = amount.toString(16).padStart(64, '0')
        return addressHex + amountHex
    }



}
